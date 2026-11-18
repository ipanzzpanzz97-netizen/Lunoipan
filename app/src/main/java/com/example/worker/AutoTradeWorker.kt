package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.model.ChartTimeframe
import com.example.data.repository.LunoRepository
import com.example.domain.AutoTradeCoordinator
import com.example.domain.QuantEngine
import com.example.domain.TradingAiEngine
import com.example.notifications.DcaReminderScheduler
import com.example.security.SecureVault
import kotlinx.coroutines.flow.first

class AutoTradeWorker(
  appContext: Context,
  workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

  override suspend fun doWork(): Result {
    val secureVault = SecureVault(applicationContext)
    val prefs = secureVault.preferencesFlow.first()
    if (!prefs.autoPilotEnabled && !prefs.liveAutoTradeEnabled) {
      return Result.success()
    }

    val repository = LunoRepository(secureVault)
    val dcaScheduler = DcaReminderScheduler(applicationContext)
    val aiEngine = TradingAiEngine(applicationContext)
    val coordinator = AutoTradeCoordinator(secureVault, repository, dcaScheduler)

    val snapshotResult =
      repository.fetchMarketSnapshot(
        pair = prefs.selectedPair,
        timeframe = ChartTimeframe.H1,
        previousLastPrice = null,
      )

    val snapshot = snapshotResult.getOrNull() ?: return Result.retry()
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
        totalCapital = prefs.totalCapitalMyr,
        requestedRiskTolerancePct = prefs.riskTolerancePct,
        stopLossPct = prefs.defaultStopLossPct,
        entryPrice = snapshot.ask.takeIf { it > 0.0 } ?: snapshot.lastPrice,
      )

    val advice =
      aiEngine.analyzeMarket(
        snapshot = snapshot,
        setup = setup,
        positionSize = pos,
        isOfflineMode = false,
      )

    coordinator.evaluateTick(snapshot = snapshot, advice = advice)
    return Result.success()
  }
}
