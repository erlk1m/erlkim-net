package com.erlkim.mikrotik.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.erlkim.mikrotik.R
import com.erlkim.mikrotik.data.model.ConnectionStatus
import com.erlkim.mikrotik.ui.components.ErlkimBrandTitle
import com.erlkim.mikrotik.ui.theme.JetBrainsMono
import com.erlkim.mikrotik.ui.theme.LocalErlkimColors
import com.erlkim.mikrotik.ui.viewmodel.ErlkimUiState

enum class SettingsItemAction {
    IP_CONFIG,
    DHCP_SERVER,
    WIRELESS,
    HOTSPOT_USERMAN,
    IP_BINDINGS,
    IDENTITY_TIME,
    USERS_SECURITY,
    QUOTA_ALERTS,
    THROUGHPUT_ALERTS,
    RESOURCE_ALERTS,
    SYSTEM_LOGS,
    BACKUP_RESTORE,
    REBOOT_SHUTDOWN,
    THEME,
    ABOUT,
}

private data class SettingRowSpec(
    val action: SettingsItemAction,
    val title: String,
    val subtitle: String,
    val badgeColor: Color,
    val iconTint: Color,
    val icon: ImageVector? = null,
    val badgeText: String? = null,
    val trailingPill: String? = null,
    val trailingPillColor: Color? = null,
    val trailingPillBg: Color? = null,
    val alertCount: Int = 0,
    val alertAccent: Color? = null,
)

