package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Fence
import androidx.compose.material.icons.rounded.HowToReg
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.SolidColor
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
import com.example.data.model.RouterInterface
import com.example.data.model.UserFilter
import com.example.data.model.WalledGardenEntry
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalErlkimColors
import com.example.ui.viewmodel.ErlkimUiState
import java.util.Locale

@Composable
internal fun HotspotUsersTabContent(
    uiState: ErlkimUiState,
    onUserClick: (HotspotUser) -> Unit,
    onShowQrCode: (HotspotUser) -> Unit,
    onDisconnectUser: (HotspotUser) -> Unit,
    onKickSession: (HotspotActiveSession) -> Unit,
    onOpenActiveSessions: () -> Unit,
    onOpenIpBindings: () -> Unit,
    onNavigateToBulkUsers: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ext = LocalErlkimColors.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(UserFilter.ALL) }

    val filteredUsers = remember(uiState.hotspotUsers, uiState.activeSessions, searchQuery, selectedFilter) {
        val q = searchQuery.trim()
        val sessionsByUser = uiState.activeSessions.associateBy { it.user.lowercase() }
        uiState.hotspotUsers.filter { user ->
            val session = sessionsByUser[user.name.lowercase()]
            val matchesQuery = q.isBlank() ||
                user.name.contains(q, ignoreCase = true) ||
                user.profile.contains(q, ignoreCase = true) ||
                user.comment.contains(q, ignoreCase = true) ||
                (session?.address?.contains(q, ignoreCase = true) == true)
            val matchesFilter = when (selectedFilter) {
                UserFilter.ALL -> true
                UserFilter.ACTIVE -> user.isActive && !user.disabled
                UserFilter.EXPIRED -> user.isExpired || user.disabled
                UserFilter.VIP -> user.isVip
            }
            matchesQuery && matchesFilter
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Quick Action Shortcuts Bar
        item(key = "hs_users_shortcuts") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    HotspotQuickShortcutChip(
                        icon = Icons.Rounded.HowToReg,
                        label = "Active (${uiState.activeHotspotCount})",
                        accent = ext.onlineGreen,
                        onClick = onOpenActiveSessions,
                        modifier = Modifier.testTag("hotspot_shortcut_active"),
                    )
                }
                item {
                    HotspotQuickShortcutChip(
                        icon = Icons.Rounded.Link,
                        label = "IP Bindings (${uiState.ipBindings.size})",
                        accent = cs.primary,
                        onClick = onOpenIpBindings,
                        modifier = Modifier.testTag("hotspot_shortcut_bindings"),
                    )
                }
                item {
                    HotspotQuickShortcutChip(
                        icon = Icons.Rounded.Checklist,
                        label = "Hapus Massal",
                        accent = cs.secondary,
                        onClick = onNavigateToBulkUsers,
                        modifier = Modifier.testTag("hotspot_shortcut_bulk"),
                    )
                }
            }
        }

        // Search Bar
        item(key = "hs_users_search") {
            HotspotInlineSearchBar(
                query = searchQuery,
                placeholder = stringResource(R.string.search_placeholder),
                onQueryChange = { searchQuery = it },
                testTag = "hotspot_tab_users_search",
            )
        }

        // Filter Chips
        item(key = "hs_users_filters") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                UserFilter.entries.forEach { filter ->
                    val selected = selectedFilter == filter
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedFilter = filter }
                            .testTag("hotspot_users_filter_${filter.name.lowercase()}"),
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) cs.primaryContainer else cs.surfaceContainer,
                        border = if (selected) null else BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.6f)),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = filter.label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                ),
                                color = if (selected) cs.onPrimaryContainer else cs.onSurface,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        // Registered Users Section
        item(key = "hs_users_list_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "HOTSPOT USERS (${filteredUsers.size})",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = cs.onSurfaceVariant,
                )
                Text(
                    text = "Ketuk user untuk detail & ganti password",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                )
            }
        }

        if (filteredUsers.isEmpty()) {
            item(key = "hs_users_empty") {
                HotspotEmptyCard(
                    icon = Icons.Rounded.Person,
                    title = "Belum ada user hotspot",
                    subtitle = if (searchQuery.isNotBlank()) {
                        "Tidak ada user yang cocok dengan \"$searchQuery\"."
                    } else {
                        "Tekan tombol + Add User di bawah untuk membuat akun hotspot baru."
                    },
                )
            }
        } else {
            items(filteredUsers, key = { "hs_u_${it.id}" }) { user ->
                val activeIp = uiState.activeIpForUser(user.name)
                val isOnline = user.isActive && !user.disabled
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = cs.surfaceContainer,
                    border = BorderStroke(
                        1.dp,
                        if (isOnline) ext.onlineGreen.copy(alpha = 0.35f) else cs.outlineVariant.copy(alpha = 0.5f),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onUserClick(user) }
                        .testTag("hotspot_inline_user_${user.name}"),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        user.isVip -> ext.avatarVipBg
                                        isOnline -> ext.avatarActiveBg
                                        else -> ext.avatarInactiveBg
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                tint = when {
                                    user.isVip -> ext.avatarVipIcon
                                    isOnline -> ext.avatarActiveIcon
                                    else -> ext.avatarInactiveIcon
                                },
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = user.name,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = cs.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                user.disabled -> ext.offlineRed
                                                isOnline -> ext.onlineGreen
                                                else -> cs.onSurfaceVariant.copy(alpha = 0.4f)
                                            },
                                        ),
                                )
                            }
                            Text(
                                text = "Profil: ${user.profile}  •  ${user.formattedQuota()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = buildString {
                                    append("Timeout: ${user.formattedSessionTimeout()}")
                                    if (!activeIp.isNullOrBlank()) append("  •  IP: $activeIp")
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                                color = if (!activeIp.isNullOrBlank()) cs.primary else cs.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = { onShowQrCode(user) }) {
                            Icon(
                                imageVector = Icons.Rounded.QrCode2,
                                contentDescription = stringResource(R.string.cd_show_user_qr),
                                tint = cs.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        if (isOnline) {
                            IconButton(onClick = { onDisconnectUser(user) }) {
                                Icon(
                                    imageVector = Icons.Rounded.LinkOff,
                                    contentDescription = stringResource(R.string.action_disconnect_user),
                                    tint = ext.offlineRed,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                tint = cs.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }

        // Active Sessions Preview
        if (uiState.activeSessions.isNotEmpty() && selectedFilter != UserFilter.EXPIRED) {
            item(key = "hs_users_active_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "SESI ONLINE (${uiState.activeSessions.size})",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = cs.onSurfaceVariant,
                    )
                    TextButton(onClick = onOpenActiveSessions) {
                        Text(stringResource(R.string.action_view_all))
                    }
                }
            }
            items(uiState.activeSessions.take(4), key = { "hs_active_inline_${it.id}" }) { session ->
                ActiveHotspotUserCard(
                    session = session,
                    onKick = { onKickSession(session) },
                )
            }
        }
    }
}

