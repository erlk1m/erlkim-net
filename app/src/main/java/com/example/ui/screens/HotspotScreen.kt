package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Fence
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.HotspotActiveSession
import com.example.data.model.HotspotServer
import com.example.data.model.HotspotUser
import com.example.data.model.HotspotUserProfile
import com.example.data.model.WalledGardenEntry
import com.example.ui.components.ErlkimBrandTitle
import com.example.ui.theme.JetBrainsMono
import com.example.ui.viewmodel.ErlkimUiState

enum class HotspotFeature {
    ACTIVE_SESSIONS,
    WALLED_GARDEN,
    IP_BINDINGS,
    HOTSPOT_SERVERS,
    USER_PROFILES,
}

enum class HotspotSectionTab(
    val labelRes: Int,
    val icon: ImageVector,
    val testTag: String,
) {
    USERS(R.string.hotspot_tab_users, Icons.Rounded.Group, "hotspot_tab_users"),
    PROFILES(R.string.hotspot_tab_profiles, Icons.Rounded.Badge, "hotspot_tab_profiles"),
    SERVER(R.string.hotspot_tab_server, Icons.Rounded.Dns, "hotspot_tab_server"),
    WALLED_GARDEN(R.string.hotspot_tab_walled_garden, Icons.Rounded.Fence, "hotspot_tab_walled_garden"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HotspotScreen(
    uiState: ErlkimUiState,
    onNavigateBack: () -> Unit,
    onNavigateToUsers: () -> Unit,
    onOpenFeature: (HotspotFeature) -> Unit,
    onUserClick: (HotspotUser) -> Unit,
    onShowQrCode: (HotspotUser) -> Unit,
    onDisconnectUser: (HotspotUser) -> Unit,
    onAddProfile: (name: String, rateLimit: String, sharedUsers: String, timeout: String) -> Unit,
    onUpdateProfile: (profile: HotspotUserProfile, name: String, rateLimit: String, sharedUsers: String, timeout: String) -> Unit,
    onDeleteProfile: (HotspotUserProfile) -> Unit,
    onAddHotspotServer: (name: String, interfaceName: String, addressPool: String, profile: String) -> Unit,
    onToggleHotspotServer: (HotspotServer) -> Unit,
    onDeleteHotspotServer: (HotspotServer) -> Unit,
    onAddWalledGarden: (host: String, port: String, action: String, comment: String) -> Unit,
    onUpdateWalledGarden: (entry: WalledGardenEntry, host: String, port: String, action: String, comment: String) -> Unit,
    onToggleWalledGarden: (WalledGardenEntry) -> Unit,
    onDeleteWalledGarden: (WalledGardenEntry) -> Unit,
    onKickSession: (HotspotActiveSession) -> Unit,
    onAddUserClick: () -> Unit,
    onRefresh: () -> Unit,
    onOpenConnection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    var menuExpanded by remember { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableStateOf(HotspotSectionTab.USERS) }
    var showProfileAddForm by remember { mutableStateOf(false) }
    var showServerAddForm by remember { mutableStateOf(false) }
    var showWalledAddForm by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

    val tabCounts = mapOf(
        HotspotSectionTab.USERS to uiState.hotspotUsers.size,
        HotspotSectionTab.PROFILES to uiState.userProfiles.size,
        HotspotSectionTab.SERVER to uiState.hotspotServers.size,
        HotspotSectionTab.WALLED_GARDEN to uiState.walledGarden.size,
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(cs.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("hotspot_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back),
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.testTag("hotspot_more_button"),
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
                                text = { Text("Segarkan Data") },
                                onClick = {
                                    menuExpanded = false
                                    onRefresh()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Active Sessions (${uiState.activeSessions.size})") },
                                onClick = {
                                    menuExpanded = false
                                    onOpenFeature(HotspotFeature.ACTIVE_SESSIONS)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("IP Bindings (${uiState.ipBindings.size})") },
                                onClick = {
                                    menuExpanded = false
                                    onOpenFeature(HotspotFeature.IP_BINDINGS)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Koneksi RouterOS") },
                                onClick = {
                                    menuExpanded = false
                                    onOpenConnection()
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

            // Material 3 Horizontal Scrollable Tab Row
            PrimaryScrollableTabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = cs.background,
                contentColor = cs.primary,
                edgePadding = 16.dp,
                divider = { HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.45f)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hotspot_horizontal_tabs"),
            ) {
                HotspotSectionTab.entries.forEach { tab ->
                    val isSelected = selectedTab == tab
                    val count = tabCounts[tab] ?: 0
                    Tab(
                        selected = isSelected,
                        onClick = { selectedTab = tab },
                        selectedContentColor = cs.primary,
                        unselectedContentColor = cs.onSurfaceVariant,
                        modifier = Modifier.testTag(tab.testTag),
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = stringResource(tab.labelRes),
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) cs.primaryContainer else cs.surfaceContainerHigh,
                                ) {
                                    Text(
                                        text = count.toString(),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = JetBrainsMono,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                        ),
                                        color = if (isSelected) cs.onPrimaryContainer else cs.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                    )
                                }
                            }
                        },
                    )
                }
            }

            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh,
                state = pullRefreshState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("hotspot_pull_refresh"),
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
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "hotspot_tab_content_transition",
                ) { tab ->
                    when (tab) {
                        HotspotSectionTab.USERS -> HotspotUsersTabContent(
                            uiState = uiState,
                            onUserClick = onUserClick,
                            onShowQrCode = onShowQrCode,
                            onDisconnectUser = onDisconnectUser,
                            onKickSession = onKickSession,
                            onOpenActiveSessions = { onOpenFeature(HotspotFeature.ACTIVE_SESSIONS) },
                            onOpenIpBindings = { onOpenFeature(HotspotFeature.IP_BINDINGS) },
                            onNavigateToBulkUsers = onNavigateToUsers,
                        )
                        HotspotSectionTab.PROFILES -> HotspotProfilesTabContent(
                            profiles = uiState.userProfiles,
                            users = uiState.hotspotUsers,
                            showAddForm = showProfileAddForm,
                            onShowAddFormChange = { showProfileAddForm = it },
                            onAddProfile = onAddProfile,
                            onUpdateProfile = onUpdateProfile,
                            onDeleteProfile = onDeleteProfile,
                        )
                        HotspotSectionTab.SERVER -> HotspotServerTabContent(
                            uiState = uiState,
                            showAddForm = showServerAddForm,
                            onShowAddFormChange = { showServerAddForm = it },
                            onAddServer = onAddHotspotServer,
                            onToggleServer = onToggleHotspotServer,
                            onDeleteServer = onDeleteHotspotServer,
                            onOpenActiveSessions = { onOpenFeature(HotspotFeature.ACTIVE_SESSIONS) },
                            onOpenIpBindings = { onOpenFeature(HotspotFeature.IP_BINDINGS) },
                            onSwitchToUsersTab = { selectedTab = HotspotSectionTab.USERS },
                            onRefresh = onRefresh,
                        )
                        HotspotSectionTab.WALLED_GARDEN -> HotspotWalledGardenTabContent(
                            entries = uiState.walledGarden,
                            showAddForm = showWalledAddForm,
                            onShowAddFormChange = { showWalledAddForm = it },
                            onAdd = onAddWalledGarden,
                            onUpdate = onUpdateWalledGarden,
                            onToggleDisabled = onToggleWalledGarden,
                            onDelete = onDeleteWalledGarden,
                        )
                    }
                }
            }
        }

        // Contextual Material 3 FAB per active Hotspot Tab
        val fabLabel = when (selectedTab) {
            HotspotSectionTab.USERS -> stringResource(R.string.action_add_user)
            HotspotSectionTab.PROFILES -> if (showProfileAddForm) "Tutup Form" else "Tambah Profil"
            HotspotSectionTab.SERVER -> if (showServerAddForm) "Tutup Form" else "Tambah Server"
            HotspotSectionTab.WALLED_GARDEN -> if (showWalledAddForm) "Tutup Form" else "Tambah Aturan"
        }

        ExtendedFloatingActionButton(
            onClick = {
                when (selectedTab) {
                    HotspotSectionTab.USERS -> onAddUserClick()
                    HotspotSectionTab.PROFILES -> showProfileAddForm = !showProfileAddForm
                    HotspotSectionTab.SERVER -> showServerAddForm = !showServerAddForm
                    HotspotSectionTab.WALLED_GARDEN -> showWalledAddForm = !showWalledAddForm
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = 18.dp)
                .testTag("hotspot_add_user_fab"),
            shape = RoundedCornerShape(18.dp),
            containerColor = cs.primary,
            contentColor = cs.onPrimary,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = fabLabel,
                )
            },
            text = {
                Text(
                    text = fabLabel,
                    style = MaterialTheme.typography.labelLarge,
                )
            },
        )
    }
}
