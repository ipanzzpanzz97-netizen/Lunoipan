package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AiEngineSource
import com.example.data.model.AiTradeAdvice
import com.example.data.model.ChartTimeframe
import com.example.data.model.DcaFrequency
import com.example.data.model.DcaSummary
import com.example.data.model.HighProbabilitySetup
import com.example.data.model.MarketSnapshot
import com.example.data.model.PortfolioAsset
import com.example.data.model.PositionSizeResult
import com.example.data.model.TradePlanRecord
import com.example.data.model.TradePlanStatus
import com.example.data.model.TradingPair
import com.example.data.repository.LunoRepository
import com.example.domain.QuantEngine
import com.example.domain.TradingAiEngine
import com.example.notifications.DcaReminderScheduler
import com.example.security.SecureVault
import com.example.security.UserQuantPreferences
import com.example.security.VaultCredentials
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface MarketUiState {
  data object Loading : MarketUiState
  data class Success(val snapshot: MarketSnapshot) : MarketUiState
  data class Error(val message: String, val cachedSnapshot: MarketSnapshot? = null) : MarketUiState
}

sealed interface PortfolioUiState {
  data object Unconfigured : PortfolioUiState
  data object Loading : PortfolioUiState
  data class Success(val assets: List<PortfolioAsset>, val lastSyncedMs: Long) : PortfolioUiState
  data class Error(val message: String) : PortfolioUiState
}

data class QuantPulseUiState(
  val selectedPair: TradingPair = TradingPair.XBTMYR,
  val selectedTimeframe: ChartTimeframe = ChartTimeframe.H1,
  val marketState: MarketUiState = MarketUiState.Loading,
  val isRefreshing: Boolean = false,
  val isLiveStreamEnabled: Boolean = true,
  val preferences: UserQuantPreferences = UserQuantPreferences(),
  val credentials: VaultCredentials = VaultCredentials("", ""),
  val portfolioState: PortfolioUiState = PortfolioUiState.Unconfigured,
  val showPositionCalculator: Boolean = false,
  val calcTotalCapital: Double = 10000.0,
  val calcRiskPct: Double = 1.5,
  val calcStopLossPct: Double = 3.0,
  val calcEntryPriceOverride: Double? = null,
  val dcaSummary: DcaSummary = QuantEngine.calculateDcaProjection(500.0, 12, 18.0),
  val tradePlans: List<TradePlanRecord> = emptyList(),
  val aiAdvice: AiTradeAdvice? = null,
  val isAiLoading: Boolean = false,
  val preferOfflineAi: Boolean = false,
  val isOfflineDataMode: Boolean = false,
  val aiChatQuestion: String? = null,
  val aiChatAnswer: String? = null,
  val aiChatSource: AiEngineSource? = null,
  val isAiChatStreaming: Boolean = false,
  val isOrderSubmitting: Boolean = false,
  val statusBannerMessage: String? = null,
) {
  val activeSnapshot: MarketSnapshot?
    get() =
      when (marketState) {
        is MarketUiState.Success -> marketState.snapshot
        is MarketUiState.Error -> marketState.cachedSnapshot
        MarketUiState.Loading -> null
      }

  val setupChecklist: HighProbabilitySetup?
    get() {
      val snap = activeSnapshot ?: return null
      return QuantEngine.evaluateHighProbabilitySetup(
        rsi14 = snap.signal.rsi14,
        isGoldenCross = snap.signal.isGoldenCross,
        spreadPct = snap.spreadPct,
        bidImbalanceRatio = snap.orderBook.bidImbalanceRatio,
        bestBid = snap.bid,
        lastPrice = snap.lastPrice,
      )
    }

  val realizedAndUnrealizedRoiPct: Double
    get() {
      val baseCap = preferences.totalCapitalMyr.coerceAtLeast(1.0)
      val snap = activeSnapshot
      val totalPnl =
        tradePlans.sumOf { plan ->
          val exitPx =
            when (plan.status) {
              TradePlanStatus.OPEN ->
                if (snap != null && snap.pair.pairCode == plan.pairCode) snap.lastPrice else plan.entryPrice
              TradePlanStatus.CLOSED_WIN, TradePlanStatus.CLOSED_LOSS ->
                plan.exitPrice ?: plan.entryPrice
            }
          (exitPx - plan.entryPrice) * plan.units
        }
      return (totalPnl / baseCap) * 100.0
    }

  val winRatePct: Double?
    get() {
      val closed = tradePlans.filter { it.status != TradePlanStatus.OPEN }
      if (closed.isEmpty()) return null
      val wins = closed.count { it.status == TradePlanStatus.CLOSED_WIN }
      return (wins.toDouble() / closed.size.toDouble()) * 100.0
    }

  val positionSizeResult: PositionSizeResult
    get() {
      val livePrice = activeSnapshot?.lastPrice ?: 0.0
      val entry = calcEntryPriceOverride ?: livePrice.takeIf { it > 0.0 } ?: 1.0
      return QuantEngine.calculatePositionSize(
        totalCapital = calcTotalCapital,
        requestedRiskTolerancePct = calcRiskPct,
        stopLossPct = calcStopLossPct,
        entryPrice = entry,
      )
    }
}

