package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.BandwidthQuotaAlert
import com.example.data.model.HotspotUser
import com.example.data.model.QuotaAlertSeverity
import com.example.data.model.UserFilter
import com.example.ui.components.ErlkimBrandTitle
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalErlkimColors
import com.example.ui.viewmodel.ErlkimUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsersScreen(
    uiState: ErlkimUiState,
    onNavigateBack: () -> Unit,
    onSearchChange: (String) -> Unit,
    onSelectFilter: (UserFilter) -> Unit,
    onUserClick: (HotspotUser) -> Unit,
    onShowQrCode: (HotspotUser) -> Unit,
    onDisconnectUser: (HotspotUser) -> Unit,
    onBulkDeleteUsers: (List<HotspotUser>, (Int, Int) -> Unit) -> Unit,
    onAddUserClick: () -> Unit,
    onRefresh: () -> Unit,
    onOpenConnection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val ext = LocalErlkimColors.current
    var menuExpanded by remember { mutableStateOf(false) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedUserIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

    val filtered = uiState.filteredUsers
    val activeUsers = remember(filtered) { filtered.filter { it.isActive && !it.disabled } }
    val inactiveUsers = remember(filtered) { filtered.filter { !it.isActive || it.disabled } }

    // Prune any selected IDs that no longer exist after refresh or deletion
    LaunchedEffect(uiState.hotspotUsers) {
        val validIds = uiState.hotspotUsers.map { it.id }.toSet()
        val pruned = selectedUserIds.intersect(validIds)
        if (pruned.size != selectedUserIds.size) {
            selectedUserIds = pruned
        }
    }

    val filteredIds = remember(filtered) { filtered.map { it.id }.toSet() }
    val allFilteredSelected = filteredIds.isNotEmpty() && filteredIds.all { it in selectedUserIds }
    val selectedUsers = remember(uiState.hotspotUsers, selectedUserIds) {
        uiState.hotspotUsers.filter { it.id in selectedUserIds }
    }

    fun exitSelectionMode() {
        isSelectionMode = false
        selectedUserIds = emptySet()
        showBulkDeleteDialog = false
    }

    fun toggleUserSelection(userId: String) {
        selectedUserIds = if (userId in selectedUserIds) {
            selectedUserIds - userId
        } else {
            selectedUserIds + userId
        }
    }

    BackHandler(enabled = isSelectionMode) {
        if (!uiState.isBulkDeleting) {
            exitSelectionMode()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(cs.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = isSelectionMode,
                label = "users_top_bar_transition",
            ) { selecting ->
                if (selecting) {
                    TopAppBar(
                        title = {
                            Text(
                                text = "${selectedUserIds.size} dipilih",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("users_selected_count_title"),
                            )
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = { if (!uiState.isBulkDeleting) exitSelectionMode() },
                                enabled = !uiState.isBulkDeleting,
                                modifier = Modifier.testTag("users_exit_selection_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.cd_exit_selection),
                                )
                            }
                        },
                        actions = {
                            if (filtered.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        selectedUserIds = if (allFilteredSelected) {
                                            selectedUserIds - filteredIds
                                        } else {
                                            selectedUserIds + filteredIds
                                        }
                                    },
                                    enabled = !uiState.isBulkDeleting,
                                    modifier = Modifier.testTag("users_select_all_button"),
                                ) {
                                    Icon(
                                        imageVector = if (allFilteredSelected) Icons.Rounded.Deselect else Icons.Rounded.SelectAll,
                                        contentDescription = stringResource(
                                            if (allFilteredSelected) R.string.users_deselect_all else R.string.users_select_all,
                                        ),
                                        tint = cs.primary,
                                    )
                                }
                            }
                            if (selectedUsers.isNotEmpty()) {
                                IconButton(
                                    onClick = { showBulkDeleteDialog = true },
                                    enabled = !uiState.isBulkDeleting,
                                    modifier = Modifier.testTag("users_top_bulk_delete_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteSweep,
                                        contentDescription = stringResource(R.string.users_bulk_delete),
                                        tint = ext.offlineRed,
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = cs.surfaceContainerHigh,
                            titleContentColor = cs.onSurface,
                            navigationIconContentColor = cs.onSurface,
                            actionIconContentColor = cs.onSurface,
                        ),
                    )
                } else {
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
                                modifier = Modifier.testTag("users_back_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = stringResource(R.string.cd_navigate_back),
                                )
                            }
                        },
                        actions = {
                            if (uiState.hotspotUsers.isNotEmpty()) {
                                IconButton(
                                    onClick = { isSelectionMode = true },
                                    modifier = Modifier.testTag("users_select_mode_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Checklist,
                                        contentDescription = stringResource(R.string.cd_select_users),
                                        tint = cs.primary,
                                    )
                                }
                            }
                            Box {
                                IconButton(
                                    onClick = { menuExpanded = true },
                                    modifier = Modifier.testTag("users_more_button"),
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
                                    if (uiState.hotspotUsers.isNotEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.users_select_mode)) },
                                            onClick = {
                                                menuExpanded = false
                                                isSelectionMode = true
                                            },
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Segarkan Daftar") },
                                        onClick = {
                                            menuExpanded = false
                                            onRefresh()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Tambah User Baru") },
                                        onClick = {
                                            menuExpanded = false
                                            onAddUserClick()
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
                }
            }

            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh,
                state = pullRefreshState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("users_pull_refresh"),
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
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 104.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (isSelectionMode) {
                        item(key = "selection_helper_bar") {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("users_selection_banner"),
                                shape = RoundedCornerShape(16.dp),
                                color = cs.surfaceContainerHigh,
                                border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.55f)),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = if (selectedUserIds.isEmpty()) {
                                            "Ketuk user untuk memilih lalu hapus massal"
                                        } else {
                                            "${selectedUserIds.size} dari ${filtered.size} user dipilih"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = cs.onSurfaceVariant,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (filtered.isNotEmpty()) {
                                        TextButton(
                                            onClick = {
                                                selectedUserIds = if (allFilteredSelected) {
                                                    selectedUserIds - filteredIds
                                                } else {
                                                    selectedUserIds + filteredIds
                                                }
                                            },
                                            enabled = !uiState.isBulkDeleting,
                                            modifier = Modifier.testTag("users_banner_toggle_all"),
                                        ) {
                                            Text(
                                                text = stringResource(
                                                    if (allFilteredSelected) R.string.users_deselect_all else R.string.users_select_all,
                                                ),
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item(key = "search_pill") {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(26.dp),
                            color = cs.surfaceContainerHigh,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = stringResource(R.string.cd_search),
                                    tint = cs.onSurfaceVariant,
                                    modifier = Modifier.size(21.dp),
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Box(modifier = Modifier.weight(1f)) {
                                    if (uiState.userSearchQuery.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.search_placeholder),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = cs.onSurfaceVariant,
                                        )
                                    }
                                    BasicTextField(
                                        value = uiState.userSearchQuery,
                                        onValueChange = onSearchChange,
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface),
                                        cursorBrush = SolidColor(cs.primary),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("users_search_input"),
                                    )
                                }
                                if (uiState.userSearchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { onSearchChange("") },
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

                    item(key = "filter_chips") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            UserFilter.entries.forEach { filter ->
                                val selected = uiState.selectedUserFilter == filter
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .testTag("filter_chip_${filter.name.lowercase()}")
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onSelectFilter(filter) },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) cs.primaryContainer else cs.background,
                                    border = if (selected) null else BorderStroke(1.dp, cs.outline),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = filter.label,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (selected) cs.onPrimaryContainer else cs.onSurface,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (filtered.isEmpty()) {
                        item(key = "empty_users") {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                                shape = RoundedCornerShape(22.dp),
                                color = cs.surfaceContainer,
                                border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.6f)),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Person,
                                        contentDescription = null,
                                        tint = cs.onSurfaceVariant,
                                        modifier = Modifier.size(40.dp),
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = if (uiState.connectionStatus.isConnected) {
                                            "Tidak ada user hotspot ditemukan"
                                        } else {
                                            "Belum terhubung ke RouterOS"
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        color = cs.onSurface,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (uiState.connectionStatus.isConnected) {
                                            "Gunakan tombol + Add User di bawah atau ubah kata kunci pencarian."
                                        } else {
                                            "Hubungkan MikroTik Anda di Settings atau aktifkan Mode Demo dari Dashboard."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = cs.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    if (activeUsers.isNotEmpty()) {
                        item(key = "header_active") {
                            Text(
                                text = stringResource(R.string.section_active),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = cs.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp, start = 2.dp),
                            )
                        }
                        items(activeUsers, key = { "active_${it.id}" }) { user ->
                            HotspotUserCard(
                                user = user,
                                activeIp = uiState.activeIpForUser(user.name),
                                quotaAlert = uiState.quotaAlertForUser(user.name),
                                isActiveSection = true,
                                selectionMode = isSelectionMode,
                                isSelected = user.id in selectedUserIds,
                                onClick = {
                                    if (isSelectionMode) {
                                        toggleUserSelection(user.id)
                                    } else {
                                        onUserClick(user)
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        isSelectionMode = true
                                    }
                                    selectedUserIds = selectedUserIds + user.id
                                },
                                onToggleSelect = { toggleUserSelection(user.id) },
                                onShowQr = { onShowQrCode(user) },
                                onDisconnect = { onDisconnectUser(user) },
                            )
                        }
                    }

                    if (inactiveUsers.isNotEmpty()) {
                        item(key = "header_inactive") {
                            Text(
                                text = stringResource(R.string.section_inactive),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = cs.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp, start = 2.dp),
                            )
                        }
                        items(inactiveUsers, key = { "inactive_${it.id}" }) { user ->
                            HotspotUserCard(
                                user = user,
                                activeIp = null,
                                quotaAlert = uiState.quotaAlertForUser(user.name),
                                isActiveSection = false,
                                selectionMode = isSelectionMode,
                                isSelected = user.id in selectedUserIds,
                                onClick = {
                                    if (isSelectionMode) {
                                        toggleUserSelection(user.id)
                                    } else {
                                        onUserClick(user)
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        isSelectionMode = true
                                    }
                                    selectedUserIds = selectedUserIds + user.id
                                },
                                onToggleSelect = { toggleUserSelection(user.id) },
                                onShowQr = { onShowQrCode(user) },
                                onDisconnect = null,
                            )
                        }
                    }
                }
            }
        }

        if (isSelectionMode) {
            if (selectedUsers.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { if (!uiState.isBulkDeleting) showBulkDeleteDialog = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 18.dp, bottom = 18.dp)
                        .testTag("users_bulk_delete_fab"),
                    shape = RoundedCornerShape(18.dp),
                    containerColor = ext.offlineRed,
                    contentColor = Color(0xFF141115),
                    icon = {
                        if (uiState.isBulkDeleting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF141115),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = stringResource(R.string.users_bulk_delete),
                            )
                        }
                    },
                    text = {
                        Text(
                            text = if (uiState.isBulkDeleting) {
                                stringResource(R.string.users_bulk_deleting)
                            } else {
                                "${stringResource(R.string.users_bulk_delete)} (${selectedUsers.size})"
                            },
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        )
                    },
                )
            }
        } else {
            ExtendedFloatingActionButton(
                onClick = onAddUserClick,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 18.dp, bottom = 18.dp)
                    .testTag("users_add_user_fab"),
                shape = RoundedCornerShape(18.dp),
                containerColor = cs.primary,
                contentColor = cs.onPrimary,
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.cd_add_user),
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.action_add_user),
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
            )
        }
    }

    if (showBulkDeleteDialog && selectedUsers.isNotEmpty()) {
        val previewNames = selectedUsers.take(5).joinToString(", ") { it.name } +
            if (selectedUsers.size > 5) ", +${selectedUsers.size - 5} lainnya" else ""
        AlertDialog(
            onDismissRequest = { if (!uiState.isBulkDeleting) showBulkDeleteDialog = false },
            containerColor = cs.surfaceContainerHigh,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.DeleteSweep,
                    contentDescription = null,
                    tint = ext.offlineRed,
                    modifier = Modifier.size(28.dp),
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.users_bulk_delete_confirm_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Anda akan menghapus ${selectedUsers.size} akun hotspot user secara permanen dari router MikroTik:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = cs.surfaceContainer,
                        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.55f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = previewNames,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.Medium,
                            ),
                            color = cs.onSurface,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBulkDeleteDialog = false
                        onBulkDeleteUsers(selectedUsers) { _, _ ->
                            exitSelectionMode()
                        }
                    },
                    enabled = !uiState.isBulkDeleting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ext.offlineRed,
                        contentColor = Color(0xFF141115),
                    ),
                    modifier = Modifier.testTag("users_confirm_bulk_delete_button"),
                ) {
                    Text(
                        text = stringResource(R.string.users_bulk_delete_confirm_action),
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showBulkDeleteDialog = false },
                    enabled = !uiState.isBulkDeleting,
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HotspotUserCard(
    user: HotspotUser,
    activeIp: String?,
    quotaAlert: BandwidthQuotaAlert?,
    isActiveSection: Boolean,
    selectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleSelect: () -> Unit,
    onShowQr: () -> Unit,
    onDisconnect: (() -> Unit)?,
) {
    val cs = MaterialTheme.colorScheme
    val ext = LocalErlkimColors.current

    val (avatarBg, avatarIcon) = when {
        user.isVip -> ext.avatarVipBg to ext.avatarVipIcon
        isActiveSection -> ext.avatarActiveBg to ext.avatarActiveIcon
        else -> ext.avatarInactiveBg to ext.avatarInactiveIcon
    }

    val cardBgColor = if (selectionMode && isSelected) {
        cs.primaryContainer.copy(alpha = 0.24f)
    } else {
        cs.surfaceContainer
    }

    val cardBorderColor = when {
        selectionMode && isSelected -> cs.primary
        quotaAlert?.severity == QuotaAlertSeverity.EXCEEDED -> ext.offlineRed.copy(alpha = 0.55f)
        quotaAlert?.severity == QuotaAlertSeverity.WARNING -> ext.demoAmber.copy(alpha = 0.45f)
        else -> cs.outlineVariant.copy(alpha = 0.55f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("user_card_${user.name}")
            .clip(RoundedCornerShape(22.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(22.dp),
        color = cardBgColor,
        border = BorderStroke(if (selectionMode && isSelected) 1.5.dp else 1.dp, cardBorderColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = cs.primary,
                        checkmarkColor = cs.onPrimary,
                    ),
                    modifier = Modifier.testTag("user_checkbox_${user.name}"),
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(avatarBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    tint = avatarIcon,
                    modifier = Modifier.size(28.dp),
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Profil: ${user.profile}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Kuota: ${user.formattedQuota()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (quotaAlert != null) {
                    val isExceeded = quotaAlert.severity == QuotaAlertSeverity.EXCEEDED
                    val badgeBg = if (isExceeded) ext.offlineContainer else ext.demoContainer
                    val badgeFg = if (isExceeded) ext.offlineRed else ext.demoAmber
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = badgeBg,
                        modifier = Modifier.testTag("user_quota_badge_${user.name}"),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = if (isExceeded) Icons.Rounded.ErrorOutline else Icons.Rounded.WarningAmber,
                                contentDescription = null,
                                tint = badgeFg,
                                modifier = Modifier.size(12.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isExceeded) {
                                    "${stringResource(R.string.quota_badge_exceeded)} ${quotaAlert.usagePercent}%"
                                } else {
                                    "${stringResource(R.string.quota_badge_warning)} ${quotaAlert.usagePercent}%"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = badgeFg,
                                maxLines = 1,
                            )
                        }
                    }
                }
                if (!activeIp.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "IP: $activeIp",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                        color = cs.tertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (!selectionMode) {
                IconButton(
                    onClick = onShowQr,
                    modifier = Modifier.testTag("qr_user_${user.name}"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.QrCode2,
                        contentDescription = stringResource(R.string.cd_show_user_qr),
                        tint = cs.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }

                if (onDisconnect != null) {
                    Spacer(modifier = Modifier.width(2.dp))
                    FilledTonalButton(
                        onClick = onDisconnect,
                        modifier = Modifier.testTag("disconnect_user_${user.name}"),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
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
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.action_disconnect_user),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.cd_chevron),
                        tint = cs.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
