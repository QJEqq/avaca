package com.mydrop.vpn.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mydrop.vpn.ui.screens.design.DesignPickerScreen
import com.mydrop.vpn.ui.screens.home.GitHomeScreen
import com.mydrop.vpn.core.model.AppDesign
import com.mydrop.vpn.ui.components.ImportConfirmDialog
import com.mydrop.vpn.ui.components.OpenfluxLoginScreen
import com.mydrop.vpn.ui.screens.apps.SplitTunnelScreen
import com.mydrop.vpn.ui.screens.dev.ConnectionLogScreen
import com.mydrop.vpn.ui.screens.dev.DiagnosticsScreen
import com.mydrop.vpn.ui.screens.dev.XrayConfigScreen
import com.mydrop.vpn.ui.screens.failover.NodePickerKind
import com.mydrop.vpn.ui.screens.failover.NodePickerScreen
import com.mydrop.vpn.ui.screens.home.HomeScreen
import com.mydrop.vpn.ui.screens.logs.LogsScreen
import com.mydrop.vpn.ui.screens.rules.CustomRulesScreen
import com.mydrop.vpn.ui.screens.scan.ScanScreen
import com.mydrop.vpn.ui.screens.settings.SettingsScreen
import com.mydrop.vpn.ui.screens.home.AddSheet

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val LOGS = "logs"
    const val SPLIT_TUNNEL = "split_tunnel"
    const val SCAN = "scan"
    const val FAILOVER = "failover"
    const val DIAGNOSTICS = "diagnostics"
    const val XRAY_CONFIG = "xray-config"
    const val CUSTOM_RULES = "custom-rules"
    const val CONNECTIONS = "connections"
}

/**
 * One screen and the pages behind it.
 *
 * There are no tabs: [HomeScreen] is the tunnel and the servers together, and everything else is
 * a page that slides in over it and slides back out — settings from the gear, the journal and the
 * per-app list from settings, the scanner from the add sheet.
 */
