package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.model.AiVerdictType
import com.example.data.model.HighProbabilitySetup
import com.example.data.model.MarketSnapshot
import com.example.data.model.SignalAction
import com.example.data.model.TradePlanRecord
import com.example.data.model.TradePlanStatus
import com.example.data.model.TradingPair
import com.example.ui.FormatUtils
import com.example.ui.MarketUiState
import com.example.ui.PortfolioUiState
import com.example.ui.QuantPulseUiState
import com.example.ui.components.MiniPriceSparkline
import com.example.ui.components.RsiSpeedometerGauge
import com.example.ui.theme.LocalQuantColors
import com.example.ui.theme.QuantMonoLarge
import com.example.ui.theme.QuantMonoMedium
import com.example.ui.theme.QuantMonoSmall

@Composable
fun TerminalScreen(
  uiState: QuantPulseUiState,
  onSelectPair: (TradingPair) -> Unit,
  onRefresh: () -> Unit,
  onToggleLive: () -> Unit,
  onOpenPositionCalculator: () -> Unit,
  onNavigateToSignals: () -> Unit,
  onCloseTradePlan: (String, Double) -> Unit,
  onDeleteTradePlan: (String) -> Unit,
  onRequestAiAnalysis: () -> Unit,
  onTogglePreferOfflineAi: () -> Unit,
  onToggleAutoPilot: (Boolean) -> Unit,
  onAskAiQuestion: (String) -> Unit,
  onExecuteLunoOrder: (Boolean) -> Unit,
  onShowBanner: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = LocalQuantColors.current
  val context = LocalContext.current
  val snapshot = uiState.activeSnapshot
  var pendingOrderIsBuy by remember { mutableStateOf<Boolean?>(null) }

  val notificationPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      onToggleAutoPilot(true)
      if (!granted) {
        onShowBanner("Auto-Monitor aktif, tetapi izin notifikasi ditolak (amaran skrin kekal aktif).")
      }
    }

  fun handleAutoPilotToggle(enable: Boolean) {
    if (!enable) {
      onToggleAutoPilot(false)
      return
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val granted =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
          PackageManager.PERMISSION_GRANTED
      if (granted) {
        onToggleAutoPilot(true)
      } else {
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
      }
    } else {
      onToggleAutoPilot(true)
    }
  }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item {
        TerminalTopHeader(
          isLive = uiState.isLiveStreamEnabled,
          isRefreshing = uiState.isRefreshing,
          hasError = uiState.marketState is MarketUiState.Error,
          onToggleLive = onToggleLive,
          onRefresh = onRefresh,
        )
      }

      item {
        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          items(TradingPair.entries) { pair ->
            val selected = pair == uiState.selectedPair
            FilterChip(
              selected = selected,
              onClick = { onSelectPair(pair) },
              label = {
                Text(
                  text = pair.pairCode,
                  style = QuantMonoSmall,
                )
              },
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
              modifier = Modifier.testTag("pair_chip_${pair.pairCode.lowercase()}"),
            )
          }
        }
      }

      if (uiState.marketState is MarketUiState.Error) {
        item {
          val isOfflineWithCache = uiState.isOfflineDataMode && snapshot != null
          Surface(
            color = if (isOfflineWithCache) colors.amberContainer else colors.crimsonContainer,
            shape = MaterialTheme.shapes.medium,
            border =
              BorderStroke(
                1.dp,
                (if (isOfflineWithCache) colors.amber else colors.crimson).copy(alpha = 0.6f),
              ),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text =
                    if (isOfflineWithCache) "MOD OFFLINE AKTIF (DATA CACHE)"
                    else "Luno Connection Interrupted",
                  style = MaterialTheme.typography.titleMedium,
                  color = if (isOfflineWithCache) colors.amber else colors.crimsonBright,
                )
                Text(
                  text =
                    if (isOfflineWithCache) {
                      "Tiada sambungan internet. Aplikasi menggunakan harga cache terakhir & enjin Offline Quant AI."
                    } else {
                      uiState.marketState.message
                    },
                  style = MaterialTheme.typography.bodySmall,
                  color = colors.textPrimary,
                )
              }
              Spacer(modifier = Modifier.width(8.dp))
              OutlinedButton(
                onClick = onRefresh,
                modifier = Modifier.testTag("retry_connection_button"),
              ) {
                Text(if (isOfflineWithCache) "Sambung" else "Retry", color = colors.textPrimary)
              }
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
            Column(
              modifier = Modifier.fillMaxWidth().padding(32.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              CircularProgressIndicator(color = colors.cyan)
              Text(
                text = "Connecting to api.luno.com (${uiState.selectedPair.pairCode})...",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
              )
            }
          }
        }
      } else {
        item {
          LiveTickerHeroCard(snapshot = snapshot)
        }

        item {
          AiCopilotAndAutoMonitorCard(
            snapshot = snapshot,
            uiState = uiState,
            onRequestAiAnalysis = onRequestAiAnalysis,
            onTogglePreferOfflineAi = onTogglePreferOfflineAi,
            onToggleAutoPilot = ::handleAutoPilotToggle,
            onAskAiQuestion = onAskAiQuestion,
            onPrepareLunoOrder = { isBuy -> pendingOrderIsBuy = isBuy },
          )
        }

        item {
          QuantSignalGaugeCard(
            snapshot = snapshot,
            onNavigateToSignals = onNavigateToSignals,
          )
        }

        item {
          RiskAndOrderBookCard(
            snapshot = snapshot,
            uiState = uiState,
            onOpenPositionCalculator = onOpenPositionCalculator,
          )
        }

        uiState.setupChecklist?.let { setup ->
          item {
            HighProbabilitySetupCard(
              snapshot = snapshot,
              setup = setup,
            )
          }
        }

        item {
          ProfitGoalTrackerCard(
            uiState = uiState,
            snapshot = snapshot,
            onCloseTradePlan = onCloseTradePlan,
            onDeleteTradePlan = onDeleteTradePlan,
          )
        }

        item {
          Surface(
            color = colors.slate.copy(alpha = 0.65f),
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(1.dp, colors.border.copy(alpha = 0.6f)),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(12.dp),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              verticalAlignment = Alignment.Top,
            ) {
              Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = "Quantitative risk notice",
                tint = colors.textMuted,
                modifier = Modifier.size(18.dp),
              )
              Text(
                text = stringResource(R.string.risk_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
              )
            }
          }
        }
      }
    }

    val confirmIsBuy = pendingOrderIsBuy
    if (confirmIsBuy != null && snapshot != null) {
      LunoOrderConfirmationDialog(
        snapshot = snapshot,
        uiState = uiState,
        isBuy = confirmIsBuy,
        onConfirm = {
          pendingOrderIsBuy = null
          onExecuteLunoOrder(confirmIsBuy)
        },
        onDismiss = { pendingOrderIsBuy = null },
      )
    }
  }
}

