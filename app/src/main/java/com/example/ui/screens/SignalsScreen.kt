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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.ChartTimeframe
import com.example.ui.FormatUtils
import com.example.ui.QuantPulseUiState
import com.example.ui.components.PriceEmaChart
import com.example.ui.components.RsiSubChart
import com.example.ui.theme.LocalQuantColors
import com.example.ui.theme.QuantMonoSmall

@Composable
fun SignalsScreen(
  uiState: QuantPulseUiState,
  onSelectTimeframe: (ChartTimeframe) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = LocalQuantColors.current
  val snapshot = uiState.activeSnapshot

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Column {
            Text(
              text = "TECHNICAL & ORDERBOOK DEPTH",
              style = MaterialTheme.typography.labelSmall,
              color = colors.cyan,
            )
            Text(
              text = "${uiState.selectedPair.displayName} Analytics",
              style = MaterialTheme.typography.headlineSmall,
              color = colors.textPrimary,
            )
          }

          LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            items(ChartTimeframe.entries) { tf ->
              val selected = tf == uiState.selectedTimeframe
              FilterChip(
                selected = selected,
                onClick = { onSelectTimeframe(tf) },
                label = { Text(tf.label, style = MaterialTheme.typography.labelSmall) },
                colors =
                  FilterChipDefaults.filterChipColors(
                    containerColor = colors.slate,
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
                modifier = Modifier.testTag("timeframe_chip_${tf.label.lowercase()}"),
              )
            }
          }
        }
      }

      if (snapshot == null) {
        item {
          Surface(
            color = colors.slate,
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, colors.border),
          ) {
            Box(
              modifier = Modifier.fillMaxWidth().padding(40.dp),
              contentAlignment = Alignment.Center,
            ) {
              CircularProgressIndicator(color = colors.cyan)
            }
          }
        }
      } else {
        item {
          Surface(
            color = colors.slate,
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, colors.border),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = "SOURCE: ${snapshot.signal.candleSource.uppercase()}",
                  style = MaterialTheme.typography.labelSmall,
                  color = colors.textMuted,
                  modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
                Text(
                  text = "TAP TO INSPECT",
                  style = MaterialTheme.typography.labelSmall,
                  color = colors.cyan,
                )
              }

              PriceEmaChart(
                candles = snapshot.candles,
                ema50 = snapshot.signal.ema50Series,
                ema200 = snapshot.signal.ema200Series,
                quoteSymbol = snapshot.pair.quoteSymbol,
                priceDecimals = snapshot.pair.priceDecimals,
              )

              HorizontalDivider(color = colors.border)

              RsiSubChart(rsiSeries = snapshot.signal.rsiSeries)
            }
          }
        }

        item {
          val ob = snapshot.orderBook
          Surface(
            color = colors.slate,
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, colors.border),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Text(
                text = "LUNO ORDERBOOK TOP (${snapshot.pair.pairCode})",
                style = MaterialTheme.typography.labelSmall,
                color = colors.cyan,
              )

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                  Text("BIDS (BUY)", style = MaterialTheme.typography.labelSmall, color = colors.emeraldBright)
                  if (ob.bids.isEmpty()) {
                    Text("No bids", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                  } else {
                    ob.bids.take(6).forEach { lvl ->
                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                      ) {
                        Text(
                          text = FormatUtils.formatNumber(lvl.price, snapshot.pair.priceDecimals),
                          style = QuantMonoSmall,
                          color = colors.emeraldBright,
                        )
                        Text(
                          text = FormatUtils.formatNumber(lvl.volume, 4),
                          style = QuantMonoSmall,
                          color = colors.textSecondary,
                        )
                      }
                    }
                  }
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                  Text("ASKS (SELL)", style = MaterialTheme.typography.labelSmall, color = colors.crimsonBright)
                  if (ob.asks.isEmpty()) {
                    Text("No asks", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                  } else {
                    ob.asks.take(6).forEach { lvl ->
                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                      ) {
                        Text(
                          text = FormatUtils.formatNumber(lvl.price, snapshot.pair.priceDecimals),
                          style = QuantMonoSmall,
                          color = colors.crimsonBright,
                        )
                        Text(
                          text = FormatUtils.formatNumber(lvl.volume, 4),
                          style = QuantMonoSmall,
                          color = colors.textSecondary,
                        )
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
              modifier = Modifier.fillMaxWidth().padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text(
                text = "RECENT EXECUTED TRADES",
                style = MaterialTheme.typography.labelSmall,
                color = colors.cyan,
              )
              if (snapshot.recentTrades.isEmpty()) {
                Text(
                  text = "Waiting for recent trade prints from Luno...",
                  style = MaterialTheme.typography.bodyMedium,
                  color = colors.textMuted,
                )
              } else {
                snapshot.recentTrades.take(12).forEach { trade ->
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                  ) {
                    Text(
                      text = if (trade.isBuy) "BUY" else "SELL",
                      style = MaterialTheme.typography.labelSmall,
                      color = if (trade.isBuy) colors.emeraldBright else colors.crimsonBright,
                    )
                    Text(
                      text =
                        FormatUtils.formatCurrency(
                          trade.price,
                          snapshot.pair.quoteSymbol,
                          snapshot.pair.priceDecimals,
                        ),
                      style = QuantMonoSmall,
                      color = colors.textPrimary,
                    )
                    Text(
                      text = FormatUtils.formatNumber(trade.volume, 4),
                      style = QuantMonoSmall,
                      color = colors.textSecondary,
                    )
                    Text(
                      text = FormatUtils.formatTime(trade.timestampMs),
                      style = MaterialTheme.typography.labelSmall,
                      color = colors.textMuted,
                    )
                  }
                }
              }
              Spacer(modifier = Modifier.height(4.dp))
            }
          }
        }
      }
    }
  }
}
