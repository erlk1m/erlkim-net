package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.BandwidthQuotaAlert
import com.example.data.model.ConnectionStatus
import com.example.data.model.QuotaAlertSeverity
import com.example.data.model.formatBytes
import com.example.ui.components.ErlkimBrandTitle
import com.example.ui.components.PingDiagnosticCard
import com.example.ui.components.RouterStatusCard
import com.example.ui.components.RouterSummaryCardsSection
import com.example.ui.components.TrafficChartCard
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalErlkimColors
import com.example.ui.viewmodel.ErlkimUiState

enum class DashboardFeature {
    INTERFACES,
    DHCP_SERVER,
    WIRELESS,
    IP_ADDRESSES,
    SYSTEM_REBOOT,
    IDENTITY_TIME,
    ACTIVE_SESSIONS,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    uiState: ErlkimUiState,
    onOpenMenu: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenThroughputAlerts: () -> Unit,
    onOpenResourceAlerts: () -> Unit,
    onOpenConnection: () -> Unit,
    onToggleDarkMode: () -> Unit,
    onEnableDemo: () -> Unit,
    onRefresh: () -> Unit,
    onSelectTrafficInterface: (String) -> Unit,
    onPingTargetChange: (String) -> Unit,
    onPingPacketCountChange: (Int) -> Unit,
    onPingSourceToggle: (Boolean) -> Unit,
    onStartPing: () -> Unit,
    onStopPing: () -> Unit,
    onClearPing: () -> Unit,
    onNavigateToHotspot: () -> Unit,
    onOpenFeature: (DashboardFeature) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val ext = LocalErlkimColors.current
    val pullRefreshState = rememberPullToRefreshState()
    val isDarkActive = when (uiState.themeMode) {
        com.example.data.model.ThemeMode.DARK -> true
        com.example.data.model.ThemeMode.LIGHT -> false
        com.example.data.model.ThemeMode.SAKURA_ANIME -> false
        com.example.data.model.ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(cs.background),
    ) {
        TopAppBar(
            title = {
                ErlkimBrandTitle(
                    compact = true,
                    connectionStatus = uiState.connectionStatus,
                    onStatusClick = onOpenConnection,
                )
            },
            navigationIcon = {
                IconButton(
                    onClick = onOpenMenu,
                    modifier = Modifier.testTag("dashboard_menu_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Menu,
                        contentDescription = stringResource(R.string.cd_open_menu),
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = onToggleDarkMode,
                    modifier = Modifier.testTag("dashboard_theme_toggle"),
                ) {
                    Icon(
                        imageVector = if (isDarkActive) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                        contentDescription = stringResource(R.string.cd_toggle_dark_mode),
                        tint = if (isDarkActive) ext.demoAmber else cs.secondary,
                    )
                }
                Box(contentAlignment = Alignment.Center) {
                    val unreadCount = uiState.unreadQuotaAlertCount
                    val hasExceeded = uiState.exceededQuotaCount > 0
                    IconButton(
                        onClick = onOpenNotifications,
                        modifier = Modifier.testTag("dashboard_notifications_button"),
                    ) {
                        Icon(
                            imageVector = if (unreadCount > 0) Icons.Rounded.NotificationsActive else Icons.Outlined.Notifications,
                            contentDescription = stringResource(R.string.cd_notifications),
                            tint = when {
                                unreadCount > 0 && hasExceeded -> ext.offlineRed
                                unreadCount > 0 -> ext.demoAmber
                                else -> cs.onBackground
                            },
                        )
                    }
                    if (unreadCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 6.dp, end = 6.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (hasExceeded) ext.offlineRed else ext.demoAmber)
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (unreadCount > 9) "9+" else unreadCount.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = Color(0xFF141115),
                            )
                        }
                    } else if (uiState.logs.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 11.dp, end = 11.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(cs.primary),
                        )
                    }
                }
                IconButton(
                    onClick = onOpenConnection,
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .testTag("dashboard_profile_button"),
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(cs.secondary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = stringResource(R.string.cd_router_profile),
                            tint = cs.onSecondary,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = cs.background,
                titleContentColor = cs.onBackground,
                navigationIconContentColor = cs.onBackground,
                actionIconContentColor = cs.onBackground,
            ),
        )

        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            state = pullRefreshState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("dashboard_pull_refresh"),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullRefreshState,
                    isRefreshing = uiState.isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                    containerColor = cs.surfaceContainerHighest,
                    color = cs.primary,
                )
            },
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
            item(key = "router_status") {
                RouterStatusCard(
                    resource = uiState.resource,
                    connectionStatus = uiState.connectionStatus,
                    routerAddress = uiState.routerConfig.displayAddress,
                    onCardClick = onOpenConnection,
                    onOpenConnection = onOpenConnection,
                    onEnableDemo = onEnableDemo,
                )
            }

            if (uiState.isThroughputThresholdExceeded || uiState.unreadThroughputAlertCount > 0) {
                item(key = "throughput_alert_banner") {
                    ThroughputAlertBannerCard(
                        totalMbps = uiState.currentTotalThroughputMbps,
                        thresholdMbps = uiState.throughputAlertConfig.thresholdMbps,
                        isCurrentlyExceeded = uiState.isThroughputThresholdExceeded,
                        unreadCount = uiState.unreadThroughputAlertCount,
                        onClick = onOpenThroughputAlerts,
                    )
                }
            }

            if (uiState.isResourceThresholdExceeded || uiState.unreadResourceAlertCount > 0) {
                item(key = "resource_alert_banner") {
                    ResourceAlertBannerCard(
                        cpuPercent = uiState.resource.cpuLoadPercent,
                        cpuThreshold = uiState.resourceAlertConfig.cpuThresholdPercent,
                        memPercent = uiState.resource.ramUsagePercent,
                        memThreshold = uiState.resourceAlertConfig.memoryThresholdPercent,
                        isCpuExceeded = uiState.isCpuThresholdExceeded,
                        isMemExceeded = uiState.isMemoryThresholdExceeded,
                        unreadCount = uiState.unreadResourceAlertCount,
                        onClick = onOpenResourceAlerts,
                    )
                }
            }

            if (uiState.unreadQuotaAlertCount > 0) {
                val topAlert = uiState.quotaAlerts.firstOrNull { !it.isAcknowledged } ?: uiState.quotaAlerts.first()
                item(key = "quota_alert_banner") {
                    QuotaAlertBannerCard(
                        unreadCount = uiState.unreadQuotaAlertCount,
                        exceededCount = uiState.exceededQuotaCount,
                        topAlert = topAlert,
                        onClick = onOpenNotifications,
                    )
                }
            }

            item(key = "router_summary_cards") {
                RouterSummaryCardsSection(
                    uptime = uiState.resource.uptime,
                    cpuLoadPercent = uiState.resource.cpuLoadPercent,
                    activeUsersCount = uiState.activeHotspotCount,
                    isConnected = uiState.connectionStatus.isConnected,
                    onUptimeClick = { onOpenFeature(DashboardFeature.IDENTITY_TIME) },
                    onCpuClick = onOpenResourceAlerts,
                    onActiveUsersClick = { onOpenFeature(DashboardFeature.ACTIVE_SESSIONS) },
                )
            }

            item(key = "traffic_chart") {
                TrafficChartCard(
                    samples = uiState.trafficHistory,
                    downloadMbps = uiState.currentDownloadMbps,
                    uploadMbps = uiState.currentUploadMbps,
                    isLive = uiState.connectionStatus.isConnected,
                    interfaces = uiState.interfaces,
                    selectedInterface = uiState.selectedTrafficInterface,
                    onSelectInterface = onSelectTrafficInterface,
                    throughputThresholdMbps = if (uiState.throughputAlertConfig.enabled) {
                        uiState.throughputAlertConfig.thresholdMbps
                    } else {
                        null
                    },
                    onConfigureThreshold = onOpenThroughputAlerts,
                )
            }

            item(key = "ping_diagnostic") {
                PingDiagnosticCard(
                    pingState = uiState.pingState,
                    gatewayHost = uiState.routerConfig.host,
                    isConnectedLive = uiState.connectionStatus is ConnectionStatus.ConnectedLive,
                    onTargetChange = onPingTargetChange,
                    onSelectPacketCount = onPingPacketCountChange,
                    onToggleRouterSource = onPingSourceToggle,
                    onStartPing = onStartPing,
                    onStopPing = onStopPing,
                    onClearResults = onClearPing,
                )
            }

            item(key = "quick_grid_row_1") {
                val activeInterfaces = uiState.interfaces.count { it.running && !it.disabled }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    DashboardQuickTile(
                        title = stringResource(R.string.tile_hotspot),
                        subtitle = "${uiState.activeHotspotCount} Aktif",
                        icon = Icons.Rounded.WifiTethering,
                        cardColor = ext.tileHotspotBg,
                        badgeColor = ext.tileHotspotBadge,
                        onClick = onNavigateToHotspot,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tile_hotspot"),
                    )
                    DashboardQuickTile(
                        title = stringResource(R.string.tile_interfaces),
                        subtitle = if (uiState.interfaces.isNotEmpty()) {
                            "$activeInterfaces/${uiState.interfaces.size} running"
                        } else {
                            "WAN / LAN aktif"
                        },
                        icon = Icons.Rounded.SettingsEthernet,
                        cardColor = ext.tileInterfacesBg,
                        badgeColor = ext.tileInterfacesBadge,
                        onClick = { onOpenFeature(DashboardFeature.INTERFACES) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tile_interfaces"),
                    )
                }
            }

            item(key = "quick_grid_row_2") {
                val activeWifi = uiState.wirelessInterfaces.count { !it.disabled }
                val totalWifiClients = uiState.wirelessInterfaces.sumOf { if (!it.disabled) it.connectedClients else 0 }
                val activeDhcpServers = uiState.dhcpServers.count { !it.disabled }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    DashboardQuickTile(
                        title = stringResource(R.string.tile_dhcp_server),
                        subtitle = if (uiState.dhcpServers.isNotEmpty() || uiState.dhcpLeases.isNotEmpty()) {
                            "$activeDhcpServers Server • ${uiState.dhcpLeases.size} Lease"
                        } else {
                            "IP / server"
                        },
                        icon = Icons.Rounded.Dns,
                        cardColor = ext.tileDhcpBg,
                        badgeColor = ext.tileDhcpBadge,
                        onClick = { onOpenFeature(DashboardFeature.DHCP_SERVER) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tile_dhcp_server"),
                    )
                    DashboardQuickTile(
                        title = stringResource(R.string.tile_wireless),
                        subtitle = if (uiState.wirelessInterfaces.isNotEmpty()) {
                            "$activeWifi SSID • $totalWifiClients klien"
                        } else {
                            "SSID yang aktif"
                        },
                        icon = Icons.Rounded.Wifi,
                        cardColor = ext.tileWirelessBg,
                        badgeColor = ext.tileWirelessBadge,
                        onClick = { onOpenFeature(DashboardFeature.WIRELESS) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tile_wireless"),
                    )
                }
            }

            item(key = "quick_grid_row_3") {
                val activeIps = uiState.ipAddresses.count { !it.disabled && !it.invalid }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    DashboardQuickTile(
                        title = stringResource(R.string.tile_ip_addresses),
                        subtitle = if (uiState.ipAddresses.isNotEmpty()) {
                            "$activeIps/${uiState.ipAddresses.size} IP aktif"
                        } else {
                            "Jaringan"
                        },
                        icon = Icons.Rounded.AccountTree,
                        cardColor = ext.tileIpBg,
                        badgeColor = ext.tileIpBadge,
                        onClick = { onOpenFeature(DashboardFeature.IP_ADDRESSES) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tile_ip_addresses"),
                    )
                    DashboardQuickTile(
                        title = stringResource(R.string.tile_system_reboot),
                        subtitle = if (uiState.connectionStatus.isConnected) {
                            "CPU ${uiState.resource.cpuLoadPercent}% • ${uiState.resource.uptime}"
                        } else {
                            "Pengaturan & daya"
                        },
                        icon = Icons.Rounded.Settings,
                        cardColor = ext.tileSystemBg,
                        badgeColor = ext.tileSystemBadge,
                        onClick = { onOpenFeature(DashboardFeature.SYSTEM_REBOOT) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tile_system_reboot"),
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun DashboardQuickTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    cardColor: Color,
    badgeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .height(92.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = cardColor,
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 13.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(badgeColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFF151316),
                    modifier = Modifier.size(23.dp),
                )
            }
            Spacer(modifier = Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = cs.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun QuotaAlertBannerCard(
    unreadCount: Int,
    exceededCount: Int,
    topAlert: BandwidthQuotaAlert,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ext = LocalErlkimColors.current
    val isCritical = exceededCount > 0 || topAlert.severity == QuotaAlertSeverity.EXCEEDED
    val accent = if (isCritical) ext.offlineRed else ext.demoAmber
    val container = if (isCritical) ext.offlineContainer else ext.demoContainer

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_quota_alert_banner")
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = container,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isCritical) Icons.Rounded.ErrorOutline else Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isCritical) {
                        "$unreadCount Peringatan Kuota Bandwidth"
                    } else {
                        "$unreadCount User Mendekati Batas Kuota"
                    },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${topAlert.username} • ${formatBytes(topAlert.usedBytes)} / ${formatBytes(topAlert.limitBytes)} (${topAlert.usagePercent}%)",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accent.copy(alpha = 0.2f),
            ) {
                Text(
                    text = "Lihat",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = accent,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun ThroughputAlertBannerCard(
    totalMbps: Float,
    thresholdMbps: Float,
    isCurrentlyExceeded: Boolean,
    unreadCount: Int,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ext = LocalErlkimColors.current
    val accent = if (isCurrentlyExceeded) ext.offlineRed else ext.demoAmber
    val container = if (isCurrentlyExceeded) ext.offlineContainer else ext.demoContainer

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_throughput_alert_banner")
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = container,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Speed,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isCurrentlyExceeded) {
                        "Batas Throughput Terlampaui"
                    } else {
                        "$unreadCount Peringatan Lonjakan Trafik"
                    },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = String.format(
                        java.util.Locale.US,
                        "Total %.1f Mbps  •  Batas %.0f Mbps",
                        totalMbps,
                        thresholdMbps,
                    ),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accent.copy(alpha = 0.2f),
            ) {
                Text(
                    text = "Kelola",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = accent,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun ResourceAlertBannerCard(
    cpuPercent: Int,
    cpuThreshold: Int,
    memPercent: Int,
    memThreshold: Int,
    isCpuExceeded: Boolean,
    isMemExceeded: Boolean,
    unreadCount: Int,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ext = LocalErlkimColors.current
    val isCurrentlyExceeded = isCpuExceeded || isMemExceeded
    val accent = if (isCurrentlyExceeded) ext.offlineRed else ext.demoAmber
    val container = if (isCurrentlyExceeded) ext.offlineContainer else ext.demoContainer

    val titleText = when {
        isCpuExceeded && isMemExceeded -> "Beban CPU & Memori Tinggi"
        isCpuExceeded -> "Batas CPU Router Terlampaui"
        isMemExceeded -> "Batas Memori Router Terlampaui"
        else -> "$unreadCount Peringatan Resource Router"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_resource_alert_banner")
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = container,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Memory,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "CPU $cpuPercent% (/${cpuThreshold}%)  •  RAM $memPercent% (/${memThreshold}%)",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accent.copy(alpha = 0.2f),
            ) {
                Text(
                    text = "Kelola",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = accent,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}