@Composable
private fun TerminalTopHeader(
  isLive: Boolean,
  isRefreshing: Boolean,
  hasError: Boolean,
  onToggleLive: () -> Unit,
  onRefresh: () -> Unit,
) {
  val colors = LocalQuantColors.current
  val statusColor =
    when {
      hasError -> colors.crimson
      isLive -> colors.emerald
      else -> colors.amber
    }

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column {
      Text(
        text = "QUANTPULSE TERMINAL",
        style = MaterialTheme.typography.labelSmall,
        color = colors.cyan,
      )
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Box(
          modifier =
            Modifier.size(8.dp).clip(CircleShape).background(statusColor),
        )
        Text(
          text =
            when {
              hasError -> "OFFLINE / RETRYING"
              isLive -> "LUNO LIVE FEED (15s)"
              else -> "LIVE STREAM PAUSED"
            },
          style = MaterialTheme.typography.labelMedium,
          color = colors.textSecondary,
        )
      }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(
        onClick = onToggleLive,
        modifier = Modifier.testTag("toggle_live_stream_button"),
      ) {
        Icon(
          imageVector = if (isLive) Icons.Outlined.PauseCircle else Icons.Outlined.PlayCircle,
          contentDescription = if (isLive) "Pause live polling" else "Resume live polling",
          tint = if (isLive) colors.cyanBright else colors.textSecondary,
        )
      }
      IconButton(
        onClick = onRefresh,
        modifier = Modifier.testTag("refresh_market_button"),
      ) {
        if (isRefreshing) {
          CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = colors.cyan,
          )
        } else {
          Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = "Refresh Luno market feed",
            tint = colors.textPrimary,
          )
        }
      }
    }
  }
}

@Composable
private fun LiveTickerHeroCard(snapshot: MarketSnapshot) {
  val colors = LocalQuantColors.current
  val pair = snapshot.pair
  val isUp = snapshot.change24hPct >= 0.0
  val tickShiftUp =
    snapshot.previousPrice != null && snapshot.lastPrice >= snapshot.previousPrice
  val shiftBadgeColor by
    animateColorAsState(
      targetValue = if (isUp) colors.emeraldContainer else colors.crimsonContainer,
      label = "shift_bg",
    )
  val shiftTextColor = if (isUp) colors.emeraldBright else colors.crimsonBright

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
        verticalAlignment = Alignment.Top,
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            text = "${pair.displayName} • ${snapshot.status}",
            style = MaterialTheme.typography.labelLarge,
            color = colors.textSecondary,
          )
          Text(
            text = FormatUtils.formatCurrency(snapshot.lastPrice, pair.quoteSymbol, pair.priceDecimals),
            style = QuantMonoLarge,
            color =
              if (snapshot.previousPrice == null) colors.textPrimary
              else if (tickShiftUp) colors.emeraldBright
              else colors.crimsonBright,
          )
        }

        Surface(
          color = shiftBadgeColor,
          shape = MaterialTheme.shapes.small,
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(
              imageVector =
                if (isUp) Icons.AutoMirrored.Filled.TrendingUp
                else Icons.AutoMirrored.Filled.TrendingDown,
              contentDescription = if (isUp) "Price trend up" else "Price trend down",
              tint = shiftTextColor,
              modifier = Modifier.size(16.dp),
            )
            Text(
              text = FormatUtils.formatSignedPct(snapshot.change24hPct),
              style = QuantMonoSmall,
              color = shiftTextColor,
            )
          }
        }
      }

      MiniPriceSparkline(
        closes = snapshot.candles.map { it.close },
        isPositive = isUp,
        modifier = Modifier.fillMaxWidth().height(56.dp),
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        MetricPill(
          label = "BEST BID",
          value = FormatUtils.formatCurrency(snapshot.bid, pair.quoteSymbol, pair.priceDecimals),
          valueColor = colors.emeraldBright,
        )
        MetricPill(
          label = "BEST ASK",
          value = FormatUtils.formatCurrency(snapshot.ask, pair.quoteSymbol, pair.priceDecimals),
          valueColor = colors.crimsonBright,
        )
        MetricPill(
          label = "24H VOL",
          value = FormatUtils.formatUnits(snapshot.volume24h, pair.baseAsset),
          valueColor = colors.textPrimary,
        )
      }
    }
  }
}

