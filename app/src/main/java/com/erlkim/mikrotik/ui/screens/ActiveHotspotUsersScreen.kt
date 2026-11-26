package com.erlkim.mikrotik.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.erlkim.mikrotik.R
import com.erlkim.mikrotik.data.model.HotspotActiveSession
import com.erlkim.mikrotik.data.model.formatBytes
import com.erlkim.mikrotik.ui.theme.JetBrainsMono
import com.erlkim.mikrotik.ui.theme.LocalErlkimColors
import com.erlkim.mikrotik.ui.viewmodel.ErlkimUiState

const val ROUTE_ACTIVE_HOTSPOT_USERS = "active_hotspot_users"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveHotspotUsersScreen(
    uiState: ErlkimUiState,
    onNavigateBack: () -> Unit,
    onKickSession: (HotspotActiveSession) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val ext = LocalErlkimColors.current
    var searchQuery by remember { mutableStateOf("") }
    val pullRefreshState = rememberPullToRefreshState()

    LaunchedEffect(Unit) {
        onRefresh()
    }

    val filteredSessions = remember(uiState.activeSessions, searchQuery) {
        val q = searchQuery.trim()
        if (q.isBlank()) {
            uiState.activeSessions
        } else {
            uiState.activeSessions.filter { s ->
                s.user.contains(q, ignoreCase = true) ||
                    s.address.contains(q, ignoreCase = true) ||
                    s.macAddress.contains(q, ignoreCase = true) ||
                    s.server.contains(q, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(cs.background)
            .testTag("active_hotspot_users_screen"),
    ) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.title_active_hotspot_users),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = CircleShape,
                        color = ext.onlineContainer,
                    ) {
                        Text(
                            text = "${uiState.activeSessions.size}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = ext.onlineGreen,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.testTag("active_users_back_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.cd_navigate_back),
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.testTag("active_users_refresh_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.cd_refresh),
                    )
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
                .testTag("active_users_pull_refresh"),
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
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("active_hotspot_users_list"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
            item(key = "active_users_search") {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(25.dp),
                    color = cs.surfaceContainerHigh,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = stringResource(R.string.cd_search),
                            tint = cs.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.active_users_search_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = cs.onSurfaceVariant,
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = cs.onSurface),
                                cursorBrush = SolidColor(cs.primary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("active_users_search_input"),
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.cd_clear_search),
                                    tint = cs.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }

            if (filteredSessions.isEmpty()) {
                item(key = "empty_active_sessions") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer),
                        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.6f)),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WifiTethering,
                                contentDescription = null,
                                tint = cs.onSurfaceVariant,
                                modifier = Modifier.size(40.dp),
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (uiState.connectionStatus.isConnected) {
                                    stringResource(R.string.active_users_empty_title)
                                } else {
                                    "Router belum terhubung"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = cs.onSurface,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (uiState.connectionStatus.isConnected) {
                                    stringResource(R.string.active_users_empty_desc)
                                } else {
                                    "Hubungkan ke MikroTik RouterOS atau aktifkan Mode Demo untuk melihat sesi hotspot aktif."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                items(filteredSessions, key = { it.id }) { session ->
                    ActiveHotspotUserCard(
                        session = session,
                        onKick = { onKickSession(session) },
                    )
                }
            }
            }
        }
    }
}

@Composable
fun ActiveHotspotUserCard(
    session: HotspotActiveSession,
    onKick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val ext = LocalErlkimColors.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("active_user_card_${session.user}"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer),
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(ext.avatarActiveBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        tint = ext.avatarActiveIcon,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(ext.onlineGreen),
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = session.user,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = cs.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = session.server,
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                FilledTonalButton(
                    onClick = onKick,
                    modifier = Modifier.testTag("disconnect_session_${session.user}"),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = ext.offlineContainer,
                        contentColor = ext.offlineRed,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LinkOff,
                        contentDescription = stringResource(R.string.cd_kick_session),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = stringResource(R.string.action_disconnect_user),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Highlighted IP Address & MAC Address metadata strip
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = cs.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Language,
                                contentDescription = null,
                                tint = cs.tertiary,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.label_ip_address),
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = session.address.ifBlank { "-" },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = cs.onSurface,
                        )
                    }

                    HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.45f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Devices,
                                contentDescription = null,
                                tint = cs.secondary,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.label_mac_address),
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = session.macAddress.ifBlank { "-" },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = cs.onSurface,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = cs.onSurfaceVariant,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Up: ${session.uptime}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                        color = cs.onSurfaceVariant,
                    )
                    if (session.idleTime.isNotBlank() && session.idleTime != "0s") {
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = "• Idle: ${session.idleTime}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = JetBrainsMono,
                                fontWeight = if (session.isIdleLong) FontWeight.SemiBold else FontWeight.Normal,
                            ),
                            color = if (session.isIdleLong) ext.demoAmber else cs.onSurfaceVariant,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.SwapVert,
                        contentDescription = null,
                        tint = cs.primary,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = formatBytes(session.bytesIn + session.bytesOut),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = cs.onSurface,
                    )
                }
            }
        }
    }
}
