package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.data.model.CandlePoint
import com.example.data.model.DcaProjectionPoint
import com.example.ui.FormatUtils
import com.example.ui.theme.LocalQuantColors
import com.example.ui.theme.QuantMonoSmall
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun MiniPriceSparkline(
  closes: List<Double>,
  isPositive: Boolean,
  modifier: Modifier = Modifier,
) {
  val colors = LocalQuantColors.current
  val lineColor = if (isPositive) colors.emerald else colors.crimson

  Canvas(modifier = modifier) {
    if (closes.size < 2) return@Canvas
    val slice = closes.takeLast(48)
    val minVal = slice.minOrNull() ?: return@Canvas
    val maxVal = slice.maxOrNull() ?: return@Canvas
    val range = (maxVal - minVal).takeIf { it > 0.0 } ?: (maxVal * 0.01).coerceAtLeast(1.0)

    val stepX = size.width / (slice.size - 1).coerceAtLeast(1)
    val linePath = Path()
    val fillPath = Path()

    slice.forEachIndexed { idx, price ->
      val x = idx * stepX
      val y = size.height - (((price - minVal) / range).toFloat() * (size.height * 0.85f)) - size.height * 0.075f
      if (idx == 0) {
        linePath.moveTo(x, y)
        fillPath.moveTo(x, size.height)
        fillPath.lineTo(x, y)
      } else {
        linePath.lineTo(x, y)
        fillPath.lineTo(x, y)
      }
      if (idx == slice.lastIndex) {
        fillPath.lineTo(x, size.height)
        fillPath.close()
      }
    }

    drawPath(
      path = fillPath,
      brush =
        Brush.verticalGradient(
          colors = listOf(lineColor.copy(alpha = 0.30f), Color.Transparent),
          startY = 0f,
          endY = size.height,
        ),
    )
    drawPath(
      path = linePath,
      color = lineColor,
      style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
  }
}

@Composable
fun PriceEmaChart(
  candles: List<CandlePoint>,
  ema50: List<Double>,
  ema200: List<Double>,
  quoteSymbol: String,
  priceDecimals: Int,
  modifier: Modifier = Modifier,
) {
  val colors = LocalQuantColors.current
  val displayCount = minOf(candles.size, 90)
  val visibleCandles = candles.takeLast(displayCount)
  val visibleEma50 = ema50.takeLast(displayCount)
  val visibleEma200 = ema200.takeLast(displayCount)

  var selectedIdx by remember(visibleCandles.size) { mutableIntStateOf(visibleCandles.lastIndex.coerceAtLeast(0)) }

  Column(modifier = modifier) {
    if (visibleCandles.isNotEmpty()) {
      val idx = selectedIdx.coerceIn(0, visibleCandles.lastIndex)
      val c = visibleCandles[idx]
      val e50 = visibleEma50.getOrNull(idx) ?: c.close
      val e200 = visibleEma200.getOrNull(idx) ?: c.close
      @OptIn(ExperimentalLayoutApi::class)
      FlowRow(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
          text = "PX ${FormatUtils.formatCurrency(c.close, quoteSymbol, priceDecimals)}",
          style = QuantMonoSmall,
          color = colors.textPrimary,
        )
        Text(
          text = "EMA50 ${FormatUtils.formatNumber(e50, priceDecimals)}",
          style = MaterialTheme.typography.labelSmall,
          color = colors.cyan,
        )
        Text(
          text = "EMA200 ${FormatUtils.formatNumber(e200, priceDecimals)}",
          style = MaterialTheme.typography.labelSmall,
          color = colors.amber,
        )
      }
    }

    Canvas(
      modifier =
        Modifier.fillMaxWidth()
          .height(210.dp)
          .pointerInput(visibleCandles.size) {
            detectTapGestures { offset ->
              if (visibleCandles.size > 1) {
                val ratio = (offset.x / size.width).coerceIn(0f, 1f)
                selectedIdx = (ratio * (visibleCandles.size - 1)).roundToInt()
              }
            }
          },
    ) {
      if (visibleCandles.size < 2) return@Canvas
      val allValues = buildList {
        visibleCandles.forEach { add(it.close) }
        addAll(visibleEma50)
        addAll(visibleEma200)
      }
      val minVal = allValues.minOrNull() ?: return@Canvas
      val maxVal = allValues.maxOrNull() ?: return@Canvas
      val range = (maxVal - minVal).takeIf { it > 0.0 } ?: 1.0
      val stepX = size.width / (visibleCandles.size - 1).coerceAtLeast(1)

      fun yFor(value: Double): Float {
        val norm = ((value - minVal) / range).toFloat()
        return size.height - (norm * size.height * 0.86f) - size.height * 0.07f
      }

      // Horizontal subtle grid lines
      val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
      for (g in 0..3) {
        val gy = size.height * (0.1f + g * 0.26f)
        drawLine(
          color = colors.border.copy(alpha = 0.5f),
          start = Offset(0f, gy),
          end = Offset(size.width, gy),
          strokeWidth = 1.dp.toPx(),
          pathEffect = dashEffect,
        )
      }

      // Price area + line
      val pricePath = Path()
      val fillPath = Path()
      visibleCandles.forEachIndexed { idx, pt ->
        val x = idx * stepX
        val y = yFor(pt.close)
        if (idx == 0) {
          pricePath.moveTo(x, y)
          fillPath.moveTo(x, size.height)
          fillPath.lineTo(x, y)
        } else {
          pricePath.lineTo(x, y)
          fillPath.lineTo(x, y)
        }
        if (idx == visibleCandles.lastIndex) {
          fillPath.lineTo(x, size.height)
          fillPath.close()
        }
      }
      drawPath(
        path = fillPath,
        brush =
          Brush.verticalGradient(
            colors = listOf(colors.cyan.copy(alpha = 0.22f), Color.Transparent),
          ),
      )
      drawPath(
        path = pricePath,
        color = colors.textPrimary,
        style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
      )

      // EMA 200 line (amber)
      if (visibleEma200.size == visibleCandles.size) {
        val path200 = Path()
        visibleEma200.forEachIndexed { idx, v ->
          val x = idx * stepX
          val y = yFor(v)
          if (idx == 0) path200.moveTo(x, y) else path200.lineTo(x, y)
        }
        drawPath(
          path = path200,
          color = colors.amber,
          style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round),
        )
      }

      // EMA 50 line (cyan)
      if (visibleEma50.size == visibleCandles.size) {
        val path50 = Path()
        visibleEma50.forEachIndexed { idx, v ->
          val x = idx * stepX
          val y = yFor(v)
          if (idx == 0) path50.moveTo(x, y) else path50.lineTo(x, y)
        }
        drawPath(
          path = path50,
          color = colors.cyanBright,
          style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round),
        )
      }

      // Tap crosshair indicator
      val selIdx = selectedIdx.coerceIn(0, visibleCandles.lastIndex)
      val selX = selIdx * stepX
      val selY = yFor(visibleCandles[selIdx].close)
      drawLine(
        color = colors.cyan.copy(alpha = 0.45f),
        start = Offset(selX, 0f),
        end = Offset(selX, size.height),
        strokeWidth = 1.dp.toPx(),
      )
      drawCircle(color = colors.cyanBright, radius = 4.5.dp.toPx(), center = Offset(selX, selY))
    }
  }
}

