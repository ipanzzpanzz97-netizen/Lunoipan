package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

enum class TradingPair(
  val pairCode: String,
  val baseAsset: String,
  val quoteAsset: String,
  val quoteSymbol: String,
  val displayName: String,
  val coinGeckoId: String,
  val coinGeckoVsCurrency: String,
  val priceDecimals: Int,
  val volumeDecimals: Int,
  val minOrderUnits: Double,
) {
  XBTMYR(
    pairCode = "XBTMYR",
    baseAsset = "XBT",
    quoteAsset = "MYR",
    quoteSymbol = "RM",
    displayName = "BTC / MYR",
    coinGeckoId = "bitcoin",
    coinGeckoVsCurrency = "myr",
    priceDecimals = 0,
    volumeDecimals = 4,
    minOrderUnits = 0.0002,
  ),
  ETHMYR(
    pairCode = "ETHMYR",
    baseAsset = "ETH",
    quoteAsset = "MYR",
    quoteSymbol = "RM",
    displayName = "ETH / MYR",
    coinGeckoId = "ethereum",
    coinGeckoVsCurrency = "myr",
    priceDecimals = 2,
    volumeDecimals = 4,
    minOrderUnits = 0.001,
  ),
  SOLMYR(
    pairCode = "SOLMYR",
    baseAsset = "SOL",
    quoteAsset = "MYR",
    quoteSymbol = "RM",
    displayName = "SOL / MYR",
    coinGeckoId = "solana",
    coinGeckoVsCurrency = "myr",
    priceDecimals = 2,
    volumeDecimals = 2,
    minOrderUnits = 0.01,
  ),
  XRPMYR(
    pairCode = "XRPMYR",
    baseAsset = "XRP",
    quoteAsset = "MYR",
    quoteSymbol = "RM",
    displayName = "XRP / MYR",
    coinGeckoId = "ripple",
    coinGeckoVsCurrency = "myr",
    priceDecimals = 4,
    volumeDecimals = 0,
    minOrderUnits = 1.0,
  ),
  LTCMYR(
    pairCode = "LTCMYR",
    baseAsset = "LTC",
    quoteAsset = "MYR",
    quoteSymbol = "RM",
    displayName = "LTC / MYR",
    coinGeckoId = "litecoin",
    coinGeckoVsCurrency = "myr",
    priceDecimals = 2,
    volumeDecimals = 2,
    minOrderUnits = 0.01,
  ),
  XBTUSDC(
    pairCode = "XBTUSDC",
    baseAsset = "XBT",
    quoteAsset = "USDC",
    quoteSymbol = "$",
    displayName = "BTC / USDC",
    coinGeckoId = "bitcoin",
    coinGeckoVsCurrency = "usd",
    priceDecimals = 2,
    volumeDecimals = 4,
    minOrderUnits = 0.0002,
  );

  companion object {
    fun fromCode(code: String): TradingPair =
      entries.firstOrNull { it.pairCode == code } ?: XBTMYR
  }
}

enum class ChartTimeframe(
  val label: String,
  val lunoDurationSeconds: Int,
  val coinGeckoDays: Int,
) {
  M15("15M", 900, 3),
  H1("1H", 3600, 12),
  H4("4H", 14400, 45),
  D1("1D", 86400, 220),
}

// --- Luno API DTOs ---

@JsonClass(generateAdapter = true)
data class LunoTickerResponse(
  val pair: String = "",
  val timestamp: Long = 0L,
  val bid: String = "0",
  val ask: String = "0",
  @Json(name = "last_trade") val lastTrade: String = "0",
  @Json(name = "rolling_24_hour_volume") val rolling24HourVolume: String = "0",
  val status: String = "ACTIVE",
)

@JsonClass(generateAdapter = true)
data class LunoOrderBookEntry(
  val price: String = "0",
  val volume: String = "0",
)

