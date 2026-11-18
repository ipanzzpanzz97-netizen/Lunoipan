package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.PositionSizeResult
import com.example.data.model.TradingPair
import com.example.ui.FormatUtils
import com.example.ui.theme.LocalQuantColors
import com.example.ui.theme.QuantMonoMedium
import com.example.ui.theme.QuantMonoSmall
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PositionSizingSheet(
  pair: TradingPair,
  totalCapital: Double,
  riskTolerancePct: Double,
  stopLossPct: Double,
  result: PositionSizeResult,
  onInputsChanged: (Double?, Double?, Double?) -> Unit,
  onSaveAsDefault: (Double, Double, Double) -> Unit,
  onLogTradePlan: () -> Unit,
  onDismiss: () -> Unit,
) {
  val colors = LocalQuantColors.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var capitalText by remember(totalCapital) { mutableStateOf(totalCapital.toInt().toString()) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = colors.slate,
    contentColor = colors.textPrimary,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp)
          .padding(bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          Text(
            text = "INSTITUTIONAL POSITION SIZER",
            style = MaterialTheme.typography.labelSmall,
            color = colors.cyan,
          )
          Text(
            text = "${pair.displayName} Risk Engine",
            style = MaterialTheme.typography.headlineSmall,
            color = colors.textPrimary,
          )
        }
        Surface(
          color = colors.emeraldContainer,
          shape = MaterialTheme.shapes.small,
          border = BorderStroke(1.dp, colors.emerald.copy(alpha = 0.45f)),
        ) {
          Text(
            text = "1%–2% CAP",
            style = MaterialTheme.typography.labelSmall,
            color = colors.emeraldBright,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
          )
        }
      }

      OutlinedTextField(
        value = capitalText,
        onValueChange = { raw ->
          val filtered = raw.filter { it.isDigit() || it == '.' }
          capitalText = filtered
          filtered.toDoubleOrNull()?.let { parsed ->
            onInputsChanged(parsed, null, null)
          }
        },
        label = { Text("Total Portfolio Capital (${pair.quoteAsset})") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth().testTag("position_capital_input"),
      )

      Column {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = "Portfolio Risk Tolerance (Hard-Capped)",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
          )
          Text(
            text = "${String.format(Locale.US, "%.2f", riskTolerancePct)}%",
            style = QuantMonoSmall,
            color = colors.cyanBright,
          )
        }
        Slider(
          value = riskTolerancePct.toFloat().coerceIn(1.0f, 2.0f),
          onValueChange = { onInputsChanged(null, it.toDouble(), null) },
          valueRange = 1.0f..2.0f,
          steps = 9,
          colors =
            SliderDefaults.colors(
              thumbColor = colors.cyanBright,
              activeTrackColor = colors.cyan,
            ),
          modifier = Modifier.testTag("risk_tolerance_slider"),
        )
      }

      Column {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = "Automated Stop-Loss Distance",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
          )
          Text(
            text = "${String.format(Locale.US, "%.1f", stopLossPct)}%",
            style = QuantMonoSmall,
            color = colors.crimsonBright,
          )
        }
        Slider(
          value = stopLossPct.toFloat().coerceIn(0.5f, 15.0f),
          onValueChange = { onInputsChanged(null, null, it.toDouble()) },
          valueRange = 0.5f..15.0f,
          colors =
            SliderDefaults.colors(
              thumbColor = colors.crimsonBright,
              activeTrackColor = colors.crimson,
            ),
          modifier = Modifier.testTag("stop_loss_slider"),
        )
      }

      Surface(
        color = colors.charcoal,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, colors.border),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
            text = "ALLOCATION OUTPUT",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textMuted,
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
          ) {
            Column {
              Text(
                text = "Recommended Order Size",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
              )
              Text(
                text =
                  FormatUtils.formatCurrency(
                    result.spotCappedPositionSize,
                    pair.quoteSymbol,
                    2,
                  ),
                style = QuantMonoMedium,
                color = colors.emeraldBright,
              )
            }
            Text(
              text = FormatUtils.formatUnits(result.unitsToAcquire, pair.baseAsset),
              style = QuantMonoSmall,
              color = colors.cyanBright,
            )
          }
          if (result.isSpotCapped) {
            Text(
              text = "Spot-capped to 100% of available capital (uncapped formula: ${FormatUtils.formatCurrency(result.theoreticalPositionSize, pair.quoteSymbol, 0)})",
              style = MaterialTheme.typography.bodySmall,
              color = colors.amber,
            )
          }

          HorizontalDivider(color = colors.border)

          CalcRow(
            label = "Max Risk Capital (${String.format(Locale.US, "%.2f", result.riskTolerancePct)}%)",
            value = FormatUtils.formatCurrency(result.riskCapitalAmount, pair.quoteSymbol, 2),
            valueColor = colors.amber,
          )
          CalcRow(
            label = "Live Luno Entry (Ask)",
            value = FormatUtils.formatCurrency(result.entryPrice, pair.quoteSymbol, pair.priceDecimals),
            valueColor = colors.textPrimary,
          )
          CalcRow(
            label = "Stop-Loss Trigger (-${String.format(Locale.US, "%.1f", result.stopLossPct)}%)",
            value = FormatUtils.formatCurrency(result.stopLossPrice, pair.quoteSymbol, pair.priceDecimals),
            valueColor = colors.crimsonBright,
          )
          CalcRow(
            label = "Take-Profit Target (2:1 R:R)",
            value = FormatUtils.formatCurrency(result.takeProfit2RPrice, pair.quoteSymbol, pair.priceDecimals),
            valueColor = colors.emeraldBright,
          )
          CalcRow(
            label = "Extended Target (3:1 R:R)",
            value = FormatUtils.formatCurrency(result.takeProfit3RPrice, pair.quoteSymbol, pair.priceDecimals),
            valueColor = colors.emerald,
          )
          CalcRow(
            label = "Est. Luno Taker Fee (~0.5%)",
            value = FormatUtils.formatCurrency(result.estimatedFeeAmount, pair.quoteSymbol, 2),
            valueColor = colors.textSecondary,
          )
        }
      }

      Spacer(modifier = Modifier.height(2.dp))
      Button(
        onClick = {
          onSaveAsDefault(
            result.totalCapital,
            result.riskTolerancePct,
            result.stopLossPct,
          )
          onLogTradePlan()
        },
        colors =
          ButtonDefaults.buttonColors(
            containerColor = colors.emerald,
            contentColor = colors.charcoalDeep,
          ),
        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("log_trade_plan_button"),
      ) {
        Text("Save Rules & Log Trade Plan", style = MaterialTheme.typography.labelLarge)
      }
      androidx.compose.material3.OutlinedButton(
        onClick = {
          onSaveAsDefault(
            result.totalCapital,
            result.riskTolerancePct,
            result.stopLossPct,
          )
          onDismiss()
        },
        border = BorderStroke(1.dp, colors.cyan.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_risk_rules_button"),
      ) {
        Text("Lock Risk Rules Only", color = colors.cyanBright)
      }
    }
  }
}

@Composable
private fun CalcRow(
  label: String,
  value: String,
  valueColor: androidx.compose.ui.graphics.Color,
) {
  val colors = LocalQuantColors.current
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(text = label, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
    Text(text = value, style = QuantMonoSmall, color = valueColor)
  }
}
