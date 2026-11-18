package com.example

import com.example.data.model.CandlePoint
import com.example.data.model.OrderBookSnapshot
import com.example.data.model.SignalAction
import com.example.domain.QuantEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

  @Test
  fun `position sizing strictly enforces 1 to 2 percent risk cap`() {
    val overLimit =
      QuantEngine.calculatePositionSize(
        totalCapital = 10_000.0,
        requestedRiskTolerancePct = 15.0,
        stopLossPct = 5.0,
        entryPrice = 400_000.0,
      )
    assertEquals(2.0, overLimit.riskTolerancePct, 0.0001)
    assertEquals(200.0, overLimit.riskCapitalAmount, 0.01)
    assertEquals(4_000.0, overLimit.spotCappedPositionSize, 0.01)

    val underLimit =
      QuantEngine.calculatePositionSize(
        totalCapital = 10_000.0,
        requestedRiskTolerancePct = 0.1,
        stopLossPct = 5.0,
        entryPrice = 400_000.0,
      )
    assertEquals(1.0, underLimit.riskTolerancePct, 0.0001)
    assertEquals(100.0, underLimit.riskCapitalAmount, 0.01)
    assertEquals(2_000.0, underLimit.spotCappedPositionSize, 0.01)
  }

  @Test
  fun `wilder rsi detects oversold and overbought regimes`() {
    val falling = (0..30).map { 100.0 - it * 2.0 }
    val oversoldRsi = QuantEngine.computeRsiSeries(falling).last()
    assertTrue("Expected RSI < 30 for sustained decline, got $oversoldRsi", oversoldRsi < 30.0)

    val rising = (0..30).map { 100.0 + it * 2.5 }
    val overboughtRsi = QuantEngine.computeRsiSeries(rising).last()
    assertTrue("Expected RSI > 70 for sustained rally, got $overboughtRsi", overboughtRsi > 70.0)
  }

  @Test
  fun `golden cross plus oversold pullback triggers STRONG_BUY`() {
    // Long uptrend establishing EMA50 > EMA200, followed by sharp short-term oversold pullback
    val prices = buildList {
      for (i in 0 until 210) add(100.0 + i * 0.8)
      for (j in 1..15) add(268.0 - j * 3.5)
    }
    val candles =
      prices.mapIndexed { idx, p ->
        CandlePoint(idx * 3600_000L, p, p, p, p, 1.0)
      }
    val signal =
      QuantEngine.evaluateSignal(
        candles = candles,
        livePrice = prices.last(),
        orderBook = OrderBookSnapshot(emptyList(), emptyList(), 5.0, 2.0, 0.71),
        spreadPct = 0.2,
        candleSource = "UnitTest",
      )
    assertTrue(signal.isGoldenCross)
    assertTrue(signal.rsi14 < 30.0)
    assertEquals(SignalAction.STRONG_BUY, signal.action)
  }

  @Test
  fun `dca projection compounds over 3 to 36 months`() {
    val summary = QuantEngine.calculateDcaProjection(500.0, 12, 12.0)
    assertEquals(6_000.0, summary.totalInvested, 0.01)
    assertTrue(summary.finalValue > summary.totalInvested)
    assertEquals(13, summary.points.size)
  }

  @Test
  fun `high probability setup requires all four conditions for A+ readiness`() {
    val setup =
      QuantEngine.evaluateHighProbabilitySetup(
        rsi14 = 28.5,
        isGoldenCross = true,
        spreadPct = 0.25,
        bidImbalanceRatio = 0.60,
        bestBid = 400_000.0,
        lastPrice = 401_000.0,
      )
    assertEquals(4, setup.passedCount)
    assertEquals(400_000.0, setup.suggestedLimitPrice, 0.01)
    assertEquals(402_000.0, setup.feeAdjustedBreakeven, 0.01)
    assertEquals("A+ HIGH-PROBABILITY ENTRY", setup.readinessLabel)
  }

  @Test
  fun `offline AI engine generates BUY on oversold Golden Cross and SELL on overbought RSI`() {
    val engine = com.example.domain.TradingAiEngine()

    val bullishSetup =
      QuantEngine.evaluateHighProbabilitySetup(
        rsi14 = 27.5,
        isGoldenCross = true,
        spreadPct = 0.20,
        bidImbalanceRatio = 0.65,
        bestBid = 400_000.0,
        lastPrice = 400_500.0,
      )
    val bullCandles =
      (0 until 210).map { i ->
        val p = if (i < 195) 350_000.0 + i * 300.0 else 408_500.0 - (i - 195) * 600.0
        CandlePoint(i * 3600_000L, p, p, p, p, 2.0)
      }
    val bullSignal =
      QuantEngine.evaluateSignal(
        candles = bullCandles,
        livePrice = bullCandles.last().close,
        orderBook = OrderBookSnapshot(emptyList(), emptyList(), 6.5, 3.5, 0.65),
        spreadPct = 0.20,
        candleSource = "UnitTest",
      )
    val bullSnapshot =
      com.example.data.model.MarketSnapshot(
        pair = com.example.data.model.TradingPair.XBTMYR,
        timeframe = com.example.data.model.ChartTimeframe.H1,
        lastPrice = 400_500.0,
        previousPrice = 400_200.0,
        bid = 400_000.0,
        ask = 400_800.0,
        spreadPct = 0.20,
        volume24h = 120.0,
        change24hPct = -2.1,
        status = "ACTIVE",
        timestampMs = 1L,
        candles = bullCandles,
        orderBook = OrderBookSnapshot(emptyList(), emptyList(), 6.5, 3.5, 0.65),
        recentTrades = emptyList(),
        signal = bullSignal,
      )
    val pos = QuantEngine.calculatePositionSize(10_000.0, 1.5, 3.0, 400_000.0)
    val buyAdvice = engine.generateOfflineAdvice(bullSnapshot, bullishSetup, pos)
    assertEquals(com.example.data.model.AiVerdictType.BUY, buyAdvice.verdict)
    assertEquals(com.example.data.model.AiEngineSource.OFFLINE_QUANT, buyAdvice.source)

    val bearCandles =
      (0..40).map { i ->
        val p = 350_000.0 + i * 1_500.0
        CandlePoint(i * 3600_000L, p, p, p, p, 2.0)
      }
    val bearSignal =
      QuantEngine.evaluateSignal(
        candles = bearCandles,
        livePrice = bearCandles.last().close,
        orderBook = OrderBookSnapshot(emptyList(), emptyList(), 3.0, 7.0, 0.30),
        spreadPct = 0.20,
        candleSource = "UnitTest",
      )
    val bearSnapshot = bullSnapshot.copy(candles = bearCandles, signal = bearSignal, lastPrice = bearCandles.last().close)
    val bearSetup =
      QuantEngine.evaluateHighProbabilitySetup(
        rsi14 = bearSignal.rsi14,
        isGoldenCross = bearSignal.isGoldenCross,
        spreadPct = 0.20,
        bidImbalanceRatio = 0.30,
        bestBid = bearSnapshot.bid,
        lastPrice = bearSnapshot.lastPrice,
      )
    val sellAdvice = engine.generateOfflineAdvice(bearSnapshot, bearSetup, pos)
    assertEquals(com.example.data.model.AiVerdictType.SELL, sellAdvice.verdict)

    val slAnswer = engine.generateOfflineChatAnswer("Di mana SL & TP?", bullSnapshot, bullishSetup, pos)
    assertTrue(slAnswer.contains("Stop-Loss"))
    assertTrue(slAnswer.contains("Take-Profit"))

    val buyAnswer = engine.generateOfflineChatAnswer("Sesuai beli sekarang?", bullSnapshot, bullishSetup, pos)
    assertTrue(buyAnswer.contains("RSI(14)"))
  }
}