class QuantPulseViewModel(application: Application) : AndroidViewModel(application) {

  private val secureVault = SecureVault(application)
  private val repository = LunoRepository(secureVault)
  private val dcaScheduler = DcaReminderScheduler(application)
  private val tradingAiEngine = TradingAiEngine()

  private val _uiState = MutableStateFlow(QuantPulseUiState())
  val uiState: StateFlow<QuantPulseUiState> = _uiState.asStateFlow()

  private var pollingJob: Job? = null
  private var aiJob: Job? = null
  private var aiChatJob: Job? = null
  private var preferencesInitialized = false

  init {
    observeVaultData()
    startLivePolling()
  }

  private fun observeVaultData() {
    viewModelScope.launch {
      secureVault.preferencesFlow.collect { prefs ->
        val dcaSummary =
          QuantEngine.calculateDcaProjection(
            monthlyContribution = prefs.dcaMonthlyAmount,
            durationMonths = prefs.dcaDurationMonths,
            annualYieldPct = prefs.dcaAnnualYieldPct,
          )
        _uiState.update { state ->
          if (!preferencesInitialized) {
            preferencesInitialized = true
            state.copy(
              selectedPair = prefs.selectedPair,
              preferences = prefs,
              calcTotalCapital = prefs.totalCapitalMyr,
              calcRiskPct = prefs.riskTolerancePct,
              calcStopLossPct = prefs.defaultStopLossPct,
              dcaSummary = dcaSummary,
            )
          } else {
            state.copy(
              preferences = prefs,
              dcaSummary = dcaSummary,
            )
          }
        }
      }
    }

    viewModelScope.launch {
      secureVault.tradePlansFlow.collect { plans ->
        _uiState.update { it.copy(tradePlans = plans) }
      }
    }

    viewModelScope.launch {
      secureVault.credentialsFlow.collect { creds ->
        _uiState.update { state ->
          state.copy(
            credentials = creds,
            portfolioState =
              if (creds.isConfigured) state.portfolioState else PortfolioUiState.Unconfigured,
          )
        }
        if (creds.isConfigured) {
          refreshPortfolioBalance()
        }
      }
    }
  }

  private fun startLivePolling() {
    pollingJob?.cancel()
    pollingJob =
      viewModelScope.launch {
        while (isActive) {
          if (_uiState.value.isLiveStreamEnabled) {
            fetchMarketData(showSpinner = _uiState.value.activeSnapshot == null)
          }
          delay(15_000L)
        }
      }
  }

  fun selectPair(pair: TradingPair) {
    if (_uiState.value.selectedPair == pair) return
    _uiState.update {
      it.copy(
        selectedPair = pair,
        calcEntryPriceOverride = null,
        marketState = MarketUiState.Loading,
      )
    }
    viewModelScope.launch {
      secureVault.updateSelectedPair(pair)
      fetchMarketData(showSpinner = true)
    }
  }

