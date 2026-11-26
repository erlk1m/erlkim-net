package com.erlkim.mikrotik.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PushPin
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
import com.erlkim.mikrotik.data.model.ConnectionStatus
import com.erlkim.mikrotik.data.model.DhcpLeaseEntry
import com.erlkim.mikrotik.data.model.DhcpServerEntry
import com.erlkim.mikrotik.data.model.RouterInterface
import com.erlkim.mikrotik.ui.theme.JetBrainsMono
import com.erlkim.mikrotik.ui.theme.LocalErlkimColors
import com.erlkim.mikrotik.ui.viewmodel.DhcpFeatureTab
import com.erlkim.mikrotik.ui.viewmodel.DhcpLeaseFilter
import com.erlkim.mikrotik.ui.viewmodel.DhcpServerFeatureUiState
import com.erlkim.mikrotik.ui.viewmodel.formatFeatureSyncBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DhcpServerSheet(
    servers: List<DhcpServerEntry>,
    leases: List<DhcpLeaseEntry>,
    interfaces: List<RouterInterface>,
    featureState: DhcpServerFeatureUiState = DhcpServerFeatureUiState(),
    onFeatureStateChange: ((DhcpServerFeatureUiState) -> DhcpServerFeatureUiState) -> Unit = {},
    scrollState: ScrollState = rememberScrollState(),
    connectionStatus: ConnectionStatus = ConnectionStatus.ConnectedDemo,
    isRefreshing: Boolean = false,
    isMutating: Boolean = false,
    onAddServer: (name: String, iface: String, addressPool: String, leaseTime: String) -> Unit,
    onUpdateServer: (server: DhcpServerEntry, name: String, iface: String, addressPool: String, leaseTime: String) -> Unit,
    onToggleServer: (DhcpServerEntry) -> Unit,
    onDeleteServer: (DhcpServerEntry) -> Unit,
    onAddStaticLease: (address: String, macAddress: String, server: String, comment: String) -> Unit,
    onMakeLeaseStatic: (DhcpLeaseEntry) -> Unit,
    onToggleLeaseDisabled: (DhcpLeaseEntry) -> Unit,
    onDeleteLease: (DhcpLeaseEntry) -> Unit,
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
    val defaultLeaseServerName = remember(servers) { servers.firstOrNull()?.name ?: "all" }

    val activeTab = featureState.activeTab

    // DHCP Server persisted form state
    val showServerForm = featureState.showServerForm
    val editingServer = remember(servers, featureState.editingServerId) {
        featureState.editingServerId?.let { id -> servers.firstOrNull { it.id == id } }
    }
    val srvName = featureState.serverNameDraft
    val srvInterface = featureState.serverInterfaceDraft.ifBlank { defaultIfaceName }
    val srvPool = featureState.serverPoolDraft
    val srvLeaseTime = featureState.serverLeaseTimeDraft
    var pendingDeleteServer by remember { mutableStateOf<DhcpServerEntry?>(null) }

    // DHCP Lease persisted form & filter state
    val showLeaseForm = featureState.showLeaseForm
    val leaseIp = featureState.leaseIpDraft
    val leaseMac = featureState.leaseMacDraft
    val leaseServer = featureState.leaseServerDraft.ifBlank { defaultLeaseServerName }
    val leaseComment = featureState.leaseCommentDraft
    val leaseSearch = featureState.leaseSearchQuery
    val leaseFilter = featureState.leaseFilter
    var pendingDeleteLease by remember { mutableStateOf<DhcpLeaseEntry?>(null) }

    val leaseTimePresets = listOf("10m", "30m", "1h", "12h", "1d")
    val poolSuggestions = remember(servers) {
        (servers.map { it.addressPool }.filter { it.isNotBlank() } + listOf("pool-lan", "hs-pool-1", "static-only")).distinct()
    }
    val refreshingNow = isRefreshing || featureState.isRefreshingSection
    val syncLabel = formatFeatureSyncBadge(featureState.lastSyncedMs, connectionStatus.isConnected)

    val activeServerCount = remember(servers) { servers.count { !it.disabled } }
    val boundLeaseCount = remember(leases) { leases.count { it.status.equals("bound", true) && !it.disabled } }
    val staticLeaseCount = remember(leases) { leases.count { !it.dynamic } }

    val filteredLeases = remember(leases, leaseSearch, leaseFilter) {
        val q = leaseSearch.trim()
        leases.filter { item ->
            val matchesQuery = q.isBlank() ||
                item.hostName.contains(q, ignoreCase = true) ||
                item.address.contains(q, ignoreCase = true) ||
                item.macAddress.contains(q, ignoreCase = true) ||
                item.server.contains(q, ignoreCase = true) ||
                item.comment.contains(q, ignoreCase = true)
            val matchesFilter = when (leaseFilter) {
                DhcpLeaseFilter.ALL -> true
                DhcpLeaseFilter.BOUND -> item.status.equals("bound", true)
                DhcpLeaseFilter.STATIC -> !item.dynamic
                DhcpLeaseFilter.DYNAMIC -> item.dynamic
            }
            matchesQuery && matchesFilter
        }
    }

    fun resetServerForm() {
        onFeatureStateChange {
            it.copy(
                editingServerId = null,
                serverNameDraft = "",
                serverPoolDraft = "pool-lan",
                serverLeaseTimeDraft = "1h",
                serverInterfaceDraft = defaultIfaceName,
                showServerForm = false,
            )
        }
    }

    fun resetLeaseForm() {
        onFeatureStateChange {
            it.copy(
                leaseIpDraft = "",
                leaseMacDraft = "",
                leaseCommentDraft = "",
                leaseServerDraft = defaultLeaseServerName,
                showLeaseForm = false,
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
                            imageVector = Icons.Rounded.Dns,
                            contentDescription = null,
                            tint = cs.onPrimaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "DHCP Server & Leases",
                            style = MaterialTheme.typography.titleLarge,
                            color = cs.onSurface,
                        )
                        Text(
                            text = "$activeServerCount server • $boundLeaseCount bound • $syncLabel",
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
                    modifier = Modifier.testTag("dhcp_refresh_button"),
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
                            contentDescription = "Segarkan data DHCP",
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
                            modifier = Modifier.testTag("dhcp_reconnect_button"),
                        ) {
                            Text("Hubungkan Ulang", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // KPI Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DhcpStatTile("Server Aktif", "$activeServerCount/${servers.size}", ext.onlineGreen, Modifier.weight(1f))
                DhcpStatTile("Total Lease", leases.size.toString(), cs.primary, Modifier.weight(1f))
                DhcpStatTile("Static Lease", staticLeaseCount.toString(), ext.demoAmber, Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Segmented Tab Bar
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = cs.surfaceContainerHigh,
                border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    DhcpSegmentButton(
                        label = "Servers (${servers.size})",
                        icon = Icons.Rounded.Dns,
                        selected = activeTab == DhcpFeatureTab.SERVERS,
                        onClick = { onFeatureStateChange { it.copy(activeTab = DhcpFeatureTab.SERVERS) } },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dhcp_tab_servers"),
                    )
                    DhcpSegmentButton(
                        label = "Leases (${leases.size})",
                        icon = Icons.Rounded.Devices,
                        selected = activeTab == DhcpFeatureTab.LEASES,
                        onClick = { onFeatureStateChange { it.copy(activeTab = DhcpFeatureTab.LEASES) } },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dhcp_tab_leases"),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (activeTab == DhcpFeatureTab.SERVERS) {
                // Servers Action Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "DAFTAR DHCP SERVER",
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = {
                            if (showServerForm && editingServer != null) {
                                resetServerForm()
                            } else {
                                onFeatureStateChange {
                                    it.copy(
                                        showServerForm = !it.showServerForm,
                                        serverInterfaceDraft = it.serverInterfaceDraft.ifBlank { defaultIfaceName },
                                    )
                                }
                            }
                        },
                        modifier = Modifier.testTag("dhcp_server_toggle_form"),
                    ) {
                        Icon(
                            imageVector = if (showServerForm) Icons.Rounded.Close else Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (showServerForm) "Tutup" else "Tambah Server")
                    }
                }

                // Collapsible Add / Edit DHCP Server Form
                AnimatedVisibility(visible = showServerForm) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = cs.surfaceContainerHigh,
                        border = BorderStroke(
                            1.dp,
                            if (editingServer != null) cs.primary else cs.outlineVariant.copy(alpha = 0.55f),
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
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
                                    text = if (editingServer != null) "Edit DHCP Server '${editingServer?.name}'" else "Tambah DHCP Server Baru",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = cs.onSurface,
                                )
                                if (editingServer != null) {
                                    TextButton(onClick = ::resetServerForm) { Text("Batal Edit") }
                                }
                            }

                            OutlinedTextField(
                                value = srvName,
                                onValueChange = { v -> onFeatureStateChange { it.copy(serverNameDraft = v) } },
                                label = { Text("Nama DHCP Server") },
                                placeholder = { Text("dhcp-kantor") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("dhcp_server_name_input"),
                            )

                            if (interfaces.isNotEmpty()) {
                                Text("Interface:", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(interfaces, key = { it.id }) { iface ->
                                        FilterChip(
                                            selected = srvInterface == iface.name,
                                            onClick = { onFeatureStateChange { it.copy(serverInterfaceDraft = iface.name) } },
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

                            OutlinedTextField(
                                value = srvInterface,
                                onValueChange = { v -> onFeatureStateChange { it.copy(serverInterfaceDraft = v) } },
                                label = { Text("Interface") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = srvPool,
                                    onValueChange = { v -> onFeatureStateChange { it.copy(serverPoolDraft = v) } },
                                    label = { Text("Address Pool") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1.2f),
                                )
                                OutlinedTextField(
                                    value = srvLeaseTime,
                                    onValueChange = { v -> onFeatureStateChange { it.copy(serverLeaseTimeDraft = v) } },
                                    label = { Text("Lease Time") },
                                    placeholder = { Text("1h") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(0.9f),
                                )
                            }

                            Text("Pool cepat:", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(poolSuggestions) { pool ->
                                    FilterChip(
                                        selected = srvPool == pool,
                                        onClick = { onFeatureStateChange { it.copy(serverPoolDraft = pool) } },
                                        label = {
                                            Text(
                                                text = pool,
                                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                                            )
                                        },
                                    )
                                }
                            }

                            Text("Durasi lease:", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(leaseTimePresets) { preset ->
                                    FilterChip(
                                        selected = srvLeaseTime == preset,
                                        onClick = { onFeatureStateChange { it.copy(serverLeaseTimeDraft = preset) } },
                                        label = {
                                            Text(
                                                text = preset,
                                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                                            )
                                        },
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    if (srvName.isNotBlank() && srvInterface.isNotBlank()) {
                                        val editing = editingServer
                                        if (editing != null) {
                                            onUpdateServer(editing, srvName, srvInterface, srvPool, srvLeaseTime)
                                        } else {
                                            onAddServer(srvName, srvInterface, srvPool, srvLeaseTime)
                                        }
                                        resetServerForm()
                                    }
                                },
                                enabled = srvName.isNotBlank() && srvInterface.isNotBlank() && !isMutating,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("dhcp_server_save_button"),
                            ) {
                                Text(if (editingServer != null) "Simpan Perubahan Server" else "Simpan DHCP Server")
                            }
                        }
                    }
                }

                if (servers.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = cs.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "Belum ada DHCP Server dikonfigurasi. Tekan 'Tambah Server' untuk membuat DHCP Server baru.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        servers.forEach { srv ->
                            val active = !srv.disabled
                            val serverLeaseCount = leases.count { it.server.equals(srv.name, ignoreCase = true) }
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = cs.surfaceContainerHigh,
                                border = BorderStroke(
                                    1.dp,
                                    when {
                                        editingServer?.id == srv.id -> cs.primary
                                        active -> ext.onlineGreen.copy(alpha = 0.4f)
                                        else -> cs.outlineVariant.copy(alpha = 0.45f)
                                    },
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("dhcp_server_card_${srv.name}"),
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
                                                    text = srv.name,
                                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = if (active) cs.onSurface else cs.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (active) ext.onlineContainer else ext.offlineContainer,
                                                ) {
                                                    Text(
                                                        text = if (active) "Active" else "Disabled",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontSize = 9.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                        ),
                                                        color = if (active) ext.onlineGreen else ext.offlineRed,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    )
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = cs.secondaryContainer,
                                                ) {
                                                    Text(
                                                        text = "$serverLeaseCount lease",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                                                        color = cs.onSecondaryContainer,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
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
                                                text = "Lease Time: ${srv.leaseTime}  •  Authoritative: ${srv.authoritative}",
                                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                                                color = cs.onSurfaceVariant,
                                            )
                                        }
                                        Switch(
                                            checked = active,
                                            onCheckedChange = { onToggleServer(srv) },
                                            enabled = !isMutating,
                                            modifier = Modifier.testTag("dhcp_server_switch_${srv.name}"),
                                        )
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 6.dp),
                                        horizontalArrangement = Arrangement.End,
                                    ) {
                                        TextButton(
                                            onClick = {
                                                onFeatureStateChange {
                                                    it.copy(
                                                        editingServerId = srv.id,
                                                        serverNameDraft = srv.name,
                                                        serverInterfaceDraft = srv.interfaceName,
                                                        serverPoolDraft = srv.addressPool,
                                                        serverLeaseTimeDraft = srv.leaseTime,
                                                        showServerForm = true,
                                                    )
                                                }
                                            },
                                            modifier = Modifier.testTag("dhcp_server_edit_${srv.name}"),
                                        ) {
                                            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Edit")
                                        }
                                        TextButton(
                                            onClick = { pendingDeleteServer = srv },
                                            colors = ButtonDefaults.textButtonColors(contentColor = ext.offlineRed),
                                            modifier = Modifier.testTag("dhcp_server_delete_${srv.name}"),
                                        ) {
                                            Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Hapus")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // LEASES TAB
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "DAFTAR DHCP LEASES (${filteredLeases.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = {
                            onFeatureStateChange {
                                it.copy(
                                    showLeaseForm = !it.showLeaseForm,
                                    leaseServerDraft = it.leaseServerDraft.ifBlank { defaultLeaseServerName },
                                )
                            }
                        },
                        modifier = Modifier.testTag("dhcp_lease_toggle_form"),
                    ) {
                        Icon(
                            imageVector = if (showLeaseForm) Icons.Rounded.Close else Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (showLeaseForm) "Tutup" else "Static Lease")
                    }
                }

                AnimatedVisibility(visible = showLeaseForm) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = cs.surfaceContainerHigh,
                        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "Tambah Static DHCP Lease",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = cs.onSurface,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = leaseIp,
                                    onValueChange = { v -> onFeatureStateChange { it.copy(leaseIpDraft = v) } },
                                    label = { Text("IP Address") },
                                    placeholder = { Text("192.168.88.50") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("dhcp_lease_ip_input"),
                                )
                                OutlinedTextField(
                                    value = leaseMac,
                                    onValueChange = { v -> onFeatureStateChange { it.copy(leaseMacDraft = v.uppercase()) } },
                                    label = { Text("MAC Address") },
                                    placeholder = { Text("AA:BB:CC:DD:EE:FF") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1.25f)
                                        .testTag("dhcp_lease_mac_input"),
                                )
                            }
                            if (servers.isNotEmpty()) {
                                Text("Server:", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(servers.map { it.name } + "all") { sName ->
                                        FilterChip(
                                            selected = leaseServer == sName,
                                            onClick = { onFeatureStateChange { it.copy(leaseServerDraft = sName) } },
                                            label = {
                                                Text(
                                                    text = sName,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                                                )
                                            },
                                        )
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = leaseComment,
                                onValueChange = { v -> onFeatureStateChange { it.copy(leaseCommentDraft = v) } },
                                label = { Text("Nama Perangkat / Keterangan") },
                                placeholder = { Text("Printer Kasir / CCTV") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("dhcp_lease_comment_input"),
                            )
                            Button(
                                onClick = {
                                    if (leaseIp.isNotBlank() && leaseMac.isNotBlank()) {
                                        onAddStaticLease(leaseIp, leaseMac, leaseServer, leaseComment)
                                        resetLeaseForm()
                                    }
                                },
                                enabled = leaseIp.isNotBlank() && leaseMac.isNotBlank() && !isMutating,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("dhcp_lease_save_button"),
                            ) {
                                Text("Simpan Static Lease")
                            }
                        }
                    }
                }

                // Search & Filter Leases
                OutlinedTextField(
                    value = leaseSearch,
                    onValueChange = { q -> onFeatureStateChange { it.copy(leaseSearchQuery = q) } },
                    placeholder = { Text("Cari hostname, IP, MAC, atau server...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (leaseSearch.isNotEmpty()) {
                            IconButton(onClick = { onFeatureStateChange { it.copy(leaseSearchQuery = "") } }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Hapus pencarian", modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dhcp_lease_search_input"),
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(DhcpLeaseFilter.entries) { filter ->
                        FilterChip(
                            selected = leaseFilter == filter,
                            onClick = { onFeatureStateChange { it.copy(leaseFilter = filter) } },
                            label = { Text(filter.label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("dhcp_lease_filter_${filter.name.lowercase()}"),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (filteredLeases.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = cs.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = if (leases.isEmpty()) {
                                "Belum ada perangkat DHCP lease terdeteksi."
                            } else {
                                "Tidak ada DHCP lease yang sesuai dengan filter saat ini."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        filteredLeases.forEach { lease ->
                            val isBound = lease.status.equals("bound", ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = cs.surfaceContainerHigh,
                                border = BorderStroke(
                                    1.dp,
                                    when {
                                        lease.disabled -> cs.outlineVariant.copy(alpha = 0.35f)
                                        !lease.dynamic -> cs.primary.copy(alpha = 0.45f)
                                        isBound -> ext.onlineGreen.copy(alpha = 0.3f)
                                        else -> cs.outlineVariant.copy(alpha = 0.5f)
                                    },
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("dhcp_lease_card_${lease.id}"),
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
                                                    text = lease.hostName,
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = if (lease.disabled) cs.onSurfaceVariant else cs.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (lease.dynamic) cs.secondaryContainer else cs.primaryContainer,
                                                ) {
                                                    Text(
                                                        text = if (lease.dynamic) "Dynamic" else "Static",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontSize = 9.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                        ),
                                                        color = if (lease.dynamic) cs.onSecondaryContainer else cs.onPrimaryContainer,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                                    )
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = when {
                                                        lease.disabled -> ext.offlineContainer
                                                        isBound -> ext.onlineContainer
                                                        else -> ext.demoContainer
                                                    },
                                                ) {
                                                    Text(
                                                        text = when {
                                                            lease.disabled -> "Disabled"
                                                            else -> lease.status.replaceFirstChar { it.uppercase() }
                                                        },
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontSize = 9.5.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                        ),
                                                        color = when {
                                                            lease.disabled -> ext.offlineRed
                                                            isBound -> ext.onlineGreen
                                                            else -> ext.demoAmber
                                                        },
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "${lease.address}  •  ${lease.macAddress}",
                                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                                                color = cs.onSurfaceVariant,
                                            )
                                            Text(
                                                text = buildString {
                                                    append("Server: ${lease.server}")
                                                    if (lease.expiresAfter.isNotBlank()) append("  •  Exp: ${lease.expiresAfter}")
                                                    if (lease.comment.isNotBlank() && !lease.comment.equals(lease.hostName, true)) {
                                                        append("  •  ${lease.comment}")
                                                    }
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = cs.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        if (!lease.dynamic) {
                                            Switch(
                                                checked = !lease.disabled,
                                                onCheckedChange = { onToggleLeaseDisabled(lease) },
                                                enabled = !isMutating,
                                                modifier = Modifier.testTag("dhcp_lease_switch_${lease.id}"),
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 6.dp),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        if (lease.dynamic) {
                                            TextButton(
                                                onClick = { onMakeLeaseStatic(lease) },
                                                enabled = !isMutating,
                                                modifier = Modifier.testTag("dhcp_lease_make_static_${lease.id}"),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.PushPin,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Make Static")
                                            }
                                        }
                                        TextButton(
                                            onClick = { pendingDeleteLease = lease },
                                            colors = ButtonDefaults.textButtonColors(contentColor = ext.offlineRed),
                                            modifier = Modifier.testTag("dhcp_lease_delete_${lease.id}"),
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

    pendingDeleteServer?.let { srv ->
        AlertDialog(
            onDismissRequest = { pendingDeleteServer = null },
            containerColor = cs.surfaceContainer,
            title = { Text("Hapus DHCP Server?") },
            text = {
                Text(
                    text = "DHCP Server '${srv.name}' pada interface ${srv.interfaceName} akan dihapus. Klien baru pada jaringan ini tidak akan mendapatkan IP otomatis.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteServer(srv)
                        if (editingServer?.id == srv.id) resetServerForm()
                        pendingDeleteServer = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ext.offlineRed),
                ) {
                    Text("Hapus")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteServer = null }) { Text("Batal") }
            },
        )
    }

    pendingDeleteLease?.let { lease ->
        AlertDialog(
            onDismissRequest = { pendingDeleteLease = null },
            containerColor = cs.surfaceContainer,
            title = { Text("Hapus DHCP Lease?") },
            text = {
                Text(
                    text = "Lease ${lease.hostName} (${lease.address} • ${lease.macAddress}) akan dihapus dari tabel DHCP server.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteLease(lease)
                        pendingDeleteLease = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ext.offlineRed),
                ) {
                    Text("Hapus")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteLease = null }) { Text("Batal") }
            },
        )
    }
}

@Composable
private fun DhcpStatTile(
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
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DhcpSegmentButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) cs.primaryContainer else cs.surfaceContainerHigh,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                ),
                color = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
