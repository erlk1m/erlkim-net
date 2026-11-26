package com.erlkim.mikrotik.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.erlkim.mikrotik.data.model.ConnectionStatus
import com.erlkim.mikrotik.data.model.RouterInterface
import com.erlkim.mikrotik.data.model.formatBytes
import com.erlkim.mikrotik.ui.theme.JetBrainsMono
import com.erlkim.mikrotik.ui.theme.LocalErlkimColors
import com.erlkim.mikrotik.ui.viewmodel.InterfaceFilter
import com.erlkim.mikrotik.ui.viewmodel.InterfacesFeatureUiState
import com.erlkim.mikrotik.ui.viewmodel.formatFeatureSyncBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InterfacesSheet(
    interfaces: List<RouterInterface>,
    selectedTrafficInterface: String,
    featureState: InterfacesFeatureUiState = InterfacesFeatureUiState(),
    onFeatureStateChange: ((InterfacesFeatureUiState) -> InterfacesFeatureUiState) -> Unit = {},
    scrollState: ScrollState = rememberScrollState(),
    connectionStatus: ConnectionStatus = ConnectionStatus.ConnectedDemo,
    isRefreshing: Boolean = false,
    isMutating: Boolean = false,
    onToggle: (RouterInterface) -> Unit,
    onUpdate: (RouterInterface, comment: String, mtu: String) -> Unit,
    onResetCounters: (RouterInterface) -> Unit,
    onMonitorTraffic: (String) -> Unit,
    onRefresh: () -> Unit,
    onReconnect: () -> Unit = onRefresh,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ext = LocalErlkimColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val searchQuery = featureState.searchQuery
    val selectedFilter = featureState.selectedFilter
    val editingIface = remember(interfaces, featureState.editingInterfaceId) {
        featureState.editingInterfaceId?.let { id -> interfaces.firstOrNull { it.id == id } }
    }
    val commentInput = featureState.commentDraft
    val mtuInput = featureState.mtuDraft
    var pendingDisableIface by remember { mutableStateOf<RouterInterface?>(null) }

    val runningCount = remember(interfaces) { interfaces.count { it.running && !it.disabled } }
    val downCount = remember(interfaces) { interfaces.count { !it.running && !it.disabled } }
    val disabledCount = remember(interfaces) { interfaces.count { it.disabled } }
    val refreshingNow = isRefreshing || featureState.isRefreshingSection
    val syncLabel = formatFeatureSyncBadge(featureState.lastSyncedMs, connectionStatus.isConnected)

    val filteredInterfaces = remember(interfaces, searchQuery, selectedFilter) {
        val q = searchQuery.trim()
        interfaces.filter { iface ->
            val matchesQuery = q.isBlank() ||
                iface.name.contains(q, ignoreCase = true) ||
                iface.type.contains(q, ignoreCase = true) ||
                iface.macAddress.contains(q, ignoreCase = true) ||
                iface.comment.contains(q, ignoreCase = true)
            val matchesFilter = when (selectedFilter) {
                InterfaceFilter.ALL -> true
                InterfaceFilter.RUNNING -> iface.running && !iface.disabled
                InterfaceFilter.DOWN -> !iface.running && !iface.disabled
                InterfaceFilter.DISABLED -> iface.disabled
            }
            matchesQuery && matchesFilter
        }
    }

    val mtuInt = mtuInput.trim().toIntOrNull()
    val mtuInvalid = mtuInput.isNotBlank() && (mtuInt == null || mtuInt !in 68..65535)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = cs.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(cs.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SettingsEthernet,
                            contentDescription = null,
                            tint = cs.onPrimaryContainer,
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Interfaces Router",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = cs.onSurface,
                        )
                        Text(
                            text = "${interfaces.size} port • $runningCount running • $syncLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(
                    onClick = onRefresh,
                    enabled = !refreshingNow,
                    modifier = Modifier.testTag("interfaces_refresh_button"),
                ) {
                    if (refreshingNow) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = cs.primary,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Segarkan daftar interface",
                            tint = cs.onSurfaceVariant,
                        )
                    }
                }
            }

            if (connectionStatus is ConnectionStatus.Disconnected) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ext.offlineContainer,
                    border = BorderStroke(1.dp, ext.offlineRed.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Koneksi terputus • ${connectionStatus.reason}",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = onReconnect,
                            modifier = Modifier.testTag("interfaces_reconnect_button"),
                        ) {
                            Text("Hubungkan Ulang", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // KPI Summary Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                InterfaceKpiCard(
                    label = "Running",
                    value = runningCount.toString(),
                    accent = ext.onlineGreen,
                    modifier = Modifier.weight(1f),
                )
                InterfaceKpiCard(
                    label = "Link Down",
                    value = downCount.toString(),
                    accent = ext.demoAmber,
                    modifier = Modifier.weight(1f),
                )
                InterfaceKpiCard(
                    label = "Disabled",
                    value = disabledCount.toString(),
                    accent = ext.offlineRed,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Inline Edit Form
            AnimatedVisibility(visible = editingIface != null) {
                val target = editingIface
                if (target != null) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = cs.surfaceContainerHigh,
                        border = BorderStroke(1.dp, cs.primary.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Edit ${target.name}",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = cs.primary,
                                )
                                IconButton(
                                    onClick = { onFeatureStateChange { it.copy(editingInterfaceId = null) } },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Tutup form edit interface",
                                        tint = cs.onSurfaceVariant,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = commentInput,
                                onValueChange = { v -> onFeatureStateChange { it.copy(commentDraft = v) } },
                                label = { Text("Komentar / Label Port") },
                                placeholder = { Text("mis. WAN ISP Utama / AP Lantai 2") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("interface_edit_comment_input"),
                                shape = RoundedCornerShape(14.dp),
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = mtuInput,
                                onValueChange = { raw ->
                                    val digits = raw.filter { it.isDigit() }.take(5)
                                    onFeatureStateChange { it.copy(mtuDraft = digits) }
                                },
                                label = { Text("MTU (Byte)") },
                                placeholder = { Text("1500") },
                                singleLine = true,
                                isError = mtuInvalid,
                                supportingText = {
                                    Text(
                                        if (mtuInvalid) "MTU harus antara 68 – 65535" else "Standar Ethernet: 1500 • PPPoE: 1480",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("interface_edit_mtu_input"),
                                shape = RoundedCornerShape(14.dp),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = { onFeatureStateChange { it.copy(editingInterfaceId = null) } },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Text("Batal")
                                }
                                Button(
                                    onClick = {
                                        onUpdate(target, commentInput, mtuInput)
                                        onFeatureStateChange { it.copy(editingInterfaceId = null) }
                                    },
                                    enabled = !mtuInvalid && !isMutating,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("interface_save_edit_button"),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Text("Simpan")
                                }
                            }
                        }
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { q -> onFeatureStateChange { it.copy(searchQuery = q) } },
                placeholder = { Text("Cari nama port, tipe, MAC, atau komentar...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onFeatureStateChange { it.copy(searchQuery = "") } }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Hapus pencarian")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("interfaces_search_input"),
                shape = RoundedCornerShape(14.dp),
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(InterfaceFilter.entries) { filter ->
                    val count = when (filter) {
                        InterfaceFilter.ALL -> interfaces.size
                        InterfaceFilter.RUNNING -> runningCount
                        InterfaceFilter.DOWN -> downCount
                        InterfaceFilter.DISABLED -> disabledCount
                    }
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { onFeatureStateChange { it.copy(selectedFilter = filter) } },
                        label = { Text("${filter.label} ($count)") },
                        modifier = Modifier.testTag("interface_filter_${filter.name.lowercase()}"),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (filteredInterfaces.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = cs.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = if (interfaces.isEmpty()) {
                            "Belum ada data interface. Hubungkan ke RouterOS atau aktifkan Mode Demo."
                        } else {
                            "Tidak ada interface yang cocok dengan pencarian/filter."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    filteredInterfaces.forEach { iface ->
                        val isMonitored = selectedTrafficInterface.equals(iface.name, ignoreCase = true)
                        InterfaceItemCard(
                            iface = iface,
                            isMonitored = isMonitored,
                            controlsEnabled = !isMutating,
                            onSwitchToggle = {
                                if (!iface.disabled && iface.running) {
                                    pendingDisableIface = iface
                                } else {
                                    onToggle(iface)
                                }
                            },
                            onEdit = {
                                onFeatureStateChange {
                                    it.copy(
                                        editingInterfaceId = iface.id,
                                        commentDraft = iface.comment,
                                        mtuDraft = iface.mtu.ifBlank { "1500" },
                                    )
                                }
                            },
                            onResetCounters = { onResetCounters(iface) },
                            onMonitorTraffic = {
                                onMonitorTraffic(iface.name)
                                onDismiss()
                            },
                        )
                    }
                }
            }
        }
    }

    // Safety Confirmation before Disabling an Active/Running Interface
    pendingDisableIface?.let { iface ->
        AlertDialog(
            onDismissRequest = { pendingDisableIface = null },
            containerColor = cs.surfaceContainer,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = ext.demoAmber,
                )
            },
            title = { Text("Nonaktifkan ${iface.name}?") },
            text = {
                Text(
                    text = "Interface ${iface.name} (${iface.type.uppercase()}) sedang dalam status Running. Menonaktifkan port WAN/uplink dapat memutus koneksi API ke router.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onToggle(iface)
                        pendingDisableIface = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ext.offlineRed,
                        contentColor = cs.onError,
                    ),
                    modifier = Modifier.testTag("confirm_disable_interface_button"),
                ) {
                    Text("Nonaktifkan")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDisableIface = null }) {
                    Text("Batal")
                }
            },
        )
    }
}