private data class ExpandableSettingsCategory(
    val id: String,
    val title: String,
    val summary: String,
    val icon: ImageVector,
    val iconContainer: Color,
    val iconTint: Color,
    val items: List<SettingRowSpec>,
    val alertBadgeCount: Int = 0,
    val alertBadgeColor: Color? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: ErlkimUiState,
    onNavigateBack: () -> Unit,
    onOpenConnection: () -> Unit,
    onRefresh: () -> Unit,
    onSearchChange: (String) -> Unit,
    onSelectSetting: (SettingsItemAction) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    expandedSections: Set<String> = uiState.dashboardFeatures.settingsState.expandedSections,
    onToggleSection: (String) -> Unit = {},
    onSetAllSectionsExpanded: (Set<String>) -> Unit = {},
    onReconnect: () -> Unit = onRefresh,
) {
    val cs = MaterialTheme.colorScheme
    val ext = LocalErlkimColors.current
    var searchActive by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

    val (statusLabel, statusColor, statusBg) = when (uiState.connectionStatus) {
        ConnectionStatus.ConnectedLive -> Triple(
            stringResource(R.string.status_connected_online),
            ext.onlineGreen,
            ext.onlineContainer,
        )
        ConnectionStatus.ConnectedDemo -> Triple(
            stringResource(R.string.status_connected_demo),
            ext.demoAmber,
            ext.demoContainer,
        )
        ConnectionStatus.Connecting -> Triple(
            stringResource(R.string.status_connecting),
            cs.tertiary,
            cs.tertiaryContainer,
        )
        is ConnectionStatus.Disconnected -> Triple(
            stringResource(R.string.status_offline),
            ext.offlineRed,
            ext.offlineContainer,
        )
    }

    val query = uiState.settingsSearchQuery.trim()
    val bypassedCount = remember(uiState.ipBindings) {
        uiState.ipBindings.count { !it.disabled && it.type.equals("bypassed", true) }
    }

    val networkItems = listOf(
        SettingRowSpec(
            action = SettingsItemAction.IP_CONFIG,
            title = stringResource(R.string.setting_ip_config),
            subtitle = stringResource(R.string.setting_ip_config_desc),
            badgeColor = ext.badgeIp,
            iconTint = Color(0xFF101926),
            badgeText = "IP.",
            trailingPill = "${uiState.ipAddresses.size} IP",
        ),
        SettingRowSpec(
            action = SettingsItemAction.DHCP_SERVER,
            title = stringResource(R.string.setting_dhcp_server),
            subtitle = stringResource(R.string.setting_dhcp_server_desc),
            badgeColor = ext.badgeBackup,
            iconTint = Color(0xFF7CC0FA),
            icon = Icons.Rounded.Dns,
            trailingPill = "${uiState.dhcpLeases.size} lease",
        ),
        SettingRowSpec(
            action = SettingsItemAction.WIRELESS,
            title = stringResource(R.string.setting_wireless),
            subtitle = stringResource(R.string.setting_wireless_desc),
            badgeColor = ext.badgeWireless,
            iconTint = Color(0xFF1D130A),
            icon = Icons.Rounded.Wifi,
            trailingPill = "${uiState.wirelessInterfaces.size} SSID",
        ),
        SettingRowSpec(
            action = SettingsItemAction.HOTSPOT_USERMAN,
            title = stringResource(R.string.setting_hotspot_userman),
            subtitle = stringResource(R.string.setting_hotspot_userman_desc),
            badgeColor = ext.badgeHotspot,
            iconTint = Color(0xFF1B0E1D),
            icon = Icons.Rounded.WifiTethering,
            trailingPill = "${uiState.activeHotspotCount} aktif",
            trailingPillColor = ext.onlineGreen,
            trailingPillBg = ext.onlineContainer,
        ),
        SettingRowSpec(
            action = SettingsItemAction.IP_BINDINGS,
            title = stringResource(R.string.setting_ip_bindings),
            subtitle = stringResource(R.string.setting_ip_bindings_desc),
            badgeColor = ext.badgeIdentity,
            iconTint = Color(0xFF151226),
            icon = Icons.Rounded.Link,
            trailingPill = if (bypassedCount > 0) "$bypassedCount bypass" else "${uiState.ipBindings.size} aturan",
        ),
    ).filter { query.isBlank() || it.title.contains(query, true) || it.subtitle.contains(query, true) }

    val throughputAlertCount = if (uiState.unreadThroughputAlertCount > 0) {
        uiState.unreadThroughputAlertCount
    } else if (uiState.isThroughputThresholdExceeded) {
        1
    } else {
        0
    }
    val resourceAlertCount = if (uiState.unreadResourceAlertCount > 0) {
        uiState.unreadResourceAlertCount
    } else if (uiState.isResourceThresholdExceeded) {
        1
    } else {
        0
    }
    val totalUnreadAlerts = uiState.unreadQuotaAlertCount + throughputAlertCount + resourceAlertCount
    val activeAlertRules = listOf(
        uiState.quotaAlertConfig.enabled,
        uiState.throughputAlertConfig.enabled,
        uiState.resourceAlertConfig.enabled,
    ).count { it }

    val monitoringItems = listOf(
        SettingRowSpec(
            action = SettingsItemAction.QUOTA_ALERTS,
            title = stringResource(R.string.setting_quota_alerts),
            subtitle = if (uiState.quotaAlertConfig.enabled) {
                "Batas ${uiState.quotaAlertConfig.globalQuotaLimitMb} MB (${uiState.quotaAlertConfig.warningThresholdPercent}%)"
            } else {
                "Pemantauan kuota bandwidth nonaktif"
            },
            badgeColor = ext.offlineContainer,
            iconTint = ext.offlineRed,
            icon = Icons.Rounded.NotificationsActive,
            trailingPill = if (uiState.quotaAlertConfig.enabled) "Aktif" else "Nonaktif",
            trailingPillColor = if (uiState.quotaAlertConfig.enabled) ext.onlineGreen else cs.onSurfaceVariant,
            trailingPillBg = if (uiState.quotaAlertConfig.enabled) ext.onlineContainer else cs.surfaceContainerHighest,
            alertCount = uiState.unreadQuotaAlertCount,
            alertAccent = if (uiState.exceededQuotaCount > 0) ext.offlineRed else ext.demoAmber,
        ),
        SettingRowSpec(
            action = SettingsItemAction.THROUGHPUT_ALERTS,
            title = stringResource(R.string.setting_throughput_alerts),
            subtitle = if (uiState.throughputAlertConfig.enabled) {
                "Batas ${uiState.throughputAlertConfig.thresholdMbps.toInt()} Mbps • jeda ${uiState.throughputAlertConfig.cooldownSeconds}d"
            } else {
                "Pemantauan lonjakan trafik nonaktif"
            },
            badgeColor = ext.demoContainer,
            iconTint = ext.demoAmber,
            icon = Icons.Rounded.Speed,
            trailingPill = if (uiState.throughputAlertConfig.enabled) "Aktif" else "Nonaktif",
            trailingPillColor = if (uiState.throughputAlertConfig.enabled) ext.onlineGreen else cs.onSurfaceVariant,
            trailingPillBg = if (uiState.throughputAlertConfig.enabled) ext.onlineContainer else cs.surfaceContainerHighest,
            alertCount = throughputAlertCount,
            alertAccent = ext.offlineRed,
        ),
        SettingRowSpec(
            action = SettingsItemAction.RESOURCE_ALERTS,
            title = stringResource(R.string.setting_resource_alerts),
            subtitle = if (uiState.resourceAlertConfig.enabled) {
                val cfg = uiState.resourceAlertConfig
                "Batas CPU ${cfg.cpuThresholdPercent}% • RAM ${cfg.memoryThresholdPercent}%"
            } else {
                "Pemantauan CPU & memori nonaktif"
            },
            badgeColor = ext.offlineContainer,
            iconTint = ext.offlineRed,
            icon = Icons.Rounded.Memory,
            trailingPill = if (uiState.resourceAlertConfig.enabled) "Aktif" else "Nonaktif",
            trailingPillColor = if (uiState.resourceAlertConfig.enabled) ext.onlineGreen else cs.onSurfaceVariant,
            trailingPillBg = if (uiState.resourceAlertConfig.enabled) ext.onlineContainer else cs.surfaceContainerHighest,
            alertCount = resourceAlertCount,
            alertAccent = ext.offlineRed,
        ),
    ).filter { query.isBlank() || it.title.contains(query, true) || it.subtitle.contains(query, true) }

    val systemItems = listOf(
        SettingRowSpec(
            action = SettingsItemAction.IDENTITY_TIME,
            title = stringResource(R.string.setting_identity_time),
            subtitle = stringResource(R.string.setting_identity_time_desc),
            badgeColor = ext.badgeIdentity,
            iconTint = Color(0xFF151226),
            icon = Icons.Rounded.Schedule,
            trailingPill = uiState.resource.clockTime.takeIf { it.isNotBlank() && it != "--:--:--" },
        ),
        SettingRowSpec(
            action = SettingsItemAction.USERS_SECURITY,
            title = stringResource(R.string.setting_users_security),
            subtitle = stringResource(R.string.setting_users_security_desc),
            badgeColor = ext.badgeSecurity,
            iconTint = Color(0xFFF6B57A),
            icon = Icons.Rounded.Lock,
            trailingPill = "${uiState.systemUsers.size} akun",
        ),
        SettingRowSpec(
            action = SettingsItemAction.SYSTEM_LOGS,
            title = stringResource(R.string.setting_system_logs),
            subtitle = stringResource(R.string.setting_system_logs_desc),
            badgeColor = ext.badgeAbout,
            iconTint = Color(0xFF7CC0FA),
            icon = Icons.Rounded.ReceiptLong,
            trailingPill = "${uiState.logs.size} log",
        ),
        SettingRowSpec(
            action = SettingsItemAction.BACKUP_RESTORE,
            title = stringResource(R.string.setting_backup_restore),
            subtitle = stringResource(R.string.setting_backup_restore_desc),
            badgeColor = ext.badgeBackup,
            iconTint = Color(0xFF7CC0FA),
            icon = Icons.Rounded.CloudUpload,
        ),
        SettingRowSpec(
            action = SettingsItemAction.REBOOT_SHUTDOWN,
            title = stringResource(R.string.setting_reboot_shutdown),
            subtitle = stringResource(R.string.setting_reboot_shutdown_desc),
            badgeColor = ext.badgeReboot,
            iconTint = Color(0xFF8BE296),
            icon = Icons.Rounded.PowerSettingsNew,
        ),
    ).filter { query.isBlank() || it.title.contains(query, true) || it.subtitle.contains(query, true) }

    val appItems = listOf(
        SettingRowSpec(
            action = SettingsItemAction.THEME,
            title = stringResource(R.string.setting_theme),
            subtitle = when {
                uiState.themeMode == com.erlkim.mikrotik.data.model.ThemeMode.SAKURA_ANIME -> "Anime Sakura • Gaya Kelopak Sakura"
                uiState.dynamicColor -> "${uiState.themeMode.label} • Material You"
                else -> "${uiState.themeMode.label} • Palet Erlkim"
            },
            badgeColor = ext.badgeTheme,
            iconTint = Color(0xFFF5B89B),
            icon = Icons.Rounded.Palette,
            trailingPill = uiState.themeMode.label,
        ),
        SettingRowSpec(
            action = SettingsItemAction.ABOUT,
            title = stringResource(R.string.setting_about),
            subtitle = stringResource(R.string.setting_about_desc),
            badgeColor = ext.badgeAbout,
            iconTint = Color(0xFF8CC4E8),
            icon = Icons.Rounded.Info,
            trailingPill = "v1.0",
        ),
    ).filter { query.isBlank() || it.title.contains(query, true) || it.subtitle.contains(query, true) }

    val categories = buildList {
        if (networkItems.isNotEmpty()) {
            add(
                ExpandableSettingsCategory(
                    id = "connection",
                    title = stringResource(R.string.section_connection_network),
                    summary = "${uiState.ipAddresses.size} IP • ${uiState.dhcpLeases.size} lease • ${uiState.ipBindings.size} binding",
                    icon = Icons.Rounded.Hub,
                    iconContainer = cs.primaryContainer,
                    iconTint = cs.onPrimaryContainer,
                    items = networkItems,
                ),
            )
        }
        if (monitoringItems.isNotEmpty()) {
            add(
                ExpandableSettingsCategory(
                    id = "alerts",
                    title = stringResource(R.string.section_monitoring_alerts),
                    summary = if (totalUnreadAlerts > 0) {
                        "$totalUnreadAlerts peringatan baru • $activeAlertRules/3 sensor aktif"
                    } else {
                        "$activeAlertRules dari 3 peringatan aktif"
                    },
                    icon = Icons.Rounded.NotificationsActive,
                    iconContainer = if (totalUnreadAlerts > 0) ext.offlineContainer else ext.demoContainer,
                    iconTint = if (totalUnreadAlerts > 0) ext.offlineRed else ext.demoAmber,
                    items = monitoringItems,
                    alertBadgeCount = totalUnreadAlerts,
                    alertBadgeColor = if (uiState.exceededQuotaCount > 0 || throughputAlertCount > 0 || resourceAlertCount > 0) {
                        ext.offlineRed
                    } else {
                        ext.demoAmber
                    },
                ),
            )
        }
        if (systemItems.isNotEmpty()) {
            add(
                ExpandableSettingsCategory(
                    id = "system",
                    title = stringResource(R.string.section_system),
                    summary = "${uiState.systemUsers.size} akun • ${uiState.logs.size} log • Backup & Daya",
                    icon = Icons.Rounded.AdminPanelSettings,
                    iconContainer = cs.secondaryContainer,
                    iconTint = cs.onSecondaryContainer,
                    items = systemItems,
                ),
            )
        }
        if (appItems.isNotEmpty()) {
            add(
                ExpandableSettingsCategory(
                    id = "appearance",
                    title = stringResource(R.string.section_application),
                    summary = "${uiState.themeMode.label}${if (uiState.dynamicColor && uiState.themeMode != com.erlkim.mikrotik.data.model.ThemeMode.SAKURA_ANIME) " • Material You" else ""} • ErlkimNET v1.0",
                    icon = Icons.Rounded.Palette,
                    iconContainer = cs.tertiaryContainer,
                    iconTint = cs.onTertiaryContainer,
                    items = appItems,
                ),
            )
        }
    }

    val allCategoryIds = remember(categories) { categories.map { it.id }.toSet() }
    val allExpanded = allCategoryIds.isNotEmpty() && allCategoryIds.all { it in expandedSections }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(cs.background),
    ) {
        TopAppBar(
            title = {
                if (searchActive) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = cs.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 4.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = cs.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            BasicTextField(
                                value = uiState.settingsSearchQuery,
                                onValueChange = onSearchChange,
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = cs.onSurface),
                                cursorBrush = SolidColor(cs.primary),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("settings_search_input"),
                                decorationBox = { inner ->
                                    if (uiState.settingsSearchQuery.isEmpty()) {
                                        Text(
                                            text = "Cari pengaturan router…",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = cs.onSurfaceVariant,
                                        )
                                    }
                                    inner()
                                },
                            )
                        }
                    }
                } else {
                    ErlkimBrandTitle(
                        compact = true,
                        connectionStatus = uiState.connectionStatus,
                        onStatusClick = onOpenConnection,
                    )
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = {
                        if (searchActive) {
                            searchActive = false
                            onSearchChange("")
                        } else {
                            onNavigateBack()
                        }
                    },
                    modifier = Modifier.testTag("settings_back_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.cd_navigate_back),
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = {
                        if (searchActive) {
                            searchActive = false
                            onSearchChange("")
                        } else {
                            searchActive = true
                        }
                    },
                    modifier = Modifier.testTag("settings_search_button"),
                ) {
                    Icon(
                        imageVector = if (searchActive) Icons.Rounded.Close else Icons.Rounded.Search,
                        contentDescription = stringResource(R.string.cd_search),
                    )
                }
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.testTag("settings_more_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = stringResource(R.string.cd_more_options),
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        containerColor = cs.surfaceContainerHigh,
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.router_settings_title)) },
                            leadingIcon = { Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                menuExpanded = false
                                onOpenConnection()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Segarkan Router") },
                            leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                menuExpanded = false
                                onRefresh()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(if (allExpanded) "Tutup Semua Kategori" else "Buka Semua Kategori") },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (allExpanded) Icons.Rounded.UnfoldLess else Icons.Rounded.UnfoldMore,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onSetAllSectionsExpanded(if (allExpanded) emptySet() else allCategoryIds)
                            },
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
            modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullRefreshState,
                    isRefreshing = uiState.isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                    containerColor = cs.surfaceContainerHigh,
                    color = cs.primary,
                )
            },
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Hero Router Summary & Quick Control Card
                if (query.isBlank()) {
                    item(key = "router_connection_summary") {
                        SettingsRouterHeroCard(
                            uiState = uiState,
                            statusLabel = statusLabel,
                            statusColor = statusColor,
                            statusBg = statusBg,
                            onOpenConnection = onOpenConnection,
                            onRefreshOrReconnect = if (uiState.connectionStatus is ConnectionStatus.Disconnected) {
                                onReconnect
                            } else {
                                onRefresh
                            },
                        )
                    }

                    // Compact Categories Header with Expand All / Collapse All toggle
                    item(key = "settings_categories_toolbar") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 6.dp, top = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "KATEGORI PENGATURAN",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp,
                                ),
                                color = cs.onSurfaceVariant,
                            )
                            TextButton(
                                onClick = {
                                    onSetAllSectionsExpanded(if (allExpanded) emptySet() else allCategoryIds)
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("settings_toggle_all_sections_button"),
                            ) {
                                Icon(
                                    imageVector = if (allExpanded) Icons.Rounded.UnfoldLess else Icons.Rounded.UnfoldMore,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (allExpanded) "Tutup Semua" else "Buka Semua",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }

                if (categories.isEmpty() && query.isNotBlank()) {
                    item(key = "settings_search_empty") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp),
                            color = cs.surfaceContainer,
                            border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.55f)),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(cs.surfaceContainerHighest),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SearchOff,
                                        contentDescription = null,
                                        tint = cs.onSurfaceVariant,
                                        modifier = Modifier.size(26.dp),
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Pengaturan tidak ditemukan",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = cs.onSurface,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tidak ada menu yang cocok dengan \"$query\".",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                OutlinedButton(
                                    onClick = { onSearchChange("") },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("settings_reset_search_button"),
                                ) {
                                    Text("Reset Pencarian")
                                }
                            }
                        }
                    }
                }

                categories.forEach { category ->
                    item(key = "expandable_section_${category.id}") {
                        val isExpanded = query.isNotBlank() || category.id in expandedSections
                        ExpandableSettingsSectionCard(
                            category = category,
                            expanded = isExpanded,
                            isSearchForced = query.isNotBlank(),
                            onToggleExpand = { onToggleSection(category.id) },
                            onSelect = onSelectSetting,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpandableSettingsSectionCard(
    category: ExpandableSettingsCategory,
    expanded: Boolean,
    isSearchForced: Boolean,
    onToggleExpand: () -> Unit,
    onSelect: (SettingsItemAction) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "section_chevron_${category.id}",
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = cs.surfaceContainer,
        tonalElevation = if (expanded) 2.dp else 0.dp,
        border = BorderStroke(
            width = 1.dp,
            color = if (expanded) {
                cs.primary.copy(alpha = 0.28f)
            } else {
                cs.outlineVariant.copy(alpha = 0.55f)
            },
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Material 3 ListItem Category Accordion Header
            ListItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_section_header_${category.id}")
                    .clickable(
                        enabled = !isSearchForced,
                        role = Role.Button,
                        onClickLabel = if (expanded) "Tutup ${category.title}" else "Buka ${category.title}",
                        onClick = onToggleExpand,
                    ),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(category.iconContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = category.icon,
                            contentDescription = null,
                            tint = category.iconTint,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                },
                headlineContent = {
                    Text(
                        text = category.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = cs.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingContent = {
                    Text(
                        text = category.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                trailingContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (category.alertBadgeCount > 0) {
                            Surface(
                                shape = CircleShape,
                                color = category.alertBadgeColor ?: cs.error,
                            ) {
                                Text(
                                    text = category.alertBadgeCount.toString(),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = JetBrainsMono,
                                    ),
                                    color = Color(0xFF141115),
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                )
                            }
                        } else if (!expanded) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = cs.surfaceContainerHigh,
                            ) {
                                Text(
                                    text = "${category.items.size}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMono,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    color = cs.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Rounded.ExpandMore,
                            contentDescription = if (expanded) "Tutup kategori" else "Buka kategori",
                            tint = if (expanded) cs.primary else cs.onSurfaceVariant,
                            modifier = Modifier
                                .size(24.dp)
                                .rotate(chevronRotation),
                        )
                    }
                },
            )

            // Smoothly expandable Material 3 ListItem children
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ) + fadeIn(animationSpec = tween(180)),
                exit = shrinkVertically(
                    animationSpec = tween(200),
                ) + fadeOut(animationSpec = tween(150)),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(
                        color = cs.outlineVariant.copy(alpha = 0.5f),
                    )
                    category.items.forEachIndexed { index, spec ->
                        ListItem(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("setting_row_${spec.action.name.lowercase()}")
                                .clickable { onSelect(spec.action) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            leadingContent = {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(spec.badgeColor),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (spec.badgeText != null) {
                                        Text(
                                            text = spec.badgeText,
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 13.sp,
                                            ),
                                            color = spec.iconTint,
                                        )
                                    } else if (spec.icon != null) {
                                        Icon(
                                            imageVector = spec.icon,
                                            contentDescription = null,
                                            tint = spec.iconTint,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            },
                            headlineContent = {
                                Text(
                                    text = spec.title,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                    ),
                                    color = cs.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = spec.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            trailingContent = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    if (spec.alertCount > 0) {
                                        Surface(
                                            shape = CircleShape,
                                            color = spec.alertAccent ?: cs.error,
                                        ) {
                                            Text(
                                                text = spec.alertCount.toString(),
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = JetBrainsMono,
                                                ),
                                                color = Color(0xFF141115),
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                            )
                                        }
                                    } else if (!spec.trailingPill.isNullOrBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = spec.trailingPillBg ?: cs.surfaceContainerHigh,
                                        ) {
                                            Text(
                                                text = spec.trailingPill,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Medium,
                                                    fontFamily = JetBrainsMono,
                                                ),
                                                color = spec.trailingPillColor ?: cs.onSurfaceVariant,
                                                maxLines = 1,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                        contentDescription = stringResource(R.string.cd_chevron),
                                        tint = cs.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            },
                        )
                        if (index < category.items.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 72.dp, end = 16.dp),
                                color = cs.outlineVariant.copy(alpha = 0.35f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRouterHeroCard(
    uiState: ErlkimUiState,
    statusLabel: String,
    statusColor: Color,
    statusBg: Color,
    onOpenConnection: () -> Unit,
    onRefreshOrReconnect: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ext = LocalErlkimColors.current
    val isOffline = uiState.connectionStatus is ConnectionStatus.Disconnected

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_router_card")
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onOpenConnection),
        shape = RoundedCornerShape(24.dp),
        color = cs.surfaceContainer,
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            statusColor.copy(alpha = 0.13f),
                            cs.surfaceContainer,
                            cs.surfaceContainer,
                        ),
                    ),
                )
                .padding(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(statusBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Router,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = uiState.resource.identity,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = cs.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${uiState.resource.boardName} • ${uiState.resource.version}",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = statusBg,
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(statusColor),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when (uiState.connectionStatus) {
                                ConnectionStatus.ConnectedLive -> "Online"
                                ConnectionStatus.ConnectedDemo -> "Demo"
                                ConnectionStatus.Connecting -> "Menghubungkan"
                                is ConnectionStatus.Disconnected -> "Offline"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = statusColor,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Endpoint & Status Row
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = cs.surfaceContainerHigh.copy(alpha = 0.78f),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SettingsEthernet,
                            contentDescription = null,
                            tint = cs.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = uiState.routerConfig.displayAddress,
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                            color = cs.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Mini Telemetry Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HeroMetricPill(
                    label = "CPU",
                    value = "${uiState.resource.cpuLoadPercent}%",
                    accent = if (uiState.isCpuThresholdExceeded) ext.offlineRed else cs.primary,
                    modifier = Modifier.weight(1f),
                )
                HeroMetricPill(
                    label = "RAM",
                    value = "${uiState.resource.ramUsagePercent}%",
                    accent = if (uiState.isMemoryThresholdExceeded) ext.offlineRed else ext.onlineGreen,
                    modifier = Modifier.weight(1f),
                )
                HeroMetricPill(
                    label = "Uptime",
                    value = uiState.resource.uptime,
                    accent = cs.onSurface,
                    modifier = Modifier.weight(1.3f),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledTonalButton(
                    onClick = onOpenConnection,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("settings_hero_connection_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Koneksi API", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = onRefreshOrReconnect,
                    enabled = !uiState.isRefreshing,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("settings_hero_refresh_button"),
                ) {
                    if (uiState.isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = cs.primary,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isOffline) "Sambungkan" else "Segarkan",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroMetricPill(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = cs.surfaceContainerHigh.copy(alpha = 0.65f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
