package com.erlkim.mikrotik.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Fence
import androidx.compose.material3.Button
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
import com.erlkim.mikrotik.data.model.HotspotServer
import com.erlkim.mikrotik.data.model.HotspotUser
import com.erlkim.mikrotik.data.model.HotspotUserProfile
import com.erlkim.mikrotik.data.model.RouterInterface
import com.erlkim.mikrotik.data.model.WalledGardenEntry
import com.erlkim.mikrotik.ui.theme.JetBrainsMono
import com.erlkim.mikrotik.ui.theme.LocalErlkimColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HotspotServersSheet(
    servers: List<HotspotServer>,
    interfaces: List<RouterInterface>,
    onAddServer: (name: String, interfaceName: String, addressPool: String, profile: String) -> Unit,
    onToggleServer: (HotspotServer) -> Unit,
    onDeleteServer: (HotspotServer) -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ext = LocalErlkimColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showAddForm by remember { mutableStateOf(false) }
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = cs.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
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
                        Icon(Icons.Rounded.Dns, contentDescription = null, tint = cs.onPrimaryContainer, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Hotspot Servers (${servers.size})", style = MaterialTheme.typography.titleLarge, color = cs.onSurface)
                        Text(
                            text = "${servers.count { !it.disabled }} aktif • kelola server & interface",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                        )
                    }
                }
                TextButton(
                    onClick = { showAddForm = !showAddForm },
                    modifier = Modifier.testTag("hotspot_server_toggle_form"),
                ) {
                    Icon(
                        imageVector = if (showAddForm) Icons.Rounded.Close else Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (showAddForm) "Tutup" else "Tambah")
                }
            }

            if (showAddForm) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = cs.surfaceContainerHigh,
                    border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Tambah Hotspot Server Baru",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = cs.onSurface,
                        )
                        OutlinedTextField(
                            value = serverName,
                            onValueChange = { serverName = it },
                            label = { Text("Nama Server") },
                            placeholder = { Text("hotspot-tamu") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("hotspot_server_name_input"),
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
                                    showAddForm = false
                                }
                            },
                            enabled = serverName.isNotBlank() && ifaceName.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("hotspot_server_save_button"),
                        ) {
                            Text("Simpan Hotspot Server")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            if (servers.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = cs.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Belum ada server hotspot terdeteksi. Tekan 'Tambah' untuk membuat server baru.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    servers.forEach { srv ->
                        val active = !srv.disabled
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = cs.surfaceContainerHigh,
                            border = BorderStroke(
                                1.dp,
                                if (active) ext.onlineGreen.copy(alpha = 0.4f) else cs.outlineVariant.copy(alpha = 0.5f),
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("hotspot_server_card_${srv.name}"),
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
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
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Switch(
                                            checked = active,
                                            onCheckedChange = { onToggleServer(srv) },
                                            modifier = Modifier.testTag("hotspot_server_switch_${srv.name}"),
                                        )
                                        IconButton(
                                            onClick = { onDeleteServer(srv) },
                                            modifier = Modifier.testTag("hotspot_server_delete_${srv.name}"),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DeleteOutline,
                                                contentDescription = "Hapus server hotspot",
                                                tint = ext.offlineRed,
                                            )
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalledGardenSheet(
    entries: List<WalledGardenEntry>,
    onAdd: (host: String, port: String, action: String, comment: String) -> Unit,
    onUpdate: (entry: WalledGardenEntry, host: String, port: String, action: String, comment: String) -> Unit,
    onToggleDisabled: (WalledGardenEntry) -> Unit,
    onDelete: (WalledGardenEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ext = LocalErlkimColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var editingEntry by remember { mutableStateOf<WalledGardenEntry?>(null) }
    var dstHost by remember { mutableStateOf("") }
    var dstPort by remember { mutableStateOf("443") }
    var action by remember { mutableStateOf("allow") }
    var comment by remember { mutableStateOf("") }

    val quickPresets = listOf(
        "*.whatsapp.net" to "Chat CS",
        "qris.bankindonesia.id" to "Pembayaran QRIS",
        "*.google.com" to "Akses Google",
    )

    fun resetForm() {
        editingEntry = null
        dstHost = ""
        dstPort = "443"
        action = "allow"
        comment = ""
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(cs.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Fence, contentDescription = null, tint = cs.onPrimaryContainer, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Walled Garden (${entries.size})", style = MaterialTheme.typography.titleLarge, color = cs.onSurface)
                    Text("Bypass atau blokir domain sebelum login hotspot", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Add / Edit Form Card
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = cs.surfaceContainerHigh,
                border = BorderStroke(1.dp, if (editingEntry != null) cs.primary else cs.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (editingEntry != null) "Edit Aturan Walled Garden" else "Tambah Aturan Baru",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = cs.onSurface,
                        )
                        if (editingEntry != null) {
                            TextButton(onClick = ::resetForm) { Text("Batal Edit") }
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
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = dstHost,
                            onValueChange = { dstHost = it },
                            label = { Text("Domain / Host") },
                            placeholder = { Text("*.domain.com") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(2f)
                                .testTag("walled_garden_host_input"),
                            shape = RoundedCornerShape(12.dp),
                        )
                        OutlinedTextField(
                            value = dstPort,
                            onValueChange = { dstPort = it.filter { ch -> ch.isDigit() || ch == '-' || ch == ',' } },
                            label = { Text("Port") },
                            placeholder = { Text("443") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Action:", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                        listOf("allow", "deny").forEach { act ->
                            FilterChip(
                                selected = action == act,
                                onClick = { action = act },
                                label = { Text(act.uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)) },
                                modifier = Modifier.testTag("walled_garden_action_$act"),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = comment,
                            onValueChange = { comment = it },
                            label = { Text("Keterangan") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
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
                            modifier = Modifier.testTag("walled_garden_save_button"),
                        ) {
                            Text(if (editingEntry != null) "Update" else "Tambah")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            if (entries.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = cs.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Belum ada aturan Walled Garden. Tambahkan domain untuk akses tanpa login.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    entries.forEach { item ->
                        val isAllow = item.action.equals("allow", ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = cs.surfaceContainerHigh,
                            border = BorderStroke(
                                1.dp,
                                if (item.disabled) cs.outlineVariant.copy(alpha = 0.35f) else cs.outlineVariant.copy(alpha = 0.65f),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = item.dstHost,
                                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMono),
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
                                    },
                                ) {
                                    Icon(Icons.Rounded.Edit, contentDescription = "Edit aturan", tint = cs.primary, modifier = Modifier.size(19.dp))
                                }
                                IconButton(onClick = { onDelete(item) }) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Hapus aturan", tint = ext.offlineRed, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfilesSheet(
    profiles: List<HotspotUserProfile>,
    users: List<HotspotUser>,
    onAdd: (name: String, rateLimit: String, sharedUsers: String, timeout: String) -> Unit,
    onUpdate: (profile: HotspotUserProfile, name: String, rateLimit: String, sharedUsers: String, timeout: String) -> Unit,
    onDelete: (HotspotUserProfile) -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ext = LocalErlkimColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var editingProfile by remember { mutableStateOf<HotspotUserProfile?>(null) }
    var name by remember { mutableStateOf("") }
    var rateLimit by remember { mutableStateOf("5M/10M") }
    var shared by remember { mutableStateOf("1") }
    var timeout by remember { mutableStateOf("1d") }

    val ratePresets = listOf("2M/5M", "5M/10M", "10M/20M", "25M/50M", "Unlimited")

    fun resetForm() {
        editingProfile = null
        name = ""
        rateLimit = "5M/10M"
        shared = "1"
        timeout = "1d"
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(cs.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Badge, contentDescription = null, tint = cs.onPrimaryContainer, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("User Profiles (${profiles.size})", style = MaterialTheme.typography.titleLarge, color = cs.onSurface)
                    Text("Atur limit kecepatan, shared user, & durasi sesi", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = cs.surfaceContainerHigh,
                border = BorderStroke(1.dp, if (editingProfile != null) cs.primary else cs.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (editingProfile != null) "Edit Profil '${editingProfile?.name}'" else "Tambah Profil Baru",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = cs.onSurface,
                        )
                        if (editingProfile != null) {
                            TextButton(onClick = ::resetForm) { Text("Batal Edit") }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Nama Profil") },
                            placeholder = { Text("voucher_1hari") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1.2f)
                                .testTag("user_profile_name_input"),
                            shape = RoundedCornerShape(12.dp),
                        )
                        OutlinedTextField(
                            value = rateLimit,
                            onValueChange = { rateLimit = it },
                            label = { Text("Rate Limit") },
                            placeholder = { Text("5M/10M") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(ratePresets) { preset ->
                            FilterChip(
                                selected = rateLimit.equals(preset, ignoreCase = true),
                                onClick = { rateLimit = if (preset == "Unlimited") "" else preset },
                                label = { Text(preset, style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono)) },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = shared,
                            onValueChange = { shared = it.filter(Char::isDigit).take(3) },
                            label = { Text("Shared") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        )
                        OutlinedTextField(
                            value = timeout,
                            onValueChange = { timeout = it },
                            label = { Text("Timeout") },
                            placeholder = { Text("1d / 3h") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        )
                        Button(
                            onClick = {
                                if (name.isNotBlank()) {
                                    val editing = editingProfile
                                    if (editing != null) {
                                        onUpdate(editing, name, rateLimit, shared.ifBlank { "1" }, timeout)
                                    } else {
                                        onAdd(name, rateLimit, shared.ifBlank { "1" }, timeout)
                                    }
                                    resetForm()
                                }
                            },
                            enabled = name.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("user_profile_save_button"),
                        ) {
                            Text(if (editingProfile != null) "Update" else "Simpan")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            if (profiles.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = cs.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Belum ada user profile hotspot terdeteksi di router.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    profiles.forEach { p ->
                        val assignedUsersCount = users.count { it.profile.equals(p.name, ignoreCase = true) }
                        val isDefaultProfile = p.name.equals("default", ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = cs.surfaceContainerHigh,
                            border = BorderStroke(
                                1.dp,
                                if (editingProfile?.id == p.id) cs.primary else cs.outlineVariant.copy(alpha = 0.5f),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = p.name,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = cs.onSurface,
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = cs.secondaryContainer,
                                        ) {
                                            Text(
                                                text = "$assignedUsersCount user",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                color = cs.onSecondaryContainer,
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
                                    },
                                    modifier = Modifier.testTag("user_profile_edit_${p.name}"),
                                ) {
                                    Icon(Icons.Rounded.Edit, contentDescription = "Edit profil", tint = cs.primary, modifier = Modifier.size(19.dp))
                                }
                                if (!isDefaultProfile) {
                                    IconButton(
                                        onClick = { onDelete(p) },
                                        modifier = Modifier.testTag("user_profile_delete_${p.name}"),
                                    ) {
                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Hapus profil", tint = ext.offlineRed, modifier = Modifier.size(20.dp))
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
