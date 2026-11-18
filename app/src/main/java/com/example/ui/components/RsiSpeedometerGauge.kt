package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalQuantColors
import com.example.ui.theme.QuantMonoLarge
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun RsiSpeedometerGauge(
  rsiValue: Double,
  modifier: Modifier = Modifier,
) {
  val colors = LocalQuantColors.current
  val clampedRsi = rsiValue.toFloat().coerceIn(0f, 100f)
  val animatedRsi by
    animateFloatAsState(
      targetValue = clampedRsi,
      animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = 0.72f),
      label = "rsi_needle",
    )

  val zoneColor =
    when {
      animatedRsi < 30f -> colors.emerald
      animatedRsi > 70f -> colors.crimson
      else -> colors.cyan
    }

  val zoneLabel =
    when {
      animatedRsi < 30f -> "OVERSOLD • BUY ZONE"
      animatedRsi > 70f -> "OVERBOUGHT • TAKE PROFIT"
      animatedRsi < 45f -> "COOLING • WATCH"
      else -> "NEUTRAL MOMENTUM"
    }

  Column(
    modifier =
      modifier.semantics {
        contentDescription = "RSI 14 speedometer gauge showing ${String.format(Locale.US, "%.1f", clampedRsi)}"
      },
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(
      modifier = Modifier.fillMaxWidth().height(172.dp),
      contentAlignment = Alignment.BottomCenter,
    ) {
      Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        val strokeWidth = 18.dp.toPx()
        val diameter = min(size.width, size.height * 1.85f) - strokeWidth
        if (diameter <= 4f) return@Canvas
        val radius = diameter / 2f
        val center = Offset(size.width / 2f, size.height - 14.dp.toPx())
        val arcTopLeft = Offset(center.x - radius, center.y - radius)
        val arcSize = Size(diameter, diameter)

        // Track background arc (180 degrees from left 180° to right 360°)
        drawArc(
          color = colors.slateElevated,
          startAngle = 180f,
          sweepAngle = 180f,
          useCenter = false,
          topLeft = arcTopLeft,
          size = arcSize,
          style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )

        // Oversold 0..30 -> 180° sweep * 0.30 = 54°
        drawArc(
          color = colors.emerald.copy(alpha = 0.85f),
          startAngle = 180f,
          sweepAngle = 52f,
          useCenter = false,
          topLeft = arcTopLeft,
          size = arcSize,
          style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
        )

        // Neutral 30..70 -> 72°
        drawArc(
          color = colors.cyan.copy(alpha = 0.45f),
          startAngle = 234f,
          sweepAngle = 72f,
          useCenter = false,
          topLeft = arcTopLeft,
          size = arcSize,
          style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
        )

        // Overbought 70..100 -> 54°
        drawArc(
          color = colors.crimson.copy(alpha = 0.85f),
          startAngle = 308f,
          sweepAngle = 52f,
          useCenter = false,
          topLeft = arcTopLeft,
          size = arcSize,
          style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
        )

        // Tick marks every 10 RSI units
        for (tick in 0..100 step 10) {
          val deg = 180.0 + (tick / 100.0) * 180.0
          val rad = deg * PI / 180.0
          val innerR = radius - strokeWidth * 0.82f
          val outerR = radius - strokeWidth * 0.56f
          val p1 = Offset(center.x + (cos(rad) * innerR).toFloat(), center.y + (sin(rad) * innerR).toFloat())
          val p2 = Offset(center.x + (cos(rad) * outerR).toFloat(), center.y + (sin(rad) * outerR).toFloat())
          drawLine(
            color = colors.textMuted,
            start = p1,
            end = p2,
            strokeWidth = if (tick == 30 || tick == 70) 3.dp.toPx() else 1.5.dp.toPx(),
          )
        }

        // Animated Needle
        val needleDeg = 180.0 + (animatedRsi / 100.0) * 180.0
        val needleRad = needleDeg * PI / 180.0
        val needleLen = radius - strokeWidth * 0.7f
        val tip =
          Offset(
            x = center.x + (cos(needleRad) * needleLen).toFloat(),
            y = center.y + (sin(needleRad) * needleLen).toFloat(),
          )

        // Subtle glow line
        drawLine(
          color = zoneColor.copy(alpha = 0.28f),
          start = center,
          end = tip,
          strokeWidth = 10.dp.toPx(),
          cap = StrokeCap.Round,
        )
        drawLine(
          color = colors.textPrimary,
          start = center,
          end = tip,
          strokeWidth = 3.5.dp.toPx(),
          cap = StrokeCap.Round,
        )
        drawCircle(
          color = zoneColor,
          radius = 9.dp.toPx(),
          center = center,
        )
        drawCircle(
          color = colors.charcoalDeep,
          radius = 4.dp.toPx(),
          center = center,
        )
      }

      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(bottom = 26.dp),
      ) {
        Text(
          text = String.format(Locale.US, "%.1f", animatedRsi),
          style = QuantMonoLarge,
          color = zoneColor,
        )
        Text(
          text = "RSI (14)",
          style = MaterialTheme.typography.labelSmall,
          color = colors.textSecondary,
        )
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text("0", style = MaterialTheme.typography.labelSmall, color = colors.emerald)
      Text("30 BUY", style = MaterialTheme.typography.labelSmall, color = colors.emerald)
      Text(zoneLabel, style = MaterialTheme.typography.labelSmall, color = zoneColor)
      Text("70 SELL", style = MaterialTheme.typography.labelSmall, color = colors.crimson)
      Text("100", style = MaterialTheme.typography.labelSmall, color = colors.crimson)
    }
  }
}
