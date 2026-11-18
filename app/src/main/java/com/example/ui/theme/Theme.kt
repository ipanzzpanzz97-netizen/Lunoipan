package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp

private val QuantDarkColorScheme =
  darkColorScheme(
    primary = QuantCyan,
    onPrimary = QuantCharcoalDeep,
    primaryContainer = QuantCyanContainer,
    onPrimaryContainer = QuantCyanBright,
    secondary = QuantEmerald,
    onSecondary = QuantCharcoalDeep,
    secondaryContainer = QuantEmeraldContainer,
    onSecondaryContainer = QuantEmeraldBright,
    tertiary = QuantAmber,
    onTertiary = QuantCharcoalDeep,
    tertiaryContainer = QuantAmberContainer,
    onTertiaryContainer = QuantAmber,
    error = QuantCrimson,
    onError = QuantTextPrimary,
    errorContainer = QuantCrimsonContainer,
    onErrorContainer = QuantCrimsonBright,
    background = QuantCharcoal,
    onBackground = QuantTextPrimary,
    surface = QuantSlate,
    onSurface = QuantTextPrimary,
    surfaceVariant = QuantSlateElevated,
    onSurfaceVariant = QuantTextSecondary,
    outline = QuantBorder,
    outlineVariant = QuantSlateElevated,
  )

val QuantShapes =
  Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
  )

@Composable
fun QuantPulseTheme(content: @Composable () -> Unit) {
  CompositionLocalProvider(LocalQuantColors provides QuantSemanticColors()) {
    MaterialTheme(
      colorScheme = QuantDarkColorScheme,
      typography = Typography,
      shapes = QuantShapes,
      content = content,
    )
  }
}