  fun selectTimeframe(timeframe: ChartTimeframe) {
    if (_uiState.value.selectedTimeframe == timeframe) return
    _uiState.update { it.copy(selectedTimeframe = timeframe, isRefreshing = true) }
    viewModelScope.launch { fetchMarketData(showSpinner = false) }
  }

  fun toggleLiveStream() {
    val next = !_uiState.value.isLiveStreamEnabled
    _uiState.update { it.copy(isLiveStreamEnabled = next) }
    if (next) {
      refreshNow()
    }
  }

  fun refreshNow() {
    viewModelScope.launch { fetchMarketData(showSpinner = false) }
  }

  private suspend fun fetchMarketData(showSpinner: Boolean) {
    val current = _uiState.value
    val prevPrice = current.activeSnapshot?.lastPrice
    _uiState.update {
      it.copy(
        isRefreshing = !showSpinner,
        marketState = if (showSpinner) MarketUiState.Loading else it.marketState,
      )
    }

    val result =
      repository.fetchMarketSnapshot(
        pair = current.selectedPair,
        timeframe = current.selectedTimeframe,
        previousLastPrice = prevPrice,
      )

    result
      .onSuccess { snapshot ->
        val hadNoAdvice = _uiState.value.aiAdvice == null || _uiState.value.selectedPair != snapshot.pair
        _uiState.update {
          it.copy(
            isRefreshing = false,
            isOfflineDataMode = false,
            marketState = MarketUiState.Success(snapshot),
          )
        }
        evaluateOfflineBaselineAndMaybeOnlineAi(snapshot, triggerOnline = hadNoAdvice)
        runAutoPilotEvaluation(snapshot)
      }
      .onFailure { error ->
        val offlineCached =
          current.activeSnapshot ?: repository.loadOfflineCachedSnapshot(
            pair = current.selectedPair,
            timeframe = current.selectedTimeframe,
          )
        _uiState.update {
          it.copy(
            isRefreshing = false,
            isOfflineDataMode = offlineCached != null,
            marketState =
              MarketUiState.Error(
                message = error.message ?: "Tiada internet — menggunakan mod Offline AI.",
                cachedSnapshot = offlineCached,
              ),
          )
        }
        if (offlineCached != null) {
          evaluateOfflineBaselineAndMaybeOnlineAi(offlineCached, triggerOnline = false)
        }
      }
  }

  private fun evaluateOfflineBaselineAndMaybeOnlineAi(
    snapshot: MarketSnapshot,
    triggerOnline: Boolean,
  ) {
    val state = _uiState.value
    val setup =
      QuantEngine.evaluateHighProbabilitySetup(
        rsi14 = snapshot.signal.rsi14,
        isGoldenCross = snapshot.signal.isGoldenCross,
        spreadPct = snapshot.spreadPct,
        bidImbalanceRatio = snapshot.orderBook.bidImbalanceRatio,
        bestBid = snapshot.bid,
        lastPrice = snapshot.lastPrice,
      )
    val pos =
      QuantEngine.calculatePositionSize(
        totalCapital = state.preferences.totalCapitalMyr,
        requestedRiskTolerancePct = state.preferences.riskTolerancePct,
        stopLossPct = state.preferences.defaultStopLossPct,
        entryPrice = snapshot.ask.takeIf { it > 0.0 } ?: snapshot.lastPrice,
      )

    if (state.aiAdvice == null || state.isOfflineDataMode || state.preferOfflineAi) {
      val offlineAdvice =
        tradingAiEngine.generateOfflineAdvice(
          snapshot = snapshot,
          setup = setup,
          positionSize = pos,
          offlineNote =
            if (state.isOfflineDataMode) "Mod Offline Aktif — Analisis daripada data cache tempatan."
            else null,
        )
      _uiState.update { it.copy(aiAdvice = offlineAdvice) }
    }

    if (triggerOnline && !state.preferOfflineAi && !state.isOfflineDataMode) {
      requestAiAnalysis()
    }
  }