@Composable
private fun InterfaceKpiCard(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cs.surfaceContainerHigh,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.3f)),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Bold,
                ),
                color = cs.onSurface,
            )
        }
    }
}

@Composable
private fun InterfaceItemCard(
    iface: RouterInterface,
    isMonitored: Boolean,
    controlsEnabled: Boolean,
    onSwitchToggle: () -> Unit,
    onEdit: () -> Unit,
    onResetCounters: () -> Unit,
    onMonitorTraffic: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ext = LocalErlkimColors.current
    val statusLabel = when {
        iface.disabled -> "Disabled"
        iface.running -> "Running"
        else -> "Link Down"
    }
    val statusColor = when {
        iface.disabled -> ext.offlineRed
        iface.running -> ext.onlineGreen
        else -> ext.demoAmber
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = cs.surfaceContainerHigh,
        border = BorderStroke(
            width = 1.dp,
            color = if (isMonitored) cs.primary.copy(alpha = 0.6f) else cs.outlineVariant.copy(alpha = 0.35f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = iface.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = cs.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = statusColor.copy(alpha = 0.16f),
                        ) {
                            Text(
                                text = statusLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = statusColor,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                        }
                        if (isMonitored) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = cs.primaryContainer,
                            ) {
                                Text(
                                    text = "Grafik",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = cs.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    val metaParts = buildList {
                        add(iface.type.uppercase())
                        if (iface.mtu.isNotBlank()) add("MTU ${iface.mtu}")
                        if (iface.macAddress.isNotBlank()) add(iface.macAddress)
                    }
                    Text(
                        text = metaParts.joinToString("  •  "),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = JetBrainsMono,
                            fontSize = 11.sp,
                        ),
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    if (iface.comment.isNotBlank()) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = iface.comment,
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Switch(
                    checked = !iface.disabled,
                    onCheckedChange = { onSwitchToggle() },
                    enabled = controlsEnabled,
                    modifier = Modifier.testTag("switch_interface_${iface.name}"),
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // RX / TX Counters Strip
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = cs.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "RX ${formatBytes(iface.rxBytes)}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = cs.primary,
                    )
                    Text(
                        text = "TX ${formatBytes(iface.txBytes)}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = cs.tertiary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(
                    onClick = onMonitorTraffic,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("monitor_interface_${iface.name}"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ShowChart,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isMonitored) "Dipantau" else "Pantau Grafik",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                OutlinedButton(
                    onClick = onEdit,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("edit_interface_${iface.name}"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Edit ${iface.name}",
                        modifier = Modifier.size(16.dp),
                    )
                }
                OutlinedButton(
                    onClick = onResetCounters,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("reset_counter_interface_${iface.name}"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RestartAlt,
                        contentDescription = "Reset counter ${iface.name}",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
