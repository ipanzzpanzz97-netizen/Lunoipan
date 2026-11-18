package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.model.DcaFrequency
import com.example.ui.FormatUtils
import com.example.ui.QuantPulseUiState
import com.example.ui.components.DcaCompoundingChart
import com.example.ui.theme.LocalQuantColors
import com.example.ui.theme.QuantMonoMedium
import com.example.ui.theme.QuantMonoSmall
import java.util.Locale

@Composable
fun DcaPlannerScreen(
  uiState: QuantPulseUiState,
  onUpdateDca: (Double?, Int?, Double?, Boolean?, DcaFrequency?) -> Unit,
  onSendTestAlert: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = LocalQuantColors.current
  val context = LocalContext.current
  val prefs = uiState.preferences
  val summary = uiState.dcaSummary
  val symbol = uiState.selectedPair.quoteSymbol

  var amountText by remember(prefs.dcaMonthlyAmount) {
    mutableStateOf(prefs.dcaMonthlyAmount.toInt().toString())
  }

  val notificationLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (granted) {
        onUpdateDca(null, null, null, true, null)
      } else {
        onUpdateDca(null, null, null, false, null)
      }
    }

  fun requestAndEnableReminder(enable: Boolean) {
    if (!enable) {
      onUpdateDca(null, null, null, false, null)
      return
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val granted =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
          PackageManager.PERMISSION_GRANTED
      if (granted) {
        onUpdateDca(null, null, null, true, null)
      } else {
        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
      }
    } else {
      onUpdateDca(null, null, null, true, null)
    }
  }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item {
        Column {
          Text(
            text = "DOLLAR-COST AVERAGING ENGINE",
            style = MaterialTheme.typography.labelSmall,
            color = colors.cyan,
          )
          Text(
            text = "Compounding DCA Planner",
            style = MaterialTheme.typography.headlineSmall,
            color = colors.textPrimary,
          )
        }
      }

      item {
        Surface(
          color = colors.slate,
          shape = MaterialTheme.shapes.large,
          border = BorderStroke(1.dp, colors.border),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column {
                Text("PROJECTED VALUE", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                Text(
                  text = FormatUtils.formatCurrency(summary.finalValue, symbol, 0),
                  style = QuantMonoMedium,
                  color = colors.emeraldBright,
                )
              }
              Column(horizontalAlignment = Alignment.End) {
                Text("TOTAL INVESTED", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                Text(
                  text = FormatUtils.formatCurrency(summary.totalInvested, symbol, 0),
                  style = QuantMonoMedium,
                  color = colors.cyanBright,
                )
              }
            }

            DcaCompoundingChart(points = summary.points)

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "Est. Compounding Gain: +${FormatUtils.formatCurrency(summary.totalGain, symbol, 0)}",
                style = QuantMonoSmall,
                color = colors.emeraldBright,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
              )
              Text(
                text = FormatUtils.formatSignedPct(summary.returnPct),
                style = QuantMonoSmall,
                color = colors.emerald,
              )
            }
          }
        }
      }

      item {
        Surface(
          color = colors.slate,
          shape = MaterialTheme.shapes.large,
          border = BorderStroke(1.dp, colors.border),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            OutlinedTextField(
              value = amountText,
              onValueChange = { raw ->
                val clean = raw.filter { it.isDigit() || it == '.' }
                amountText = clean
                clean.toDoubleOrNull()?.let { parsed ->
                  if (parsed >= 10.0) onUpdateDca(parsed, null, null, null, null)
                }
              },
              label = { Text("Monthly Contribution (${uiState.selectedPair.quoteAsset})") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
              modifier = Modifier.fillMaxWidth().testTag("dca_amount_input"),
            )

            Column {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = "Horizon Duration",
                  style = MaterialTheme.typography.bodyMedium,
                  color = colors.textSecondary,
                  modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
                Text(
                  text = "${prefs.dcaDurationMonths} Months",
                  style = QuantMonoSmall,
                  color = colors.cyanBright,
                )
              }
              Slider(
                value = prefs.dcaDurationMonths.toFloat(),
                onValueChange = { onUpdateDca(null, it.toInt().coerceIn(3, 36), null, null, null) },
                valueRange = 3f..36f,
                colors =
                  SliderDefaults.colors(
                    thumbColor = colors.cyanBright,
                    activeTrackColor = colors.cyan,
                  ),
                modifier = Modifier.testTag("dca_duration_slider"),
              )
            }

            Column {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = "Projected Annual Return",
                  style = MaterialTheme.typography.bodyMedium,
                  color = colors.textSecondary,
                  modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
                Text(
                  text = "${String.format(Locale.US, "%.1f", prefs.dcaAnnualYieldPct)}% APY",
                  style = QuantMonoSmall,
                  color = colors.emeraldBright,
                )
              }
              Slider(
                value = prefs.dcaAnnualYieldPct.toFloat(),
                onValueChange = { onUpdateDca(null, null, it.toDouble(), null, null) },
                valueRange = 4f..45f,
                colors =
                  SliderDefaults.colors(
                    thumbColor = colors.emeraldBright,
                    activeTrackColor = colors.emerald,
                  ),
                modifier = Modifier.testTag("dca_yield_slider"),
              )
            }
          }
        }
      }

      item {
        Surface(
          color = colors.slate,
          shape = MaterialTheme.shapes.large,
          border = BorderStroke(1.dp, colors.border),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
              ) {
                Icon(
                  imageVector = Icons.Default.NotificationsActive,
                  contentDescription = "DCA Reminder Schedule",
                  tint = colors.cyanBright,
                )
                Column {
                  Text(
                    text = "Recurring DCA Reminder",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                  )
                  Text(
                    text = "Local periodic alert prompting disciplined buys",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                  )
                }
              }
              Switch(
                checked = prefs.dcaReminderEnabled,
                onCheckedChange = { requestAndEnableReminder(it) },
                colors =
                  SwitchDefaults.colors(
                    checkedThumbColor = colors.charcoalDeep,
                    checkedTrackColor = colors.emeraldBright,
                  ),
                modifier = Modifier.testTag("dca_reminder_switch"),
              )
            }

            LazyRow(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              items(DcaFrequency.entries) { freq ->
                val selected = freq == prefs.dcaFrequency
                FilterChip(
                  selected = selected,
                  onClick = { onUpdateDca(null, null, null, null, freq) },
                  label = { Text(freq.label, style = MaterialTheme.typography.labelSmall) },
                  colors =
                    FilterChipDefaults.filterChipColors(
                      containerColor = colors.charcoal,
                      labelColor = colors.textSecondary,
                      selectedContainerColor = colors.cyanContainer,
                      selectedLabelColor = colors.cyanBright,
                    ),
                  border =
                    FilterChipDefaults.filterChipBorder(
                      enabled = true,
                      selected = selected,
                      borderColor = colors.border,
                      selectedBorderColor = colors.cyan,
                    ),
                  modifier = Modifier.testTag("dca_freq_${freq.name.lowercase()}"),
                )
              }
            }

            OutlinedButton(
              onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                  ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                  notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                  onSendTestAlert()
                }
              },
              border = BorderStroke(1.dp, colors.cyan.copy(alpha = 0.6f)),
              modifier = Modifier.fillMaxWidth().height(48.dp).testTag("test_dca_alert_button"),
            ) {
              Icon(
                imageVector = Icons.Default.NotificationsActive,
                contentDescription = "Send Test DCA Alert",
                tint = colors.cyanBright,
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text("Send Test DCA Notification", color = colors.cyanBright)
            }
          }
        }
      }
    }
  }
}
