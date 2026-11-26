package com.erlkim.mikrotik

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Badge
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.erlkim.mikrotik.ui.components.AddUserDialog
import com.erlkim.mikrotik.ui.components.BackupRestoreSheet
import com.erlkim.mikrotik.ui.components.ErlkimBottomBar
import com.erlkim.mikrotik.ui.components.ErlkimBrandTitle
import com.erlkim.mikrotik.ui.components.ErlkimNavRail
import com.erlkim.mikrotik.ui.components.ErlkimTab
import com.erlkim.mikrotik.ui.components.QuotaAlertsSheet
import com.erlkim.mikrotik.ui.components.ResourceAlertsSheet
import com.erlkim.mikrotik.ui.components.RouterConnectionSheet
import com.erlkim.mikrotik.ui.components.ThroughputAlertsSheet
import com.erlkim.mikrotik.ui.components.UserQrCodeDialog
import com.erlkim.mikrotik.ui.screens.AboutAppSheet
import com.erlkim.mikrotik.ui.screens.ActiveSessionsSheet
import com.erlkim.mikrotik.ui.screens.DashboardFeature
import com.erlkim.mikrotik.ui.screens.DashboardScreen
import com.erlkim.mikrotik.ui.screens.DhcpServerSheet
import com.erlkim.mikrotik.ui.screens.HotspotFeature
import com.erlkim.mikrotik.ui.screens.HotspotScreen
import com.erlkim.mikrotik.ui.screens.HotspotServersSheet
import com.erlkim.mikrotik.ui.screens.HotspotUserDetailSheet
import com.erlkim.mikrotik.ui.screens.IdentityAndTimeSheet
import com.erlkim.mikrotik.ui.screens.InterfacesSheet
import com.erlkim.mikrotik.ui.screens.IpAddressesSheet
import com.erlkim.mikrotik.ui.screens.IpBindingsSheet
import com.erlkim.mikrotik.ui.screens.NotificationsLogSheet
import com.erlkim.mikrotik.ui.screens.SakuraAmbientOverlay
import com.erlkim.mikrotik.ui.screens.SettingsItemAction
import com.erlkim.mikrotik.ui.screens.SettingsScreen
import com.erlkim.mikrotik.ui.screens.SystemControlSheet
import com.erlkim.mikrotik.ui.screens.ThemeSettingsSheet
import com.erlkim.mikrotik.ui.screens.UserProfilesSheet
import com.erlkim.mikrotik.ui.screens.UsersScreen
import com.erlkim.mikrotik.ui.screens.UsersSecuritySheet
import com.erlkim.mikrotik.ui.screens.WalledGardenSheet
import com.erlkim.mikrotik.ui.screens.WirelessSheet
import com.erlkim.mikrotik.ui.theme.ErlkimNetTheme
import com.erlkim.mikrotik.ui.theme.JetBrainsMono
import com.erlkim.mikrotik.ui.theme.LocalErlkimColors
import com.erlkim.mikrotik.ui.viewmodel.ActiveModal
import com.erlkim.mikrotik.ui.viewmodel.ErlkimViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        setContent {
            ErlkimNetApp()
        }
    }
}

