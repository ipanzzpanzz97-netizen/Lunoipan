package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val QuantCharcoal = Color(0xFF0F172A)
val QuantCharcoalDeep = Color(0xFF090E1A)
val QuantSlate = Color(0xFF1E293B)
val QuantSlateElevated = Color(0xFF273549)
val QuantBorder = Color(0xFF334155)

val QuantCyan = Color(0xFF06B6D4)
val QuantCyanBright = Color(0xFF22D3EE)
val QuantCyanContainer = Color(0xFF0E3A4A)

val QuantEmerald = Color(0xFF10B981)
val QuantEmeraldBright = Color(0xFF34D399)
val QuantEmeraldContainer = Color(0xFF0B3B2E)

val QuantCrimson = Color(0xFFEF4444)
val QuantCrimsonBright = Color(0xFFF87171)
val QuantCrimsonContainer = Color(0xFF451A1A)

val QuantAmber = Color(0xFFF59E0B)
val QuantAmberContainer = Color(0xFF422D0B)

val QuantTextPrimary = Color(0xFFF8FAFC)
val QuantTextSecondary = Color(0xFF94A3B8)
val QuantTextMuted = Color(0xFF64748B)

@Immutable
data class QuantSemanticColors(
  val charcoal: Color = QuantCharcoal,
  val charcoalDeep: Color = QuantCharcoalDeep,
  val slate: Color = QuantSlate,
  val slateElevated: Color = QuantSlateElevated,
  val border: Color = QuantBorder,
  val cyan: Color = QuantCyan,
  val cyanBright: Color = QuantCyanBright,
  val cyanContainer: Color = QuantCyanContainer,
  val emerald: Color = QuantEmerald,
  val emeraldBright: Color = QuantEmeraldBright,
  val emeraldContainer: Color = QuantEmeraldContainer,
  val crimson: Color = QuantCrimson,
  val crimsonBright: Color = QuantCrimsonBright,
  val crimsonContainer: Color = QuantCrimsonContainer,
  val amber: Color = QuantAmber,
  val amberContainer: Color = QuantAmberContainer,
  val textPrimary: Color = QuantTextPrimary,
  val textSecondary: Color = QuantTextSecondary,
  val textMuted: Color = QuantTextMuted,
)

val LocalQuantColors = staticCompositionLocalOf { QuantSemanticColors() }
