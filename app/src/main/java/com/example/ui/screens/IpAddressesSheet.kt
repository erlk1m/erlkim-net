package com.example.ui.screens

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ConnectionStatus
import com.example.data.model.IpAddressEntry
import com.example.data.model.RouterInterface
import com.example.data.model.parseIpv4Cidr
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalErlkimColors
import com.example.ui.viewmodel.IpAddressFilter
import com.example.ui.viewmodel.IpAddressesFeatureUiState
import com.example.ui.viewmodel.formatFeatureSyncBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IpAddressesSheet(
    addresses: List<IpAddressEntry>,
    interfaces: List<RouterInterface>,
    featureState: IpAddressesFeatureUiState = IpAddressesFeatureUiState(),
    onFeatureStateChange: ((IpAddressesFeatureUiState) -> IpAddressesFeatureUiState) -> Unit = {},
    scrollState: ScrollState = rememberScrollState(),
    connectionStatus: ConnectionStatus = ConnectionStatus.ConnectedDemo,
    isRefreshing: Boolean = false,
    isMutating: Boolean = false,
    onAdd: (address: String, iface: String, comment: String) -> Unit,
    onUpdate: (entry: IpAddressEntry, address: String, iface: String, comment: String) -> Unit,
    onToggleDisabled: (IpAddressEntry) -> Unit,
    onDelete: (IpAddressEntry) -> Unit,
    onRefresh: () -> Unit,
    onReconnect: () -> Unit = onRefresh,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ext = LocalErlkimColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val defaultIfaceName = remember(interfaces) {
        interfaces.firstOrNull { it.name.contains("bridge", true) || it.name.contains("lan", true) }?.name
            ?: interfaces.firstOrNull()?.name
            ?: "bridge-LAN"
    }

    val showForm = featureState.showForm
    val editingEntry = remember(addresses, featureState.editingEntryId) {
        featureState.editingEntryId?.let { id -> addresses.firstOrNull { it.id == id } }
    }
    val ipText = featureState.ipDraft
    val ifaceText = featureState.interfaceDraft.ifBlank { defaultIfaceName }
    val comment = featureState.commentDraft
    val searchQuery = featureState.searchQuery
    val selectedFilter = featureState.selectedFilter
    var pendingDelete by remember { mutableStateOf<IpAddressEntry?>(null) }

    val cidrInfo = remember(ipText) { parseIpv4Cidr(ipText) }
    val cidrError = ipText.isNotBlank() && cidrInfo == null
    val cidrPresets = listOf("/24", "/22", "/28", "/30")
    val refreshingNow = isRefreshing || featureState.isRefreshingSection
    val syncLabel = formatFeatureSyncBadge(featureState.lastSyncedMs, connectionStatus.isConnected)

    val activeCount = remember(addresses) { addresses.count { !it.disabled && !it.invalid } }
    val disabledCount = remember(addresses) { addresses.count { it.disabled } }
    val dynamicCount = remember(addresses) { addresses.count { it.dynamic } }

    val filteredAddresses = remember(addresses, searchQuery, selectedFilter) {
        val q = searchQuery.trim()
        addresses.filter { entry ->
            val matchesQuery = q.isBlank() ||
                entry.address.contains(q, ignoreCase = true) ||
                entry.network.contains(q, ignoreCase = true) ||
                entry.interfaceName.contains(q, ignoreCase = true) ||
                entry.comment.contains(q, ignoreCase = true)
            val matchesFilter = when (selectedFilter) {
                IpAddressFilter.ALL -> true
                IpAddressFilter.ACTIVE -> !entry.disabled && !entry.invalid
                IpAddressFilter.DISABLED -> entry.disabled
                IpAddressFilter.DYNAMIC -> entry.dynamic
            }
            matchesQuery && matchesFilter
        }
    }

    fun resetForm() {
        onFeatureStateChange {
            it.copy(
                editingEntryId = null,
                ipDraft = "",
                commentDraft = "",
                interfaceDraft = defaultIfaceName,
                showForm = false,
            )
        }
    }

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
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(cs.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AccountTree,
                            contentDescription = null,
                            tint = cs.onPrimaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "IP Addresses (${addresses.size})",
                            style = MaterialTheme.typography.titleLarge,
                            color = cs.onSurface,
                        )
                        Text(
                            text = "$activeCount aktif • $syncLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onRefresh,
                        enabled = !refreshingNow,
                        modifier = Modifier.testTag("ip_refresh_button"),
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
                                contentDescription = "Segarkan IP Addresses",
                                tint = cs.onSurfaceVariant,
                            )
                        }
                    }
                    TextButton(
                        onClick = {
                            if (showForm && editingEntry != null) {
                                resetForm()
                            } else {
                                onFeatureStateChange {
                                    it.copy(
                                        showForm = !it.showForm,
                                        interfaceDraft = it.interfaceDraft.ifBlank { defaultIfaceName },
                                    )
                                }
                            }
                        },
                        modifier = Modifier.testTag("ip_add_toggle"),
                    ) {
                        Icon(
                            imageVector = if (showForm) Icons.Rounded.Close else Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (showForm) "Tutup" else "Tambah")
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
                            text = "Koneksi terputus • menampilkan data cache",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = onReconnect,
                            modifier = Modifier.testTag("ip_reconnect_button"),
                        ) {
                            Text("Hubungkan Ulang", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Summary KPI Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IpStatPill(
                    label = "Aktif",
                    value = activeCount.toString(),
                    accent = ext.onlineGreen,
                    modifier = Modifier.weight(1f),
                )
                IpStatPill(
                    label = "Nonaktif",
                    value = disabledCount.toString(),
                    accent = if (disabledCount > 0) ext.offlineRed else cs.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IpStatPill(
                    label = "Dynamic",
                    value = dynamicCount.toString(),
                    accent = cs.primary,
                    modifier = Modifier.weight(1f),
                )
            }

            // Collapsible Add / Edit Form
            AnimatedVisibility(visible = showForm) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = cs.surfaceContainerHigh,
                        border = BorderStroke(
                            1.dp,
                            if (editingEntry != null) cs.primary else cs.outlineVariant.copy(alpha = 0.55f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = if (editingEntry != null) "Edit IP Address" else "Tambah IP Address Baru",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = cs.onSurface,
                                )
                                if (editingEntry != null) {
                                    TextButton(onClick = ::resetForm) { Text("Batal Edit") }
                                }
                            }

                            OutlinedTextField(
                                value = ipText,
                                onValueChange = { v -> onFeatureStateChange { it.copy(ipDraft = v) } },
                                label = { Text("IP Address / CIDR") },
                                placeholder = { Text("192.168.10.1/24") },
                                singleLine = true,
                                isError = cidrError,
                                supportingText = {
                                    when {
                                        cidrError -> Text("Gunakan format IPv4/CIDR valid, contoh 192.168.10.1/24")
                                        cidrInfo != null -> Text(
                                            text = "Network: ${cidrInfo.networkAddress}/${cidrInfo.prefix} • ${cidrInfo.usableHosts} host usable",
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                                            color = ext.onlineGreen,
                                        )
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("ip_cidr_input"),
                            )

                            // Quick CIDR suffix chips
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(cidrPresets) { suffix ->
                                    val currentBase = ipText.substringBefore("/").ifBlank { "192.168.10.1" }
                                    FilterChip(
                                        selected = ipText.endsWith(suffix),
                                        onClick = { onFeatureStateChange { it.copy(ipDraft = "$currentBase$suffix") } },
                                        label = {
                                            Text(
                                                text = suffix,
                                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                                            )
                                        },
                                    )
                                }
                            }

                            if (interfaces.isNotEmpty()) {
                                Text(
                                    text = "Pilih Interface:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = cs.onSurfaceVariant,
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(interfaces, key = { it.id }) { iface ->
                                        FilterChip(
                                            selected = ifaceText == iface.name,
                                            onClick = { onFeatureStateChange { it.copy(interfaceDraft = iface.name) } },
                                            label = {
                                                Text(
                                                    text = iface.name,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                                                )
                                            },
                                        )
                                    }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = ifaceText,
                                    onValueChange = { v -> onFeatureStateChange { it.copy(interfaceDraft = v) } },
                                    label = { Text("Interface") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("ip_interface_input"),
                                )
                                OutlinedTextField(
                                    value = comment,
                                    onValueChange = { v -> onFeatureStateChange { it.copy(commentDraft = v) } },
                                    label = { Text("Keterangan") },
                                    placeholder = { Text("Gateway LAN") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1.1f)
                                        .testTag("ip_comment_input"),
                                )
                            }

                            Button(
                                onClick = {
                                    if (cidrInfo != null && ifaceText.isNotBlank()) {
                                        val editing = editingEntry
                                        if (editing != null) {
                                            onUpdate(editing, ipText.trim(), ifaceText.trim(), comment.trim())
                                        } else {
                                            onAdd(ipText.trim(), ifaceText.trim(), comment.trim())
                                        }
                                        resetForm()
                                    }
                                },
                                enabled = cidrInfo != null && ifaceText.isNotBlank() && !isMutating,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("ip_save_button"),
                            ) {
                                Text(if (editingEntry != null) "Simpan Perubahan IP" else "Simpan IP Address")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search & Filter Row
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { q -> onFeatureStateChange { it.copy(searchQuery = q) } },
                placeholder = { Text("Cari IP, network, interface, atau keterangan...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onFeatureStateChange { it.copy(searchQuery = "") } }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Hapus pencarian", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ip_search_input"),
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(IpAddressFilter.entries) { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { onFeatureStateChange { it.copy(selectedFilter = filter) } },
                        label = { Text(filter.label, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("ip_filter_${filter.name.lowercase()}"),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (filteredAddresses.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = cs.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = if (addresses.isEmpty()) {
                            "Belum ada IP Address terdaftar. Tekan 'Tambah' untuk menetapkan IP baru pada interface router."
                        } else {
                            "Tidak ada IP Address yang cocok dengan filter atau pencarian saat ini."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    filteredAddresses.forEach { ip ->
                        val isActive = !ip.disabled && !ip.invalid
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = cs.surfaceContainerHigh,
                            border = BorderStroke(
                                1.dp,
                                when {
                                    editingEntry?.id == ip.id -> cs.primary
                                    ip.invalid -> ext.offlineRed.copy(alpha = 0.5f)
                                    isActive -> ext.onlineGreen.copy(alpha = 0.35f)
                                    else -> cs.outlineVariant.copy(alpha = 0.45f)
                                },
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ip_card_${ip.id}"),
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
                                                text = ip.address,
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontFamily = JetBrainsMono,
                                                    fontWeight = FontWeight.Bold,
                                                ),
                                                color = if (ip.disabled) cs.onSurfaceVariant else cs.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = when {
                                                    ip.invalid -> ext.offlineContainer
                                                    ip.disabled -> ext.offlineContainer
                                                    else -> ext.onlineContainer
                                                },
                                            ) {
                                                Text(
                                                    text = when {
                                                        ip.invalid -> "Invalid"
                                                        ip.disabled -> "Disabled"
                                                        else -> "Active"
                                                    },
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 9.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                    ),
                                                    color = if (isActive) ext.onlineGreen else ext.offlineRed,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                )
                                            }
                                            if (ip.dynamic) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = cs.secondaryContainer,
                                                ) {
                                                    Text(
                                                        text = "Dynamic",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontSize = 9.5.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                        ),
                                                        color = cs.onSecondaryContainer,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Interface: ${ip.interfaceName}  •  Net: ${ip.network}",
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                                            color = cs.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (ip.comment.isNotBlank()) {
                                            Text(
                                                text = ip.comment,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = cs.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }

                                    if (!ip.dynamic) {
                                        Switch(
                                            checked = !ip.disabled,
                                            onCheckedChange = { onToggleDisabled(ip) },
                                            enabled = !isMutating,
                                            modifier = Modifier.testTag("ip_switch_${ip.id}"),
                                        )
                                    }
                                }

                                if (!ip.dynamic) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 6.dp),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        TextButton(
                                            onClick = {
                                                onFeatureStateChange {
                                                    it.copy(
                                                        editingEntryId = ip.id,
                                                        ipDraft = ip.address,
                                                        interfaceDraft = ip.interfaceName,
                                                        commentDraft = ip.comment,
                                                        showForm = true,
                                                    )
                                                }
                                            },
                                            modifier = Modifier.testTag("ip_edit_${ip.id}"),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Edit,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Edit")
                                        }
                                        TextButton(
                                            onClick = { pendingDelete = ip },
                                            colors = ButtonDefaults.textButtonColors(contentColor = ext.offlineRed),
                                            modifier = Modifier.testTag("ip_delete_${ip.id}"),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DeleteOutline,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Hapus")
                                        }
                                    }
                                }
                            }
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
            title = { Text("Hapus IP Address?") },
            text = {
                Text(
                    text = "IP ${entry.address} pada interface ${entry.interfaceName} akan dihapus dari router. Pastikan ini bukan gateway koneksi manajemen Anda saat ini.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete(entry)
                        if (editingEntry?.id == entry.id) resetForm()
                        pendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ext.offlineRed),
                ) {
                    Text("Hapus")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Batal") }
            },
        )
    }
}

@Composable
private fun IpStatPill(
    label: String,
    value: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = cs.surfaceContainerHigh,
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Bold,
                ),
                color = accent,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
            )
        }
    }
}
