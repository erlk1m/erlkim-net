package com.erlkim.mikrotik.ui.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.erlkim.mikrotik.R
import com.erlkim.mikrotik.data.model.BandwidthQuotaAlert
import com.erlkim.mikrotik.data.model.QuotaAlertConfig
import com.erlkim.mikrotik.data.model.QuotaAlertSeverity
import com.erlkim.mikrotik.data.model.formatBytes
import com.erlkim.mikrotik.notifications.QuotaNotificationHelper
import com.erlkim.mikrotik.ui.theme.JetBrainsMono
import com.erlkim.mikrotik.ui.theme.LocalErlkimColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuotaAlertsSheet(
    alerts: List<BandwidthQuotaAlert>,
    config: QuotaAlertConfig,
    onSaveConfig: (QuotaAlertConfig) -> Unit,
    onAcknowledgeAlert: (String) -> Unit,
    onAcknowledgeAll: () -> Unit,
    onDisconnectUser: (String) -> Unit,
    onOpenSystemLogs: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val cs = MaterialTheme.colorScheme
    val ext = LocalErlkimColors.current
    val context = LocalContext.current

    var enabled by remember(config) { mutableStateOf(config.enabled) }
    var globalLimitText by remember(config) { mutableStateOf(config.globalQuotaLimitMb.toString()) }
    var warningPercent by remember(config) { mutableIntStateOf(config.warningThresholdPercent) }
    var systemNotifEnabled by remember(config) { mutableStateOf(config.systemNotificationsEnabled) }
    var hasNotifPermission by remember {
        mutableStateOf(QuotaNotificationHelper.hasNotificationPermission(context))
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasNotifPermission = granted
        if (!granted) {
            systemNotifEnabled = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = cs.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.testTag("quota_alerts_sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
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
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(cs.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsActive,
                            contentDescription = null,
                            tint = cs.onPrimaryContainer,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.quota_alerts_title),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = cs.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.quota_alerts_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Action Bar (System Logs & Mark All Read)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onOpenSystemLogs()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quota_open_logs_button"),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Icon(Icons.Rounded.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.quota_open_system_logs),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (alerts.any { !it.isAcknowledged }) {
                    FilledTonalButton(
                        onClick = onAcknowledgeAll,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quota_mark_all_read_button"),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Icon(Icons.Rounded.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.quota_mark_all_read),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Active Quota Alerts Section
            Text(
                text = "PERINGATAN AKTIF (${alerts.size})",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = cs.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (alerts.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = cs.surfaceContainerHigh,
                    border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircleOutline,
                            contentDescription = null,
                            tint = ext.onlineGreen,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.quota_empty_title),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = cs.onSurface,
                            )
                            Text(
                                text = stringResource(R.string.quota_empty_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    alerts.forEach { alert ->
                        QuotaAlertItemCard(
                            alert = alert,
                            onAcknowledge = { onAcknowledgeAlert(alert.id) },
                            onDisconnect = { onDisconnectUser(alert.username) },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(16.dp))

            // Settings Section
            Text(
                text = "ATURAN BATAS KUOTA",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = cs.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = cs.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Aktifkan Peringatan Kuota",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = cs.onSurface,
                            )
                            Text(
                                text = "Pantau pemakaian bandwidth setiap user hotspot",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = { enabled = it },
                            modifier = Modifier.testTag("switch_quota_alerts_enabled"),
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = cs.outlineVariant.copy(alpha = 0.45f),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Notifikasi Sistem Android",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = cs.onSurface,
                            )
                            Text(
                                text = if (hasNotifPermission) {
                                    "Kirim notifikasi bar status saat kuota terlampaui"
                                } else {
                                    "Perlu izin notifikasi Android untuk peringatan bar status"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = systemNotifEnabled && enabled,
                            enabled = enabled,
                            onCheckedChange = { checked ->
                                systemNotifEnabled = checked
                                if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPermission) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            modifier = Modifier.testTag("switch_quota_system_notif"),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = globalLimitText,
                onValueChange = { globalLimitText = it.filter(Char::isDigit).take(7) },
                label = { Text("Batas Kuota Default (MB)") },
                supportingText = {
                    Text("Dipakai untuk user tanpa limit-bytes-total khusus di RouterOS (0 = hanya cek kuota per-user)")
                },
                leadingIcon = { Icon(Icons.Rounded.DataUsage, contentDescription = null) },
                singleLine = true,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_global_quota_mb"),
                shape = RoundedCornerShape(14.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Quick Quota Presets
            val presets = listOf(100L to "100 MB", 250L to "250 MB", 500L to "500 MB", 1024L to "1 GB", 2048L to "2 GB")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(presets) { (mb, label) ->
                    val selected = globalLimitText.toLongOrNull() == mb
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) cs.primaryContainer else cs.surfaceContainerHigh,
                        border = BorderStroke(1.dp, if (selected) cs.primary.copy(alpha = 0.6f) else cs.outlineVariant),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = enabled) { globalLimitText = mb.toString() },
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                            color = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Ambang Peringatan Awal ($warningPercent%)",
                style = MaterialTheme.typography.labelMedium,
                color = cs.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(70, 80, 90).forEach { pct ->
                    val selected = warningPercent == pct
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) ext.demoContainer else cs.surfaceContainerHigh,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (selected) ext.demoAmber.copy(alpha = 0.65f) else cs.outlineVariant,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = enabled) { warningPercent = pct },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "$pct%",
                                style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono),
                                color = if (selected) ext.demoAmber else cs.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = {
                    onSaveConfig(
                        QuotaAlertConfig(
                            enabled = enabled,
                            globalQuotaLimitMb = globalLimitText.toLongOrNull() ?: 500L,
                            warningThresholdPercent = warningPercent,
                            systemNotificationsEnabled = systemNotifEnabled,
                        ),
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_quota_settings_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = cs.primary,
                    contentColor = cs.onPrimary,
                ),
            ) {
                Text(
                    text = stringResource(R.string.quota_save_settings),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun QuotaAlertItemCard(
    alert: BandwidthQuotaAlert,
    onAcknowledge: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ext = LocalErlkimColors.current
    val isExceeded = alert.severity == QuotaAlertSeverity.EXCEEDED
    val accent = if (isExceeded) ext.offlineRed else ext.demoAmber
    val badgeBg = if (isExceeded) ext.offlineContainer else ext.demoContainer

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = cs.surfaceContainerHigh,
        border = BorderStroke(
            width = 1.dp,
            color = if (!alert.isAcknowledged) accent.copy(alpha = 0.55f) else cs.outlineVariant.copy(alpha = 0.5f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("quota_alert_card_${alert.username}"),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = badgeBg,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = if (isExceeded) Icons.Rounded.ErrorOutline else Icons.Rounded.WarningAmber,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isExceeded) {
                                    stringResource(R.string.quota_badge_exceeded)
                                } else {
                                    stringResource(R.string.quota_badge_warning)
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                ),
                                color = accent,
                            )
                        }
                    }

                    Text(
                        text = "${alert.usagePercent}%",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = accent,
                    )
                }

                Text(
                    text = alert.timestamp,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                    color = cs.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = alert.username,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = cs.onSurface,
            )
            Text(
                text = buildString {
                    append("${formatBytes(alert.usedBytes)} / ${formatBytes(alert.limitBytes)}")
                    append("  •  Profil: ${alert.profile}")
                    if (alert.ipAddress.isNotBlank()) append("  •  ${alert.ipAddress}")
                },
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                color = cs.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Quota usage bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(cs.surfaceContainerHighest),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((alert.usagePercent / 100f).coerceIn(0.05f, 1f))
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!alert.isAcknowledged) {
                    TextButton(
                        onClick = onAcknowledge,
                        modifier = Modifier.testTag("ack_quota_alert_${alert.username}"),
                    ) {
                        Text("Tandai Dibaca", style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }
                FilledTonalButton(
                    onClick = onDisconnect,
                    modifier = Modifier.testTag("disconnect_quota_alert_${alert.username}"),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = ext.offlineContainer,
                        contentColor = ext.offlineRed,
                    ),
                ) {
                    Icon(Icons.Rounded.LinkOff, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = stringResource(R.string.action_disconnect_user),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            }
        }
    }
}