@JsonClass(generateAdapter = true)
data class LunoOrderBookTopResponse(
  val timestamp: Long = 0L,
  val bids: List<LunoOrderBookEntry> = emptyList(),
  val asks: List<LunoOrderBookEntry> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class LunoTradeDto(
  val sequence: Long = 0L,
  val timestamp: Long = 0L,
  val price: String = "0",
  val volume: String = "0",
  @Json(name = "is_buy") val isBuy: Boolean = true,
)

@JsonClass(generateAdapter = true)
data class LunoTradesResponse(
  val trades: List<LunoTradeDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class LunoCandleDto(
  val timestamp: Long = 0L,
  val open: String = "0",
  val close: String = "0",
  val high: String = "0",
  val low: String = "0",
  val volume: String = "0",
)

@JsonClass(generateAdapter = true)
data class LunoCandlesResponse(
  val pair: String = "",
  val duration: Int = 3600,
  val candles: List<LunoCandleDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class LunoBalanceEntry(
  @Json(name = "account_id") val accountId: String = "",
  val asset: String = "",
  val balance: String = "0",
  val reserved: String = "0",
  val unconfirmed: String = "0",
  val name: String? = null,
)

@JsonClass(generateAdapter = true)
data class LunoBalanceResponse(
  val balance: List<LunoBalanceEntry> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class CoinGeckoMarketChartResponse(
  val prices: List<List<Double>> = emptyList(),
)

// --- Domain Models ---

data class CandlePoint(
  val timestampMs: Long,
  val open: Double,
  val high: Double,
  val low: Double,
  val close: Double,
  val volume: Double,
)

data class TradePrint(
  val sequence: Long,
  val timestampMs: Long,
  val price: Double,
  val volume: Double,
  val isBuy: Boolean,
)

data class OrderBookLevel(
  val price: Double,
  val volume: Double,
)

data class OrderBookSnapshot(
  val bids: List<OrderBookLevel>,
  val asks: List<OrderBookLevel>,
  val bidVolumeTotal: Double,
  val askVolumeTotal: Double,
  val bidImbalanceRatio: Double,
)

enum class SignalAction(val label: String, val subtitle: String) {
  STRONG_BUY("STRONG BUY", "Oversold RSI < 30 aligned with bullish macro EMA"),
  SPECULATIVE_BUY("COUNTER BUY", "Oversold RSI < 30 below EMA 200 — cap risk at 1.0%"),
  ACCUMULATE("DCA ZONE", "Pullback inside bullish EMA 50 > EMA 200 trend"),
  HOLD("HOLD / WAIT", "Neutral momentum — wait for high-probability zone"),
  STRONG_SELL("TAKE PROFIT", "Overbought RSI > 70 — lock gains or tighten stop"),
}

data class QuantSignal(
  val rsi14: Double,
  val rsiSeries: List<Double>,
  val ema50: Double,
  val ema200: Double,
  val ema50Series: List<Double>,
  val ema200Series: List<Double>,
  val isGoldenCross: Boolean,
  val action: SignalAction,
  val confluenceScore: Int,
  val candleSource: String,
)

data class MarketSnapshot(
  val pair: TradingPair,
  val timeframe: ChartTimeframe,
  val lastPrice: Double,
  val previousPrice: Double?,
  val bid: Double,
  val ask: Double,
  val spreadPct: Double,
  val volume24h: Double,
  val change24hPct: Double,
  val status: String,
  val timestampMs: Long,
  val candles: List<CandlePoint>,
  val orderBook: OrderBookSnapshot,
  val recentTrades: List<TradePrint>,
  val signal: QuantSignal,
)

data class PositionSizeResult(
  val totalCapital: Double,
  val riskTolerancePct: Double,
  val stopLossPct: Double,
  val entryPrice: Double,
  val riskCapitalAmount: Double,
  val theoreticalPositionSize: Double,
  val spotCappedPositionSize: Double,
  val isSpotCapped: Boolean,
  val unitsToAcquire: Double,
  val stopLossPrice: Double,
  val takeProfit2RPrice: Double,
  val takeProfit3RPrice: Double,
  val estimatedFeeAmount: Double,
)

data class DcaProjectionPoint(
  val month: Int,
  val totalInvested: Double,
  val projectedValue: Double,
)

data class DcaSummary(
  val monthlyContribution: Double,
  val durationMonths: Int,
  val annualYieldPct: Double,
  val totalInvested: Double,
  val finalValue: Double,
  val totalGain: Double,
  val returnPct: Double,
  val points: List<DcaProjectionPoint>,
)

data class PortfolioAsset(
  val accountId: String,
  val asset: String,
  val available: Double,
  val reserved: Double,
  val unconfirmed: Double,
  val label: String?,
)

enum class DcaFrequency(val label: String, val intervalDays: Long) {
  DAILY("Daily", 1L),
  WEEKLY("Weekly", 7L),
  BIWEEKLY("Bi-Weekly", 14L),
  MONTHLY("Monthly", 30L),
}

enum class TradePlanStatus {
  OPEN,
  CLOSED_WIN,
  CLOSED_LOSS,
}

@JsonClass(generateAdapter = true)
data class TradePlanRecord(
  val id: String,
  val pairCode: String,
  val entryPrice: Double,
  val allocatedAmount: Double,
  val units: Double,
  val stopLossPrice: Double,
  val takeProfitPrice: Double,
  val status: TradePlanStatus = TradePlanStatus.OPEN,
  val exitPrice: Double? = null,
  val createdAtMs: Long = System.currentTimeMillis(),
)

data class SetupCondition(
  val title: String,
  val detail: String,
  val passed: Boolean,
)

data class HighProbabilitySetup(
  val conditions: List<SetupCondition>,
  val passedCount: Int,
  val totalCount: Int,
  val suggestedLimitPrice: Double,
  val feeAdjustedBreakeven: Double,
  val readinessLabel: String,
)

@JsonClass(generateAdapter = true)
data class LunoPostOrderResponse(
  @Json(name = "order_id") val orderId: String = "",
)

@JsonClass(generateAdapter = true)
data class CachedCandleDto(
  val timestampMs: Long,
  val close: Double,
)

@JsonClass(generateAdapter = true)
data class CachedSnapshotData(
  val pairCode: String,
  val lastPrice: Double,
  val bid: Double,
  val ask: Double,
  val spreadPct: Double,
  val volume24h: Double,
  val change24hPct: Double,
  val bidImbalanceRatio: Double,
  val candles: List<CachedCandleDto>,
  val savedAtMs: Long,
)

enum class AiVerdictType {
  BUY,
  SELL,
  HOLD,
}

enum class AiEngineSource(val label: String) {
  GEMINI_ONLINE("GEMINI ONLINE AI"),
  OFFLINE_QUANT("OFFLINE QUANT AI"),
}

data class AiTradeAdvice(
  val verdict: AiVerdictType,
  val confidencePct: Int,
  val headline: String,
  val reasoning: String,
  val entryLimitPrice: Double,
  val stopLossPrice: Double,
  val takeProfitPrice: Double,
  val actionSteps: List<String>,
  val source: AiEngineSource,
  val generatedAtMs: Long = System.currentTimeMillis(),
  val onlineErrorNote: String? = null,
)