@Composable
fun RsiSubChart(
  rsiSeries: List<Double>,
  modifier: Modifier = Modifier,
) {
  val colors = LocalQuantColors.current
  val visibleRsi = rsiSeries.takeLast(90)

  Column(modifier = modifier) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = "RSI (14) OSCILLATOR",
        style = MaterialTheme.typography.labelSmall,
        color = colors.textSecondary,
      )
      val latest = visibleRsi.lastOrNull() ?: 50.0
      Text(
        text = String.format(Locale.US, "%.1f", latest),
        style = MaterialTheme.typography.labelSmall,
        color =
          when {
            latest < 30.0 -> colors.emerald
            latest > 70.0 -> colors.crimson
            else -> colors.cyan
          },
      )
    }

    Canvas(modifier = Modifier.fillMaxWidth().height(92.dp)) {
      if (visibleRsi.size < 2) return@Canvas
      val stepX = size.width / (visibleRsi.size - 1).coerceAtLeast(1)

      fun yForRsi(rsi: Double): Float {
        val norm = (rsi.coerceIn(0.0, 100.0) / 100.0).toFloat()
        return size.height - (norm * size.height * 0.88f) - size.height * 0.06f
      }

      val y70 = yForRsi(70.0)
      val y30 = yForRsi(30.0)
      val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))

      drawLine(
        color = colors.crimson.copy(alpha = 0.7f),
        start = Offset(0f, y70),
        end = Offset(size.width, y70),
        strokeWidth = 1.dp.toPx(),
        pathEffect = dash,
      )
      drawLine(
        color = colors.emerald.copy(alpha = 0.7f),
        start = Offset(0f, y30),
        end = Offset(size.width, y30),
        strokeWidth = 1.dp.toPx(),
        pathEffect = dash,
      )

      val path = Path()
      visibleRsi.forEachIndexed { idx, rsi ->
        val x = idx * stepX
        val y = yForRsi(rsi)
        if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
      }
      drawPath(
        path = path,
        color = colors.cyanBright,
        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
      )
    }
  }
}

