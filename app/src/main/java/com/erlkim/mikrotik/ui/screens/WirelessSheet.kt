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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.erlkim.mikrotik.data.model.WirelessInterface
import com.erlkim.mikrotik.ui.theme.JetBrainsMono
import com.erlkim.mikrotik.ui.theme.LocalErlkimColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WirelessSheet(
    wirelessList: List<WirelessInterface>,
    onToggle: (WirelessInterface) -> Unit,
    onUpdate: (WirelessInterface, ssid: String, frequency: String, comment: String) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ext = LocalErlkimColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var editingWlan by remember { mutableStateOf<WirelessInterface?>(null) }
    var ssidInput by remember { mutableStateOf("") }
    var frequencyInput by remember { mutableStateOf("") }
    var commentInput by remember { mutableStateOf("") }

    val activeCount = remember(wirelessList) { wirelessList.count { !it.disabled } }
    val totalClients = remember(wirelessList) { wirelessList.sumOf { if (!it.disabled) it.connectedClients else 0 } }

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
                            .background(cs.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Wifi,
                            contentDescription = null,
                            tint = cs.onSecondaryContainer,
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Wireless & Wi-Fi",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = cs.onSurface,
                        )
                        Text(
                            text = "$activeCount SSID aktif • $totalClients klien terhubung",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                        )
                    }
                }
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.testTag("wireless_refresh_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Segarkan daftar wireless",
                        tint = cs.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // KPI Summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                WirelessKpiCard(
                    label = "SSID Aktif",
                    value = "$activeCount/${wirelessList.size}",
                    accent = ext.onlineGreen,
                    modifier = Modifier.weight(1f),
                )
                WirelessKpiCard(
                    label = "Total Klien",
                    value = totalClients.toString(),
                    accent = cs.primary,
                    modifier = Modifier.weight(1f),
                )
                WirelessKpiCard(
                    label = "Radio",
                    value = wirelessList.size.toString(),
                    accent = cs.tertiary,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Inline Edit Wireless Card
            AnimatedVisibility(visible = editingWlan != null) {
                val target = editingWlan
                if (target != null) {
                    val is5Ghz = target.band.contains("5", ignoreCase = true) ||
                        (target.frequency.filter { it.isDigit() }.toIntOrNull() ?: 0) >= 5000
                    val channelPresets = if (is5Ghz) {
                        listOf("auto", "5180", "5220", "5745", "5785")
                    } else {
                        listOf("auto", "2412", "2437", "2462")
                    }

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = cs.surfaceContainerHigh,
                        border = BorderStroke(1.dp, cs.primary.copy(alpha = 0.45f)),
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
                                    text = "Edit Radio ${target.name}",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = cs.primary,
                                )
                                IconButton(
                                    onClick = { editingWlan = null },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Tutup form edit wireless",
                                        tint = cs.onSurfaceVariant,
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = ssidInput,
                                onValueChange = { ssidInput = it },
                                label = { Text("Nama Wi-Fi (SSID)") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("wireless_edit_ssid_input"),
                                shape = RoundedCornerShape(14.dp),
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = if (is5Ghz) "Channel Cepat 5 GHz (MHz)" else "Channel Cepat 2.4 GHz (MHz)",
                                style = MaterialTheme.typography.labelMedium,
                                color = cs.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(channelPresets) { preset ->
                                    val selected = frequencyInput.equals(preset, ignoreCase = true) ||
                                        frequencyInput.startsWith(preset, ignoreCase = true)
                                    FilterChip(
                                        selected = selected,
                                        onClick = { frequencyInput = preset },
                                        label = {
                                            Text(
                                                text = if (preset == "auto") "Auto" else "$preset MHz",
                                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                                            )
                                        },
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = frequencyInput,
                                onValueChange = { frequencyInput = it },
                                label = { Text("Frekuensi / Channel") },
                                placeholder = { Text(if (is5Ghz) "auto atau 5180" else "auto atau 2437") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("wireless_edit_frequency_input"),
                                shape = RoundedCornerShape(14.dp),
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = commentInput,
                                onValueChange = { commentInput = it },
                                label = { Text("Komentar / Lokasi AP") },
                                placeholder = { Text("mis. Lobby Utama / Lantai 2") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("wireless_edit_comment_input"),
                                shape = RoundedCornerShape(14.dp),
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = { editingWlan = null },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Text("Batal")
                                }
                                Button(
                                    onClick = {
                                        val normalizedFreq = frequencyInput
                                            .replace("MHz", "", ignoreCase = true)
                                            .trim()
                                        onUpdate(target, ssidInput, normalizedFreq, commentInput)
                                        editingWlan = null
                                    },
                                    enabled = ssidInput.isNotBlank(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("wireless_save_edit_button"),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Text("Simpan Wi-Fi")
                                }
                            }
                        }
                    }
                }
            }

            if (wirelessList.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = cs.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WifiOff,
                            contentDescription = null,
                            tint = cs.onSurfaceVariant,
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tidak ada interface Wireless/Wi-Fi pada router ini.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    wirelessList.forEach { wlan ->
                        WirelessItemCard(
                            wlan = wlan,
                            onToggle = { onToggle(wlan) },
                            onEdit = {
                                editingWlan = wlan
                                ssidInput = wlan.ssid
                                frequencyInput = wlan.frequency.replace("MHz", "", ignoreCase = true).trim()
                                commentInput = wlan.comment
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WirelessKpiCard(
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
private fun WirelessItemCard(
    wlan: WirelessInterface,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ext = LocalErlkimColors.current
    val is5Ghz = wlan.band.contains("5", ignoreCase = true) ||
        (wlan.frequency.filter { it.isDigit() }.toIntOrNull() ?: 0) >= 5000
    val bandLabel = if (is5Ghz) "5 GHz" else "2.4 GHz"
    val bandColor = if (is5Ghz) cs.tertiary else cs.primary
    val statusColor = if (!wlan.disabled) ext.onlineGreen else ext.offlineRed

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = cs.surfaceContainerHigh,
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.35f)),
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
                            text = wlan.ssid,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = cs.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = bandColor.copy(alpha = 0.18f),
                        ) {
                            Text(
                                text = bandLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMono,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = bandColor,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = statusColor.copy(alpha = 0.16f),
                        ) {
                            Text(
                                text = if (!wlan.disabled) "Aktif" else "Nonaktif",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = statusColor,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                        }
                        Text(
                            text = "${wlan.name} • ${wlan.frequency} • ${wlan.channelWidth}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = JetBrainsMono,
                                fontSize = 11.sp,
                            ),
                            color = cs.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Switch(
                    checked = !wlan.disabled,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.testTag("switch_wireless_${wlan.name}"),
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Radio Technical Details Strip
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Router,
                            contentDescription = null,
                            tint = cs.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = wlan.mode,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                            color = cs.onSurfaceVariant,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = null,
                            tint = cs.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = wlan.securityProfile.ifBlank { "default" },
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                            color = cs.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Devices,
                            contentDescription = null,
                            tint = if (!wlan.disabled) ext.onlineGreen else cs.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${wlan.connectedClients} klien",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = if (!wlan.disabled) ext.onlineGreen else cs.onSurfaceVariant,
                        )
                    }
                }
            }

            if (wlan.comment.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = wlan.comment,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.secondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onEdit,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_wireless_${wlan.name}"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Ubah SSID, Channel & Lokasi")
            }
        }
    }
}