  fun requestAiAnalysis() {
    val state = _uiState.value
    val snapshot = state.activeSnapshot ?: return
    val setup = state.setupChecklist ?: return
    val pos =
      QuantEngine.calculatePositionSize(
        totalCapital = state.preferences.totalCapitalMyr,
        requestedRiskTolerancePct = state.preferences.riskTolerancePct,
        stopLossPct = state.preferences.defaultStopLossPct,
        entryPrice = snapshot.ask.takeIf { it > 0.0 } ?: snapshot.lastPrice,
      )

    aiJob?.cancel()
    aiJob =
      viewModelScope.launch {
        _uiState.update { it.copy(isAiLoading = true) }
        val advice =
          tradingAiEngine.analyzeMarket(
            snapshot = snapshot,
            setup = setup,
            positionSize = pos,
            isOfflineMode = state.preferOfflineAi || state.isOfflineDataMode,
          )
        _uiState.update { it.copy(isAiLoading = false, aiAdvice = advice) }
      }
  }

  fun togglePreferOfflineAi() {
    val next = !_uiState.value.preferOfflineAi
    _uiState.update { it.copy(preferOfflineAi = next) }
    requestAiAnalysis()
  }

  fun askAiQuestion(question: String) {
    val clean = question.trim()
    if (clean.isBlank()) return
    val state = _uiState.value
    val snapshot = state.activeSnapshot ?: return
    val setup = state.setupChecklist ?: return
    val pos =
      QuantEngine.calculatePositionSize(
        totalCapital = state.preferences.totalCapitalMyr,
        requestedRiskTolerancePct = state.preferences.riskTolerancePct,
        stopLossPct = state.preferences.defaultStopLossPct,
        entryPrice = snapshot.ask.takeIf { it > 0.0 } ?: snapshot.lastPrice,
      )

    aiChatJob?.cancel()
    aiChatJob =
      viewModelScope.launch {
        _uiState.update {
          it.copy(
            aiChatQuestion = clean,
            aiChatAnswer = "",
            aiChatSource = null,
            isAiChatStreaming = true,
          )
        }
        val (finalText, source) =
          tradingAiEngine.answerTradingQuestion(
            question = clean,
            snapshot = snapshot,
            setup = setup,
            positionSize = pos,
            isOfflineMode = state.preferOfflineAi || state.isOfflineDataMode,
            onStreamingChunk = { partial ->
              _uiState.update { it.copy(aiChatAnswer = partial) }
            },
          )
        _uiState.update {
          it.copy(
            aiChatAnswer = finalText,
            aiChatSource = source,
            isAiChatStreaming = false,
          )
        }
      }
  }

  fun toggleAutoPilot(forceState: Boolean? = null) {
    val next = forceState ?: !_uiState.value.preferences.autoPilotEnabled
    viewModelScope.launch {
      secureVault.setAutoPilotEnabled(next)
      _uiState.update {
        it.copy(
          statusBannerMessage =
            if (next) "Auto-Monitor AI diaktifkan (pemantauan & Paper Auto-Pilot)."
            else "Auto-Monitor AI dijeda.",
        )
      }
    }
  }

  fun showStatusMessage(message: String) {
    _uiState.update { it.copy(statusBannerMessage = message) }
  }

