package com.example.data.repository

import com.example.data.model.CachedCandleDto
import com.example.data.model.CachedSnapshotData
import com.example.data.model.CandlePoint
import com.example.data.model.ChartTimeframe
import com.example.data.model.MarketSnapshot
import com.example.data.model.OrderBookLevel
import com.example.data.model.OrderBookSnapshot
import com.example.data.model.PortfolioAsset
import com.example.data.model.TradePrint
import com.example.data.model.TradingPair
import com.example.data.remote.CoinGeckoApiService
import com.example.data.remote.HmacSigner
import com.example.data.remote.LunoApiService
import com.example.data.remote.NetworkModule
import com.example.domain.QuantEngine
import com.example.security.SecureVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

class LunoRepository(
  private val secureVault: SecureVault,
  private val lunoApi: LunoApiService = NetworkModule.lunoApiService,
  private val coinGeckoApi: CoinGeckoApiService = NetworkModule.coinGeckoApiService,
) {

  suspend fun fetchMarketSnapshot(
    pair: TradingPair,
    timeframe: ChartTimeframe,
    previousLastPrice: Double?,
  ): Result<MarketSnapshot> =
    withContext(Dispatchers.IO) {
      runCatching {
        coroutineScope {
          val tickerDeferred = async { lunoApi.getTicker(pair.pairCode) }
          val orderBookDeferred = async { lunoApi.getOrderBookTop(pair.pairCode) }
          val tradesDeferred = async {
            runCatching { lunoApi.getTrades(pair.pairCode) }.getOrNull()
          }
          val credentials = secureVault.getCredentials()

          val candlesDeferred = async {
            fetchHistoricalCandles(
              pair = pair,
              timeframe = timeframe,
              apiKeyId = credentials.apiKeyId,
              apiKeySecret = credentials.apiKeySecret,
            )
          }

          val ticker = tickerDeferred.await()
          val orderBookRaw = orderBookDeferred.await()
          val tradesRaw = tradesDeferred.await()
          val (candlesRaw, candleSource) = candlesDeferred.await()

          val lastPrice = ticker.lastTrade.toDoubleOrNull() ?: 0.0
          val bid = ticker.bid.toDoubleOrNull() ?: lastPrice
          val ask = ticker.ask.toDoubleOrNull() ?: lastPrice
          val mid = if (bid > 0.0 && ask > 0.0) (bid + ask) / 2.0 else lastPrice
          val spreadPct = if (mid > 0.0) ((ask - bid).coerceAtLeast(0.0) / mid) * 100.0 else 0.0
          val volume24h = ticker.rolling24HourVolume.toDoubleOrNull() ?: 0.0

          val bids =
            orderBookRaw.bids.take(10).mapNotNull {
              val p = it.price.toDoubleOrNull()
              val v = it.volume.toDoubleOrNull()
              if (p != null && v != null) OrderBookLevel(p, v) else null
            }
          val asks =
            orderBookRaw.asks.take(10).mapNotNull {
              val p = it.price.toDoubleOrNull()
              val v = it.volume.toDoubleOrNull()
              if (p != null && v != null) OrderBookLevel(p, v) else null
            }
          val bidVol = bids.sumOf { it.volume }
          val askVol = asks.sumOf { it.volume }
          val imbalance =
            if (bidVol + askVol > 0.0) bidVol / (bidVol + askVol) else 0.5

          val orderBook =
            OrderBookSnapshot(
              bids = bids,
              asks = asks,
              bidVolumeTotal = bidVol,
              askVolumeTotal = askVol,
              bidImbalanceRatio = imbalance,
            )

          val recentTrades =
            tradesRaw
              ?.trades
              ?.take(25)
              ?.mapNotNull { t ->
                val p = t.price.toDoubleOrNull()
                val v = t.volume.toDoubleOrNull()
                if (p != null && v != null) {
                  TradePrint(
                    sequence = t.sequence,
                    timestampMs = t.timestamp,
                    price = p,
                    volume = v,
                    isBuy = t.isBuy,
                  )
                } else {
                  null
                }
              }
              .orEmpty()

          // Stitch live Luno ticker price onto the latest candle so indicators reflect real-time Luno price
          val stitchedCandles =
            stitchLiveTickerIntoCandles(
              candles = candlesRaw,
              livePrice = lastPrice,
              timestampMs = ticker.timestamp.takeIf { it > 0L } ?: System.currentTimeMillis(),
            )

          val price24hAgo = findPrice24hAgo(stitchedCandles, ticker.timestamp)
          val change24hPct =
            if (price24hAgo != null && price24hAgo > 0.0) {
              ((lastPrice - price24hAgo) / price24hAgo) * 100.0
            } else {
              0.0
            }

          val signal =
            QuantEngine.evaluateSignal(
              candles = stitchedCandles,
              livePrice = lastPrice,
              orderBook = orderBook,
              spreadPct = spreadPct,
              candleSource = candleSource,
            )

          val snapshot =
            MarketSnapshot(
              pair = pair,
              timeframe = timeframe,
              lastPrice = lastPrice,
              previousPrice = previousLastPrice,
              bid = bid,
              ask = ask,
              spreadPct = spreadPct,
              volume24h = volume24h,
              change24hPct = change24hPct,
              status = ticker.status,
              timestampMs = ticker.timestamp.takeIf { it > 0L } ?: System.currentTimeMillis(),
              candles = stitchedCandles,
              orderBook = orderBook,
              recentTrades = recentTrades,
              signal = signal,
            )

          // Persist compact snapshot for instant offline AI/Quant fallback
          secureVault.saveCachedSnapshot(
            CachedSnapshotData(
              pairCode = pair.pairCode,
              lastPrice = snapshot.lastPrice,
              bid = snapshot.bid,
              ask = snapshot.ask,
              spreadPct = snapshot.spreadPct,
              volume24h = snapshot.volume24h,
              change24hPct = snapshot.change24hPct,
              bidImbalanceRatio = orderBook.bidImbalanceRatio,
              candles =
                stitchedCandles.takeLast(220).map { c ->
                  CachedCandleDto(c.timestampMs, c.close)
                },
              savedAtMs = System.currentTimeMillis(),
            )
          )

          snapshot
        }
      }
    }

  suspend fun loadOfflineCachedSnapshot(
    pair: TradingPair,
    timeframe: ChartTimeframe,
  ): MarketSnapshot? =
    withContext(Dispatchers.IO) {
      val cached = secureVault.getCachedSnapshot(pair.pairCode) ?: return@withContext null
      val candles =
        cached.candles.map {
          CandlePoint(it.timestampMs, it.close, it.close, it.close, it.close, 0.0)
        }
      val orderBook =
        OrderBookSnapshot(
          bids = listOf(OrderBookLevel(cached.bid, 1.0)),
          asks = listOf(OrderBookLevel(cached.ask, 1.0)),
          bidVolumeTotal = cached.bidImbalanceRatio,
          askVolumeTotal = (1.0 - cached.bidImbalanceRatio).coerceAtLeast(0.01),
          bidImbalanceRatio = cached.bidImbalanceRatio,
        )
      val signal =
        QuantEngine.evaluateSignal(
          candles = candles,
          livePrice = cached.lastPrice,
          orderBook = orderBook,
          spreadPct = cached.spreadPct,
          candleSource = "Offline Cache",
        )
      MarketSnapshot(
        pair = pair,
        timeframe = timeframe,
        lastPrice = cached.lastPrice,
        previousPrice = null,
        bid = cached.bid,
        ask = cached.ask,
        spreadPct = cached.spreadPct,
        volume24h = cached.volume24h,
        change24hPct = cached.change24hPct,
        status = "OFFLINE CACHE",
        timestampMs = cached.savedAtMs,
        candles = candles,
        orderBook = orderBook,
        recentTrades = emptyList(),
        signal = signal,
      )
    }

  suspend fun placeLunoLimitOrder(
    pair: TradingPair,
    isBuy: Boolean,
    volume: Double,
    price: Double,
  ): Result<String> =
    withContext(Dispatchers.IO) {
      runCatching {
        val creds = secureVault.getCredentials()
        require(creds.isConfigured) {
          "Simpan Luno API Key (kebenaran Trade) di Vault dahulu untuk hantar pesanan ke Luno."
        }
        val headers =
          HmacSigner.signRequest(
            apiKeyId = creds.apiKeyId,
            apiKeySecret = creds.apiKeySecret,
            method = "POST",
            requestPath = "/api/1/postorder",
          )
        val formattedVol = String.format(java.util.Locale.US, "%.6f", volume.coerceAtLeast(0.0001))
        val formattedPrice =
          String.format(java.util.Locale.US, "%.${pair.priceDecimals}f", price)
        val response =
          lunoApi.postLimitOrder(
            authorization = headers.authorizationBasic,
            timestamp = headers.timestampMs,
            signature = headers.hmacSha256Signature,
            pair = pair.pairCode,
            type = if (isBuy) "BID" else "ASK",
            volume = formattedVol,
            price = formattedPrice,
            postOnly = true,
          )
        response.orderId
      }
    }

  suspend fun fetchAuthenticatedBalances(): Result<List<PortfolioAsset>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val creds = secureVault.getCredentials()
        require(creds.isConfigured) {
          "Luno API Key ID and Secret must be saved in the Vault first."
        }
        val headers =
          HmacSigner.signRequest(
            apiKeyId = creds.apiKeyId,
            apiKeySecret = creds.apiKeySecret,
            method = "GET",
            requestPath = "/api/1/balance",
          )
        val response =
          lunoApi.getBalance(
            authorization = headers.authorizationBasic,
            timestamp = headers.timestampMs,
            signature = headers.hmacSha256Signature,
          )
        response.balance
          .map { entry ->
            PortfolioAsset(
              accountId = entry.accountId,
              asset = entry.asset,
              available = entry.balance.toDoubleOrNull() ?: 0.0,
              reserved = entry.reserved.toDoubleOrNull() ?: 0.0,
              unconfirmed = entry.unconfirmed.toDoubleOrNull() ?: 0.0,
              label = entry.name,
            )
          }
          .sortedByDescending { it.available + it.reserved }
      }
    }

  private suspend fun fetchHistoricalCandles(
    pair: TradingPair,
    timeframe: ChartTimeframe,
    apiKeyId: String,
    apiKeySecret: String,
  ): Pair<List<CandlePoint>, String> {
    val nowMs = System.currentTimeMillis()
    val sinceMs = nowMs - (timeframe.lunoDurationSeconds.toLong() * 240L * 1000L)

    // 1. Prefer authenticated Luno Exchange candles when API credentials exist
    if (apiKeyId.isNotBlank() && apiKeySecret.isNotBlank()) {
      val path = "/api/exchange/1/candles"
      val headers = HmacSigner.signRequest(apiKeyId, apiKeySecret, "GET", path)
      val lunoResult =
        runCatching {
          lunoApi.getCandles(
            pair = pair.pairCode,
            since = sinceMs,
            duration = timeframe.lunoDurationSeconds,
            authorization = headers.authorizationBasic,
            timestamp = headers.timestampMs,
            signature = headers.hmacSha256Signature,
          )
        }
      val lunoCandles =
        lunoResult
          .getOrNull()
          ?.candles
          ?.mapNotNull { c ->
            val o = c.open.toDoubleOrNull()
            val h = c.high.toDoubleOrNull()
            val l = c.low.toDoubleOrNull()
            val cl = c.close.toDoubleOrNull()
            val v = c.volume.toDoubleOrNull() ?: 0.0
            if (o != null && h != null && l != null && cl != null) {
              CandlePoint(c.timestamp, o, h, l, cl, v)
            } else {
              null
            }
          }
          ?.sortedBy { it.timestampMs }
      if (!lunoCandles.isNullOrEmpty() && lunoCandles.size >= 30) {
        return lunoCandles to "Luno Exchange Candles"
      }
    }

    // 2. Public CoinGecko market_chart historical series (resilient live fallback)
    val cgResult =
      runCatching {
        coinGeckoApi.getMarketChart(
          coinId = pair.coinGeckoId,
          vsCurrency = pair.coinGeckoVsCurrency,
          days = timeframe.coinGeckoDays,
        )
      }
    val cgPoints =
      cgResult
        .getOrNull()
        ?.prices
        ?.mapNotNull { row ->
          if (row.size >= 2) {
            val ts = row[0].toLong()
            val price = row[1]
            CandlePoint(
              timestampMs = ts,
              open = price,
              high = price,
              low = price,
              close = price,
              volume = 0.0,
            )
          } else {
            null
          }
        }
        ?.sortedBy { it.timestampMs }
    if (!cgPoints.isNullOrEmpty()) {
      return cgPoints to "CoinGecko + Luno Live"
    }

    // 3. Final fallback: construct points from Luno public recent trades
    val trades =
      runCatching { lunoApi.getTrades(pair.pairCode).trades }.getOrDefault(emptyList())
    val tradeCandles =
      trades
        .reversed()
        .mapNotNull { t ->
          val p = t.price.toDoubleOrNull()
          val v = t.volume.toDoubleOrNull() ?: 0.0
          if (p != null) CandlePoint(t.timestamp, p, p, p, p, v) else null
        }
    return tradeCandles to "Luno Trade Tape"
  }

  private fun stitchLiveTickerIntoCandles(
    candles: List<CandlePoint>,
    livePrice: Double,
    timestampMs: Long,
  ): List<CandlePoint> {
    if (livePrice <= 0.0) return candles
    if (candles.isEmpty()) {
      return listOf(CandlePoint(timestampMs, livePrice, livePrice, livePrice, livePrice, 0.0))
    }
    val last = candles.last()
    val updatedLast =
      last.copy(
        close = livePrice,
        high = maxOf(last.high, livePrice),
        low = minOf(last.low, livePrice),
        timestampMs = maxOf(last.timestampMs, timestampMs),
      )
    return candles.dropLast(1) + updatedLast
  }

  private fun findPrice24hAgo(candles: List<CandlePoint>, nowMs: Long): Double? {
    if (candles.size < 2) return null
    val targetTs = nowMs - 24L * 60L * 60L * 1000L
    return candles.minByOrNull { kotlin.math.abs(it.timestampMs - targetTs) }?.close
      ?: candles.first().close
  }
}
