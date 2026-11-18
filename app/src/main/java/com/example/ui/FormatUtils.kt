package com.example.ui

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FormatUtils {

  fun formatCurrency(amount: Double, symbol: String, decimals: Int = 2): String {
    val formatter =
      NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = decimals
        maximumFractionDigits = decimals
      }
    return "$symbol ${formatter.format(amount)}"
  }

  fun formatUnits(units: Double, asset: String): String {
    val decimals = if (units >= 100.0) 2 else if (units >= 1.0) 4 else 6
    val formatter =
      NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = decimals
      }
    return "${formatter.format(units)} $asset"
  }

  fun formatSignedPct(value: Double): String {
    val sign = if (value >= 0.0) "+" else ""
    return "$sign${String.format(Locale.US, "%.2f", value)}%"
  }

  fun formatNumber(value: Double, decimals: Int = 2): String {
    val formatter =
      NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = decimals
        maximumFractionDigits = decimals
      }
    return formatter.format(value)
  }

  fun formatTime(timestampMs: Long): String {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestampMs))
  }
}
