package com.example.domain

import com.example.data.model.AiTradeAdvice
import com.example.data.model.AiVerdictType
import com.example.data.model.MarketSnapshot
import com.example.data.model.PortfolioAsset
import com.example.data.model.TradePlanRecord
import com.example.data.model.TradePlanStatus
import com.example.data.model.TradingPair
import com.example.data.repository.LunoRepository
import com.example.notifications.DcaReminderScheduler
import com.example.security.SecureVault
import com.example.security.UserQuantPreferences
import com.example.ui.FormatUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.floor
import kotlin.math.pow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AutoTradeTickResult(
  val executedLiveOrder: Boolean = false,
  val logMessage: String? = null,
  val refreshedBalances: List<PortfolioAsset>? = null,
)

class AutoTradeCoordinator(
  private val secureVault: SecureVault,
  private val repository: LunoRepository,
  private val dcaScheduler: DcaReminderScheduler,
) {

  suspend fun evaluateTick(
    snapshot: MarketSnapshot,
    advice: AiTradeAdvice?,
    cachedAssets: List<PortfolioAsset>? = null,
  ): AutoTradeTickResult =
    tickMutex.withLock {
      val prefs = secureVault.preferencesFlow.first()
      val creds = secureVault.getCredentials()
      val currentPlans = secureVault.tradePlansFlow.first().toMutableList()
      val pair = snapshot.pair
      val pairCode = pair.pairCode

      // 1. Always maintain Paper Auto-Monitor when enabled (for simulated non-live plans)
      if (prefs.autoPilotEnabled) {
        evaluatePaperPlans(
          snapshot = snapshot,
          prefs = prefs,
          currentPlans = currentPlans,
          allowNewPaperEntry = !prefs.liveAutoTradeEnabled,
        )
      }

      // 2. Live Auto-Trade Luno Bot (opt-in only)
      if (!prefs.liveAutoTradeEnabled) {
        return@withLock AutoTradeTickResult()
      }

      if (!creds.isConfigured) {
        val msg = "Bot dijeda: Simpan Luno API Key (kebenaran Trade) dalam tab Vault dahulu."
        secureVault.setLiveAutoTradeEnabled(false, msg)
        return@withLock AutoTradeTickResult(logMessage = msg)
      }

      if (snapshot.lastPrice <= 0.0 || snapshot.bid <= 0.0 || snapshot.ask <= 0.0) {
        return@withLock AutoTradeTickResult(logMessage = "Menunggu data harga Luno yang sah...")
      }

      val nowMs = System.currentTimeMillis()
      val todayKey = currentDateKey(nowMs)
      val countToday = if (prefs.liveAutoTradeDateKey == todayKey) prefs.liveAutoTradeCountToday else 0
      if (countToday >= prefs.liveAutoTradeDailyCap) {
        val msg = "Had harian bot (${prefs.liveAutoTradeDailyCap} order/hari) telah dicapai. Bersambung esok."
        if (prefs.lastAutoTradeLog != msg) {
          secureVault.updateAutoTradeLog(msg)
        }
        return@withLock AutoTradeTickResult(logMessage = msg)
      }

      val elapsedMs = nowMs - prefs.lastLiveAutoTradeEpochMs
      if (prefs.lastLiveAutoTradeEpochMs > 0L && elapsedMs < COOLDOWN_MS) {
        val waitSec = ((COOLDOWN_MS - elapsedMs) / 1000L).coerceAtLeast(1L)
        val msg = "Cooldown keselamatan aktif (${waitSec}s lagi) sebelum semakan order seterusnya."
        return@withLock AutoTradeTickResult(logMessage = msg)
      }

      // Fetch live pending orders & live wallet balances to prevent duplicate/over-budget trades
      val pendingOrdersResult = repository.fetchPendingOrders(pair)
      if (pendingOrdersResult.isFailure) {
        val errText = pendingOrdersResult.exceptionOrNull()?.message.orEmpty()
        if (errText.contains("401") || errText.contains("403") || errText.contains("permission", ignoreCase = true)) {
          val msg = "Bot dijeda: API Key Luno tiada kebenaran Trade/ListOrders."
          secureVault.setLiveAutoTradeEnabled(false, msg)
          return@withLock AutoTradeTickResult(logMessage = msg)
        }
        val msg = "Gagal semak pesanan Luno: ${errText.take(80)}"
        secureVault.updateAutoTradeLog(msg)
        return@withLock AutoTradeTickResult(logMessage = msg)
      }

      val pendingOrders = pendingOrdersResult.getOrDefault(emptyList())
      if (pendingOrders.isNotEmpty()) {
        val firstOrder = pendingOrders.first()
        val msg =
          "Menunggu pesanan limit sedia ada di Luno (${firstOrder.type} ${firstOrder.limitVolume} ${pair.baseAsset} @ ${firstOrder.limitPrice}) dipadankan."
        if (prefs.lastAutoTradeLog != msg) {
          secureVault.updateAutoTradeLog(msg)
        }
        return@withLock AutoTradeTickResult(logMessage = msg)
      }

      val balancesResult = repository.fetchAuthenticatedBalances()
      val assets = balancesResult.getOrNull() ?: cachedAssets
      if (assets == null) {
        val msg = "Gagal membaca baki Luno terkini. Cuba lagi sebentar lagi."
        secureVault.updateAutoTradeLog(msg)
        return@withLock AutoTradeTickResult(logMessage = msg)
      }

      val availableQuote = assets.firstOrNull { it.asset == pair.quoteAsset }?.available ?: 0.0
      val availableBase = assets.firstOrNull { it.asset == pair.baseAsset }?.available ?: 0.0
      val openLiveIndex =
        currentPlans.indexOfFirst {
          it.isLiveOrder && it.status == TradePlanStatus.OPEN && it.pairCode == pairCode
        }

      // CASE A: Open live position exists -> monitor Take Profit, Stop Loss, or AI Overbought Sell
      if (openLiveIndex >= 0) {
        val openPlan = currentPlans[openLiveIndex]
        if (availableBase < pair.minOrderUnits) {
          val msg =
            "Posisi $pairCode direkodkan, menunggu baki ${pair.baseAsset} tersedia (baki: ${FormatUtils.formatNumber(availableBase, pair.volumeDecimals)})."
          if (prefs.lastAutoTradeLog != msg) {
            secureVault.updateAutoTradeLog(msg)
          }
          return@withLock AutoTradeTickResult(logMessage = msg, refreshedBalances = assets)
        }

        val hitTakeProfit = snapshot.lastPrice >= openPlan.takeProfitPrice
        val hitStopLoss = snapshot.lastPrice <= openPlan.stopLossPrice
        val hitAiSell =
          advice?.verdict == AiVerdictType.SELL &&
            snapshot.signal.rsi14 >= 68.0 &&
            snapshot.lastPrice > openPlan.entryPrice * 1.005

        if (!hitTakeProfit && !hitStopLoss && !hitAiSell) {
          val msg =
            "Memegang ${FormatUtils.formatNumber(availableBase, pair.volumeDecimals)} ${pair.baseAsset} • Menunggu TP ${FormatUtils.formatCurrency(openPlan.takeProfitPrice, pair.quoteSymbol, pair.priceDecimals)} atau SL ${FormatUtils.formatCurrency(openPlan.stopLossPrice, pair.quoteSymbol, pair.priceDecimals)}."
          if (prefs.lastAutoTradeLog != msg) {
            secureVault.updateAutoTradeLog(msg)
          }
          return@withLock AutoTradeTickResult(logMessage = msg, refreshedBalances = assets)
        }

        val sellVolume = computeSafeSellVolume(openPlan.units, availableBase, pair)
        if (sellVolume == null) {
          val msg = "Baki ${pair.baseAsset} bawah had minimum jualan Luno (${pair.minOrderUnits})."
          secureVault.updateAutoTradeLog(msg)
          return@withLock AutoTradeTickResult(logMessage = msg, refreshedBalances = assets)
        }

        val sellPrice = if (hitStopLoss) snapshot.bid else snapshot.ask
        val usePostOnly = !hitStopLoss
        val sellReason =
          when {
            hitTakeProfit -> "TAKE PROFIT"
            hitStopLoss -> "STOP-LOSS"
            else -> "AI OVERBOUGHT SELL"
          }

        val orderResult =
          repository.placeLunoLimitOrder(
            pair = pair,
            isBuy = false,
            volume = sellVolume,
            price = sellPrice,
            postOnly = usePostOnly,
          )
        return@withLock orderResult.fold(
          onSuccess = { orderId ->
            val isWin = sellPrice >= openPlan.entryPrice
            currentPlans[openLiveIndex] =
              openPlan.copy(
                status = if (isWin) TradePlanStatus.CLOSED_WIN else TradePlanStatus.CLOSED_LOSS,
                exitPrice = sellPrice,
                lunoOrderId = orderId.ifBlank { openPlan.lunoOrderId },
              )
            secureVault.saveTradePlans(currentPlans)
            val nextCount = countToday + 1
            val msg =
              "Auto-Bot JUAL ($sellReason) dihantar: $sellVolume ${pair.baseAsset} @ ${FormatUtils.formatCurrency(sellPrice, pair.quoteSymbol, pair.priceDecimals)} (Order: ${orderId.ifBlank { "OK" }})."
            secureVault.recordLiveAutoTradeExecution(todayKey, nextCount, nowMs, msg)
            dcaScheduler.sendAutoPilotSignalNotification(
              title = "Auto-Bot JUAL Luno ($pairCode)",
              message = msg,
            )
            AutoTradeTickResult(
              executedLiveOrder = true,
              logMessage = msg,
              refreshedBalances = repository.fetchAuthenticatedBalances().getOrNull() ?: assets,
            )
          },
          onFailure = { err ->
            val errMsg = err.message ?: "Gagal hantar pesanan JUAL automatik ke Luno."
            if (errMsg.contains("kebenaran 'Trade'", ignoreCase = true)) {
              secureVault.setLiveAutoTradeEnabled(false, "Bot dijeda: $errMsg")
            } else {
              secureVault.updateAutoTradeLog("Ralat Jual Bot: $errMsg")
            }
            AutoTradeTickResult(logMessage = errMsg, refreshedBalances = assets)
          },
        )
      }

      // CASE B: If user already holds enough base coin on Luno without an open plan (e.g., bought manually/earlier),
      // wait for AI SELL / RSI >= 70 instead of buying more blindly, OR evaluate BUY if holding < minOrderUnits.
      val setup =
        QuantEngine.evaluateHighProbabilitySetup(
          rsi14 = snapshot.signal.rsi14,
          isGoldenCross = snapshot.signal.isGoldenCross,
          spreadPct = snapshot.spreadPct,
          bidImbalanceRatio = snapshot.orderBook.bidImbalanceRatio,
          bestBid = snapshot.bid,
          lastPrice = snapshot.lastPrice,
        )

      val shouldBuy =
        (setup.passedCount >= 3 && snapshot.signal.rsi14 < 42.0) ||
          (advice?.verdict == AiVerdictType.BUY && advice.confidencePct >= 68 && setup.passedCount >= 2)

      if (!shouldBuy) {
        val msg =
          "Bot aktif memantau $pairCode (${setup.passedCount}/4 lulus, RSI ${String.format(Locale.US, "%.1f", snapshot.signal.rsi14)}) — menunggu zon beli A+."
        if (prefs.lastAutoTradeLog != msg) {
          secureVault.updateAutoTradeLog(msg)
        }
        return@withLock AutoTradeTickResult(logMessage = msg, refreshedBalances = assets)
      }

      val entryPrice = snapshot.bid
      val pos =
        QuantEngine.calculatePositionSize(
          totalCapital = prefs.totalCapitalMyr,
          requestedRiskTolerancePct = prefs.riskTolerancePct,
          stopLossPct = prefs.defaultStopLossPct,
          entryPrice = entryPrice,
        )
      val buyVolume =
        computeSafeLiveBuyVolume(
          availableQuote = availableQuote,
          maxOrderMyr = prefs.liveAutoTradeMaxOrderMyr,
          riskSizedMyr = pos.spotCappedPositionSize,
          limitPrice = entryPrice,
          pair = pair,
        )

      if (buyVolume == null) {
        val minCost = pair.minOrderUnits * entryPrice
        val msg =
          "Isyarat BELI ditemui, tetapi baki ${pair.quoteAsset} (${FormatUtils.formatNumber(availableQuote, 2)}) atau had order (${FormatUtils.formatNumber(prefs.liveAutoTradeMaxOrderMyr, 0)}) bawah had min ${pair.pairCode} (~${FormatUtils.formatCurrency(minCost, pair.quoteSymbol, 2)})."
        secureVault.updateAutoTradeLog(msg)
        return@withLock AutoTradeTickResult(logMessage = msg, refreshedBalances = assets)
      }

      val buyOrderResult =
        repository.placeLunoLimitOrder(
          pair = pair,
          isBuy = true,
          volume = buyVolume,
          price = entryPrice,
          postOnly = true,
        )

      return@withLock buyOrderResult.fold(
        onSuccess = { orderId ->
          val allocated = buyVolume * entryPrice
          val slPrice = entryPrice * (1.0 - prefs.defaultStopLossPct / 100.0)
          val tpPrice = entryPrice * (1.0 + 2.0 * (prefs.defaultStopLossPct / 100.0))
          val livePlan =
            TradePlanRecord(
              id = UUID.randomUUID().toString(),
              pairCode = pairCode,
              entryPrice = entryPrice,
              allocatedAmount = allocated,
              units = buyVolume,
              stopLossPrice = slPrice,
              takeProfitPrice = tpPrice,
              status = TradePlanStatus.OPEN,
              createdAtMs = nowMs,
              isLiveOrder = true,
              lunoOrderId = orderId,
            )
          currentPlans.add(0, livePlan)
          secureVault.saveTradePlans(currentPlans)
          val nextCount = countToday + 1
          val msg =
            "Auto-Bot BELI dihantar: $buyVolume ${pair.baseAsset} @ ${FormatUtils.formatCurrency(entryPrice, pair.quoteSymbol, pair.priceDecimals)} (~${FormatUtils.formatCurrency(allocated, pair.quoteSymbol, 2)}) [Order: ${orderId.ifBlank { "OK" }}]."
          secureVault.recordLiveAutoTradeExecution(todayKey, nextCount, nowMs, msg)
          dcaScheduler.sendAutoPilotSignalNotification(
            title = "Auto-Bot BELI Luno ($pairCode)",
            message = msg,
          )
          AutoTradeTickResult(
            executedLiveOrder = true,
            logMessage = msg,
            refreshedBalances = repository.fetchAuthenticatedBalances().getOrNull() ?: assets,
          )
        },
        onFailure = { err ->
          val errMsg = err.message ?: "Gagal hantar pesanan BELI automatik ke Luno."
          if (errMsg.contains("kebenaran 'Trade'", ignoreCase = true)) {
            secureVault.setLiveAutoTradeEnabled(false, "Bot dijeda: $errMsg")
          } else {
            secureVault.updateAutoTradeLog("Ralat Beli Bot: $errMsg")
          }
          AutoTradeTickResult(logMessage = errMsg, refreshedBalances = assets)
        },
      )
    }

  private suspend fun evaluatePaperPlans(
    snapshot: MarketSnapshot,
    prefs: UserQuantPreferences,
    currentPlans: MutableList<TradePlanRecord>,
    allowNewPaperEntry: Boolean,
  ) {
    val pairCode = snapshot.pair.pairCode
    var plansChanged = false

    for (i in currentPlans.indices) {
      val plan = currentPlans[i]
      if (!plan.isLiveOrder && plan.status == TradePlanStatus.OPEN && plan.pairCode == pairCode) {
        if (snapshot.lastPrice >= plan.takeProfitPrice) {
          currentPlans[i] =
            plan.copy(status = TradePlanStatus.CLOSED_WIN, exitPrice = snapshot.lastPrice)
          plansChanged = true
          dcaScheduler.sendAutoPilotSignalNotification(
            title = "Auto-Monitor TAKE PROFIT ($pairCode)",
            message =
              "Sasaran TP dicapai pada ${FormatUtils.formatCurrency(snapshot.lastPrice, snapshot.pair.quoteSymbol, snapshot.pair.priceDecimals)}! Keuntungan simulasi dikunci.",
          )
        } else if (snapshot.lastPrice <= plan.stopLossPrice) {
          currentPlans[i] =
            plan.copy(status = TradePlanStatus.CLOSED_LOSS, exitPrice = snapshot.lastPrice)
          plansChanged = true
          dcaScheduler.sendAutoPilotSignalNotification(
            title = "Auto-Monitor STOP-LOSS ($pairCode)",
            message =
              "Had risiko 1%–2% melindungi modal simulasi pada ${FormatUtils.formatCurrency(snapshot.lastPrice, snapshot.pair.quoteSymbol, snapshot.pair.priceDecimals)}.",
          )
        }
      }
    }

    if (allowNewPaperEntry) {
      val hasOpenForPair =
        currentPlans.any { !it.isLiveOrder && it.status == TradePlanStatus.OPEN && it.pairCode == pairCode }
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
            totalCapital = prefs.totalCapitalMyr,
            requestedRiskTolerancePct = prefs.riskTolerancePct,
            stopLossPct = prefs.defaultStopLossPct,
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
            isLiveOrder = false,
          )
        currentPlans.add(0, autoPlan)
        plansChanged = true
        dcaScheduler.sendAutoPilotSignalNotification(
          title = "Auto-Monitor BELI A+ ($pairCode)",
          message =
            "Isyarat RSI ${String.format(Locale.US, "%.1f", snapshot.signal.rsi14)} (${setup.passedCount}/4 lulus). Limit Bid: ${FormatUtils.formatCurrency(pos.entryPrice, snapshot.pair.quoteSymbol, snapshot.pair.priceDecimals)}.",
        )
      }
    }

    if (plansChanged) {
      secureVault.saveTradePlans(currentPlans)
    }
  }

  companion object {
    private val tickMutex = Mutex()
    const val COOLDOWN_MS = 5 * 60 * 1000L

    fun computeSafeLiveBuyVolume(
      availableQuote: Double,
      maxOrderMyr: Double,
      riskSizedMyr: Double,
      limitPrice: Double,
      pair: TradingPair,
    ): Double? {
      if (limitPrice <= 0.0 || availableQuote <= 0.0 || maxOrderMyr <= 0.0) return null
      val safeCapMyr = minOf(maxOrderMyr, availableQuote * 0.99)
      val minCostMyr = pair.minOrderUnits * limitPrice
      if (safeCapMyr < minCostMyr) return null

      val targetQuote =
        if (riskSizedMyr >= minCostMyr) {
          minOf(riskSizedMyr, safeCapMyr)
        } else {
          minOf(safeCapMyr, minCostMyr * 1.02)
        }
      val rawUnits = targetQuote / limitPrice
      val floored = floorToDecimals(rawUnits, pair.volumeDecimals)
      return if (floored >= pair.minOrderUnits && floored * limitPrice <= availableQuote * 0.995) {
        floored
      } else {
        null
      }
    }

    fun computeSafeSellVolume(
      plannedUnits: Double,
      availableBase: Double,
      pair: TradingPair,
    ): Double? {
      val raw = minOf(plannedUnits, availableBase)
      val floored = floorToDecimals(raw, pair.volumeDecimals)
      return if (floored >= pair.minOrderUnits) floored else null
    }

    fun floorToDecimals(value: Double, decimals: Int): Double {
      val factor = 10.0.pow(decimals.toDouble())
      return floor(value * factor + 1e-9) / factor
    }

    private fun currentDateKey(nowMs: Long): String =
      SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(nowMs))
  }
}
