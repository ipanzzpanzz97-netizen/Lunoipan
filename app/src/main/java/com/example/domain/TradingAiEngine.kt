package com.example.domain

import android.content.Context
import android.util.Log
import com.example.data.model.AiEngineSource
import com.example.data.model.AiTradeAdvice
import com.example.data.model.AiVerdictType
import com.example.data.model.HighProbabilitySetup
import com.example.data.model.MarketSnapshot
import com.example.data.model.PositionSizeResult
import com.example.data.model.SignalAction
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "TradingAiEngine"

class TradingAiEngine(private val appContext: Context? = null) {

  private fun isFirebaseReady(): Boolean {
    return try {
      val ctx = appContext ?: return false
      if (FirebaseApp.getApps(ctx).isEmpty()) {
        FirebaseApp.initializeApp(ctx)
      }
      FirebaseApp.getApps(ctx).isNotEmpty()
    } catch (e: Exception) {
      Log.w(TAG, "FirebaseApp unavailable; using Offline Quant AI", e)
      false
    }
  }

  private val tradeAdviceSchema =
    Schema.obj(
      properties =
        mapOf(
          "verdict" to Schema.enumeration(values = listOf("BUY", "SELL", "HOLD")),
          "confidencePct" to Schema.integer(description = "Confidence score from 0 to 100"),
          "headline" to Schema.string(description = "Short 3-6 word headline in Bahasa Melayu"),
          "reasoning" to
            Schema.string(
              description =
                "Concise 2-sentence quantitative explanation in Bahasa Melayu referencing RSI, EMA50/200, spread, and risk limit",
            ),
          "actionSteps" to
            Schema.array(
              items = Schema.string(),
              description = "2 to 3 concise actionable steps in Bahasa Melayu for Luno Exchange",
            ),
        ),
    )

  private val geminiModel by lazy {
    Firebase.ai.generativeModel(
      modelName = "gemini-3.8-flash",
      generationConfig =
        generationConfig {
          responseMimeType = "application/json"
          responseSchema = tradeAdviceSchema
          temperature = 0.25f
        },
    )
  }

  /**
   * Generates smart online Gemini AI trading guidance when internet + Firebase AI Logic are
   * available, and seamlessly falls back to the deterministic Offline Quantitative AI engine if
   * offline or if cloud inference fails.
   */
  suspend fun analyzeMarket(
    snapshot: MarketSnapshot,
    setup: HighProbabilitySetup,
    positionSize: PositionSizeResult,
    isOfflineMode: Boolean,
  ): AiTradeAdvice {
    val offlineBaseline = generateOfflineAdvice(snapshot, setup, positionSize)
    if (isOfflineMode) {
      return offlineBaseline
    }
    if (!isFirebaseReady()) {
      return offlineBaseline.copy(
        onlineErrorNote = "Enjin Kuantitatif Offline Aktif (AI awan tidak disambungkan dalam binaan ini).",
      )
    }

    val prompt = buildPrompt(snapshot, setup, positionSize)
    return try {
      val response = withTimeout(60_000L) { geminiModel.generateContent(prompt) }
      val rawJson =
        response.text ?: throw IllegalStateException("Gemini returned an empty response.")
      val json = JSONObject(rawJson)
      val verdictStr = json.optString("verdict", offlineBaseline.verdict.name).uppercase()
      val verdict =
        runCatching { AiVerdictType.valueOf(verdictStr) }.getOrDefault(offlineBaseline.verdict)
      val confidence = json.optInt("confidencePct", offlineBaseline.confidencePct).coerceIn(10, 95)
      val headline = json.optString("headline", offlineBaseline.headline).ifBlank { offlineBaseline.headline }
      val reasoning = json.optString("reasoning", offlineBaseline.reasoning).ifBlank { offlineBaseline.reasoning }
      val stepsArr = json.optJSONArray("actionSteps")
      val steps =
        if (stepsArr != null && stepsArr.length() > 0) {
          (0 until stepsArr.length()).mapNotNull { idx ->
            stepsArr.optString(idx).takeIf { it.isNotBlank() }
          }
        } else {
          offlineBaseline.actionSteps
        }

      offlineBaseline.copy(
        verdict = verdict,
        confidencePct = confidence,
        headline = headline,
        reasoning = reasoning,
        actionSteps = steps.ifEmpty { offlineBaseline.actionSteps },
        source = AiEngineSource.GEMINI_ONLINE,
        generatedAtMs = System.currentTimeMillis(),
        onlineErrorNote = null,
      )
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "Gemini AI inference timed out; using offline quant engine", e)
      offlineBaseline.copy(
        onlineErrorNote = "Sambungan AI awan tamat masa — beralih ke Enjin Kuantitatif Offline.",
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Gemini AI inference unavailable; using offline quant engine", e)
      offlineBaseline.copy(
        onlineErrorNote = "Mod Offline Quant AI aktif — analisis dikira secara tempatan.",
      )
    }
  }