@Composable
fun DcaCompoundingChart(
  points: List<DcaProjectionPoint>,
  modifier: Modifier = Modifier,
) {
  val colors = LocalQuantColors.current
  Canvas(modifier = modifier.fillMaxWidth().height(180.dp)) {
    if (points.size < 2) return@Canvas
    val maxVal = (points.maxOfOrNull { it.projectedValue } ?: 1.0).coerceAtLeast(1.0)
    val stepX = size.width / (points.size - 1).coerceAtLeast(1)

    fun yFor(v: Double): Float {
      val norm = (v / maxVal).toFloat().coerceIn(0f, 1f)
      return size.height - (norm * size.height * 0.86f) - size.height * 0.07f
    }

    // Principal invested area + line
    val investedPath = Path()
    val investedFill = Path()
    val valuePath = Path()
    val valueFill = Path()

    points.forEachIndexed { idx, pt ->
      val x = idx * stepX
      val yInv = yFor(pt.totalInvested)
      val yVal = yFor(pt.projectedValue)
      if (idx == 0) {
        investedPath.moveTo(x, yInv)
        investedFill.moveTo(x, size.height)
        investedFill.lineTo(x, yInv)
        valuePath.moveTo(x, yVal)
        valueFill.moveTo(x, size.height)
        valueFill.lineTo(x, yVal)
      } else {
        investedPath.lineTo(x, yInv)
        investedFill.lineTo(x, yInv)
        valuePath.lineTo(x, yVal)
        valueFill.lineTo(x, yVal)
      }
      if (idx == points.lastIndex) {
        investedFill.lineTo(x, size.height)
        investedFill.close()
        valueFill.lineTo(x, size.height)
        valueFill.close()
      }
    }

    drawPath(
      path = valueFill,
      brush =
        Brush.verticalGradient(
          colors = listOf(colors.emerald.copy(alpha = 0.34f), Color.Transparent),
        ),
    )
    drawPath(
      path = investedFill,
      brush =
        Brush.verticalGradient(
          colors = listOf(colors.cyan.copy(alpha = 0.24f), Color.Transparent),
        ),
    )
    drawPath(
      path = investedPath,
      color = colors.cyanBright,
      style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
    )
    drawPath(
      path = valuePath,
      color = colors.emeraldBright,
      style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round),
    )
  }
}