@Composable
private fun MetricPill(
  label: String,
  value: String,
  valueColor: androidx.compose.ui.graphics.Color,
  modifier: Modifier = Modifier,
) {
  val colors = LocalQuantColors.current
  Column(modifier = modifier) {
    Text(text = label, style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
    Text(text = value, style = QuantMonoSmall, color = valueColor)
  }
}

@Composable
private fun QuantSignalGaugeCard(
  snapshot: MarketSnapshot,
  onNavigateToSignals: () -> Unit,
) {
  val colors = LocalQuantColors.current
  val signal = snapshot.signal

  val actionColor =
    when (signal.action) {
      SignalAction.STRONG_BUY, SignalAction.ACCUMULATE -> colors.emeraldBright
      SignalAction.SPECULATIVE_BUY -> colors.amber
      SignalAction.STRONG_SELL -> colors.crimsonBright
      SignalAction.HOLD -> colors.cyanBright
    }
  val actionBg =
    when (signal.action) {
      SignalAction.STRONG_BUY, SignalAction.ACCUMULATE -> colors.emeraldContainer
      SignalAction.SPECULATIVE_BUY -> colors.amberContainer
      SignalAction.STRONG_SELL -> colors.crimsonContainer
      SignalAction.HOLD -> colors.cyanContainer
    }

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
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "QUANTITATIVE SIGNAL ENGINE",
            style = MaterialTheme.typography.labelSmall,
            color = colors.cyan,
          )
          Text(
            text = signal.action.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
          )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
          color = actionBg,
          shape = MaterialTheme.shapes.small,
          border = BorderStroke(1.dp, actionColor.copy(alpha = 0.55f)),
        ) {
          Text(
            text = signal.action.label,
            style = MaterialTheme.typography.labelLarge,
            color = actionColor,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
          )
        }
      }

      RsiSpeedometerGauge(rsiValue = signal.rsi14)

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Surface(
          modifier = Modifier.weight(1f),
          color = colors.charcoal,
          shape = MaterialTheme.shapes.small,
          border = BorderStroke(1.dp, colors.border),
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Text(
              text = "EMA 50 / 200 FILTER",
              style = MaterialTheme.typography.labelSmall,
              color = colors.textMuted,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = if (signal.isGoldenCross) "GOLDEN CROSS" else "BEARISH CROSS",
              style = MaterialTheme.typography.labelLarge,
              color = if (signal.isGoldenCross) colors.emeraldBright else colors.crimsonBright,
            )
            Text(
              text =
                "50: ${FormatUtils.formatNumber(signal.ema50, snapshot.pair.priceDecimals)} | 200: ${FormatUtils.formatNumber(signal.ema200, snapshot.pair.priceDecimals)}",
              style = MaterialTheme.typography.bodySmall,
              color = colors.textSecondary,
            )
          }
        }

        Surface(
          modifier = Modifier.weight(1f),
          color = colors.charcoal,
          shape = MaterialTheme.shapes.small,
          border = BorderStroke(1.dp, colors.border),
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Text(
              text = "CONFLUENCE SCORE",
              style = MaterialTheme.typography.labelSmall,
              color = colors.textMuted,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "${signal.confluenceScore} / 100",
              style = QuantMonoMedium,
              color = actionColor,
            )
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
              progress = { signal.confluenceScore / 100f },
              color = actionColor,
              trackColor = colors.slateElevated,
              modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            )
          }
        }
      }

      OutlinedButton(
        onClick = onNavigateToSignals,
        border = BorderStroke(1.dp, colors.cyan.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("open_full_charts_button"),
      ) {
        Text("Inspect EMA & Orderbook Charts", color = colors.cyanBright)
      }
    }
  }
}

@Composable
private fun RiskAndOrderBookCard(
  snapshot: MarketSnapshot,
  uiState: QuantPulseUiState,
  onOpenPositionCalculator: () -> Unit,
) {
  val colors = LocalQuantColors.current
  val pos = uiState.positionSizeResult
  val ob = snapshot.orderBook
  val bidPct = (ob.bidImbalanceRatio * 100.0).toInt()

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
            imageVector = Icons.Default.Shield,
            contentDescription = "Risk Engine Active",
            tint = colors.emeraldBright,
          )
          Column {
            Text(
              text = "POSITION SIZING & ORDERBOOK",
              style = MaterialTheme.typography.labelSmall,
              color = colors.emeraldBright,
            )
            Text(
              text = "Spread ${String.format("%.3f", snapshot.spreadPct)}% • Bid Pressure $bidPct%",
              style = MaterialTheme.typography.bodySmall,
              color = colors.textSecondary,
            )
          }
        }
      }

      // Bid/Ask pressure bar
      LinearProgressIndicator(
        progress = { ob.bidImbalanceRatio.toFloat().coerceIn(0.05f, 0.95f) },
        color = colors.emerald,
        trackColor = colors.crimson,
        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = "Max Order: ${FormatUtils.formatCurrency(pos.spotCappedPositionSize, snapshot.pair.quoteSymbol, 0)}",
          style = QuantMonoSmall,
          color = colors.textPrimary,
        )
        Text(
          text = "Risk Cap: ${String.format("%.1f", pos.riskTolerancePct)}% (${FormatUtils.formatCurrency(pos.riskCapitalAmount, snapshot.pair.quoteSymbol, 0)})",
          style = QuantMonoSmall,
          color = colors.amber,
        )
      }

      Button(
        onClick = onOpenPositionCalculator,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = colors.cyan,
            contentColor = colors.charcoalDeep,
          ),
        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("open_position_calculator_button"),
      ) {
        Icon(
          imageVector = Icons.Default.Calculate,
          contentDescription = "Open Position Size Calculator",
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("Calculate Order Size (1%–2% Risk)", style = MaterialTheme.typography.labelLarge)
      }
    }
  }
}