  /**
   * 100% offline deterministic quantitative AI advisor that runs with zero network dependency.
   */
  fun generateOfflineAdvice(
    snapshot: MarketSnapshot,
    setup: HighProbabilitySetup,
    positionSize: PositionSizeResult,
    offlineNote: String? = null,
  ): AiTradeAdvice {
    val rsi = snapshot.signal.rsi14
    val verdict =
      when (snapshot.signal.action) {
        SignalAction.STRONG_BUY, SignalAction.SPECULATIVE_BUY -> AiVerdictType.BUY
        SignalAction.ACCUMULATE -> if (setup.passedCount >= 3) AiVerdictType.BUY else AiVerdictType.HOLD
        SignalAction.STRONG_SELL -> AiVerdictType.SELL
        SignalAction.HOLD -> AiVerdictType.HOLD
      }

    val headline =
      when (verdict) {
        AiVerdictType.BUY ->
          if (setup.passedCount == 4) "Entri Beli Limit A+ Disahkan" else "Zon Diskaun Beli Berhati-hati"
        AiVerdictType.SELL -> "Ambil Untung — RSI Terlebih Beli"
        AiVerdictType.HOLD -> "Tunggu Harga Diskaun (Sabar)"
      }

    val rsiText = String.format(Locale.US, "%.1f", rsi)
    val emaStatus = if (snapshot.signal.isGoldenCross) "Golden Cross (EMA50 > EMA200)" else "EMA50 di bawah EMA200"
    val spreadText = String.format(Locale.US, "%.3f", snapshot.spreadPct)

    val reasoning =
      when (verdict) {
        AiVerdictType.BUY ->
          "RSI(14) berada di paras rendah $rsiText dengan struktur $emaStatus dan ${setup.passedCount}/4 syarat entri dipenuhi. Gunakan pesanan Maker Limit untuk elak caj tinggi dan hadkan risiko kepada ${String.format(Locale.US, "%.1f", positionSize.riskTolerancePct)}% modal."
        AiVerdictType.SELL ->
          "RSI(14) mencecah zon terlebih beli ($rsiText > 70) yang berisiko mengalami pembetulan harga jangka pendek. Kunci keuntungan secara berperingkat menggunakan pesanan Limit Ask pada Luno."
        AiVerdictType.HOLD ->
          "Pasaran belum memenuhi syarat entri diskaun (${setup.passedCount}/4 lulus, RSI $rsiText, spread $spreadText%). Elakkan mengejar harga dan biarkan pemantau automatik menunggu paras lebih selamat."
      }

    val entryPrice =
      if (verdict == AiVerdictType.SELL) snapshot.ask else setup.suggestedLimitPrice
    val steps =
      when (verdict) {
        AiVerdictType.BUY ->
          listOf(
            "Letak pesanan Limit Buy (Post-Only) pada harga ${String.format(Locale.US, "%.2f", entryPrice)}.",
            "Tetapkan pelan Stop-Loss pada ${String.format(Locale.US, "%.2f", positionSize.stopLossPrice)} (risiko <= ${String.format(Locale.US, "%.1f", positionSize.riskTolerancePct)}%).",
            "Sasaran Take-Profit 2:1 pada ${String.format(Locale.US, "%.2f", positionSize.takeProfit2RPrice)}.",
          )
        AiVerdictType.SELL ->
          listOf(
            "Pertimbangkan jualan sebahagian atau penuh melalui Limit Sell pada ${String.format(Locale.US, "%.2f", snapshot.ask)}.",
            "Tunggu RSI kembali di bawah 40 sebelum membuat pembelian semula.",
          )
        AiVerdictType.HOLD ->
          listOf(
            "Aktifkan Auto-Monitor supaya aplikasi memberi amaran automatik bila RSI < 35.",
            "Teruskan pelan DCA berkala jika strategi jangka panjang aktif.",
          )
      }

    return AiTradeAdvice(
      verdict = verdict,
      confidencePct = snapshot.signal.confluenceScore,
      headline = headline,
      reasoning = reasoning,
      entryLimitPrice = entryPrice,
      stopLossPrice = positionSize.stopLossPrice,
      takeProfitPrice = positionSize.takeProfit2RPrice,
      actionSteps = steps,
      source = AiEngineSource.OFFLINE_QUANT,
      generatedAtMs = System.currentTimeMillis(),
      onlineErrorNote = offlineNote,
    )
  }