@Composable
fun ErlkimNetApp(viewModel: ErlkimViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val dashboardListState = rememberLazyListState()
    val settingsListState = rememberLazyListState()
    val interfacesScrollState = rememberScrollState()
    val ipAddressesScrollState = rememberScrollState()
    val dhcpScrollState = rememberScrollState()
    val systemControlScrollState = rememberScrollState()
    val ipBindingsScrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Pause background polling when app goes to background and resume cleanly on foreground
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onAppForegroundChanged(true)
                Lifecycle.Event.ON_STOP -> viewModel.onAppForegroundChanged(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: ErlkimTab.DASHBOARD.route
    val currentTab = when (currentRoute) {
        com.erlkim.mikrotik.ui.screens.ROUTE_ACTIVE_HOTSPOT_USERS -> ErlkimTab.HOTSPOT
        com.erlkim.mikrotik.ui.screens.ROUTE_SYSTEM_LOGS -> ErlkimTab.SETTINGS
        else -> ErlkimTab.entries.firstOrNull { it.route == currentRoute } ?: ErlkimTab.DASHBOARD
    }

    LaunchedEffect(currentRoute) {
        viewModel.onTabChanged(currentRoute)
    }

    fun navigateToTab(tab: ErlkimTab) {
        val liveRoute = navController.currentDestination?.route
        viewModel.closeModal()
        if (liveRoute == tab.route) {
            viewModel.ensureTelemetryAlive()
            return
        }
        if (tab == ErlkimTab.DASHBOARD) {
            val popped = navController.popBackStack(
                route = ErlkimTab.DASHBOARD.route,
                inclusive = false,
                saveState = true,
            )
            if (!popped) {
                navController.navigate(ErlkimTab.DASHBOARD.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        inclusive = true
                    }
                    launchSingleTop = true
                }
            }
            viewModel.ensureTelemetryAlive()
            return
        }
        navController.navigate(tab.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    ErlkimNetTheme(
        themeMode = uiState.themeMode,
        dynamicColor = uiState.dynamicColor,
    ) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        ErlkimBrandTitle(
                            compact = false,
                            connectionStatus = uiState.connectionStatus,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${uiState.resource.identity}  •  ${uiState.routerConfig.displayAddress}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    ErlkimTab.entries.forEach { tab ->
                        NavigationDrawerItem(
                            label = { Text(stringResource(tab.labelRes)) },
                            selected = currentTab == tab,
                            onClick = {
                                scope.launch { drawerState.close() }
                                navigateToTab(tab)
                            },
                            icon = { Icon(tab.selectedIcon, contentDescription = null) },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.router_settings_title)) },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            viewModel.openModal(ActiveModal.Connection)
                        },
                        icon = { Icon(Icons.Rounded.Router, contentDescription = null) },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    )
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.setting_quota_alerts)) },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            viewModel.openModal(ActiveModal.QuotaAlerts)
                        },
                        icon = { Icon(Icons.Rounded.NotificationsActive, contentDescription = null) },
                        badge = {
                            if (uiState.unreadQuotaAlertCount > 0) {
                                val ext = LocalErlkimColors.current
                                Badge(
                                    containerColor = if (uiState.exceededQuotaCount > 0) ext.offlineRed else ext.demoAmber,
                                    contentColor = androidx.compose.ui.graphics.Color(0xFF141115),
                                ) {
                                    Text(uiState.unreadQuotaAlertCount.toString())
                                }
                            }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    )
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.setting_throughput_alerts)) },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            viewModel.openModal(ActiveModal.ThroughputAlerts)
                        },
                        icon = { Icon(Icons.Rounded.Speed, contentDescription = null) },
                        badge = {
                            if (uiState.unreadThroughputAlertCount > 0 || uiState.isThroughputThresholdExceeded) {
                                val ext = LocalErlkimColors.current
                                Badge(
                                    containerColor = ext.offlineRed,
                                    contentColor = androidx.compose.ui.graphics.Color(0xFF141115),
                                ) {
                                    Text(uiState.unreadThroughputAlertCount.coerceAtLeast(1).toString())
                                }
                            }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    )
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.setting_resource_alerts)) },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            viewModel.openModal(ActiveModal.ResourceAlerts)
                        },
                        icon = { Icon(Icons.Rounded.Memory, contentDescription = null) },
                        badge = {
                            if (uiState.unreadResourceAlertCount > 0 || uiState.isResourceThresholdExceeded) {
                                val ext = LocalErlkimColors.current
                                Badge(
                                    containerColor = ext.offlineRed,
                                    contentColor = androidx.compose.ui.graphics.Color(0xFF141115),
                                ) {
                                    Text(uiState.unreadResourceAlertCount.coerceAtLeast(1).toString())
                                }
                            }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    )
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.title_system_logs)) },
                        selected = currentRoute == com.erlkim.mikrotik.ui.screens.ROUTE_SYSTEM_LOGS,
                        onClick = {
                            scope.launch { drawerState.close() }
                            viewModel.closeModal()
                            navController.navigate(com.erlkim.mikrotik.ui.screens.ROUTE_SYSTEM_LOGS) {
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(Icons.AutoMirrored.Rounded.ReceiptLong, contentDescription = null) },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    )
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.setting_backup_restore)) },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            viewModel.openModal(ActiveModal.BackupRestore)
                        },
                        icon = { Icon(Icons.Rounded.CloudUpload, contentDescription = null) },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    )
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.setting_theme)) },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            viewModel.openModal(ActiveModal.ThemeSettings)
                        },
                        icon = { Icon(Icons.Rounded.DarkMode, contentDescription = null) },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    )
                    NavigationDrawerItem(
                        label = { Text("Segarkan Data") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            viewModel.refreshNow()
                        },
                        icon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    )
                }
            },
        ) {
            val isDarkActive = when (uiState.themeMode) {
                com.erlkim.mikrotik.data.model.ThemeMode.DARK -> true
                com.erlkim.mikrotik.data.model.ThemeMode.LIGHT -> false
                com.erlkim.mikrotik.data.model.ThemeMode.SAKURA_ANIME -> false
                com.erlkim.mikrotik.data.model.ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useNavRail = maxWidth >= 600.dp

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    snackbarHost = {
                        SnackbarHost(hostState = snackbarHostState) { data ->
                            Snackbar(
                                snackbarData = data,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                actionColor = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    bottomBar = {
                        if (!useNavRail) {
                            ErlkimBottomBar(
                                currentTab = currentTab,
                                onSelectTab = { tab -> navigateToTab(tab) },
                            )
                        }
                    },
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    ) {
                        if (useNavRail) {
                            ErlkimNavRail(
                                currentTab = currentTab,
                                onSelectTab = { tab -> navigateToTab(tab) },
                            )
                        }

                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            NavHost(
                                navController = navController,
                                startDestination = ErlkimTab.DASHBOARD.route,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 720.dp),
                            ) {
                                composable(ErlkimTab.DASHBOARD.route) {
                                    DashboardScreen(
                                        uiState = uiState,
                                        onOpenMenu = { scope.launch { drawerState.open() } },
                                        onOpenNotifications = {
                                            viewModel.openModal(ActiveModal.QuotaAlerts)
                                        },
                                        onOpenThroughputAlerts = {
                                            viewModel.openModal(ActiveModal.ThroughputAlerts)
                                        },
                                        onOpenResourceAlerts = {
                                            viewModel.openModal(ActiveModal.ResourceAlerts)
                                        },
                                        onOpenConnection = { viewModel.openModal(ActiveModal.Connection) },
                                        onToggleDarkMode = { viewModel.toggleDarkMode(isDarkActive) },
                                        onEnableDemo = { viewModel.enableDemoMode() },
                                        onRefresh = viewModel::refreshNow,
                                        onSelectTrafficInterface = viewModel::selectTrafficInterface,
                                        onPingTargetChange = viewModel::updatePingTarget,
                                        onPingPacketCountChange = viewModel::updatePingCount,
                                        onPingSourceToggle = viewModel::togglePingSource,
                                        onStartPing = viewModel::startPing,
                                        onStopPing = viewModel::stopPing,
                                        onClearPing = viewModel::clearPingResults,
                                        onNavigateToHotspot = { navigateToTab(ErlkimTab.HOTSPOT) },
                                        onOpenFeature = { feature ->
                                            when (feature) {
                                                DashboardFeature.INTERFACES -> viewModel.openModal(ActiveModal.Interfaces)
                                                DashboardFeature.DHCP_SERVER -> viewModel.openModal(ActiveModal.DhcpServer)
                                                DashboardFeature.WIRELESS -> viewModel.openModal(ActiveModal.Wireless)
                                                DashboardFeature.IP_ADDRESSES -> viewModel.openModal(ActiveModal.IpAddresses)
                                                DashboardFeature.SYSTEM_REBOOT -> viewModel.openModal(ActiveModal.RebootShutdown)
                                                DashboardFeature.IDENTITY_TIME -> viewModel.openModal(ActiveModal.IdentityTime)
                                                DashboardFeature.ACTIVE_SESSIONS -> navController.navigate(com.erlkim.mikrotik.ui.screens.ROUTE_ACTIVE_HOTSPOT_USERS)
                                            }
                                        },
                                        listState = dashboardListState,
                                    )
                                }

                                composable(ErlkimTab.USERS.route) {
                                    UsersScreen(
                                        uiState = uiState,
                                        onNavigateBack = { navigateToTab(ErlkimTab.DASHBOARD) },
                                        onSearchChange = viewModel::updateUserSearch,
                                        onSelectFilter = viewModel::selectUserFilter,
                                        onUserClick = { user -> viewModel.openModal(ActiveModal.UserDetail(user)) },
                                        onShowQrCode = { user -> viewModel.openModal(ActiveModal.UserQrCode(user)) },
                                        onDisconnectUser = viewModel::disconnectHotspotUser,
                                        onBulkDeleteUsers = viewModel::deleteHotspotUsersBulk,
                                        onAddUserClick = { viewModel.openModal(ActiveModal.AddUser) },
                                        onRefresh = viewModel::refreshNow,
                                        onOpenConnection = { viewModel.openModal(ActiveModal.Connection) },
                                    )
                                }

                                composable(ErlkimTab.HOTSPOT.route) {
                                    HotspotScreen(
                                        uiState = uiState,
                                        onNavigateBack = { navigateToTab(ErlkimTab.DASHBOARD) },
                                        onNavigateToUsers = { navigateToTab(ErlkimTab.USERS) },
                                        onOpenFeature = { feature ->
                                            when (feature) {
                                                HotspotFeature.ACTIVE_SESSIONS -> navController.navigate(com.erlkim.mikrotik.ui.screens.ROUTE_ACTIVE_HOTSPOT_USERS)
                                                HotspotFeature.WALLED_GARDEN -> viewModel.openModal(ActiveModal.WalledGarden)
                                                HotspotFeature.IP_BINDINGS -> viewModel.openModal(ActiveModal.IpBindings)
                                                HotspotFeature.HOTSPOT_SERVERS -> viewModel.openModal(ActiveModal.HotspotServers)
                                                HotspotFeature.USER_PROFILES -> viewModel.openModal(ActiveModal.UserProfiles)
                                            }
                                        },
                                        onUserClick = { user -> viewModel.openModal(ActiveModal.UserDetail(user)) },
                                        onShowQrCode = { user -> viewModel.openModal(ActiveModal.UserQrCode(user)) },
                                        onDisconnectUser = viewModel::disconnectHotspotUser,
                                        onAddProfile = viewModel::addUserProfile,
                                        onUpdateProfile = viewModel::updateUserProfile,
                                        onDeleteProfile = viewModel::deleteUserProfile,
                                        onAddHotspotServer = viewModel::addHotspotServer,
                                        onToggleHotspotServer = viewModel::toggleHotspotServerDisabled,
                                        onDeleteHotspotServer = viewModel::deleteHotspotServer,
                                        onAddWalledGarden = viewModel::addWalledGarden,
                                        onUpdateWalledGarden = viewModel::updateWalledGarden,
                                        onToggleWalledGarden = viewModel::toggleWalledGardenDisabled,
                                        onDeleteWalledGarden = viewModel::deleteWalledGarden,
                                        onKickSession = viewModel::kickActiveSession,
                                        onAddUserClick = { viewModel.openModal(ActiveModal.AddUser) },
                                        onRefresh = viewModel::refreshNow,
                                        onOpenConnection = { viewModel.openModal(ActiveModal.Connection) },
                                    )
                                }

                                composable(com.erlkim.mikrotik.ui.screens.ROUTE_ACTIVE_HOTSPOT_USERS) {
                                    com.erlkim.mikrotik.ui.screens.ActiveHotspotUsersScreen(
                                        uiState = uiState,
                                        onNavigateBack = { navController.popBackStack() },
                                        onKickSession = viewModel::kickActiveSession,
                                        onRefresh = viewModel::refreshNow,
                                    )
                                }

                                composable(ErlkimTab.SETTINGS.route) {
                                    SettingsScreen(
                                        uiState = uiState,
                                        onNavigateBack = { navigateToTab(ErlkimTab.DASHBOARD) },
                                        onOpenConnection = { viewModel.openModal(ActiveModal.Connection) },
                                        onRefresh = viewModel::refreshNow,
                                        onSearchChange = viewModel::updateSettingsSearch,
                                        onSelectSetting = { action ->
                                            when (action) {
                                                SettingsItemAction.IP_CONFIG -> viewModel.openModal(ActiveModal.IpAddresses)
                                                SettingsItemAction.DHCP_SERVER -> viewModel.openModal(ActiveModal.DhcpServer)
                                                SettingsItemAction.WIRELESS -> viewModel.openModal(ActiveModal.Wireless)
                                                SettingsItemAction.HOTSPOT_USERMAN -> navigateToTab(ErlkimTab.HOTSPOT)
                                                SettingsItemAction.IP_BINDINGS -> viewModel.openModal(ActiveModal.IpBindings)
                                                SettingsItemAction.IDENTITY_TIME -> viewModel.openModal(ActiveModal.IdentityTime)
                                                SettingsItemAction.USERS_SECURITY -> viewModel.openModal(ActiveModal.UsersSecurity)
                                                SettingsItemAction.QUOTA_ALERTS -> viewModel.openModal(ActiveModal.QuotaAlerts)
                                                SettingsItemAction.THROUGHPUT_ALERTS -> viewModel.openModal(ActiveModal.ThroughputAlerts)
                                                SettingsItemAction.RESOURCE_ALERTS -> viewModel.openModal(ActiveModal.ResourceAlerts)
                                                SettingsItemAction.SYSTEM_LOGS -> navController.navigate(com.erlkim.mikrotik.ui.screens.ROUTE_SYSTEM_LOGS) {
                                                    launchSingleTop = true
                                                }
                                                SettingsItemAction.BACKUP_RESTORE -> viewModel.openModal(ActiveModal.BackupRestore)
                                                SettingsItemAction.REBOOT_SHUTDOWN -> viewModel.openModal(ActiveModal.RebootShutdown)
                                                SettingsItemAction.THEME -> viewModel.openModal(ActiveModal.ThemeSettings)
                                                SettingsItemAction.ABOUT -> viewModel.openModal(ActiveModal.AboutApp)
                                            }
                                        },
                                        listState = settingsListState,
                                        expandedSections = uiState.dashboardFeatures.settingsState.expandedSections,
                                        onToggleSection = viewModel::toggleSettingsSection,
                                        onSetAllSectionsExpanded = viewModel::setAllSettingsSectionsExpanded,
                                        onReconnect = viewModel::reconnectNow,
                                    )
                                }

                                composable(com.erlkim.mikrotik.ui.screens.ROUTE_SYSTEM_LOGS) {
                                    com.erlkim.mikrotik.ui.screens.SystemLogsScreen(
                                        uiState = uiState,
                                        onNavigateBack = { navController.popBackStack() },
                                        onRefreshLogs = viewModel::refreshSystemLogs,
                                    )
                                }
                            }
                        }
                    }
                }
                if (uiState.themeMode == com.erlkim.mikrotik.data.model.ThemeMode.SAKURA_ANIME) {
                    SakuraAmbientOverlay(
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }
            }
        }

        // Active Modals / Bottom Sheets (managed centrally via ViewModel StateFlow)
        when (val modal = uiState.activeModal) {
            ActiveModal.Connection -> RouterConnectionSheet(
                currentConfig = uiState.routerConfig,
                onDismiss = viewModel::closeModal,
                onTestConnection = viewModel::testRouterConnection,
                onSaveOnly = viewModel::saveRouterSettingsOnly,
                onConnect = { cfg -> viewModel.connectWithConfig(cfg, persist = true) },
            )
            ActiveModal.AddUser -> AddUserDialog(
                profiles = uiState.userProfiles,
                isLoadingProfiles = uiState.isLoadingProfiles,
                profilesError = uiState.profilesFetchError,
                existingUsernames = uiState.hotspotUsers.map { it.name },
                routerTargetLabel = if (uiState.routerConfig.demoMode) {
                    "Mode Demo • ${uiState.resource.identity}"
                } else {
                    "PUT /rest/ip/hotspot/user • ${uiState.routerConfig.displayAddress}"
                },
                isDemoMode = uiState.routerConfig.demoMode,
                onRefreshProfiles = viewModel::fetchHotspotUserProfiles,
                onOpenRouterSettings = { viewModel.openModal(ActiveModal.Connection) },
                onDismiss = viewModel::closeModal,
                onCreate = viewModel::addHotspotUser,
            )
            is ActiveModal.UserDetail -> {
                val liveUser = uiState.hotspotUsers.firstOrNull { it.id == modal.user.id } ?: modal.user
                HotspotUserDetailSheet(
                    user = liveUser,
                    onDismiss = viewModel::closeModal,
                    onShowQrCode = { viewModel.openModal(ActiveModal.UserQrCode(liveUser)) },
                    onUpdatePassword = { newPass ->
                        viewModel.updateHotspotUserPassword(liveUser, newPass)
                    },
                    onUpdateSessionTimeout = { timeout ->
                        viewModel.updateHotspotUserSessionTimeout(liveUser, timeout)
                    },
                    onToggleDisabled = { viewModel.toggleHotspotUserDisabled(liveUser) },
                    onDelete = { viewModel.deleteHotspotUser(liveUser) },
                )
            }
            is ActiveModal.UserQrCode -> UserQrCodeDialog(
                user = modal.user,
                defaultPortalHost = uiState.routerConfig.host,
                hotspotName = uiState.primaryHotspotName,
                onDismiss = viewModel::closeModal,
            )
            ActiveModal.ActiveSessions -> ActiveSessionsSheet(
                sessions = uiState.activeSessions,
                onKick = viewModel::kickActiveSession,
                onDismiss = viewModel::closeModal,
            )
            ActiveModal.WalledGarden -> WalledGardenSheet(
                entries = uiState.walledGarden,
                onAdd = viewModel::addWalledGarden,
                onUpdate = viewModel::updateWalledGarden,
                onToggleDisabled = viewModel::toggleWalledGardenDisabled,
                onDelete = viewModel::deleteWalledGarden,
                onDismiss = viewModel::closeModal,
            )
            ActiveModal.IpBindings -> IpBindingsSheet(
                entries = uiState.ipBindings,
                hotspotServers = uiState.hotspotServers,
                activeSessions = uiState.activeSessions,
                dhcpLeases = uiState.dhcpLeases,
                featureState = uiState.dashboardFeatures.ipBindingsState,
                onFeatureStateChange = viewModel::updateIpBindingsUiState,
                scrollState = ipBindingsScrollState,
                connectionStatus = uiState.connectionStatus,
                isRefreshing = uiState.isRefreshing,
                isMutating = uiState.isMutating,
                onAdd = viewModel::addIpBinding,
                onUpdate = viewModel::updateIpBinding,
                onToggleDisabled = viewModel::toggleIpBindingDisabled,
                onDelete = viewModel::deleteIpBinding,
                onRefresh = { viewModel.refreshIpBindingsSection(silent = false) },
                onReconnect = viewModel::reconnectNow,
                onDismiss = viewModel::closeModal,
            )
            ActiveModal.HotspotServers -> HotspotServersSheet(
                servers = uiState.hotspotServers,
                interfaces = uiState.interfaces,
                onAddServer = viewModel::addHotspotServer,
                onToggleServer = viewModel::toggleHotspotServerDisabled,
                onDeleteServer = viewModel::deleteHotspotServer,
                onDismiss = viewModel::closeModal,
            )
            ActiveModal.UserProfiles -> UserProfilesSheet(
                profiles = uiState.userProfiles,
                users = uiState.hotspotUsers,
                onAdd = viewModel::addUserProfile,
                onUpdate = viewModel::updateUserProfile,
                onDelete = viewModel::deleteUserProfile,
                onDismiss = viewModel::closeModal,
            )
            ActiveModal.Interfaces -> InterfacesSheet(
                interfaces = uiState.interfaces,
                selectedTrafficInterface = uiState.selectedTrafficInterface,
                featureState = uiState.dashboardFeatures.interfacesState,
                onFeatureStateChange = viewModel::updateInterfacesUiState,
                scrollState = interfacesScrollState,
                connectionStatus = uiState.connectionStatus,
                isRefreshing = uiState.isRefreshing,
                isMutating = uiState.isMutating,
                onToggle = { viewModel.toggleInterface(it.id, it.name, it.disabled) },
                onUpdate = viewModel::updateInterface,
                onResetCounters = viewModel::resetInterfaceCounters,
                onMonitorTraffic = { ifaceName ->
                    viewModel.selectTrafficInterface(ifaceName)
                    navigateToTab(ErlkimTab.DASHBOARD)
                },
                onRefresh = { viewModel.refreshInterfacesSection(silent = false) },
                onReconnect = viewModel::reconnectNow,
                onDismiss = viewModel::closeModal,
            )
            ActiveModal.DhcpServer -> DhcpServerSheet(
                servers = uiState.dhcpServers,
                leases = uiState.dhcpLeases,
                interfaces = uiState.interfaces,
                featureState = uiState.dashboardFeatures.dhcpState,
                onFeatureStateChange = viewModel::updateDhcpUiState,
                scrollState = dhcpScrollState,
                connectionStatus = uiState.connectionStatus,
                isRefreshing = uiState.isRefreshing,
                isMutating = uiState.isMutating,
                onAddServer = viewModel::addDhcpServer,
                onUpdateServer = viewModel::updateDhcpServer,
                onToggleServer = viewModel::toggleDhcpServerDisabled,
                onDeleteServer = viewModel::deleteDhcpServer,
                onAddStaticLease = viewModel::addStaticDhcpLease,
                onMakeLeaseStatic = viewModel::makeDhcpLeaseStatic,
                onToggleLeaseDisabled = viewModel::toggleDhcpLeaseDisabled,
                onDeleteLease = viewModel::deleteDhcpLease,
                onRefresh = { viewModel.refreshDhcpSection(silent = false) },
                onReconnect = viewModel::reconnectNow,
                onDismiss = viewModel::closeModal,
            )
            ActiveModal.Wireless -> WirelessSheet(
                wirelessList = uiState.wirelessInterfaces,
                onToggle = viewModel::toggleWirelessInterface,
                onUpdate = viewModel::updateWirelessInterface,
                onRefresh = viewModel::refreshNow,
                onDismiss = viewModel::closeModal,
            )
            ActiveModal.IpAddresses -> IpAddressesSheet(
                addresses = uiState.ipAddresses,
                interfaces = uiState.interfaces,
                featureState = uiState.dashboardFeatures.ipAddressesState,
                onFeatureStateChange = viewModel::updateIpAddressesUiState,
                scrollState = ipAddressesScrollState,
                connectionStatus = uiState.connectionStatus,
                isRefreshing = uiState.isRefreshing,
                isMutating = uiState.isMutating,
                onAdd = viewModel::addIpAddress,
                onUpdate = viewModel::updateIpAddress,
                onToggleDisabled = viewModel::toggleIpAddressDisabled,
                onDelete = viewModel::deleteIpAddress,
                onRefresh = { viewModel.refreshIpAddressesSection(silent = false) },
                onReconnect = viewModel::reconnectNow,
                onDismiss = viewModel::closeModal,
            )
            ActiveModal.IdentityTime -> IdentityAndTimeSheet(
                resource = uiState.resource,
                onSaveIdentity = viewModel::updateIdentity,
                onDismiss = viewModel::closeModal,
            )
            ActiveModal.UsersSecurity -> UsersSecuritySheet(
                systemUsers = uiState.systemUsers,
                onOpenApiConnection = { viewModel.openModal(ActiveModal.Connection) },
                onDismiss = viewModel::closeModal,
            )
            ActiveModal.BackupRestore -> BackupRestoreSheet(
                uiState = uiState,
                onExportToUri = viewModel::exportBackupToUri,
                onParseBackupUri = viewModel::parseBackupFromUri,
                onRestoreBackup = viewModel::restoreFromBackup,
                onCreateRouterBackup = viewModel::createBackup,
                onDismiss = viewModel::closeModal,
            )
            ActiveModal.RebootShutdown -> SystemControlSheet(
                resource = uiState.resource,
                routerAddress = uiState.routerConfig.displayAddress,
                connectionStatus = uiState.connectionStatus,
                featureState = uiState.dashboardFeatures.systemState,
                scrollState = systemControlScrollState,
                isRefreshing = uiState.isRefreshing,
                isMutating = uiState.isMutating,
                onOpenIdentityTime = { viewModel.openModal(ActiveModal.IdentityTime) },
                onOpenBackupRestore = { viewModel.openModal(ActiveModal.BackupRestore) },
                onOpenSystemLogs = {
                    viewModel.closeModal()
                    navController.navigate(com.erlkim.mikrotik.ui.screens.ROUTE_SYSTEM_LOGS) {
                        launchSingleTop = true
                    }
                },
                onOpenConnection = { viewModel.openModal(ActiveModal.Connection) },
                onQuickRouterBackup = viewModel::createBackup,
                onReboot = viewModel::rebootRouter,
                onShutdown = viewModel::shutdownRouter,
                onRefresh = { viewModel.refreshSystemHealthSection(silent = false) },
                onReconnect = viewModel::reconnectNow,
                onDismiss = viewModel::closeModal,
            )
            ActiveModal.ThemeSettings -> ThemeSettingsSheet(
                currentMode = uiState.themeMode,
                dynamicColor = uiState.dynamicColor,
                onUpdateTheme = viewModel::updateTheme,
                onDismiss = viewModel::closeModal,
            )
            ActiveModal.AboutApp -> AboutAppSheet(onDismiss = viewModel::closeModal)
            ActiveModal.Notifications -> NotificationsLogSheet(
                logs = uiState.logs,
                onDismiss = viewModel::closeModal,
            )
            ActiveModal.QuotaAlerts -> QuotaAlertsSheet(
                alerts = uiState.quotaAlerts,
                config = uiState.quotaAlertConfig,
                onSaveConfig = viewModel::updateQuotaAlertConfig,
                onAcknowledgeAlert = viewModel::acknowledgeQuotaAlert,
                onAcknowledgeAll = viewModel::acknowledgeAllQuotaAlerts,
                onDisconnectUser = viewModel::disconnectUserByName,
                onOpenSystemLogs = {
                    viewModel.closeModal()
                    navController.navigate(com.erlkim.mikrotik.ui.screens.ROUTE_SYSTEM_LOGS) {
                        launchSingleTop = true
                    }
                },
                onDismiss = viewModel::closeModal,
            )
            ActiveModal.ThroughputAlerts -> ThroughputAlertsSheet(
                currentDownloadMbps = uiState.currentDownloadMbps,
                currentUploadMbps = uiState.currentUploadMbps,
                config = uiState.throughputAlertConfig,
                alerts = uiState.throughputAlerts,
                onSaveConfig = viewModel::updateThroughputAlertConfig,
                onAcknowledgeAlert = viewModel::acknowledgeThroughputAlert,
                onAcknowledgeAll = viewModel::acknowledgeAllThroughputAlerts,
                onClearAlerts = viewModel::clearThroughputAlerts,
                onDismiss = viewModel::closeModal,
            )
            ActiveModal.ResourceAlerts -> ResourceAlertsSheet(
                resource = uiState.resource,
                config = uiState.resourceAlertConfig,
                alerts = uiState.resourceAlerts,
                onSaveConfig = viewModel::updateResourceAlertConfig,
                onAcknowledgeAlert = viewModel::acknowledgeResourceAlert,
                onAcknowledgeAll = viewModel::acknowledgeAllResourceAlerts,
                onClearAlerts = viewModel::clearResourceAlerts,
                onDismiss = viewModel::closeModal,
            )
            null -> Unit
        }
    }
}
