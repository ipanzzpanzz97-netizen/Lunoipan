package com.example.ui.screens

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.ui.FormatUtils
import com.example.ui.PortfolioUiState
import com.example.ui.QuantPulseUiState
import com.example.ui.theme.LocalQuantColors
import com.example.ui.theme.QuantMonoSmall
import java.util.Locale

@Composable
fun VaultScreen(
  uiState: QuantPulseUiState,
  onSaveCredentials: (String, String) -> Unit,
  onClearCredentials: () -> Unit,
  onRefreshBalances: () -> Unit,
  onSaveRiskRules: (Double, Double, Double) -> Unit,
  onSyncCapitalFromLuno: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = LocalQuantColors.current
  val creds = uiState.credentials
  val prefs = uiState.preferences

  var keyIdInput by remember(creds.apiKeyId) { mutableStateOf(creds.apiKeyId) }
  var keySecretInput by remember(creds.apiKeySecret) { mutableStateOf(creds.apiKeySecret) }
  var showSecret by remember { mutableStateOf(false) }

  var capitalInput by remember(prefs.totalCapitalMyr) {
    mutableStateOf(prefs.totalCapitalMyr.toInt().toString())
  }
  var riskSlider by remember(prefs.riskTolerancePct) {
    mutableStateOf(prefs.riskTolerancePct.toFloat().coerceIn(1.0f, 2.0f))
  }
  var stopLossSlider by remember(prefs.defaultStopLossPct) {
    mutableStateOf(prefs.defaultStopLossPct.toFloat().coerceIn(0.5f, 15.0f))
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
            text = "ANDROID KEYSTORE AES-256 VAULT",
            style = MaterialTheme.typography.labelSmall,
            color = colors.cyan,
          )
          Text(
            text = "API Vault & Portfolio",
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.Lock,
                  contentDescription = "Encrypted Keystore Storage",
                  tint = if (creds.isConfigured) colors.emeraldBright else colors.amber,
                )
                Text(
                  text = if (creds.isConfigured) "Vault Encrypted & Active" else "No Luno API Keys Saved",
                  style = MaterialTheme.typography.titleMedium,
                  color = colors.textPrimary,
                )
              }
            }

            Text(
              text = "Use a Read-Only Luno API Key ID & Secret to inspect live wallet balances via HMAC-SHA256 signed requests. Add Trade permission on Luno only if you want to submit confirmed Limit Orders from Terminal. Keys are encrypted locally and never logged.",
              style = MaterialTheme.typography.bodySmall,
              color = colors.textSecondary,
            )

            OutlinedTextField(
              value = keyIdInput,
              onValueChange = { keyIdInput = it },
              label = { Text("Luno API Key ID") },
              leadingIcon = {
                Icon(Icons.Default.Key, contentDescription = "API Key ID")
              },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("luno_key_id_input"),
            )

            OutlinedTextField(
              value = keySecretInput,
              onValueChange = { keySecretInput = it },
              label = { Text("Luno API Key Secret") },
              visualTransformation =
                if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
              trailingIcon = {
                IconButton(onClick = { showSecret = !showSecret }) {
                  Icon(
                    imageVector =
                      if (showSecret) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (showSecret) "Hide secret" else "Show secret",
                  )
                }
              },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("luno_key_secret_input"),
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Button(
                onClick = { onSaveCredentials(keyIdInput, keySecretInput) },
                enabled = keyIdInput.isNotBlank() && keySecretInput.isNotBlank(),
                colors =
                  ButtonDefaults.buttonColors(
                    containerColor = colors.cyan,
                    contentColor = colors.charcoalDeep,
                  ),
                modifier = Modifier.weight(1f).height(48.dp).testTag("save_vault_keys_button"),
              ) {
                Text("Encrypt & Connect", style = MaterialTheme.typography.labelLarge)
              }

              if (creds.isConfigured) {
                OutlinedButton(
                  onClick = {
                    keyIdInput = ""
                    keySecretInput = ""
                    onClearCredentials()
                  },
                  border = BorderStroke(1.dp, colors.crimson.copy(alpha = 0.6f)),
                  modifier = Modifier.height(48.dp).testTag("clear_vault_keys_button"),
                ) {
                  Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Clear keys",
                    tint = colors.crimsonBright,
                  )
                }
              }
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.AccountBalanceWallet,
                  contentDescription = "Luno Portfolio",
                  tint = colors.emeraldBright,
                )
                Text(
                  text = "Luno Authenticated Balances",
                  style = MaterialTheme.typography.titleMedium,
                  color = colors.textPrimary,
                )
              }
              if (creds.isConfigured) {
                IconButton(
                  onClick = onRefreshBalances,
                  modifier = Modifier.testTag("refresh_balances_button"),
                ) {
                  Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh balances",
                    tint = colors.cyanBright,
                  )
                }
              }
            }

            when (val portfolio = uiState.portfolioState) {
              PortfolioUiState.Unconfigured -> {
                Text(
                  text = "Save your Luno Read-Only API credentials above to sync GET /api/1/balance.",
                  style = MaterialTheme.typography.bodySmall,
                  color = colors.textMuted,
                )
              }
              PortfolioUiState.Loading -> {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                  horizontalArrangement = Arrangement.Center,
                ) {
                  CircularProgressIndicator(color = colors.cyan)
                }
              }
              is PortfolioUiState.Error -> {
                Text(
                  text = portfolio.message,
                  style = MaterialTheme.typography.bodySmall,
                  color = colors.crimsonBright,
                )
              }
              is PortfolioUiState.Success -> {
                if (portfolio.assets.isEmpty()) {
                  Text(
                    text = "No wallet accounts returned by Luno.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                  )
                } else {
                  portfolio.assets.forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider(color = colors.border.copy(alpha = 0.6f))
                    Row(
                      modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      Column {
                        Text(
                          text = item.asset,
                          style = MaterialTheme.typography.titleMedium,
                          color = colors.textPrimary,
                        )
                        if (!item.label.isNullOrBlank()) {
                          Text(
                            text = item.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                          )
                        }
                      }
                      Column(horizontalAlignment = Alignment.End) {
                        Text(
                          text = FormatUtils.formatUnits(item.available, item.asset),
                          style = QuantMonoSmall,
                          color = colors.emeraldBright,
                        )
                        if (item.reserved > 0.0) {
                          Text(
                            text = "Reserved: ${FormatUtils.formatNumber(item.reserved, 4)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.amber,
                          )
                        }
                      }
                    }
                  }
                }
              }
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
            Text(
              text = "QUANTITATIVE RISK ENFORCEMENT (1%–2% CAP)",
              style = MaterialTheme.typography.labelSmall,
              color = colors.amber,
            )

            OutlinedTextField(
              value = capitalInput,
              onValueChange = { raw -> capitalInput = raw.filter { it.isDigit() || it == '.' } },
              label = { Text("Default Trading Capital (${uiState.selectedPair.quoteAsset})") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
              modifier = Modifier.fillMaxWidth().testTag("vault_capital_input"),
            )

            if (uiState.portfolioState is PortfolioUiState.Success) {
              OutlinedButton(
                onClick = onSyncCapitalFromLuno,
                border = BorderStroke(1.dp, colors.cyan.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("sync_luno_capital_button"),
              ) {
                Text("Guna Baki Luno Sebenar sebagai Modal", color = colors.cyanBright)
              }
            }

            Column {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(
                  text = "Max Risk Per Trade",
                  style = MaterialTheme.typography.bodyMedium,
                  color = colors.textSecondary,
                )
                Text(
                  text = "${String.format(Locale.US, "%.2f", riskSlider)}%",
                  style = QuantMonoSmall,
                  color = colors.cyanBright,
                )
              }
              Slider(
                value = riskSlider,
                onValueChange = { riskSlider = it.coerceIn(1.0f, 2.0f) },
                valueRange = 1.0f..2.0f,
                steps = 9,
                colors =
                  SliderDefaults.colors(
                    thumbColor = colors.cyanBright,
                    activeTrackColor = colors.cyan,
                  ),
                modifier = Modifier.testTag("vault_risk_slider"),
              )
            }

            Column {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(
                  text = "Default Stop-Loss Distance",
                  style = MaterialTheme.typography.bodyMedium,
                  color = colors.textSecondary,
                )
                Text(
                  text = "${String.format(Locale.US, "%.1f", stopLossSlider)}%",
                  style = QuantMonoSmall,
                  color = colors.crimsonBright,
                )
              }
              Slider(
                value = stopLossSlider,
                onValueChange = { stopLossSlider = it },
                valueRange = 0.5f..15.0f,
                colors =
                  SliderDefaults.colors(
                    thumbColor = colors.crimsonBright,
                    activeTrackColor = colors.crimson,
                  ),
                modifier = Modifier.testTag("vault_stoploss_slider"),
              )
            }

            Button(
              onClick = {
                val cap = capitalInput.toDoubleOrNull() ?: prefs.totalCapitalMyr
                onSaveRiskRules(cap, riskSlider.toDouble(), stopLossSlider.toDouble())
              },
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = colors.emerald,
                  contentColor = colors.charcoalDeep,
                ),
              modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_vault_risk_button"),
            ) {
              Text("Save Enforced Risk Profile", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(modifier = Modifier.height(4.dp))
          }
        }
      }
    }
  }
}