@Composable
private fun HighProbabilitySetupCard(
  snapshot: MarketSnapshot,
  setup: HighProbabilitySetup,
) {
  val colors = LocalQuantColors.current
  val pair = snapshot.pair
  val badgeColor =
    when (setup.passedCount) {
      4 -> colors.emeraldBright
      3 -> colors.cyanBright
      2 -> colors.amber
      else -> colors.crimsonBright
    }

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
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "A+ LUNO ENTRY CHECKLIST",
            style = MaterialTheme.typography.labelSmall,
            color = colors.cyan,
          )
          Text(
            text = setup.readinessLabel,
            style = MaterialTheme.typography.titleMedium,
            color = badgeColor,
          )
        }
        Surface(
          color = colors.charcoal,
          shape = MaterialTheme.shapes.small,
          border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f)),
        ) {
          Text(
            text = "${setup.passedCount}/${setup.totalCount} PASS",
            style = QuantMonoSmall,
            color = badgeColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
          )
        }
      }

      setup.conditions.forEach { cond ->
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.Top,
        ) {
          Icon(
            imageVector =
              if (cond.passed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = if (cond.passed) "Condition passed" else "Condition waiting",
            tint = if (cond.passed) colors.emeraldBright else colors.textMuted,
            modifier = Modifier.size(18.dp),
          )
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = cond.title,
              style = MaterialTheme.typography.labelLarge,
              color = if (cond.passed) colors.textPrimary else colors.textSecondary,
            )
            Text(
              text = cond.detail,
              style = MaterialTheme.typography.bodySmall,
              color = colors.textMuted,
            )
          }
        }
      }

      HorizontalDivider(color = colors.border)

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column {
          Text(
            text = "MAKER LIMIT BID (LOW FEE)",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textMuted,
          )
          Text(
            text = FormatUtils.formatCurrency(setup.suggestedLimitPrice, pair.quoteSymbol, pair.priceDecimals),
            style = QuantMonoSmall,
            color = colors.cyanBright,
          )
        }
        Column(horizontalAlignment = Alignment.End) {
          Text(
            text = "FEE BREAKEVEN",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textMuted,
          )
          Text(
            text = FormatUtils.formatCurrency(setup.feeAdjustedBreakeven, pair.quoteSymbol, pair.priceDecimals),
            style = QuantMonoSmall,
            color = colors.emeraldBright,
          )
        }
      }
    }
  }
}

@Composable
private fun ProfitGoalTrackerCard(
  uiState: QuantPulseUiState,
  snapshot: MarketSnapshot,
  onCloseTradePlan: (String, Double) -> Unit,
  onDeleteTradePlan: (String) -> Unit,
) {
  val colors = LocalQuantColors.current
  val goalPct = uiState.preferences.profitGoalPct
  val currentRoi = uiState.realizedAndUnrealizedRoiPct
  val progress = (currentRoi / goalPct).toFloat().coerceIn(0f, 1f)
  val winRate = uiState.winRatePct

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
            imageVector = Icons.Default.Flag,
            contentDescription = "Profit Goal Tracker",
            tint = colors.emeraldBright,
          )
          Column {
            Text(
              text = "+${goalPct.toInt()}% PORTFOLIO GROWTH TRACKER",
              style = MaterialTheme.typography.labelSmall,
              color = colors.emeraldBright,
            )
            Text(
              text =
                if (winRate != null) {
                  "Logged Win Rate: ${String.format("%.0f", winRate)}% • ${uiState.tradePlans.size} trades"
                } else {
                  "Log trades from Position Sizer to track real ROI"
                },
              style = MaterialTheme.typography.bodySmall,
              color = colors.textSecondary,
            )
          }
        }
        Text(
          text = FormatUtils.formatSignedPct(currentRoi),
          style = QuantMonoMedium,
          color = if (currentRoi >= 0.0) colors.emeraldBright else colors.crimsonBright,
        )
      }

      LinearProgressIndicator(
        progress = { progress },
        color = colors.emeraldBright,
        trackColor = colors.slateElevated,
        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
      )

      if (uiState.tradePlans.isEmpty()) {
        Text(
          text = "Tip: Wait for 3–4/4 checklist signals above, tap 'Calculate Order Size', then 'Save Rules & Log Trade Plan' to track live Luno P&L toward +${goalPct.toInt()}%.",
          style = MaterialTheme.typography.bodySmall,
          color = colors.textMuted,
        )
      } else {
        uiState.tradePlans.take(5).forEach { plan ->
          TradePlanRow(
            plan = plan,
            snapshot = snapshot,
            onCloseTradePlan = onCloseTradePlan,
            onDeleteTradePlan = onDeleteTradePlan,
          )
        }
      }
    }
  }
}