  private fun runAutoPilotEvaluation(snapshot: MarketSnapshot) {
    val state = _uiState.value
    if (!state.preferences.autoPilotEnabled) return

    val pairCode = snapshot.pair.pairCode
    var plansChanged = false
    val currentPlans = state.tradePlans.toMutableList()

    // 1. Monitor open trades for automatic TP / SL hits on live Luno ticks
    for (i in currentPlans.indices) {
      val plan = currentPlans[i]
      if (plan.status == TradePlanStatus.OPEN && plan.pairCode == pairCode) {
        if (snapshot.lastPrice >= plan.takeProfitPrice) {
          currentPlans[i] =
            plan.copy(status = TradePlanStatus.CLOSED_WIN, exitPrice = snapshot.lastPrice)
          plansChanged = true
          dcaScheduler.sendAutoPilotSignalNotification(
            title = "Auto-Pilot TAKE PROFIT ($pairCode)",
            message =
              "Sasaran TP dicapai pada ${FormatUtils.formatCurrency(snapshot.lastPrice, snapshot.pair.quoteSymbol, snapshot.pair.priceDecimals)}! Keuntungan dikunci.",
          )
        } else if (snapshot.lastPrice <= plan.stopLossPrice) {
          currentPlans[i] =
            plan.copy(status = TradePlanStatus.CLOSED_LOSS, exitPrice = snapshot.lastPrice)
          plansChanged = true
          dcaScheduler.sendAutoPilotSignalNotification(
            title = "Auto-Pilot STOP-LOSS ($pairCode)",
            message =
              "Had risiko 1%–2% melindungi modal pada ${FormatUtils.formatCurrency(snapshot.lastPrice, snapshot.pair.quoteSymbol, snapshot.pair.priceDecimals)}.",
          )
        }
      }
    }

    // 2. Auto-detect high-probability oversold entry if no open position exists for this pair
    val hasOpenForPair =
      currentPlans.any { it.status == TradePlanStatus.OPEN && it.pairCode == pairCode }
    val setup =
      QuantEngine.evaluateHighProbabilitySetup(
        rsi14 = snapshot.signal.rsi14,
        isGoldenCross = snapshot.signal.isGoldenCross,
        spreadPct = snapshot.spreadPct,
        bidImbalanceRatio = snapshot.orderBook.bidImbalanceRatio,
        bestBid = snapshot.bid,
        lastPrice = snapshot.lastPrice,
      )
    if (!hasOpenForPair && setup.passedCount >= 3 && snapshot.signal.rsi14 < 38.0) {
      val pos =
        QuantEngine.calculatePositionSize(
          totalCapital = state.preferences.totalCapitalMyr,
          requestedRiskTolerancePct = state.preferences.riskTolerancePct,
          stopLossPct = state.preferences.defaultStopLossPct,
          entryPrice = setup.suggestedLimitPrice,
        )
      val autoPlan =
        TradePlanRecord(
          id = UUID.randomUUID().toString(),
          pairCode = pairCode,
          entryPrice = pos.entryPrice,
          allocatedAmount = pos.spotCappedPositionSize,
          units = pos.unitsToAcquire,
          stopLossPrice = pos.stopLossPrice,
          takeProfitPrice = pos.takeProfit2RPrice,
          status = TradePlanStatus.OPEN,
          createdAtMs = System.currentTimeMillis(),
        )
      currentPlans.add(0, autoPlan)
      plansChanged = true
      dcaScheduler.sendAutoPilotSignalNotification(
        title = "Auto-Monitor BELI A+ ($pairCode)",
        message =
          "Isyarat RSI ${String.format("%.1f", snapshot.signal.rsi14)} (${setup.passedCount}/4 lulus). Limit Bid: ${FormatUtils.formatCurrency(pos.entryPrice, snapshot.pair.quoteSymbol, snapshot.pair.priceDecimals)}.",
      )
    }

    if (plansChanged) {
      viewModelScope.launch { secureVault.saveTradePlans(currentPlans) }
    }
  }

  fun executeConfirmedLunoOrder(isBuy: Boolean) {
    val state = _uiState.value
    val snapshot = state.activeSnapshot ?: return
    val price = if (isBuy) snapshot.bid else snapshot.ask
    val pos =
      QuantEngine.calculatePositionSize(
        totalCapital = state.preferences.totalCapitalMyr,
        requestedRiskTolerancePct = state.preferences.riskTolerancePct,
        stopLossPct = state.preferences.defaultStopLossPct,
        entryPrice = price,
      )

    viewModelScope.launch {
      _uiState.update { it.copy(isOrderSubmitting = true) }
      repository
        .placeLunoLimitOrder(
          pair = snapshot.pair,
          isBuy = isBuy,
          volume = pos.unitsToAcquire,
          price = price,
        )
        .onSuccess { orderId ->
          val newPlan =
            TradePlanRecord(
              id = UUID.randomUUID().toString(),
              pairCode = snapshot.pair.pairCode,
              entryPrice = price,
              allocatedAmount = pos.spotCappedPositionSize,
              units = pos.unitsToAcquire,
              stopLossPrice = pos.stopLossPrice,
              takeProfitPrice = pos.takeProfit2RPrice,
              status = TradePlanStatus.OPEN,
              createdAtMs = System.currentTimeMillis(),
            )
          secureVault.saveTradePlans(listOf(newPlan) + _uiState.value.tradePlans)
          _uiState.update {
            it.copy(
              isOrderSubmitting = false,
              statusBannerMessage =
                "Pesanan Limit Luno dihantar (ID: ${orderId.ifBlank { "OK" }}).",
            )
          }
          refreshPortfolioBalance()
        }
        .onFailure { err ->
          _uiState.update {
            it.copy(
              isOrderSubmitting = false,
              statusBannerMessage = err.message ?: "Gagal menghantar pesanan ke Luno.",
            )
          }
        }
    }
  }

