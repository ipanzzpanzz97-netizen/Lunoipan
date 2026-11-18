package com.example.domain

import com.example.data.model.CandlePoint
import com.example.data.model.DcaProjectionPoint
import com.example.data.model.DcaSummary
import com.example.data.model.OrderBookSnapshot
import com.example.data.model.PositionSizeResult
import com.example.data.model.QuantSignal
import com.example.data.model.SignalAction
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object QuantEngine {

  const val MIN_RISK_PCT = 1.0
  const val MAX_RISK_PCT = 2.0
  private const val RSI_PERIOD = 14
  private const val EMA_FAST = 50
  private const val EMA_SLOW = 200
  private const val LUNO_ESTIMATED_TAKER_FEE_RATE = 0.005

  /**
   * Computes Wilder's Smoothed RSI (14 periods) across a series of closing prices.
   * Returns a list aligned with [closes] (early indices before period are initialized smoothly).
   */
  fun computeRsiSeries(closes: List<Double>, period: Int = RSI_PERIOD): List<Double> {
    if (closes.size < 2) return List(closes.size) { 50.0 }
    val rsiOut = MutableList(closes.size) { 50.0 }

    val gains = DoubleArray(closes.size)
    val losses = DoubleArray(closes.size)
    for (i in 1 until closes.size) {
      val diff = closes[i] - closes[i - 1]
      if (diff >= 0.0) {
        gains[i] = diff
      } else {
        losses[i] = abs(diff)
      }
    }

    val effectivePeriod = min(period, closes.size - 1).coerceAtLeast(1)
    var avgGain = 0.0
    var avgLoss = 0.0
    for (i in 1..effectivePeriod) {
      avgGain += gains[i]
      avgLoss += losses[i]
    }
    avgGain /= effectivePeriod
    avgLoss /= effectivePeriod

    fun calcRsi(gain: Double, loss: Double): Double {
      if (loss == 0.0) return if (gain == 0.0) 50.0 else 100.0
      val rs = gain / loss
      return (100.0 - (100.0 / (1.0 + rs))).coerceIn(0.0, 100.0)
    }

    rsiOut[effectivePeriod] = calcRsi(avgGain, avgLoss)
    for (i in (effectivePeriod + 1) until closes.size) {
      avgGain = ((avgGain * (effectivePeriod - 1)) + gains[i]) / effectivePeriod
      avgLoss = ((avgLoss * (effectivePeriod - 1)) + losses[i]) / effectivePeriod
      rsiOut[i] = calcRsi(avgGain, avgLoss)
    }
    return rsiOut
  }

  /**
   * Computes Exponential Moving Average (EMA) series seeded with SMA of the initial window.
   */
  fun computeEmaSeries(closes: List<Double>, period: Int): List<Double> {
    if (closes.isEmpty()) return emptyList()
    val emaOut = MutableList(closes.size) { closes.first() }
    val seedLen = min(period, closes.size)
    val k = 2.0 / (period + 1.0)

    var runningSum = 0.0
    for (i in 0 until seedLen) {
      runningSum += closes[i]
      emaOut[i] = runningSum / (i + 1)
    }
    var prevEma = emaOut[seedLen - 1]
    for (i in seedLen until closes.size) {
      val current = closes[i] * k + prevEma * (1.0 - k)
      emaOut[i] = current
      prevEma = current
    }
    return emaOut
  }

  /**
   * Evaluates combined RSI(14), EMA(50/200 Golden Cross), and Luno orderbook depth into a
   * disciplined quantitative signal.
   */
  fun evaluateSignal(
    candles: List<CandlePoint>,
    livePrice: Double,
    orderBook: OrderBookSnapshot,
    spreadPct: Double,
    candleSource: String,
  ): QuantSignal {
    val closes =
      if (candles.isNotEmpty()) {
        candles.map { it.close }
      } else {
        listOf(livePrice)
      }
    val rsiSeries = computeRsiSeries(closes, RSI_PERIOD)
    val ema50Series = computeEmaSeries(closes, EMA_FAST)
    val ema200Series = computeEmaSeries(closes, EMA_SLOW)

    val currentRsi = rsiSeries.lastOrNull() ?: 50.0
    val currentEma50 = ema50Series.lastOrNull() ?: livePrice
    val currentEma200 = ema200Series.lastOrNull() ?: livePrice
    val isGoldenCross = currentEma50 > currentEma200

    val action =
      when {
        currentRsi < 30.0 && isGoldenCross -> SignalAction.STRONG_BUY
        currentRsi < 30.0 && !isGoldenCross -> SignalAction.SPECULATIVE_BUY
        currentRsi > 70.0 -> SignalAction.STRONG_SELL
        currentRsi in 30.0..45.0 && isGoldenCross && livePrice >= currentEma50 * 0.985 ->
          SignalAction.ACCUMULATE
        else -> SignalAction.HOLD
      }

    // Transparent Confluence Score (0..100)
    var score = 50
    score +=
      when {
        currentRsi < 25.0 -> 26
        currentRsi < 30.0 -> 22
        currentRsi in 30.0..42.0 -> 12
        currentRsi in 42.0..58.0 -> 2
        currentRsi in 58.0..70.0 -> -8
        else -> -25
      }
    score += if (isGoldenCross) 15 else -12
    score += if (livePrice > currentEma200) 6 else -6
    score +=
      when {
        orderBook.bidImbalanceRatio >= 0.60 -> 8
        orderBook.bidImbalanceRatio >= 0.52 -> 4
        orderBook.bidImbalanceRatio <= 0.40 -> -6
        else -> 0
      }
    if (spreadPct <= 0.35) score += 4 else if (spreadPct > 1.0) score -= 5

    return QuantSignal(
      rsi14 = currentRsi,
      rsiSeries = rsiSeries,
      ema50 = currentEma50,
      ema200 = currentEma200,
      ema50Series = ema50Series,
      ema200Series = ema200Series,
      isGoldenCross = isGoldenCross,
      action = action,
      confluenceScore = score.coerceIn(5, 98),
      candleSource = candleSource,
    )
  }

  /**
   * Institutional Position Sizing Engine:
   * Max_Position_Size = (Total_Capital * Risk_Tolerance_Percentage) / Stop_Loss_Percentage
   * Enforces strict 1%..2% risk tolerance cap to prevent catastrophic drawdown.
   */
  fun calculatePositionSize(
    totalCapital: Double,
    requestedRiskTolerancePct: Double,
    stopLossPct: Double,
    entryPrice: Double,
  ): PositionSizeResult {
    val safeCapital = max(totalCapital, 0.0)
    val enforcedRiskPct = requestedRiskTolerancePct.coerceIn(MIN_RISK_PCT, MAX_RISK_PCT)
    val safeStopLossPct = stopLossPct.coerceIn(0.5, 25.0)
    val safeEntry = max(entryPrice, 0.000001)

    val riskFraction = enforcedRiskPct / 100.0
    val stopFraction = safeStopLossPct / 100.0

    val riskCapital = safeCapital * riskFraction
    val theoreticalSize = riskCapital / stopFraction
    val spotCappedSize = min(theoreticalSize, safeCapital)
    val units = spotCappedSize / safeEntry

    val stopLossPrice = safeEntry * (1.0 - stopFraction)
    val takeProfit2R = safeEntry * (1.0 + 2.0 * stopFraction)
    val takeProfit3R = safeEntry * (1.0 + 3.0 * stopFraction)
    val fee = spotCappedSize * LUNO_ESTIMATED_TAKER_FEE_RATE

    return PositionSizeResult(
      totalCapital = safeCapital,
      riskTolerancePct = enforcedRiskPct,
      stopLossPct = safeStopLossPct,
      entryPrice = safeEntry,
      riskCapitalAmount = riskCapital,
      theoreticalPositionSize = theoreticalSize,
      spotCappedPositionSize = spotCappedSize,
      isSpotCapped = theoreticalSize > safeCapital,
      unitsToAcquire = units,
      stopLossPrice = stopLossPrice,
      takeProfit2RPrice = takeProfit2R,
      takeProfit3RPrice = takeProfit3R,
      estimatedFeeAmount = fee,
    )
  }

  /**
   * Projects monthly Dollar-Cost Averaging compounding returns over 3 to 36 months.
   */
  fun calculateDcaProjection(
    monthlyContribution: Double,
    durationMonths: Int,
    annualYieldPct: Double,
  ): DcaSummary {
    val months = durationMonths.coerceIn(3, 36)
    val contribution = max(monthlyContribution, 0.0)
    val monthlyRate = (annualYieldPct.coerceIn(0.0, 100.0) / 100.0) / 12.0

    val points = ArrayList<DcaProjectionPoint>(months + 1)
    var invested = 0.0
    var portfolio = 0.0
    points.add(DcaProjectionPoint(month = 0, totalInvested = 0.0, projectedValue = 0.0))

    for (m in 1..months) {
      invested += contribution
      portfolio = (portfolio + contribution) * (1.0 + monthlyRate)
      points.add(
        DcaProjectionPoint(
          month = m,
          totalInvested = invested,
          projectedValue = portfolio,
        )
      )
    }

    val totalGain = (portfolio - invested).coerceAtLeast(0.0)
    val returnPct = if (invested > 0.0) (totalGain / invested) * 100.0 else 0.0

    return DcaSummary(
      monthlyContribution = contribution,
      durationMonths = months,
      annualYieldPct = annualYieldPct,
      totalInvested = invested,
      finalValue = portfolio,
      totalGain = totalGain,
      returnPct = returnPct,
      points = points,
    )
  }

  /**
   * Evaluates 4 strict quantitative conditions on live Luno data to filter low-quality trades
   * and computes a Maker Limit-Order price + fee-adjusted breakeven level.
   */
  fun evaluateHighProbabilitySetup(
    rsi14: Double,
    isGoldenCross: Boolean,
    spreadPct: Double,
    bidImbalanceRatio: Double,
    bestBid: Double,
    lastPrice: Double,
  ): com.example.data.model.HighProbabilitySetup {
    val c1 =
      com.example.data.model.SetupCondition(
        title = "Macro Trend (EMA 50 > EMA 200)",
        detail = if (isGoldenCross) "Bullish Golden Cross active" else "Bearish cross — avoid aggressive longs",
        passed = isGoldenCross,
      )
    val c2 =
      com.example.data.model.SetupCondition(
        title = "Discount RSI Entry (RSI <= 42)",
        detail =
          when {
            rsi14 < 30.0 -> "Deep oversold (${String.format("%.1f", rsi14)}) — prime zone"
            rsi14 <= 42.0 -> "Healthy pullback (${String.format("%.1f", rsi14)})"
            else -> "RSI ${String.format("%.1f", rsi14)} — wait for pullback"
          },
        passed = rsi14 <= 42.0,
      )
    val c3 =
      com.example.data.model.SetupCondition(
        title = "Tight Luno Spread (<= 0.40%)",
        detail = "Current spread ${String.format("%.3f", spreadPct)}% (use Limit Order to save fees)",
        passed = spreadPct <= 0.40,
      )
    val bidPct = (bidImbalanceRatio * 100.0).toInt()
    val c4 =
      com.example.data.model.SetupCondition(
        title = "Orderbook Bid Support (>= 52%)",
        detail = "Buyers hold $bidPct% of top Luno book depth",
        passed = bidImbalanceRatio >= 0.52,
      )

    val conditions = listOf(c1, c2, c3, c4)
    val passed = conditions.count { it.passed }
    val limitEntry = if (bestBid > 0.0) bestBid else lastPrice
    // Round-trip Luno taker fee ~1.0% vs Maker limit ~0.5% total buffer
    val breakeven = limitEntry * 1.005

    val label =
      when (passed) {
        4 -> "A+ HIGH-PROBABILITY ENTRY"
        3 -> "GOOD SETUP (VERIFY STOP-LOSS)"
        2 -> "WAIT FOR MORE CONFLUENCE"
        else -> "NO TRADE — PROTECT CAPITAL"
      }

    return com.example.data.model.HighProbabilitySetup(
      conditions = conditions,
      passedCount = passed,
      totalCount = conditions.size,
      suggestedLimitPrice = limitEntry,
      feeAdjustedBreakeven = breakeven,
      readinessLabel = label,
    )
  }
}