@Composable
private fun TradePlanRow(
  plan: TradePlanRecord,
  snapshot: MarketSnapshot,
  onCloseTradePlan: (String, Double) -> Unit,
  onDeleteTradePlan: (String) -> Unit,
) {
  val colors = LocalQuantColors.current
  val pair = TradingPair.fromCode(plan.pairCode)
  val markPrice =
    when (plan.status) {
      TradePlanStatus.OPEN ->
        if (snapshot.pair.pairCode == plan.pairCode) snapshot.lastPrice else plan.entryPrice
      TradePlanStatus.CLOSED_WIN, TradePlanStatus.CLOSED_LOSS ->
        plan.exitPrice ?: plan.entryPrice
    }
  val pnlAmt = (markPrice - plan.entryPrice) * plan.units
  val pnlPct = if (plan.entryPrice > 0.0) ((markPrice - plan.entryPrice) / plan.entryPrice) * 100.0 else 0.0
  val pnlColor = if (pnlAmt >= 0.0) colors.emeraldBright else colors.crimsonBright

  Surface(
    color = colors.charcoal,
    shape = MaterialTheme.shapes.small,
    border = BorderStroke(1.dp, colors.border),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "${plan.pairCode} • ${plan.status.name.replace('_', ' ')}",
          style = MaterialTheme.typography.labelLarge,
          color = colors.textPrimary,
        )
        Text(
          text = "${FormatUtils.formatSignedPct(pnlPct)} (${FormatUtils.formatCurrency(pnlAmt, pair.quoteSymbol, 0)})",
          style = QuantMonoSmall,
          color = pnlColor,
        )
      }

      Text(
        text =
          "Entry ${FormatUtils.formatNumber(plan.entryPrice, pair.priceDecimals)} | SL ${FormatUtils.formatNumber(plan.stopLossPrice, pair.priceDecimals)} | TP ${FormatUtils.formatNumber(plan.takeProfitPrice, pair.priceDecimals)}",
        style = MaterialTheme.typography.bodySmall,
        color = colors.textSecondary,
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (plan.status == TradePlanStatus.OPEN && snapshot.pair.pairCode == plan.pairCode) {
          OutlinedButton(
            onClick = { onCloseTradePlan(plan.id, snapshot.lastPrice) },
            border = BorderStroke(1.dp, colors.cyan.copy(alpha = 0.6f)),
            modifier = Modifier.testTag("close_trade_${plan.id}"),
          ) {
            Text("Close @ Live", style = MaterialTheme.typography.labelSmall, color = colors.cyanBright)
          }
          Spacer(modifier = Modifier.width(8.dp))
        }
        IconButton(
          onClick = { onDeleteTradePlan(plan.id) },
          modifier = Modifier.testTag("delete_trade_${plan.id}"),
        ) {
          Icon(
            imageVector = Icons.Default.DeleteOutline,
            contentDescription = "Delete trade plan",
            tint = colors.textMuted,
          )
        }
      }
    }
  }
}