  private val chatModel by lazy {
    Firebase.ai.generativeModel(
      modelName = "gemini-3.8-flash",
      generationConfig =
        generationConfig {
          temperature = 0.35f
        },
    )
  }

  /**
   * Streams natural Bahasa Melayu trading Q&A from Gemini when online, and immediately answers via
   * the deterministic Offline Quantitative Engine if offline or if cloud inference fails.
   */
  suspend fun answerTradingQuestion(
    question: String,
    snapshot: MarketSnapshot,
    setup: HighProbabilitySetup,
    positionSize: PositionSizeResult,
    isOfflineMode: Boolean,
    onStreamingChunk: (String) -> Unit,
  ): Pair<String, AiEngineSource> {
    val cleanQ = question.trim()
    val offlineAnswer = generateOfflineChatAnswer(cleanQ, snapshot, setup, positionSize)
    if (isOfflineMode || !isFirebaseReady()) {
      onStreamingChunk(offlineAnswer)
      return offlineAnswer to AiEngineSource.OFFLINE_QUANT
    }

    val prompt = buildChatPrompt(cleanQ, snapshot, setup, positionSize)
    val accumulated = StringBuilder()
    return try {
      withTimeout(60_000L) {
        chatModel.generateContentStream(prompt).collect { chunk ->
          val text = chunk.text.orEmpty()
          if (text.isNotEmpty()) {
            accumulated.append(text)
            onStreamingChunk(accumulated.toString())
          }
        }
      }
      val finalAnswer = accumulated.toString().trim()
      if (finalAnswer.isNotEmpty()) {
        finalAnswer to AiEngineSource.GEMINI_ONLINE
      } else {
        onStreamingChunk(offlineAnswer)
        offlineAnswer to AiEngineSource.OFFLINE_QUANT
      }
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "Gemini chat streaming timed out; falling back to offline answer", e)
      val msg = "$offlineAnswer\n\n(Nota: Sambungan AI awan tamat masa — dijawab oleh Enjin Offline.)"
      onStreamingChunk(msg)
      msg to AiEngineSource.OFFLINE_QUANT
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Gemini chat failed; falling back to offline answer", e)
      onStreamingChunk(offlineAnswer)
      offlineAnswer to AiEngineSource.OFFLINE_QUANT
    }
  }

  fun generateOfflineChatAnswer(
    question: String,
    snapshot: MarketSnapshot,
    setup: HighProbabilitySetup,
    positionSize: PositionSizeResult,
  ): String {
    val q = question.lowercase(Locale.ROOT)
    val pair = snapshot.pair
    val rsiStr = String.format(Locale.US, "%.1f", snapshot.signal.rsi14)
    val entryStr = "${pair.quoteSymbol} ${String.format(Locale.US, "%.${pair.priceDecimals}f", setup.suggestedLimitPrice)}"
    val slStr = "${pair.quoteSymbol} ${String.format(Locale.US, "%.${pair.priceDecimals}f", positionSize.stopLossPrice)}"
    val tpStr = "${pair.quoteSymbol} ${String.format(Locale.US, "%.${pair.priceDecimals}f", positionSize.takeProfit2RPrice)}"
    val capStr = "${pair.quoteSymbol} ${String.format(Locale.US, "%.0f", positionSize.spotCappedPositionSize)}"
    val trendStr = if (snapshot.signal.isGoldenCross) "Bullish (Golden Cross EMA50 > EMA200)" else "Berhati-hati (EMA50 di bawah EMA200)"

    return when {
      q.contains("sl") || q.contains("stop") || q.contains("tp") || q.contains("untung") || q.contains("risiko") ->
        "Untuk ${pair.displayName} sekarang, cadangan Maker Limit Entry ialah $entryStr dengan saiz posisi maksimum $capStr (risiko ketat ${String.format(Locale.US, "%.1f", positionSize.riskTolerancePct)}%). Letakkan Stop-Loss pada $slStr dan Take-Profit 2:1 pada $tpStr."
      q.contains("modal") || q.contains("kecil") || q.contains("dca") ->
        "Untuk modal kecil atau disiplin jangka panjang, gunakan tab DCA Plan untuk beli berkala tanpa meneka paras terbawah, atau tunggu RSI(14) jatuh di bawah 30–35 dengan syarat risiko kekal 1%–2% sahaja per pesanan ($capStr sekarang)."
      q.contains("beli") || q.contains("jual") || q.contains("sesuai") || q.contains("sekarang") ->
        "Status ${pair.displayName}: RSI(14) = $rsiStr, struktur $trendStr, dan ${setup.passedCount}/4 syarat A+ dipenuhi. " +
          when {
            snapshot.signal.rsi14 > 70.0 -> "RSI terlebih beli (> 70) — lebih sesuai JUAL / ambil untung berperingkat."
            setup.passedCount >= 3 && snapshot.signal.rsi14 < 40.0 -> "Zon diskaun menarik — pertimbangkan BELI Limit Bid pada $entryStr dengan SL $slStr."
            else -> "Pasaran masih neutral — sebaik-baiknya TUNGGU dan biarkan Auto-Monitor memberi amaran bila syarat A+ lengkap."
          }
      else ->
        "Ringkasan Kuantitatif ${pair.displayName}: Harga semasa ${pair.quoteSymbol} ${String.format(Locale.US, "%.${pair.priceDecimals}f", snapshot.lastPrice)}, RSI(14) = $rsiStr, trend $trendStr (${setup.passedCount}/4 lulus). Limit Entry: $entryStr | SL: $slStr | TP: $tpStr."
    }
  }

  private fun buildChatPrompt(
    question: String,
    snapshot: MarketSnapshot,
    setup: HighProbabilitySetup,
    positionSize: PositionSizeResult,
  ): String {
    val pair = snapshot.pair
    return """
      You are QuantPulse, a practical and disciplined crypto trading assistant for Luno Exchange in Malaysia.
      Answer the user's question concisely (3-4 sentences max) in natural Bahasa Melayu using these live metrics:
      - Pair: ${pair.displayName} (${pair.pairCode})
      - Last Price: ${snapshot.lastPrice} ${pair.quoteAsset} (Best Bid: ${snapshot.bid}, Best Ask: ${snapshot.ask}, Spread: ${String.format(Locale.US, "%.3f", snapshot.spreadPct)}%)
      - Wilder RSI(14): ${String.format(Locale.US, "%.2f", snapshot.signal.rsi14)}
      - Golden Cross (EMA50 > EMA200): ${snapshot.signal.isGoldenCross}
      - A+ Setup Checklist: ${setup.passedCount}/${setup.totalCount} (${setup.readinessLabel})
      - Maker Limit Bid: ${setup.suggestedLimitPrice}, Stop-Loss: ${positionSize.stopLossPrice}, Take-Profit 2R: ${positionSize.takeProfit2RPrice}, Max Spot Size: ${positionSize.spotCappedPositionSize} ${pair.quoteAsset} (${positionSize.riskTolerancePct}% risk cap)
      Rules: Never promise guaranteed profits. Be clear and actionable.
      User question: "$question"
    """.trimIndent()
  }

  private fun buildPrompt(
    snapshot: MarketSnapshot,
    setup: HighProbabilitySetup,
    positionSize: PositionSizeResult,
  ): String {
    val pair = snapshot.pair
    return """
      You are QuantPulse, a disciplined quantitative crypto trading assistant for Luno Exchange (${pair.pairCode}).
      Analyze these live metrics and respond ONLY with the structured JSON schema in Bahasa Melayu:
      - Pair: ${pair.displayName}
      - Live Price: ${snapshot.lastPrice} ${pair.quoteAsset} (Bid: ${snapshot.bid}, Ask: ${snapshot.ask}, Spread: ${String.format(Locale.US, "%.3f", snapshot.spreadPct)}%)
      - 24h Change: ${String.format(Locale.US, "%.2f", snapshot.change24hPct)}%
      - Wilder RSI(14): ${String.format(Locale.US, "%.2f", snapshot.signal.rsi14)}
      - EMA50: ${snapshot.signal.ema50}, EMA200: ${snapshot.signal.ema200}, Golden Cross: ${snapshot.signal.isGoldenCross}
      - Orderbook Bid Pressure: ${(snapshot.orderBook.bidImbalanceRatio * 100).toInt()}%
      - Setup Checklist Passed: ${setup.passedCount}/${setup.totalCount} (${setup.readinessLabel})
      - Strict Risk Limit: ${positionSize.riskTolerancePct}% max portfolio risk, Recommended Order Size: ${positionSize.spotCappedPositionSize} ${pair.quoteAsset}, Stop-Loss: ${positionSize.stopLossPrice}, Take-Profit 2R: ${positionSize.takeProfit2RPrice}
      Rules:
      - Never guarantee profits. Prioritize capital preservation.
      - Recommend BUY only when pullback/oversold risk-reward is favorable; SELL when RSI > 70; HOLD when neutral.
      - Keep reasoning practical, concise, and clear for a Malaysian Luno user.
    """.trimIndent()
  }
}