@Composable
internal fun HotspotProfilesTabContent(
    profiles: List<HotspotUserProfile>,
    users: List<HotspotUser>,
    showAddForm: Boolean,
    onShowAddFormChange: (Boolean) -> Unit,
    onAddProfile: (name: String, rateLimit: String, sharedUsers: String, timeout: String) -> Unit,
    onUpdateProfile: (profile: HotspotUserProfile, name: String, rateLimit: String, sharedUsers: String, timeout: String) -> Unit,
    onDeleteProfile: (HotspotUserProfile) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ext = LocalErlkimColors.current

    var editingProfile by remember { mutableStateOf<HotspotUserProfile?>(null) }
    var name by remember { mutableStateOf("") }
    var rateLimit by remember { mutableStateOf("5M/10M") }
    var shared by remember { mutableStateOf("1") }
    var timeout by remember { mutableStateOf("1d") }
    var searchQuery by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<HotspotUserProfile?>(null) }

    val ratePresets = listOf("2M/5M", "5M/10M", "10M/20M", "25M/50M", "Unlimited")

    val filteredProfiles = remember(profiles, searchQuery) {
        val q = searchQuery.trim()
        if (q.isBlank()) profiles else profiles.filter {
            it.name.contains(q, true) || it.rateLimit.contains(q, true) || it.sessionTimeout.contains(q, true)
        }
    }

    fun resetForm() {
        editingProfile = null
        name = ""
        rateLimit = "5M/10M"
        shared = "1"
        timeout = "1d"
        onShowAddFormChange(false)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "hs_profiles_form") {
            AnimatedVisibility(visible = showAddForm) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = cs.surfaceContainer,
                    border = BorderStroke(
                        1.dp,
                        if (editingProfile != null) cs.primary else cs.outlineVariant.copy(alpha = 0.6f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (editingProfile != null) "Edit Profil '${editingProfile?.name}'" else "Tambah Profil Hotspot",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = cs.onSurface,
                            )
                            TextButton(onClick = ::resetForm) {
                                Text(if (editingProfile != null) "Batal Edit" else "Tutup")
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Nama Profil") },
                                placeholder = { Text("voucher_1hari") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .testTag("hs_tab_profile_name_input"),
                            )
                            OutlinedTextField(
                                value = rateLimit,
                                onValueChange = { rateLimit = it },
                                label = { Text("Rate Limit") },
                                placeholder = { Text("5M/10M") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                            )
                        }

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(ratePresets) { preset ->
                                FilterChip(
                                    selected = rateLimit.equals(preset, true) || (preset == "Unlimited" && rateLimit.isBlank()),
                                    onClick = { rateLimit = if (preset == "Unlimited") "" else preset },
                                    label = {
                                        Text(
                                            text = preset,
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                                        )
                                    },
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = shared,
                                onValueChange = { shared = it.filter(Char::isDigit).take(3) },
                                label = { Text("Shared") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = timeout,
                                onValueChange = { timeout = it },
                                label = { Text("Timeout") },
                                placeholder = { Text("1d / 3h") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                            )
                            Button(
                                onClick = {
                                    if (name.isNotBlank()) {
                                        val editing = editingProfile
                                        if (editing != null) {
                                            onUpdateProfile(editing, name, rateLimit, shared.ifBlank { "1" }, timeout)
                                        } else {
                                            onAddProfile(name, rateLimit, shared.ifBlank { "1" }, timeout)
                                        }
                                        resetForm()
                                    }
                                },
                                enabled = name.isNotBlank(),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("hs_tab_profile_save_button"),
                            ) {
                                Text(if (editingProfile != null) "Update" else "Simpan")
                            }
                        }
                    }
                }
            }
        }

        if (profiles.size > 2) {
            item(key = "hs_profiles_search") {
                HotspotInlineSearchBar(
                    query = searchQuery,
                    placeholder = "Cari profil atau rate limit…",
                    onQueryChange = { searchQuery = it },
                    testTag = "hs_tab_profiles_search",
                )
            }
        }

        if (filteredProfiles.isEmpty()) {
            item(key = "hs_profiles_empty") {
                HotspotEmptyCard(
                    icon = Icons.Rounded.Badge,
                    title = "Belum ada User Profile",
                    subtitle = "Tekan tombol + Profil untuk membuat profil bandwidth dan batas sesi hotspot.",
                )
            }
        } else {
            items(filteredProfiles, key = { "hs_prof_${it.id}" }) { p ->
                val assignedCount = users.count { it.profile.equals(p.name, ignoreCase = true) }
                val isDefault = p.name.equals("default", ignoreCase = true)
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = cs.surfaceContainer,
                    border = BorderStroke(
                        1.dp,
                        if (editingProfile?.id == p.id) cs.primary else cs.outlineVariant.copy(alpha = 0.55f),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hs_tab_profile_card_${p.name}"),
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
                                .clip(RoundedCornerShape(12.dp))
                                .background(cs.secondaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Badge,
                                contentDescription = null,
                                tint = cs.onSecondaryContainer,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = p.name,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = cs.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = cs.primaryContainer,
                                ) {
                                    Text(
                                        text = "$assignedCount user",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = cs.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Rate: ${p.rateLimit}  •  Shared: ${p.sharedUsers}  •  Timeout: ${p.sessionTimeout}",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                                color = cs.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = {
                                editingProfile = p
                                name = p.name
                                rateLimit = if (p.rateLimit.equals("Unlimited", true)) "" else p.rateLimit
                                shared = p.sharedUsers
                                timeout = if (p.sessionTimeout.equals("None", true)) "" else p.sessionTimeout
                                onShowAddFormChange(true)
                            },
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit profil", tint = cs.primary, modifier = Modifier.size(19.dp))
                        }
                        if (!isDefault) {
                            IconButton(onClick = { pendingDelete = p }) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Hapus profil", tint = ext.offlineRed, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = cs.surfaceContainer,
            title = { Text("Hapus User Profile?") },
            text = { Text("Profil '${profile.name}' akan dihapus dari konfigurasi Hotspot router MikroTik.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteProfile(profile)
                        if (editingProfile?.id == profile.id) resetForm()
                        pendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ext.offlineRed),
                ) { Text("Hapus") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Batal") } },
        )
    }
}

@Composable
internal fun HotspotServerTabContent(
    uiState: ErlkimUiState,
    showAddForm: Boolean,
    onShowAddFormChange: (Boolean) -> Unit,
    onAddServer: (name: String, interfaceName: String, addressPool: String, profile: String) -> Unit,
    onToggleServer: (HotspotServer) -> Unit,
    onDeleteServer: (HotspotServer) -> Unit,
    onOpenActiveSessions: () -> Unit,
    onOpenIpBindings: () -> Unit,
    onSwitchToUsersTab: () -> Unit,
    onRefresh: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ext = LocalErlkimColors.current
    val servers = uiState.hotspotServers
    val interfaces = uiState.interfaces

    var selectedServerId by remember(servers) { mutableStateOf(servers.firstOrNull()?.id.orEmpty()) }
    val selectedServer = remember(servers, selectedServerId) {
        servers.firstOrNull { it.id == selectedServerId } ?: servers.firstOrNull()
    }
    val isHotspotActive = uiState.connectionStatus.isConnected &&
        (selectedServer?.let { !it.disabled } ?: servers.any { !it.disabled })

    var serverName by remember { mutableStateOf("") }
    var ifaceName by remember(interfaces) {
        mutableStateOf(
            interfaces.firstOrNull { it.name.contains("hotspot", true) || it.name.contains("bridge", true) }?.name
                ?: interfaces.firstOrNull()?.name
                ?: "bridge-LAN",
        )
    }
    var addressPool by remember { mutableStateOf("hs-pool-1") }
    var serverProfile by remember { mutableStateOf("hsprof1") }
    var pendingDelete by remember { mutableStateOf<HotspotServer?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Interactive Hotspot Control Hero Card
        item(key = "hs_server_hero") {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hotspot_status_card"),
                shape = RoundedCornerShape(22.dp),
                color = cs.surfaceContainer,
                border = BorderStroke(
                    1.dp,
                    if (isHotspotActive) ext.onlineGreen.copy(alpha = 0.4f) else cs.outlineVariant.copy(alpha = 0.6f),
                ),
                tonalElevation = 2.dp,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isHotspotActive) ext.onlineContainer else ext.offlineContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Wifi,
                                    contentDescription = null,
                                    tint = if (isHotspotActive) ext.onlineGreen else ext.offlineRed,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = selectedServer?.name ?: uiState.primaryHotspotName,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = cs.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (isHotspotActive) ext.onlineGreen else ext.offlineRed),
                                    )
                                }
                                Text(
                                    text = if (selectedServer != null) {
                                        "${selectedServer.interfaceName}  •  Pool ${selectedServer.addressPool}"
                                    } else {
                                        if (isHotspotActive) "Hotspot Online" else "Belum ada server aktif"
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                                    color = cs.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (selectedServer != null) {
                            Switch(
                                checked = !selectedServer.disabled,
                                onCheckedChange = { onToggleServer(selectedServer) },
                                modifier = Modifier.testTag("hotspot_hero_master_switch"),
                            )
                        }
                    }

                    if (servers.size > 1) {
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(servers, key = { it.id }) { srv ->
                                FilterChip(
                                    selected = selectedServer?.id == srv.id,
                                    onClick = { selectedServerId = srv.id },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(if (!srv.disabled) ext.onlineGreen else ext.offlineRed),
                                        )
                                    },
                                    label = { Text(srv.name, style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono)) },
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(12.dp))

                    val tx = String.format(Locale.US, "%.1f", uiState.currentUploadMbps)
                    val rx = String.format(Locale.US, "%.1f", uiState.currentDownloadMbps)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HotspotHeroMetricBox(
                            label = "Active",
                            value = "${uiState.activeHotspotCount}",
                            sublabel = "online",
                            accentColor = ext.onlineGreen,
                            onClick = onOpenActiveSessions,
                            modifier = Modifier.weight(1f),
                        )
                        HotspotHeroMetricBox(
                            label = "Users",
                            value = "${uiState.hotspotUsers.size}",
                            sublabel = "${uiState.userProfiles.size} profil",
                            accentColor = cs.secondary,
                            onClick = onSwitchToUsersTab,
                            modifier = Modifier.weight(1f),
                        )
                        HotspotHeroMetricBox(
                            label = "Traffic",
                            value = "↓$rx",
                            sublabel = "↑$tx Mbps",
                            accentColor = cs.tertiary,
                            onClick = onRefresh,
                            modifier = Modifier.weight(1.15f),
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = onOpenIpBindings,
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("IP Bindings (${uiState.ipBindings.size})", style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(
                            onClick = onRefresh,
                            modifier = Modifier
                                .weight(0.8f)
                                .height(40.dp),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Segarkan", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        // Collapsible Add Hotspot Server Form
        item(key = "hs_server_form") {
            AnimatedVisibility(visible = showAddForm) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = cs.surfaceContainer,
                    border = BorderStroke(1.dp, cs.primary.copy(alpha = 0.65f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Tambah Hotspot Server Baru",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = cs.onSurface,
                            )
                            TextButton(onClick = { onShowAddFormChange(false) }) { Text("Tutup") }
                        }
                        OutlinedTextField(
                            value = serverName,
                            onValueChange = { serverName = it },
                            label = { Text("Nama Server") },
                            placeholder = { Text("hotspot-tamu") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("hs_tab_server_name_input"),
                        )
                        if (interfaces.isNotEmpty()) {
                            Text("Pilih Interface:", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(interfaces, key = { it.id }) { iface ->
                                    FilterChip(
                                        selected = ifaceName == iface.name,
                                        onClick = { ifaceName = iface.name },
                                        label = { Text(iface.name, style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono)) },
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = ifaceName,
                            onValueChange = { ifaceName = it },
                            label = { Text("Interface") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = addressPool,
                                onValueChange = { addressPool = it },
                                label = { Text("Address Pool") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = serverProfile,
                                onValueChange = { serverProfile = it },
                                label = { Text("Server Profile") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Button(
                            onClick = {
                                if (serverName.isNotBlank() && ifaceName.isNotBlank()) {
                                    onAddServer(serverName, ifaceName, addressPool, serverProfile)
                                    serverName = ""
                                    onShowAddFormChange(false)
                                }
                            },
                            enabled = serverName.isNotBlank() && ifaceName.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("hs_tab_server_save_button"),
                        ) { Text("Simpan Hotspot Server") }
                    }
                }
            }
        }

        item(key = "hs_server_list_header") {
            Text(
                text = "HOTSPOT SERVERS (${servers.size})",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = cs.onSurfaceVariant,
            )
        }

        if (servers.isEmpty()) {
            item(key = "hs_servers_empty") {
                HotspotEmptyCard(
                    icon = Icons.Rounded.Dns,
                    title = "Belum ada Hotspot Server",
                    subtitle = "Tekan tombol + Server untuk menambahkan server hotspot baru pada interface router.",
                )
            }
        } else {
            items(servers, key = { "hs_srv_${it.id}" }) { srv ->
                val active = !srv.disabled
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = cs.surfaceContainer,
                    border = BorderStroke(
                        1.dp,
                        if (active) ext.onlineGreen.copy(alpha = 0.4f) else cs.outlineVariant.copy(alpha = 0.5f),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hs_tab_server_card_${srv.name}"),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = srv.name,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = cs.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (active) ext.onlineContainer else ext.offlineContainer,
                                ) {
                                    Text(
                                        text = if (active) "Active" else "Disabled",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                        color = if (active) ext.onlineGreen else ext.offlineRed,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Interface: ${srv.interfaceName}  •  Pool: ${srv.addressPool}",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                                color = cs.onSurfaceVariant,
                            )
                            Text(
                                text = "Server Profile: ${srv.profile}",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                                color = cs.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = active,
                            onCheckedChange = { onToggleServer(srv) },
                        )
                        IconButton(onClick = { pendingDelete = srv }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Hapus server hotspot", tint = ext.offlineRed)
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { srv ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = cs.surfaceContainer,
            title = { Text("Hapus Hotspot Server?") },
            text = { Text("Server '${srv.name}' pada interface ${srv.interfaceName} akan dihapus dari router.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteServer(srv)
                        pendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ext.offlineRed),
                ) { Text("Hapus") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Batal") } },
        )
    }
}

private enum class WalledFilter(val label: String) {
    ALL("Semua"),
    ALLOW("Allow"),
    DENY("Deny"),
    DISABLED("Nonaktif"),
}

@Composable
internal fun HotspotWalledGardenTabContent(
    entries: List<WalledGardenEntry>,
    showAddForm: Boolean,
    onShowAddFormChange: (Boolean) -> Unit,
    onAdd: (host: String, port: String, action: String, comment: String) -> Unit,
    onUpdate: (entry: WalledGardenEntry, host: String, port: String, action: String, comment: String) -> Unit,
    onToggleDisabled: (WalledGardenEntry) -> Unit,
    onDelete: (WalledGardenEntry) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ext = LocalErlkimColors.current

    var editingEntry by remember { mutableStateOf<WalledGardenEntry?>(null) }
    var dstHost by remember { mutableStateOf("") }
    var dstPort by remember { mutableStateOf("443") }
    var action by remember { mutableStateOf("allow") }
    var comment by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(WalledFilter.ALL) }
    var pendingDelete by remember { mutableStateOf<WalledGardenEntry?>(null) }

    val quickPresets = listOf(
        "*.whatsapp.net" to "Chat CS",
        "qris.bankindonesia.id" to "Pembayaran QRIS",
        "*.google.com" to "Akses Google",
    )

    val filteredEntries = remember(entries, searchQuery, filter) {
        val q = searchQuery.trim()
        entries.filter { e ->
            val matchesQ = q.isBlank() || e.dstHost.contains(q, true) || e.comment.contains(q, true) || e.dstPort.contains(q, true)
            val matchesF = when (filter) {
                WalledFilter.ALL -> true
                WalledFilter.ALLOW -> e.action.equals("allow", true)
                WalledFilter.DENY -> e.action.equals("deny", true)
                WalledFilter.DISABLED -> e.disabled
            }
            matchesQ && matchesF
        }
    }

    fun resetForm() {
        editingEntry = null
        dstHost = ""
        dstPort = "443"
        action = "allow"
        comment = ""
        onShowAddFormChange(false)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "hs_wg_form") {
            AnimatedVisibility(visible = showAddForm) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = cs.surfaceContainer,
                    border = BorderStroke(
                        1.dp,
                        if (editingEntry != null) cs.primary else cs.outlineVariant.copy(alpha = 0.6f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (editingEntry != null) "Edit Aturan Walled Garden" else "Tambah Aturan Walled Garden",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = cs.onSurface,
                            )
                            TextButton(onClick = ::resetForm) {
                                Text(if (editingEntry != null) "Batal Edit" else "Tutup")
                            }
                        }

                        if (editingEntry == null) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(quickPresets) { (presetHost, presetNote) ->
                                    FilterChip(
                                        selected = dstHost == presetHost,
                                        onClick = {
                                            dstHost = presetHost
                                            comment = presetNote
                                        },
                                        label = { Text(presetHost, style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono)) },
                                    )
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = dstHost,
                                onValueChange = { dstHost = it },
                                label = { Text("Domain / Host") },
                                placeholder = { Text("*.domain.com") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(2f)
                                    .testTag("hs_tab_wg_host_input"),
                            )
                            OutlinedTextField(
                                value = dstPort,
                                onValueChange = { dstPort = it.filter { ch -> ch.isDigit() || ch == '-' || ch == ',' } },
                                label = { Text("Port") },
                                placeholder = { Text("443") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Action:", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                            listOf("allow", "deny").forEach { act ->
                                FilterChip(
                                    selected = action == act,
                                    onClick = { action = act },
                                    label = { Text(act.uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)) },
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = comment,
                                onValueChange = { comment = it },
                                label = { Text("Keterangan") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                            )
                            Button(
                                onClick = {
                                    if (dstHost.isNotBlank()) {
                                        val editing = editingEntry
                                        if (editing != null) {
                                            onUpdate(editing, dstHost, dstPort, action, comment)
                                        } else {
                                            onAdd(dstHost, dstPort, action, comment)
                                        }
                                        resetForm()
                                    }
                                },
                                enabled = dstHost.isNotBlank(),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("hs_tab_wg_save_button"),
                            ) { Text(if (editingEntry != null) "Update" else "Simpan") }
                        }
                    }
                }
            }
        }

        item(key = "hs_wg_search") {
            HotspotInlineSearchBar(
                query = searchQuery,
                placeholder = "Cari domain, port, atau keterangan…",
                onQueryChange = { searchQuery = it },
                testTag = "hs_tab_wg_search",
            )
        }

        item(key = "hs_wg_filters") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(WalledFilter.entries) { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { filter = f },
                        label = { Text(f.label, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
        }

        if (filteredEntries.isEmpty()) {
            item(key = "hs_wg_empty") {
                HotspotEmptyCard(
                    icon = Icons.Rounded.Fence,
                    title = "Belum ada aturan Walled Garden",
                    subtitle = "Tambahkan domain atau IP yang boleh diakses atau diblokir sebelum pengguna login Hotspot.",
                )
            }
        } else {
            items(filteredEntries, key = { "hs_wg_${it.id}" }) { item ->
                val isAllow = item.action.equals("allow", ignoreCase = true)
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = cs.surfaceContainer,
                    border = BorderStroke(
                        1.dp,
                        if (editingEntry?.id == item.id) cs.primary else cs.outlineVariant.copy(alpha = 0.55f),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hs_tab_wg_card_${item.id}"),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = item.dstHost,
                                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold),
                                    color = if (item.disabled) cs.onSurfaceVariant else cs.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isAllow) ext.onlineContainer else ext.offlineContainer,
                                ) {
                                    Text(
                                        text = item.action.uppercase(),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold),
                                        color = if (isAllow) ext.onlineGreen else ext.offlineRed,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Port: ${item.dstPort.ifBlank { "Any" }}${if (item.comment.isNotBlank()) "  •  ${item.comment}" else ""}${if (item.disabled) "  •  Nonaktif" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Switch(
                            checked = !item.disabled,
                            onCheckedChange = { onToggleDisabled(item) },
                        )
                        IconButton(
                            onClick = {
                                editingEntry = item
                                dstHost = item.dstHost
                                dstPort = item.dstPort
                                action = item.action.lowercase().ifBlank { "allow" }
                                comment = item.comment
                                onShowAddFormChange(true)
                            },
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit aturan", tint = cs.primary, modifier = Modifier.size(19.dp))
                        }
                        IconButton(onClick = { pendingDelete = item }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Hapus aturan", tint = ext.offlineRed, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = cs.surfaceContainer,
            title = { Text("Hapus Aturan Walled Garden?") },
            text = { Text("Aturan untuk host '${entry.dstHost}' akan dihapus dari router.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete(entry)
                        if (editingEntry?.id == entry.id) resetForm()
                        pendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ext.offlineRed),
                ) { Text("Hapus") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Batal") } },
        )
    }
}

@Composable
private fun HotspotQuickShortcutChip(
    icon: ImageVector,
    label: String,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = cs.surfaceContainer,
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.55f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = cs.onSurface,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun HotspotInlineSearchBar(
    query: String,
    placeholder: String,
    onQueryChange: (String) -> Unit,
    testTag: String,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(24.dp),
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
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = cs.onSurface),
                    cursorBrush = SolidColor(cs.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(testTag),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
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

@Composable
private fun HotspotEmptyCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = cs.surfaceContainer,
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = cs.onSurfaceVariant, modifier = Modifier.size(36.dp))
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun HotspotHeroMetricBox(
    label: String,
    value: String,
    sublabel: String,
    accentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = cs.surfaceContainerHigh,
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = cs.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Bold,
                ),
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = sublabel,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono, fontSize = 10.sp),
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