@Composable
private fun AiCopilotAndAutoMonitorCard(
  snapshot: MarketSnapshot,
  uiState: QuantPulseUiState,
  onRequestAiAnalysis: () -> Unit,
  onTogglePreferOfflineAi: () -> Unit,
  onToggleAutoPilot: (Boolean) -> Unit,
  onAskAiQuestion: (String) -> Unit,
  onPrepareLunoOrder: (Boolean) -> Unit,
) {
  val colors = LocalQuantColors.current
  val advice = uiState.aiAdvice
  val pair = snapshot.pair
  var showGuide by remember { mutableStateOf(false) }
  var questionInput by remember { mutableStateOf("") }

  val verdictColor =
    when (advice?.verdict) {
      AiVerdictType.BUY -> colors.emeraldBright
      AiVerdictType.SELL -> colors.crimsonBright
      AiVerdictType.HOLD, null -> colors.cyanBright
    }
  val verdictBg =
    when (advice?.verdict) {
      AiVerdictType.BUY -> colors.emeraldContainer
      AiVerdictType.SELL -> colors.crimsonContainer
      AiVerdictType.HOLD, null -> colors.cyanContainer
    }
  val verdictLabel =
    when (advice?.verdict) {
      AiVerdictType.BUY -> "BELI / BUY"
      AiVerdictType.SELL -> "JUAL / SELL"
      AiVerdictType.HOLD -> "TUNGGU / HOLD"
      null -> "MENGANALISIS"
    }

  Surface(
    color = colors.slate,
    shape = MaterialTheme.shapes.large,
    border = BorderStroke(1.dp, colors.cyan.copy(alpha = 0.45f)),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // Header + Online/Offline AI Mode Chip
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.weight(1f),
        ) {
          Icon(
            imageVector =
              if (uiState.preferOfflineAi || uiState.isOfflineDataMode) Icons.Default.CloudOff
              else Icons.Default.AutoAwesome,
            contentDescription = "AI Copilot Engine",
            tint = colors.cyanBright,
          )
          Column {
            Text(
              text = "AI COPILOT LUNO (ONLINE + OFFLINE)",
              style = MaterialTheme.typography.labelSmall,
              color = colors.cyanBright,
            )
            Text(
              text = advice?.source?.label ?: "Sedia menganalisis...",
              style = QuantMonoSmall,
              color = colors.textSecondary,
            )
          }
        }

        FilterChip(
          selected = uiState.preferOfflineAi,
          onClick = onTogglePreferOfflineAi,
          label = {
            Text(
              text = if (uiState.preferOfflineAi) "Offline Sahaja" else "Auto Online/Offline",
              style = MaterialTheme.typography.labelSmall,
            )
          },
          colors =
            FilterChipDefaults.filterChipColors(
              containerColor = colors.charcoal,
              labelColor = colors.textSecondary,
              selectedContainerColor = colors.amberContainer,
              selectedLabelColor = colors.amber,
            ),
          border =
            FilterChipDefaults.filterChipBorder(
              enabled = true,
              selected = uiState.preferOfflineAi,
              borderColor = colors.border,
              selectedBorderColor = colors.amber,
            ),
          modifier = Modifier.testTag("toggle_offline_ai_chip"),
        )
      }

      // Expandable "Cara Guna AI" Guide
      TextButton(
        onClick = { showGuide = !showGuide },
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
        modifier = Modifier.testTag("toggle_ai_guide_button"),
      ) {
        Icon(
          imageVector = Icons.Default.HelpOutline,
          contentDescription = "Panduan cara guna AI",
          tint = colors.cyanBright,
          modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = "Cara Guna AI & Auto-Monitor",
          style = MaterialTheme.typography.labelMedium,
          color = colors.cyanBright,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
          imageVector = if (showGuide) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
          contentDescription = if (showGuide) "Tutup panduan" else "Buka panduan",
          tint = colors.cyanBright,
          modifier = Modifier.size(16.dp),
        )
      }

      AnimatedVisibility(visible = showGuide) {
        Surface(
          color = colors.charcoal,
          shape = MaterialTheme.shapes.small,
          border = BorderStroke(1.dp, colors.cyan.copy(alpha = 0.35f)),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              text = "PANDUAN PANTAS AI QUANTPULSE:",
              style = MaterialTheme.typography.labelSmall,
              color = colors.cyanBright,
            )
            Text(
              text = "1. Pilih koin (cth. BTC/MYR) di atas — AI terus menilai harga Luno, RSI & EMA secara automatik (Online Gemini atau Offline jika tiada data).",
              style = MaterialTheme.typography.bodySmall,
              color = colors.textSecondary,
            )
            Text(
              text = "2. Hidupkan suis Auto-Monitor supaya aplikasi memantau 24/7, mencatat entri A+ simulasi & memberi notifikasi TP/SL automatik.",
              style = MaterialTheme.typography.bodySmall,
              color = colors.textSecondary,
            )
            Text(
              text = "3. Tanya soalan di ruangan 'Tanya AI' di bawah untuk nasihat SL/TP atau strategi modal kecil.",
              style = MaterialTheme.typography.bodySmall,
              color = colors.textSecondary,
            )
            Text(
              text = "4. Apabila status BELI/JUAL keluar, tekan 'Beli/Jual Limit Luno' & sahkan (simpan API Key Luno dalam tab Vault terlebih dahulu).",
              style = MaterialTheme.typography.bodySmall,
              color = colors.textSecondary,
            )
          }
        }
      }

      if (advice != null) {
        // Verdict + Confidence Pill Row
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Surface(
            color = verdictBg,
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(1.dp, verdictColor.copy(alpha = 0.6f)),
          ) {
            Text(
              text = verdictLabel,
              style = MaterialTheme.typography.labelLarge,
              color = verdictColor,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
          }
          Text(
            text = "Keyakinan AI: ${advice.confidencePct}%",
            style = QuantMonoSmall,
            color = verdictColor,
          )
        }

        Text(
          text = advice.headline,
          style = MaterialTheme.typography.titleMedium,
          color = colors.textPrimary,
        )
        Text(
          text = advice.reasoning,
          style = MaterialTheme.typography.bodySmall,
          color = colors.textSecondary,
        )

        advice.onlineErrorNote?.let { note ->
          Surface(
            color = colors.amberContainer,
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(1.dp, colors.amber.copy(alpha = 0.45f)),
          ) {
            Text(
              text = note,
              style = MaterialTheme.typography.bodySmall,
              color = colors.amber,
              modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            )
          }
        }

        // Suggested Maker Limit / Stop-Loss / Take-Profit Levels
        Surface(
          color = colors.charcoal,
          shape = MaterialTheme.shapes.small,
          border = BorderStroke(1.dp, colors.border),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            MetricPill(
              label = "LIMIT ENTRY",
              value = FormatUtils.formatCurrency(advice.entryLimitPrice, pair.quoteSymbol, pair.priceDecimals),
              valueColor = colors.cyanBright,
              modifier = Modifier.weight(1f),
            )
            MetricPill(
              label = "STOP-LOSS",
              value = FormatUtils.formatCurrency(advice.stopLossPrice, pair.quoteSymbol, pair.priceDecimals),
              valueColor = colors.crimsonBright,
              modifier = Modifier.weight(1f),
            )
            MetricPill(
              label = "TAKE PROFIT",
              value = FormatUtils.formatCurrency(advice.takeProfitPrice, pair.quoteSymbol, pair.priceDecimals),
              valueColor = colors.emeraldBright,
              modifier = Modifier.weight(1f),
            )
          }
        }

        val liveWalletAsset =
          (uiState.portfolioState as? PortfolioUiState.Success)
            ?.assets
            ?.firstOrNull { it.asset == pair.quoteAsset }
        if (liveWalletAsset != null) {
          Text(
            text =
              "Baki Luno (${pair.quoteAsset}): ${FormatUtils.formatNumber(liveWalletAsset.available, 2)} • Modal Aktif: ${FormatUtils.formatCurrency(uiState.preferences.totalCapitalMyr, pair.quoteSymbol, 0)}",
            style = QuantMonoSmall,
            color = colors.emeraldBright,
          )
        }

        if (advice.actionSteps.isNotEmpty()) {
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            advice.actionSteps.forEachIndexed { idx, step ->
              Text(
                text = "${idx + 1}. $step",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
              )
            }
          }
        }
      } else if (uiState.isAiLoading) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.cyan)
          Text(
            text = "AI sedang menilai RSI, EMA & Orderbook Luno...",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
          )
        }
      }

      // Action Buttons: Refresh AI Analysis & Confirmed Luno Limit Order
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedButton(
          onClick = onRequestAiAnalysis,
          enabled = !uiState.isAiLoading,
          border = BorderStroke(1.dp, colors.cyan.copy(alpha = 0.6f)),
          modifier = Modifier.weight(1f).height(48.dp).testTag("refresh_ai_advice_button"),
        ) {
          if (uiState.isAiLoading) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.cyan)
            Spacer(modifier = Modifier.width(6.dp))
          }
          Text("Analisis AI", color = colors.cyanBright, style = MaterialTheme.typography.labelLarge)
        }

        val canTrade =
          advice != null &&
            (advice.verdict == AiVerdictType.BUY || advice.verdict == AiVerdictType.SELL) &&
            !uiState.isOrderSubmitting
        val isBuyVerdict = advice?.verdict != AiVerdictType.SELL

        Button(
          onClick = { onPrepareLunoOrder(isBuyVerdict) },
          enabled = canTrade,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = if (isBuyVerdict) colors.emerald else colors.crimson,
              contentColor = colors.charcoalDeep,
              disabledContainerColor = colors.slateElevated,
              disabledContentColor = colors.textMuted,
            ),
          modifier = Modifier.weight(1f).height(48.dp).testTag("prepare_luno_order_button"),
        ) {
          if (uiState.isOrderSubmitting) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
          } else {
            Icon(
              imageVector = Icons.Default.Send,
              contentDescription = "Hantar Limit Order ke Luno",
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text =
                when (advice?.verdict) {
                  AiVerdictType.BUY -> "Beli Limit Luno"
                  AiVerdictType.SELL -> "Jual Limit Luno"
                  else -> "Tunggu Isyarat"
                },
              style = MaterialTheme.typography.labelLarge,
            )
          }
        }
      }

      HorizontalDivider(color = colors.border)

      // Interactive "Tanya AI Trading" Streaming Q&A (Online + Offline)
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = "TANYA AI TRADING (ONLINE / OFFLINE)",
          style = MaterialTheme.typography.labelSmall,
          color = colors.cyan,
        )

        val quickPrompts =
          listOf(
            "Sesuai beli sekarang?",
            "Di mana SL & TP?",
            "Strategi modal kecil?",
          )
        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          items(quickPrompts) { prompt ->
            SuggestionChip(
              onClick = {
                questionInput = prompt
                onAskAiQuestion(prompt)
              },
              label = {
                Text(text = prompt, style = MaterialTheme.typography.labelSmall)
              },
              colors =
                SuggestionChipDefaults.suggestionChipColors(
                  containerColor = colors.charcoal,
                  labelColor = colors.cyanBright,
                ),
              border = BorderStroke(1.dp, colors.cyan.copy(alpha = 0.4f)),
            )
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OutlinedTextField(
            value = questionInput,
            onValueChange = { questionInput = it },
            placeholder = {
              Text(
                text = "Tanya pasal ${pair.displayName} (cth: patut hold ke?)",
                style = MaterialTheme.typography.bodySmall,
              )
            },
            singleLine = true,
            modifier = Modifier.weight(1f).testTag("ask_ai_input"),
          )
          IconButton(
            onClick = {
              val q = questionInput.trim()
              if (q.isNotEmpty()) {
                onAskAiQuestion(q)
              }
            },
            enabled = questionInput.isNotBlank() && !uiState.isAiChatStreaming,
            modifier = Modifier.size(48.dp).testTag("send_ai_question_button"),
          ) {
            if (uiState.isAiChatStreaming) {
              CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = colors.cyan)
            } else {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Hantar soalan ke AI",
                tint = if (questionInput.isNotBlank()) colors.cyanBright else colors.textMuted,
              )
            }
          }
        }

        if (!uiState.aiChatAnswer.isNullOrBlank() || uiState.isAiChatStreaming) {
          Surface(
            color = colors.charcoal,
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier.fillMaxWidth().testTag("ai_chat_response_box"),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = uiState.aiChatQuestion.orEmpty(),
                  style = MaterialTheme.typography.labelSmall,
                  color = colors.cyanBright,
                  modifier = Modifier.weight(1f),
                )
                uiState.aiChatSource?.let { src ->
                  Text(
                    text = src.label,
                    style = QuantMonoSmall,
                    color = colors.textMuted,
                  )
                }
              }
              Text(
                text = uiState.aiChatAnswer.orEmpty().ifBlank { "Menjana jawapan..." },
                style = MaterialTheme.typography.bodySmall,
                color = colors.textPrimary,
              )
            }
          }
        }
      }

      HorizontalDivider(color = colors.border)

      // Hands-Free Auto-Monitor & Paper Auto-Pilot Switch
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
            contentDescription = "Auto-Monitor & Paper Auto-Pilot",
            tint = if (uiState.preferences.autoPilotEnabled) colors.emeraldBright else colors.textMuted,
          )
          Column {
            Text(
              text = "Auto-Monitor & Paper Auto-Pilot",
              style = MaterialTheme.typography.labelLarge,
              color = colors.textPrimary,
            )
            Text(
              text = "Pantau harga langsung, log entri A+ & tutup TP/SL automatik + hantar notifikasi.",
              style = MaterialTheme.typography.bodySmall,
              color = colors.textMuted,
            )
          }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
          checked = uiState.preferences.autoPilotEnabled,
          onCheckedChange = { onToggleAutoPilot(it) },
          colors =
            SwitchDefaults.colors(
              checkedThumbColor = colors.charcoalDeep,
              checkedTrackColor = colors.emeraldBright,
              uncheckedThumbColor = colors.textMuted,
              uncheckedTrackColor = colors.charcoal,
            ),
          modifier = Modifier.testTag("auto_pilot_switch"),
        )
      }
    }
  }
}