  fun openPositionCalculator() {
    _uiState.update { state ->
      state.copy(
        showPositionCalculator = true,
        calcTotalCapital = state.preferences.totalCapitalMyr,
        calcRiskPct = state.preferences.riskTolerancePct,
        calcStopLossPct = state.preferences.defaultStopLossPct,
        calcEntryPriceOverride = state.activeSnapshot?.ask?.takeIf { it > 0.0 },
      )
    }
  }

  fun closePositionCalculator() {
    _uiState.update { it.copy(showPositionCalculator = false) }
  }

  fun updateCalculatorInputs(
    totalCapital: Double? = null,
    riskPct: Double? = null,
    stopLossPct: Double? = null,
    entryPrice: Double? = null,
  ) {
    _uiState.update { state ->
      state.copy(
        calcTotalCapital = totalCapital ?: state.calcTotalCapital,
        calcRiskPct = (riskPct ?: state.calcRiskPct).coerceIn(QuantEngine.MIN_RISK_PCT, QuantEngine.MAX_RISK_PCT),
        calcStopLossPct = (stopLossPct ?: state.calcStopLossPct).coerceIn(0.5, 25.0),
        calcEntryPriceOverride = entryPrice ?: state.calcEntryPriceOverride,
      )
    }
  }

  fun saveRiskPreferences(totalCapital: Double, riskPct: Double, stopLossPct: Double) {
    viewModelScope.launch {
      secureVault.updateRiskSettings(totalCapital, riskPct, stopLossPct)
      _uiState.update {
        it.copy(
          calcTotalCapital = totalCapital,
          calcRiskPct = riskPct.coerceIn(QuantEngine.MIN_RISK_PCT, QuantEngine.MAX_RISK_PCT),
          calcStopLossPct = stopLossPct.coerceIn(0.5, 25.0),
          statusBannerMessage = "Risk rules locked (max ${String.format("%.1f", riskPct.coerceIn(1.0, 2.0))}% per trade).",
        )
      }
    }
  }

  fun updateDcaPlanner(
    monthlyAmount: Double? = null,
    durationMonths: Int? = null,
    annualYieldPct: Double? = null,
    reminderEnabled: Boolean? = null,
    frequency: DcaFrequency? = null,
  ) {
    val prefs = _uiState.value.preferences
    val nextAmount = (monthlyAmount ?: prefs.dcaMonthlyAmount).coerceAtLeast(10.0)
    val nextMonths = (durationMonths ?: prefs.dcaDurationMonths).coerceIn(3, 36)
    val nextYield = (annualYieldPct ?: prefs.dcaAnnualYieldPct).coerceIn(1.0, 60.0)
    val nextReminder = reminderEnabled ?: prefs.dcaReminderEnabled
    val nextFreq = frequency ?: prefs.dcaFrequency

    viewModelScope.launch {
      secureVault.updateDcaSettings(
        monthlyAmount = nextAmount,
        durationMonths = nextMonths,
        annualYieldPct = nextYield,
        reminderEnabled = nextReminder,
        frequency = nextFreq,
      )
      val formattedAmt =
        FormatUtils.formatCurrency(
          nextAmount,
          _uiState.value.selectedPair.quoteSymbol,
          0,
        )
      if (nextReminder) {
        dcaScheduler.schedulePeriodicReminder(_uiState.value.selectedPair, formattedAmt, nextFreq)
      } else {
        dcaScheduler.cancelReminder()
      }
    }
  }

