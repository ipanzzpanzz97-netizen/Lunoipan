package com.example

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.firebase.configureAppCheck
import com.example.ui.QuantPulseViewModel
import com.example.ui.components.PositionSizingSheet
import com.example.ui.screens.DcaPlannerScreen
import com.example.ui.screens.SignalsScreen
import com.example.ui.screens.TerminalScreen
import com.example.ui.screens.VaultScreen
import com.example.ui.theme.LocalQuantColors
import com.example.ui.theme.QuantPulseTheme

enum class QuantRoute(
  val route: String,
  val labelRes: Int,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val testTag: String,
) {
  Terminal(
    route = "terminal",
    labelRes = R.string.nav_terminal,
    selectedIcon = Icons.AutoMirrored.Filled.ShowChart,
    unselectedIcon = Icons.AutoMirrored.Outlined.ShowChart,
    testTag = "nav_terminal",
  ),
  Signals(
    route = "signals",
    labelRes = R.string.nav_analysis,
    selectedIcon = Icons.Filled.Analytics,
    unselectedIcon = Icons.Outlined.Analytics,
    testTag = "nav_signals",
  ),
  Dca(
    route = "dca_planner",
    labelRes = R.string.nav_dca,
    selectedIcon = Icons.Filled.CalendarMonth,
    unselectedIcon = Icons.Outlined.CalendarMonth,
    testTag = "nav_dca",
  ),
  Vault(
    route = "vault",
    labelRes = R.string.nav_vault,
    selectedIcon = Icons.Filled.Lock,
    unselectedIcon = Icons.Outlined.Lock,
    testTag = "nav_vault",
  );
}

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    configureAppCheck(this, intent)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
    )
    setContent {
      QuantPulseTheme {
        QuantPulseApp()
      }
    }
  }
}

@Composable
fun QuantPulseApp(
  viewModel: QuantPulseViewModel = viewModel(),
) {
  val colors = LocalQuantColors.current
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val navController = rememberNavController()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = navBackStackEntry?.destination?.route ?: QuantRoute.Terminal.route
  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(uiState.statusBannerMessage) {
    val msg = uiState.statusBannerMessage
    if (!msg.isNullOrBlank()) {
      snackbarHostState.showSnackbar(msg)
      viewModel.clearStatusBanner()
    }
  }

  fun navigateTo(route: String) {
    navController.navigate(route) {
      popUpTo(navController.graph.findStartDestination().id) { saveState = true }
      launchSingleTop = true
      restoreState = true
    }
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useNavigationRail = maxWidth >= 600.dp

    Scaffold(
      modifier = Modifier.fillMaxSize(),
      containerColor = colors.charcoal,
      contentWindowInsets = WindowInsets.safeDrawing,
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
      bottomBar = {
        if (!useNavigationRail) {
          NavigationBar(
            containerColor = colors.charcoalDeep,
            contentColor = colors.textPrimary,
          ) {
            QuantRoute.entries.forEach { dest ->
              val selected = currentRoute == dest.route
              val label = stringResource(dest.labelRes)
              NavigationBarItem(
                selected = selected,
                onClick = { navigateTo(dest.route) },
                icon = {
                  Icon(
                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                    contentDescription = label,
                  )
                },
                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                colors =
                  NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.cyanBright,
                    selectedTextColor = colors.cyanBright,
                    indicatorColor = colors.cyanContainer,
                    unselectedIconColor = colors.textMuted,
                    unselectedTextColor = colors.textMuted,
                  ),
                modifier = Modifier.testTag(dest.testTag),
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
      ) {
        if (useNavigationRail) {
          NavigationRail(
            containerColor = colors.charcoalDeep,
            contentColor = colors.textPrimary,
            modifier = Modifier.fillMaxHeight().windowInsetsPadding(WindowInsets(0)),
          ) {
            QuantRoute.entries.forEach { dest ->
              val selected = currentRoute == dest.route
              val label = stringResource(dest.labelRes)
              NavigationRailItem(
                selected = selected,
                onClick = { navigateTo(dest.route) },
                icon = {
                  Icon(
                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                    contentDescription = label,
                  )
                },
                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                colors =
                  NavigationRailItemDefaults.colors(
                    selectedIconColor = colors.cyanBright,
                    selectedTextColor = colors.cyanBright,
                    indicatorColor = colors.cyanContainer,
                    unselectedIconColor = colors.textMuted,
                    unselectedTextColor = colors.textMuted,
                  ),
                modifier = Modifier.testTag(dest.testTag),
              )
            }
          }
        }

        NavHost(
          navController = navController,
          startDestination = QuantRoute.Terminal.route,
          modifier = Modifier.weight(1f).fillMaxHeight(),
        ) {
          composable(QuantRoute.Terminal.route) {
            TerminalScreen(
              uiState = uiState,
              onSelectPair = viewModel::selectPair,
              onRefresh = viewModel::refreshNow,
              onToggleLive = viewModel::toggleLiveStream,
              onOpenPositionCalculator = viewModel::openPositionCalculator,
              onNavigateToSignals = { navigateTo(QuantRoute.Signals.route) },
              onCloseTradePlan = viewModel::closeTradePlan,
              onDeleteTradePlan = viewModel::deleteTradePlan,
              onRequestAiAnalysis = viewModel::requestAiAnalysis,
              onTogglePreferOfflineAi = viewModel::togglePreferOfflineAi,
              onToggleAutoPilot = { viewModel.toggleAutoPilot(it) },
              onSetLiveAutoTradeEnabled = viewModel::setLiveAutoTradeEnabled,
              onSetLiveAutoTradeMaxOrderMyr = viewModel::setLiveAutoTradeMaxOrderMyr,
              onAskAiQuestion = viewModel::askAiQuestion,
              onExecuteLunoOrder = viewModel::executeConfirmedLunoOrder,
              onShowBanner = viewModel::showStatusMessage,
            )
          }

          composable(QuantRoute.Signals.route) {
            SignalsScreen(
              uiState = uiState,
              onSelectTimeframe = viewModel::selectTimeframe,
            )
          }

          composable(QuantRoute.Dca.route) {
            DcaPlannerScreen(
              uiState = uiState,
              onUpdateDca = viewModel::updateDcaPlanner,
              onSendTestAlert = viewModel::sendTestDcaAlert,
            )
          }

          composable(QuantRoute.Vault.route) {
            VaultScreen(
              uiState = uiState,
              onSaveCredentials = viewModel::saveLunoCredentials,
              onClearCredentials = viewModel::clearLunoCredentials,
              onRefreshBalances = viewModel::refreshPortfolioBalance,
              onSaveRiskRules = viewModel::saveRiskPreferences,
              onSyncCapitalFromLuno = viewModel::syncCapitalFromLunoBalance,
            )
          }
        }
      }
    }

    if (uiState.showPositionCalculator) {
      PositionSizingSheet(
        pair = uiState.selectedPair,
        totalCapital = uiState.calcTotalCapital,
        riskTolerancePct = uiState.calcRiskPct,
        stopLossPct = uiState.calcStopLossPct,
        result = uiState.positionSizeResult,
        onInputsChanged = { cap, risk, sl ->
          viewModel.updateCalculatorInputs(totalCapital = cap, riskPct = risk, stopLossPct = sl)
        },
        onSaveAsDefault = viewModel::saveRiskPreferences,
        onLogTradePlan = viewModel::logCurrentTradePlan,
        onDismiss = viewModel::closePositionCalculator,
      )
    }
  }
}