@Composable
private fun LunoOrderConfirmationDialog(
  snapshot: MarketSnapshot,
  uiState: QuantPulseUiState,
  isBuy: Boolean,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  val colors = LocalQuantColors.current
  val pair = snapshot.pair
  val limitPrice = if (isBuy) snapshot.bid else snapshot.ask
  val pos =
    QuantPulseViewModelHelperPreview(
      totalCapital = uiState.preferences.totalCapitalMyr,
      riskPct = uiState.preferences.riskTolerancePct,
      stopLossPct = uiState.preferences.defaultStopLossPct,
      entryPrice = limitPrice,
    )
  val sideLabel = if (isBuy) "BELI LIMIT (POST-ONLY BID)" else "JUAL LIMIT (POST-ONLY ASK)"

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = colors.slate,
    titleContentColor = colors.textPrimary,
    textContentColor = colors.textSecondary,
    modifier = Modifier.testTag("luno_order_confirm_dialog"),
    title = {
      Text(
        text = "Sahkan Pesanan Luno (${pair.pairCode})",
        style = MaterialTheme.typography.titleMedium,
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Jenis: $sideLabel", style = QuantMonoSmall, color = colors.cyanBright)
        Text(
          text = "Harga Limit: ${FormatUtils.formatCurrency(limitPrice, pair.quoteSymbol, pair.priceDecimals)}",
          style = QuantMonoSmall,
          color = colors.textPrimary,
        )
        Text(
          text = "Anggaran Unit: ${FormatUtils.formatUnits(pos.unitsToAcquire.coerceAtLeast(pair.minOrderUnits), pair.baseAsset)} (${FormatUtils.formatCurrency(pos.spotCappedPositionSize, pair.quoteSymbol, 2)})",
          style = QuantMonoSmall,
          color = colors.textPrimary,
        )
        Text(
          text = "Had Risiko Ketat: ${String.format("%.1f", pos.riskTolerancePct)}% • SL: ${FormatUtils.formatCurrency(pos.stopLossPrice, pair.quoteSymbol, pair.priceDecimals)} • TP: ${FormatUtils.formatCurrency(pos.takeProfit2RPrice, pair.quoteSymbol, pair.priceDecimals)}",
          style = MaterialTheme.typography.bodySmall,
          color = colors.textSecondary,
        )
        val liveQuoteBal =
          (uiState.portfolioState as? PortfolioUiState.Success)
            ?.assets
            ?.firstOrNull { it.asset == pair.quoteAsset }
            ?.available
        if (liveQuoteBal != null) {
          Text(
            text = "Baki Luno Anda (${pair.quoteAsset}): ${FormatUtils.formatNumber(liveQuoteBal, 2)}",
            style = QuantMonoSmall,
            color = colors.emeraldBright,
          )
        }
        if (isBuy && pos.unitsToAcquire < pair.minOrderUnits) {
          val estMinCost = pair.minOrderUnits * limitPrice
          Text(
            text =
              "Nota Had Minimum Luno: ${pair.pairCode} memerlukan sekurang-kurangnya ${pair.minOrderUnits} ${pair.baseAsset} (~${FormatUtils.formatCurrency(estMinCost, pair.quoteSymbol, 2)}). Untuk modal kecil seperti RM 10, koin seperti XRP/MYR atau LTC/MYR lebih mudah melepasi had minimum.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.amber,
          )
        }
        if (!uiState.credentials.isConfigured) {
          Text(
            text = "Nota: Sila simpan API Key & Secret (kebenaran Trade) dalam tab Vault terlebih dahulu untuk menghantar pesanan sebenar ke Luno.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.amber,
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = onConfirm,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = if (isBuy) colors.emerald else colors.crimson,
            contentColor = colors.charcoalDeep,
          ),
        modifier = Modifier.testTag("confirm_luno_order_button"),
      ) {
        Text(if (isBuy) "Sahkan Beli" else "Sahkan Jual")
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.testTag("cancel_luno_order_button"),
      ) {
        Text("Batal", color = colors.textSecondary)
      }
    },
  )
}

private fun QuantPulseViewModelHelperPreview(
  totalCapital: Double,
  riskPct: Double,
  stopLossPct: Double,
  entryPrice: Double,
) = com.example.domain.QuantEngine.calculatePositionSize(
  totalCapital = totalCapital,
  requestedRiskTolerancePct = riskPct,
  stopLossPct = stopLossPct,
  entryPrice = entryPrice,
)