  fun sendTestDcaAlert() {
    val state = _uiState.value
    val formattedAmt =
      FormatUtils.formatCurrency(
        state.preferences.dcaMonthlyAmount,
        state.selectedPair.quoteSymbol,
        0,
      )
    dcaScheduler.sendImmediateTestReminder(state.selectedPair, formattedAmt)
    _uiState.update {
      it.copy(statusBannerMessage = "Test DCA notification dispatched.")
    }
  }

  fun saveLunoCredentials(apiKeyId: String, apiKeySecret: String) {
    viewModelScope.launch {
      secureVault.saveCredentials(apiKeyId, apiKeySecret)
      _uiState.update {
        it.copy(statusBannerMessage = "API credentials encrypted with Android Keystore.")
      }
      refreshPortfolioBalance()
    }
  }

  fun clearLunoCredentials() {
    viewModelScope.launch {
      secureVault.clearCredentials()
      _uiState.update {
        it.copy(
          portfolioState = PortfolioUiState.Unconfigured,
          statusBannerMessage = "Encrypted API credentials purged from device.",
        )
      }
    }
  }

  fun refreshPortfolioBalance() {
    viewModelScope.launch {
      if (!_uiState.value.credentials.isConfigured) {
        _uiState.update { it.copy(portfolioState = PortfolioUiState.Unconfigured) }
        return@launch
      }
      _uiState.update { it.copy(portfolioState = PortfolioUiState.Loading) }
      repository
        .fetchAuthenticatedBalances()
        .onSuccess { assets ->
          _uiState.update {
            it.copy(
              portfolioState = PortfolioUiState.Success(assets, System.currentTimeMillis()),
            )
          }
        }
        .onFailure { err ->
          _uiState.update {
            it.copy(
              portfolioState =
                PortfolioUiState.Error(
                  err.message ?: "Luno authentication failed. Check Read-Only API Key & Secret.",
                ),
            )
          }
        }
    }
  }

  fun logCurrentTradePlan() {
    val state = _uiState.value
    val result = state.positionSizeResult
    val newPlan =
      TradePlanRecord(
        id = UUID.randomUUID().toString(),
        pairCode = state.selectedPair.pairCode,
        entryPrice = result.entryPrice,
        allocatedAmount = result.spotCappedPositionSize,
        units = result.unitsToAcquire,
        stopLossPrice = result.stopLossPrice,
        takeProfitPrice = result.takeProfit2RPrice,
        status = TradePlanStatus.OPEN,
        createdAtMs = System.currentTimeMillis(),
      )
    val updated = listOf(newPlan) + state.tradePlans
    viewModelScope.launch {
      secureVault.saveTradePlans(updated)
      _uiState.update {
        it.copy(
          showPositionCalculator = false,
          statusBannerMessage = "Trade plan logged to +${state.preferences.profitGoalPct.toInt()}% tracker.",
        )
      }
    }
  }

  fun closeTradePlan(planId: String, exitPrice: Double) {
    val current = _uiState.value.tradePlans
    val updated =
      current.map { plan ->
        if (plan.id == planId) {
          val isWin = exitPrice >= plan.entryPrice
          plan.copy(
            status = if (isWin) TradePlanStatus.CLOSED_WIN else TradePlanStatus.CLOSED_LOSS,
            exitPrice = exitPrice,
          )
        } else {
          plan
        }
      }
    viewModelScope.launch {
      secureVault.saveTradePlans(updated)
      _uiState.update { it.copy(statusBannerMessage = "Trade closed & ROI tracker updated.") }
    }
  }

  fun deleteTradePlan(planId: String) {
    val updated = _uiState.value.tradePlans.filterNot { it.id == planId }
    viewModelScope.launch { secureVault.saveTradePlans(updated) }
  }

  fun clearStatusBanner() {
    _uiState.update { it.copy(statusBannerMessage = null) }
  }
}
