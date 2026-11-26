package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ConnectionStatus
import com.example.data.model.DhcpLeaseEntry
import com.example.data.model.HotspotActiveSession
import com.example.data.model.HotspotServer
import com.example.data.model.IpBindingEntry
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalErlkimColors
import com.example.ui.viewmodel.IpBindingFilter
import com.example.ui.viewmodel.IpBindingsFeatureUiState
import com.example.ui.viewmodel.formatFeatureSyncBadge

private val MAC_REGEX = Regex("^([0-9A-Fa-f]{2}[:\\-]){5}[0-9A-Fa-f]{2}$")
private val IPV4_REGEX = Regex("^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(/([0-9]|[12]\\d|3[0-2]))?$")

private data class QuickDeviceCandidate(
    val label: String,
    val mac: String,
    val ip: String,
    val source: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IpBindingsSheet(
    entries: List<IpBindingEntry>,
    hotspotServers: List<HotspotServer>,
    activeSessions: List<HotspotActiveSession>,
    dhcpLeases: List<DhcpLeaseEntry>,
    featureState: IpBindingsFeatureUiState,
    onFeatureStateChange: ((IpBindingsFeatureUiState) -> IpBindingsFeatureUiState) -> Unit,
    scrollState: ScrollState = rememberScrollState(),
    connectionStatus: ConnectionStatus = ConnectionStatus.ConnectedDemo,
    isRefreshing: Boolean = false,
    isMutating: Boolean = false,
    onAdd: (mac: String, address: String, toAddress: String, server: String, type: String, comment: String) -> Unit,
    onUpdate: (entry: IpBindingEntry, mac: String, address: String, toAddress: String, server: String, type: String, comment: String) -> Unit,
    onToggleDisabled: (IpBindingEntry) -> Unit,
    onDelete: (IpBindingEntry) -> Unit,
    onRefresh: () -> Unit,
    onReconnect: () -> Unit = onRefresh,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ext = LocalErlkimColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var deletingEntry by remember { mutableStateOf<IpBindingEntry?>(null) }

    val editingEntry = remember(entries, featureState.editingEntryId) {
        featureState.editingEntryId?.let { id -> entries.firstOrNull { it.id == id } }
    }

    val bypassedCount = remember(entries) { entries.count { !it.disabled && it.type.equals("bypassed", true) } }
    val blockedCount = remember(entries) { entries.count { !it.disabled && it.type.equals("blocked", true) } }
    val regularCount = remember(entries) { entries.count { !it.disabled && it.type.equals("regular", true) } }

    val serverChoices = remember(hotspotServers) {
        (listOf("all") + hotspotServers.map { it.name }.filter { it.isNotBlank() }).distinct()
    }

    // Quick-pick candidates from active hotspot sessions and DHCP leases (excluding already-bound MACs)
    val quickCandidates = remember(activeSessions, dhcpLeases, entries) {
        val boundMacs = entries.map { it.macAddress.uppercase() }.filter { it.isNotBlank() }.toSet()
        buildList {
            activeSessions.forEach { s ->
                if (s.macAddress.isNotBlank() && s.macAddress.uppercase() !in boundMacs) {
                    add(QuickDeviceCandidate(label = s.user, mac = s.macAddress.uppercase(), ip = s.address, source = "Hotspot"))
                }
            }
            dhcpLeases.forEach { l ->
                val macUp = l.macAddress.uppercase()
                if (macUp.isNotBlank() && macUp !in boundMacs && none { it.mac == macUp }) {
                    val name = l.hostName.ifBlank { l.comment }.ifBlank { l.address }
                    add(QuickDeviceCandidate(label = name, mac = macUp, ip = l.address, source = "DHCP"))
                }
            }
        }.take(8)
    }

    val filteredEntries = remember(entries, featureState.searchQuery, featureState.selectedFilter) {
        val q = featureState.searchQuery.trim()
        entries.filter { item ->
            val matchFilter = when (featureState.selectedFilter) {
                IpBindingFilter.ALL -> true
                IpBindingFilter.BYPASSED -> !item.disabled && item.type.equals("bypassed", true)
                IpBindingFilter.BLOCKED -> !item.disabled && item.type.equals("blocked", true)
                IpBindingFilter.REGULAR -> !item.disabled && item.type.equals("regular", true)
                IpBindingFilter.DISABLED -> item.disabled
            }
            val matchQuery = q.isBlank() ||
                item.macAddress.contains(q, true) ||
                item.address.contains(q, true) ||
                item.toAddress.contains(q, true) ||
                item.comment.contains(q, true) ||
                item.server.contains(q, true) ||
                item.type.contains(q, true)
            matchFilter && matchQuery
        }
    }

    val macText = featureState.macDraft.trim()
    val addrText = featureState.addressDraft.trim()
    val toAddrText = featureState.toAddressDraft.trim()
    val macInvalid = macText.isNotBlank() && !MAC_REGEX.matches(macText)
    val addrInvalid = addrText.isNotBlank() && !IPV4_REGEX.matches(addrText)
    val toAddrInvalid = toAddrText.isNotBlank() && !IPV4_REGEX.matches(toAddrText)
    val canSubmit = (macText.isNotBlank() || addrText.isNotBlank()) && !macInvalid && !addrInvalid && !toAddrInvalid && !isMutating

    val isSyncing = isRefreshing || featureState.isRefreshingSection
    val syncBadge = formatFeatureSyncBadge(featureState.lastSyncedMs, connectionStatus.isConnected)

    fun resetForm(closeAfter: Boolean = false) {
        onFeatureStateChange {
            it.copy(
                showForm = if (closeAfter) false else it.showForm,
                editingEntryId = null,
                macDraft = "",
                addressDraft = "",
                toAddressDraft = "",
                serverDraft = "all",
                typeDraft = "bypassed",
                commentDraft = "",
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
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(ext.badgeHotspot.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Link,
                        contentDescription = null,
                        tint = cs.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "IP Bindings (${entries.size})",
                        style = MaterialTheme.typography.titleLarge,
                        color = cs.onSurface,
                    )
                    Text(
                        text = "/ip/hotspot/ip-binding • $syncBadge",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = onRefresh,
                    enabled = !isSyncing,
                    modifier = Modifier.testTag("ip_bindings_refresh_button"),
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = cs.primary,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Segarkan IP Bindings",
                            tint = cs.onSurfaceVariant,
                        )
                    }
                }
                FilledTonalButton(
                    onClick = {
                        if (featureState.showForm) {
                            resetForm(closeAfter = true)
                        } else {
                            onFeatureStateChange {
                                it.copy(
                                    showForm = true,
                                    editingEntryId = null,
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("ip_bindings_toggle_form_button"),
                ) {
                    Icon(
                        imageVector = if (featureState.showForm) Icons.Rounded.Close else Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (featureState.showForm) "Tutup" else "Tambah")
                }
            }

            // Offline reconnect banner (preserves cached bindings)
            if (connectionStatus is ConnectionStatus.Disconnected) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = ext.offlineContainer,
                    border = BorderStroke(1.dp, ext.offlineRed.copy(alpha = 0.45f)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CloudOff,
                            contentDescription = null,
                            tint = ext.offlineRed,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Menampilkan data cache offline",
                                style = MaterialTheme.typography.labelLarge,
                                color = cs.onSurface,
                            )
                            Text(
                                text = connectionStatus.reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TextButton(
                            onClick = onReconnect,
                            modifier = Modifier.testTag("ip_bindings_reconnect_button"),
                        ) {
                            Text("Sambung", color = ext.offlineRed, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // KPI Summary Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BindingKpiCard(
                    label = "Bypassed",
                    value = bypassedCount.toString(),
                    accent = ext.onlineGreen,
                    modifier = Modifier.weight(1f),
                )
                BindingKpiCard(
                    label = "Blocked",
                    value = blockedCount.toString(),
                    accent = ext.offlineRed,
                    modifier = Modifier.weight(1f),
                )
                BindingKpiCard(
                    label = "Regular",
                    value = regularCount.toString(),
                    accent = cs.primary,
                    modifier = Modifier.weight(1f),
                )
            }

            // Collapsible Add / Edit Form
            AnimatedVisibility(visible = featureState.showForm) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = cs.surfaceContainerHigh,
                    border = BorderStroke(1.dp, cs.primary.copy(alpha = 0.35f)),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (editingEntry != null) "Edit Aturan IP Binding" else "Tambah IP Binding Baru",
                                style = MaterialTheme.typography.titleMedium,
                                color = cs.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            if (editingEntry != null) {
                                TextButton(onClick = { resetForm(closeAfter = true) }) {
                                    Text("Batal")
                                }
                            }
                        }

                        // Quick Pick from Active Sessions / DHCP Leases
                        if (editingEntry == null && quickCandidates.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Pilih cepat dari perangkat aktif:",
                                style = MaterialTheme.typography.labelMedium,
                                color = cs.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(quickCandidates, key = { "${it.source}_${it.mac}" }) { dev ->
                                    AssistChip(
                                        onClick = {
                                            onFeatureStateChange {
                                                it.copy(
                                                    macDraft = dev.mac,
                                                    addressDraft = dev.ip,
                                                    toAddressDraft = if (it.typeDraft == "bypassed") dev.ip else it.toAddressDraft,
                                                    commentDraft = it.commentDraft.ifBlank { dev.label },
                                                )
                                            }
                                        },
                                        label = {
                                            Text(
                                                text = "${dev.label} (${dev.ip})",
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Rounded.Devices,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        },
                                        modifier = Modifier.testTag("ip_binding_quick_pick_${dev.mac}"),
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = featureState.macDraft,
                            onValueChange = { value ->
                                val normalized = value.uppercase().replace('-', ':').take(17)
                                onFeatureStateChange { it.copy(macDraft = normalized) }
                            },
                            label = { Text("MAC Address") },
                            placeholder = { Text("AA:BB:CC:DD:EE:FF") },
                            singleLine = true,
                            isError = macInvalid,
                            supportingText = {
                                if (macInvalid) {
                                    Text("Format MAC tidak valid (contoh: AA:BB:CC:DD:EE:FF)")
                                } else {
                                    Text("Isi MAC Address, IP Address, atau keduanya")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ip_binding_mac_input"),
                            shape = RoundedCornerShape(14.dp),
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = featureState.addressDraft,
                                onValueChange = { v -> onFeatureStateChange { it.copy(addressDraft = v) } },
                                label = { Text("IP Asal (Address)") },
                                placeholder = { Text("10.10.0.10") },
                                singleLine = true,
                                isError = addrInvalid,
                                supportingText = if (addrInvalid) {
                                    { Text("IPv4 tidak valid") }
                                } else {
                                    null
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("ip_binding_address_input"),
                                shape = RoundedCornerShape(14.dp),
                            )
                            OutlinedTextField(
                                value = featureState.toAddressDraft,
                                onValueChange = { v -> onFeatureStateChange { it.copy(toAddressDraft = v) } },
                                label = { Text("IP Tujuan (Opsional)") },
                                placeholder = { Text("10.10.0.10") },
                                singleLine = true,
                                isError = toAddrInvalid,
                                supportingText = if (toAddrInvalid) {
                                    { Text("IPv4 tidak valid") }
                                } else {
                                    null
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("ip_binding_to_address_input"),
                                shape = RoundedCornerShape(14.dp),
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Type selector with role explanation
                        Text(
                            text = "Tipe Akses Hotspot",
                            style = MaterialTheme.typography.labelMedium,
                            color = cs.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "bypassed" to "Bypassed",
                                "blocked" to "Blocked",
                                "regular" to "Regular",
                            ).forEach { (key, label) ->
                                FilterChip(
                                    selected = featureState.typeDraft.equals(key, true),
                                    onClick = { onFeatureStateChange { it.copy(typeDraft = key) } },
                                    label = { Text(label) },
                                    leadingIcon = if (featureState.typeDraft.equals(key, true)) {
                                        { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else {
                                        null
                                    },
                                    modifier = Modifier.testTag("ip_binding_type_$key"),
                                )
                            }
                        }
                        Text(
                            text = when (featureState.typeDraft.lowercase()) {
                                "bypassed" -> "Perangkat langsung terhubung ke internet tanpa halaman login Hotspot."
                                "blocked" -> "Perangkat diblokir penuh dan tidak dapat mengakses jaringan Hotspot."
                                else -> "Perangkat tetap melewati autentikasi login Hotspot normal dengan translasi IP."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Hotspot Server Selector
                        Text(
                            text = "Server Hotspot",
                            style = MaterialTheme.typography.labelMedium,
                            color = cs.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(serverChoices) { srv ->
                                val selected = featureState.serverDraft.equals(srv, true)
                                FilterChip(
                                    selected = selected,
                                    onClick = { onFeatureStateChange { it.copy(serverDraft = srv) } },
                                    label = { Text(if (srv == "all") "Semua Server (all)" else srv) },
                                    leadingIcon = if (selected) {
                                        { Icon(Icons.Rounded.Router, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else {
                                        null
                                    },
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = featureState.commentDraft,
                            onValueChange = { v -> onFeatureStateChange { it.copy(commentDraft = v) } },
                            label = { Text("Catatan / Nama Perangkat") },
                            placeholder = { Text("Contoh: Printer Kasir / CCTV Lobby") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ip_binding_comment_input"),
                            shape = RoundedCornerShape(14.dp),
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlinedButton(
                                onClick = { resetForm(closeAfter = true) },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Batal")
                            }
                            Button(
                                onClick = {
                                    if (!canSubmit) return@Button
                                    val target = editingEntry
                                    if (target != null) {
                                        onUpdate(
                                            target,
                                            macText,
                                            addrText,
                                            toAddrText,
                                            featureState.serverDraft,
                                            featureState.typeDraft,
                                            featureState.commentDraft.trim(),
                                        )
                                    } else {
                                        onAdd(
                                            macText,
                                            addrText,
                                            toAddrText,
                                            featureState.serverDraft,
                                            featureState.typeDraft,
                                            featureState.commentDraft.trim(),
                                        )
                                    }
                                    resetForm(closeAfter = true)
                                },
                                enabled = canSubmit,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1.3f)
                                    .testTag("ip_binding_submit_button"),
                            ) {
                                Text(if (editingEntry != null) "Simpan Perubahan" else "Simpan Binding")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Search Bar
            OutlinedTextField(
                value = featureState.searchQuery,
                onValueChange = { q -> onFeatureStateChange { it.copy(searchQuery = q) } },
                placeholder = { Text("Cari MAC, IP, server, atau catatan…") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (featureState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onFeatureStateChange { it.copy(searchQuery = "") } }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Hapus pencarian")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ip_bindings_search_input"),
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(IpBindingFilter.entries) { filter ->
                    val count = when (filter) {
                        IpBindingFilter.ALL -> entries.size
                        IpBindingFilter.BYPASSED -> bypassedCount
                        IpBindingFilter.BLOCKED -> blockedCount
                        IpBindingFilter.REGULAR -> regularCount
                        IpBindingFilter.DISABLED -> entries.count { it.disabled }
                    }
                    FilterChip(
                        selected = featureState.selectedFilter == filter,
                        onClick = { onFeatureStateChange { it.copy(selectedFilter = filter) } },
                        label = { Text("${filter.label} ($count)") },
                        modifier = Modifier.testTag("ip_bindings_filter_${filter.name.lowercase()}"),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Entries list or Empty State
            if (filteredEntries.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = cs.surfaceContainerHigh,
                    border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.5f)),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Link,
                            contentDescription = null,
                            tint = cs.onSurfaceVariant,
                            modifier = Modifier.size(36.dp),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (entries.isEmpty()) {
                                "Belum ada aturan IP Binding"
                            } else {
                                "Tidak ada IP Binding yang cocok"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = cs.onSurface,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (entries.isEmpty()) {
                                "Tambahkan perangkat seperti printer kasir, CCTV, atau Access Point agar bypass login Hotspot atau diblokir."
                            } else {
                                "Coba ubah kata kunci pencarian atau pilih filter status lainnya."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                        )
                        if (featureState.searchQuery.isNotBlank() || featureState.selectedFilter != IpBindingFilter.ALL) {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    onFeatureStateChange {
                                        it.copy(searchQuery = "", selectedFilter = IpBindingFilter.ALL)
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text("Reset Filter")
                            }
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    filteredEntries.forEach { item ->
                        IpBindingItemCard(
                            entry = item,
                            enabled = !isMutating,
                            onToggleDisabled = { onToggleDisabled(item) },
                            onEdit = {
                                onFeatureStateChange {
                                    it.copy(
                                        showForm = true,
                                        editingEntryId = item.id,
                                        macDraft = item.macAddress,
                                        addressDraft = item.address,
                                        toAddressDraft = item.toAddress,
                                        serverDraft = item.server.ifBlank { "all" },
                                        typeDraft = item.type.ifBlank { "bypassed" },
                                        commentDraft = item.comment,
                                    )
                                }
                            },
                            onDeleteClick = { deletingEntry = item },
                        )
                    }
                }
            }
        }
    }

    deletingEntry?.let { target ->
        val label = target.comment.ifBlank { target.macAddress.ifBlank { target.address } }
        AlertDialog(
            onDismissRequest = { deletingEntry = null },
            containerColor = cs.surfaceContainerHigh,
            title = { Text("Hapus IP Binding?") },
            text = {
                Text(
                    "Aturan IP Binding untuk '$label' (${target.macAddress.ifBlank { target.address }}) akan dihapus dari router MikroTik.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete(target)
                        if (featureState.editingEntryId == target.id) {
                            resetForm(closeAfter = true)
                        }
                        deletingEntry = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = cs.error,
                        contentColor = cs.onError,
                    ),
                    modifier = Modifier.testTag("ip_binding_confirm_delete_button"),
                ) {
                    Text("Hapus")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingEntry = null }) {
                    Text("Batal")
                }
            },
        )
    }
}

@Composable
private fun BindingKpiCard(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = cs.surfaceContainerHigh,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
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
private fun IpBindingItemCard(
    entry: IpBindingEntry,
    enabled: Boolean,
    onToggleDisabled: () -> Unit,
    onEdit: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ext = LocalErlkimColors.current

    val (typeColor, typeContainer, typeIcon) = when (entry.type.lowercase()) {
        "bypassed" -> Triple(ext.onlineGreen, ext.onlineContainer, Icons.Rounded.VerifiedUser)
        "blocked" -> Triple(ext.offlineRed, ext.offlineContainer, Icons.Rounded.Block)
        else -> Triple(cs.primary, cs.primaryContainer, Icons.Rounded.Link)
    }
    val effectiveAccent = if (entry.disabled) cs.onSurfaceVariant else typeColor

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ip_binding_card_${entry.id}"),
        shape = RoundedCornerShape(18.dp),
        color = if (entry.disabled) cs.surfaceContainerLow else cs.surfaceContainerHigh,
        border = BorderStroke(
            width = 1.dp,
            color = if (entry.disabled) cs.outlineVariant.copy(alpha = 0.45f) else effectiveAccent.copy(alpha = 0.35f),
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (entry.disabled) cs.surfaceContainerHighest else typeContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = typeIcon,
                        contentDescription = null,
                        tint = effectiveAccent,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = entry.comment.ifBlank { entry.macAddress.ifBlank { entry.address } },
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (entry.disabled) cs.onSurfaceVariant else cs.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (entry.disabled) cs.surfaceContainerHighest else typeContainer,
                        ) {
                            Text(
                                text = entry.type.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = effectiveAccent,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                        if (entry.disabled) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = cs.surfaceContainerHighest,
                            ) {
                                Text(
                                    text = "Nonaktif",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = cs.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = entry.macAddress.ifBlank { "Tanpa MAC (berdasarkan IP)" },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = JetBrainsMono,
                            fontSize = 12.sp,
                        ),
                        color = cs.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = !entry.disabled,
                    onCheckedChange = { onToggleDisabled() },
                    enabled = enabled,
                    modifier = Modifier.testTag("ip_binding_switch_${entry.id}"),
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = entry.address.ifBlank { "Any IP" },
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                            color = cs.onSurface,
                        )
                        if (entry.toAddress.isNotBlank() && entry.toAddress != entry.address) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                tint = cs.primary,
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .size(14.dp),
                            )
                            Text(
                                text = entry.toAddress,
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                                color = cs.primary,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Server: ${entry.server.ifBlank { "all" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(
                        onClick = onEdit,
                        enabled = enabled,
                        modifier = Modifier.testTag("ip_binding_edit_${entry.id}"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Edit IP Binding",
                            tint = cs.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(
                        onClick = onDeleteClick,
                        enabled = enabled,
                        modifier = Modifier.testTag("ip_binding_delete_${entry.id}"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Hapus IP Binding",
                            tint = cs.error,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}