@Composable
fun MyDropApp(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val geoAssets by viewModel.geoAssets.collectAsStateWithLifecycle()
    val ipv6 by viewModel.ipv6.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    var showAddSheet by rememberSaveable { mutableStateOf(false) }

    val pendingImport by viewModel.pendingImport.collectAsStateWithLifecycle()
    val openfluxPrompt by viewModel.openfluxPrompt.collectAsStateWithLifecycle()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState, Modifier.navigationBarsPadding()) },
        // Every screen places itself around the system bars: the home screen draws its sky
        // under the status bar on purpose, and the pages take the insets as padding below.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { _ ->
        val pagePadding = PaddingValues(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        )

        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { pageEnter() },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(200)) },
            popExitTransition = { pageExit() },
        ) {
            composable(Routes.HOME) {
                if (state.settings.appDesign == AppDesign.Git) {
                    GitHomeScreen(
                        state = state,
                        onToggleConnection = viewModel::toggleConnection,
                        onSelect = viewModel::selectNode,
                        onPing = viewModel::pingNode,
                        onPingGroup = viewModel::pingNodes,
                        onRefreshAll = viewModel::refreshAllSubscriptions,
                        onRefreshSubscription = viewModel::refreshSubscription,
                        onRemoveSubscription = viewModel::removeSubscription,
                        onRemoveNode = viewModel::removeNode,
                        onSetTlsInsecure = viewModel::setTlsInsecure,
                        onToggleGroup = viewModel::toggleServerGroup,
                        onSort = { sort -> viewModel.updateSettings { it.copy(serverSort = sort) } },
                        onAdd = { showAddSheet = true },
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    )
                } else {
                    HomeScreen(
                        state = state,
                        onToggleConnection = viewModel::toggleConnection,
                        onSelect = viewModel::selectNode,
                        onPing = viewModel::pingNode,
                        onPingGroup = viewModel::pingNodes,
                        onRefreshAll = viewModel::refreshAllSubscriptions,
                        onRefreshSubscription = viewModel::refreshSubscription,
                        onRemoveSubscription = viewModel::removeSubscription,
                        onRemoveNode = viewModel::removeNode,
                        onSetTlsInsecure = viewModel::setTlsInsecure,
                        onToggleGroup = viewModel::toggleServerGroup,
                        onAdd = { showAddSheet = true },
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    )
                }
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    settings = state.settings,
                    splitTunnelAppCount = state.settings.splitTunnelPackages.size,
                    dnsProfiles = state.dnsProfiles,
                    selectedDnsId = state.selectedDnsId,
                    onSelectDns = viewModel::selectDns,
                    onRemoveDns = viewModel::removeDns,
                    onUpdate = viewModel::updateSettings,
                    onRoutingModeChange = viewModel::setRoutingMode,
                    onBack = { navController.popBackStack() },
                    onOpenLogs = { navController.navigate(Routes.LOGS) },
                    onOpenSplitTunnel = { navController.navigate(Routes.SPLIT_TUNNEL) },
                    onOpenFailover = { navController.navigate(Routes.FAILOVER) },
                    onOpenDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) },
                    onOpenXrayConfig = { navController.navigate(Routes.XRAY_CONFIG) },
                    onOpenCustomRules = { navController.navigate(Routes.CUSTOM_RULES) },
                    onOpenConnections = { navController.navigate(Routes.CONNECTIONS) },
                    geoAssets = geoAssets,
                    onRefreshGeo = viewModel::refreshGeoAssets,
                    ipv6 = ipv6,
                    contentPadding = pagePadding,
                )
            }

            composable(Routes.LOGS) {
                LogsScreen(
                    entries = logs,
                    onBack = { navController.popBackStack() },
                    onClear = viewModel::clearLogs,
                    contentPadding = pagePadding,
                )
            }

            composable(Routes.SCAN) {
                ScanScreen(
                    onResult = viewModel::importText,
                    onBack = { navController.popBackStack() },
                    contentPadding = pagePadding,
                )
            }

            composable(Routes.FAILOVER) {
                NodePickerScreen(
                    kind = NodePickerKind.Failover,
                    settings = state.settings,
                    nodes = state.nodes,
                    latencies = state.latencies,
                    onUpdate = viewModel::updateSettings,
                    onBack = { navController.popBackStack() },
                    contentPadding = pagePadding,
                )
            }

            composable(Routes.DIAGNOSTICS) {
                DiagnosticsScreen(
                    state = state,
                    onDiagnose = viewModel::diagnose,
                    onBack = { navController.popBackStack() },
                    contentPadding = pagePadding,
                )
            }

            composable(Routes.XRAY_CONFIG) {
                XrayConfigScreen(
                    state = state,
                    buildConfig = viewModel::xrayConfigFor,
                    onBack = { navController.popBackStack() },
                    contentPadding = pagePadding,
                )
            }

            composable(Routes.CUSTOM_RULES) {
                CustomRulesScreen(
                    settings = state.settings,
                    onUpdate = viewModel::updateSettings,
                    onBack = { navController.popBackStack() },
                    contentPadding = pagePadding,
                )
            }

            composable(Routes.CONNECTIONS) {
                val connections by viewModel.connections.collectAsStateWithLifecycle()
                ConnectionLogScreen(
                    connections = connections,
                    nodes = state.nodes,
                    onClear = viewModel::clearConnections,
                    onAddRule = viewModel::addCustomRule,
                    onBack = { navController.popBackStack() },
                    contentPadding = pagePadding,
                )
            }

            composable(Routes.SPLIT_TUNNEL) {
                SplitTunnelScreen(
                    settings = state.settings,
                    onUpdate = viewModel::updateSettings,
                    onBack = { navController.popBackStack() },
                    contentPadding = pagePadding,
                )
            }
        }
    }

    pendingImport?.let { pending ->
        ImportConfirmDialog(
            pending = pending,
            onConfirm = viewModel::confirmPendingImport,
            onDismiss = viewModel::dismissPendingImport,
        )
    }

    if (showAddSheet) {
        AddSheet(
            onDismiss = { showAddSheet = false },
            onAdd = viewModel::addFromText,
            onScan = {
                showAddSheet = false
                navController.navigate(Routes.SCAN)
            },
        )
    }

    // The first launch opens on the choice of design; once it is made, it is not asked again.
    if (!state.settings.designChosen) {
        DesignPickerScreen(
            onChoose = { design -> viewModel.updateSettings { it.copy(appDesign = design, designChosen = true) } },
        )
    }

    // Shown over everything, in this window (not a Dialog) so the sign-in form gets the keyboard.
    // Appears the moment a document transport (Yandex) needs the user to sign in, and goes away
    // on its own once the engine accepts the cookies.
    openfluxPrompt?.let { prompt ->
        OpenfluxLoginScreen(
            prompt = prompt,
            onSubmit = viewModel::submitOpenfluxCookies,
            onCancel = viewModel::cancelOpenfluxPrompt,
        )
    }
}

/** A page comes in from the right, over the screen it was opened from. */
private fun pageEnter(): EnterTransition =
    slideInHorizontally(tween(280)) { width -> width / 6 } + fadeIn(tween(220))

/** And goes back out the way it came. */
private fun pageExit(): ExitTransition =
    slideOutHorizontally(tween(220)) { width -> width / 6 } + fadeOut(tween(160))
