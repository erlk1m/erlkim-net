package com.erlkim.mikrotik.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.erlkim.mikrotik.data.model.ConnectionStatus
import com.erlkim.mikrotik.data.model.RouterResource
import com.erlkim.mikrotik.ui.theme.JetBrainsMono
import com.erlkim.mikrotik.ui.theme.LocalErlkimColors
import com.erlkim.mikrotik.ui.viewmodel.SystemControlFeatureUiState
import com.erlkim.mikrotik.ui.viewmodel.formatFeatureSyncBadge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class PowerAction {
    REBOOT,
    SHUTDOWN,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemControlSheet(
    resource: RouterResource,
    routerAddress: String,
    connectionStatus: ConnectionStatus,
    featureState: SystemControlFeatureUiState = SystemControlFeatureUiState(),
    scrollState: ScrollState = rememberScrollState(),
    isRefreshing: Boolean = false,
    isMutating: Boolean = false,
    onOpenIdentityTime: () -> Unit,
    onOpenBackupRestore: () -> Unit,
    onOpenSystemLogs: () -> Unit,
    onOpenConnection: () -> Unit,
    onQuickRouterBackup: (String) -> Unit,
    onReboot: () -> Unit,
    onShutdown: () -> Unit,
    onRefresh: () -> Unit,
    onReconnect: () -> Unit = onRefresh,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ext = LocalErlkimColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pendingPowerAction by remember { mutableStateOf<PowerAction?>(null) }

    val isConnected = connectionStatus.isConnected
    val refreshingNow = isRefreshing || featureState.isRefreshingSection
    val syncLabel = formatFeatureSyncBadge(featureState.lastSyncedMs, isConnected)
    val usedRamMb = (resource.totalMemoryMb - resource.freeMemoryMb).coerceAtLeast(0L)
    val usedHddMb = (resource.totalHddMb - resource.freeHddMb).coerceAtLeast(0L)

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
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = null,
                            tint = cs.onPrimaryContainer,
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "System & Power Control",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = cs.onSurface,
                        )
                        Text(
                            text = "${resource.identity} • $syncLabel",
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
                    modifier = Modifier.testTag("system_control_refresh_button"),
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
                            contentDescription = "Segarkan status sistem",
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
                            modifier = Modifier.testTag("system_control_reconnect_button"),
                        ) {
                            Text("Hubungkan Ulang", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Hardware Health Summary Card
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = cs.surfaceContainerHigh,
                border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    val (badgeText, badgeColor) = when (connectionStatus) {
                        ConnectionStatus.ConnectedLive -> "Live • $routerAddress" to ext.onlineGreen
                        ConnectionStatus.ConnectedDemo -> "Demo • $routerAddress" to ext.demoAmber
                        ConnectionStatus.Connecting -> "Menghubungkan…" to cs.tertiary
                        is ConnectionStatus.Disconnected -> "Offline" to ext.offlineRed
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "KESEHATAN PERANGKAT",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = cs.primary,
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = badgeColor.copy(alpha = 0.16f),
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMono,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = badgeColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    ResourceProgressBar(
                        label = "CPU Load",
                        percent = resource.cpuLoadPercent,
                        detail = "${resource.cpuLoadPercent}%",
                        barColor = metricColor(resource.cpuLoadPercent, ext.onlineGreen, ext.demoAmber, ext.offlineRed),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    ResourceProgressBar(
                        label = "Memori (RAM)",
                        percent = resource.ramUsagePercent,
                        detail = if (resource.totalMemoryMb > 0) {
                            "${usedRamMb}/${resource.totalMemoryMb} MB (${resource.ramUsagePercent}%)"
                        } else {
                            "${resource.ramUsagePercent}%"
                        },
                        barColor = metricColor(resource.ramUsagePercent, cs.secondary, ext.demoAmber, ext.offlineRed),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    ResourceProgressBar(
                        label = "Penyimpanan (Flash)",
                        percent = resource.storageUsagePercent,
                        detail = if (resource.totalHddMb > 0) {
                            "${usedHddMb}/${resource.totalHddMb} MB (${resource.storageUsagePercent}%)"
                        } else {
                            "${resource.storageUsagePercent}%"
                        },
                        barColor = metricColor(resource.storageUsagePercent, cs.tertiary, ext.demoAmber, ext.offlineRed),
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = cs.outlineVariant.copy(alpha = 0.5f),
                    )

                    DetailRow("Board", resource.boardName)
                    DetailRow("Uptime", resource.uptime)
                    DetailRow("Waktu Router", "${resource.clockDate} ${resource.clockTime}")
                    DetailRow("Zona Waktu", resource.timeZoneName)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Quick System Shortcuts (2x2 Grid)
            Text(
                text = "PINTASAN SISTEM",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = cs.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SystemShortcutTile(
                    title = "Identitas & Waktu",
                    subtitle = resource.identity,
                    icon = Icons.Rounded.AccessTime,
                    onClick = onOpenIdentityTime,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("system_shortcut_identity"),
                )
                SystemShortcutTile(
                    title = "Backup & Restore",
                    subtitle = "Ekspor & impor",
                    icon = Icons.Rounded.CloudUpload,
                    onClick = onOpenBackupRestore,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("system_shortcut_backup"),
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SystemShortcutTile(
                    title = "System Logs",
                    subtitle = "Log RouterOS",
                    icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                    onClick = onOpenSystemLogs,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("system_shortcut_logs"),
                )
                SystemShortcutTile(
                    title = "Koneksi API",
                    subtitle = routerAddress,
                    icon = Icons.Rounded.Router,
                    onClick = onOpenConnection,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("system_shortcut_connection"),
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // One-tap Quick Flash Backup before reboot
            FilledTonalButton(
                onClick = {
                    val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                    onQuickRouterBackup("erlkim-quick-$stamp")
                },
                enabled = isConnected && !isMutating,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("system_quick_backup_button"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Save,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Simpan Backup ke Router Sebelum Reboot")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Power Control Danger Zone
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = ext.offlineContainer.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, ext.offlineRed.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = ext.offlineRed,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "KONTROL DAYA ROUTER",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = ext.offlineRed,
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tindakan di bawah akan memutus seluruh sesi Hotspot dan koneksi jaringan sementara.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = { pendingPowerAction = PowerAction.REBOOT },
                            enabled = isConnected && !isMutating,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = cs.primary,
                                contentColor = cs.onPrimary,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("system_reboot_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.RestartAlt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reboot")
                        }
                        OutlinedButton(
                            onClick = { pendingPowerAction = PowerAction.SHUTDOWN },
                            enabled = isConnected && !isMutating,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, ext.offlineRed.copy(alpha = 0.7f)),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("system_shutdown_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PowerSettingsNew,
                                contentDescription = null,
                                tint = ext.offlineRed,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Shutdown", color = ext.offlineRed)
                        }
                    }
                }
            }
        }
    }

    // Explicit Confirmation Dialog for Reboot / Shutdown
    pendingPowerAction?.let { action ->
        val isShutdown = action == PowerAction.SHUTDOWN
        AlertDialog(
            onDismissRequest = { pendingPowerAction = null },
            containerColor = cs.surfaceContainer,
            icon = {
                Icon(
                    imageVector = if (isShutdown) Icons.Rounded.PowerSettingsNew else Icons.Rounded.RestartAlt,
                    contentDescription = null,
                    tint = if (isShutdown) ext.offlineRed else cs.primary,
                )
            },
            title = {
                Text(if (isShutdown) "Konfirmasi Shutdown Router?" else "Konfirmasi Reboot Router?")
            },
            text = {
                Text(
                    text = if (isShutdown) {
                        "Perangkat ${resource.identity} akan dimatikan sepenuhnya. Anda harus menyalakan daya fisik router kembali agar bisa diakses."
                    } else {
                        "Perangkat ${resource.identity} akan dimulai ulang. Semua klien jaringan dan koneksi API akan terputus sekitar 30–60 detik."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingPowerAction = null
                        if (isShutdown) onShutdown() else onReboot()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isShutdown) ext.offlineRed else cs.primary,
                        contentColor = if (isShutdown) cs.onError else cs.onPrimary,
                    ),
                    modifier = Modifier.testTag(
                        if (isShutdown) "confirm_shutdown_router_button" else "confirm_reboot_router_button",
                    ),
                ) {
                    Text(if (isShutdown) "Ya, Shutdown" else "Ya, Reboot")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPowerAction = null }) {
                    Text("Batal")
                }
            },
        )
    }
}

@Composable
private fun ResourceProgressBar(
    label: String,
    percent: Int,
    detail: String,
    barColor: Color,
) {
    val cs = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = cs.onSurface,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = barColor,
            )
        }
        Spacer(modifier = Modifier.height(5.dp))
        LinearProgressIndicator(
            progress = { (percent.coerceIn(0, 100)) / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = barColor,
            trackColor = cs.surfaceContainerHighest,
            strokeCap = StrokeCap.Round,
        )
    }
}

@Composable
private fun SystemShortcutTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cs.surfaceContainerHigh,
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(cs.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun metricColor(percent: Int, normal: Color, warning: Color, danger: Color): Color = when {
    percent >= 85 -> danger
    percent >= 65 -> warning
    else -> normal
}
