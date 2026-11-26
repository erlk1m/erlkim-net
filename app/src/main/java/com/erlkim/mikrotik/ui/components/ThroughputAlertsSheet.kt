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
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import com.erlkim.mikrotik.data.model.ThroughputAlertConfig
import com.erlkim.mikrotik.data.model.ThroughputAlertEvent
import com.erlkim.mikrotik.notifications.QuotaNotificationHelper
import com.erlkim.mikrotik.ui.theme.JetBrainsMono
import com.erlkim.mikrotik.ui.theme.LocalErlkimColors
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThroughputAlertsSheet(
    currentDownloadMbps: Float,
    currentUploadMbps: Float,
    config: ThroughputAlertConfig,
    alerts: List<ThroughputAlertEvent>,
    onSaveConfig: (ThroughputAlertConfig) -> Unit,
    onAcknowledgeAlert: (String) -> Unit,
    onAcknowledgeAll: () -> Unit,
    onClearAlerts: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val cs = MaterialTheme.colorScheme
    val ext = LocalErlkimColors.current
    val context = LocalContext.current

    var enabled by remember(config) { mutableStateOf(config.enabled) }
    var thresholdText by remember(config) {
        mutableStateOf(
            if (config.thresholdMbps % 1f == 0f) {
                config.thresholdMbps.toInt().toString()
            } else {
                String.format(Locale.US, "%.1f", config.thresholdMbps)
            }
        )
    }
    var cooldownSec by remember(config) { mutableIntStateOf(config.cooldownSeconds) }
    var systemNotifEnabled by remember(config) { mutableStateOf(config.systemNotificationsEnabled) }
    var hasNotifPermission by remember {
        mutableStateOf(QuotaNotificationHelper.hasNotificationPermission(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasNotifPermission = granted
    }

    val totalMbps = currentDownloadMbps + currentUploadMbps
    val parsedThreshold = thresholdText.trim().toFloatOrNull()
    val isThresholdValid = parsedThreshold != null && parsedThreshold in 1f..10_000f
    val activeLimit = (parsedThreshold ?: config.thresholdMbps).coerceAtLeast(1f)
    val utilization = (totalMbps / activeLimit).coerceIn(0f, 1f)
    val isExceededNow = enabled && totalMbps > activeLimit
    val unreadCount = alerts.count { !it.isAcknowledged }

    val thresholdPresets = listOf(25f, 40f, 50f, 75f, 100f, 250f, 500f)
    val cooldownPresets = listOf(10 to "10d", 30 to "30d", 60 to "1m", 300 to "5m")

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
                .padding(horizontal = 22.dp)
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
                        .clip(CircleShape)
                        .background(if (isExceededNow) ext.offlineContainer else cs.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Speed,
                        contentDescription = null,
                        tint = if (isExceededNow) ext.offlineRed else cs.onPrimaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.throughput_alerts_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = cs.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.throughput_alerts_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Live Throughput vs Threshold Meter Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("throughput_live_meter_card"),
                shape = RoundedCornerShape(18.dp),
                color = if (isExceededNow) ext.offlineContainer else cs.surfaceContainerHigh,
                border = BorderStroke(
                    1.dp,
                    if (isExceededNow) ext.offlineRed.copy(alpha = 0.55f) else cs.outlineVariant.copy(alpha = 0.5f),
                ),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "TOTAL THROUGHPUT LANGSUNG",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isExceededNow) ext.offlineRed else cs.primary,
                        )
                        Text(
                            text = String.format(Locale.US, "%.1f / %.0f Mbps", totalMbps, activeLimit),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = if (isExceededNow) ext.offlineRed else cs.onSurface,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { utilization },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = if (isExceededNow) ext.offlineRed else if (utilization >= 0.8f) ext.demoAmber else ext.onlineGreen,
                        trackColor = cs.surfaceContainerHighest,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = String.format(
                            Locale.US,
                            "Unduh: %.1f Mbps  •  Unggah: %.1f Mbps",
                            currentDownloadMbps,
                            currentUploadMbps,
                        ),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                        color = cs.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Threshold Settings Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = cs.surfaceContainerHigh,
                border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.5f)),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Aktifkan Monitoring Real-Time",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = cs.onSurface,
                            )
                            Text(
                                text = "Pantau kecepatan total jaringan pada setiap pembaruan telemetri",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = { enabled = it },
                            modifier = Modifier.testTag("throughput_enabled_switch"),
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = thresholdText,
                        onValueChange = { thresholdText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("Batas Throughput (Mbps)") },
                        suffix = { Text("Mbps", style = MaterialTheme.typography.labelMedium) },
                        isError = !isThresholdValid,
                        supportingText = if (!isThresholdValid) {
                            { Text("Masukkan nilai antara 1 hingga 10000 Mbps") }
                        } else {
                            null
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = JetBrainsMono),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("throughput_threshold_input"),
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(thresholdPresets) { preset ->
                            val selected = parsedThreshold != null && parsedThreshold == preset
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { thresholdText = preset.toInt().toString() }
                                    .testTag("throughput_preset_${preset.toInt()}"),
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) cs.primaryContainer else cs.surfaceContainer,
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) cs.primary else cs.outlineVariant.copy(alpha = 0.45f),
                                ),
                            ) {
                                Text(
                                    text = "${preset.toInt()} Mbps",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMono,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    ),
                                    color = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Jeda Peringatan (Cooldown)",
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        cooldownPresets.forEach { (seconds, label) ->
                            val selected = cooldownSec == seconds
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { cooldownSec = seconds }
                                    .testTag("throughput_cooldown_$seconds"),
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) cs.secondaryContainer else cs.surfaceContainer,
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) cs.secondary else cs.outlineVariant.copy(alpha = 0.45f),
                                ),
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                                        color = if (selected) cs.onSecondaryContainer else cs.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Notifikasi Push Android",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = cs.onSurface,
                            )
                            Text(
                                text = if (hasNotifPermission) {
                                    "Notifikasi sistem diizinkan"
                                } else {
                                    "Izin notifikasi diperlukan pada Android 13+"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = systemNotifEnabled,
                            onCheckedChange = { checked ->
                                systemNotifEnabled = checked
                                if (checked && !hasNotifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            modifier = Modifier.testTag("throughput_sys_notif_switch"),
                        )
                    }

                    if (systemNotifEnabled && !hasNotifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Rounded.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Izinkan Notifikasi Sistem")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (isThresholdValid && parsedThreshold != null) {
                                onSaveConfig(
                                    ThroughputAlertConfig(
                                        enabled = enabled,
                                        thresholdMbps = parsedThreshold,
                                        cooldownSeconds = cooldownSec,
                                        systemNotificationsEnabled = systemNotifEnabled,
                                    )
                                )
                            }
                        },
                        enabled = isThresholdValid,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("throughput_save_button"),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(stringResource(R.string.throughput_save_settings))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Alert History Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "RIWAYAT PERINGATAN (${alerts.size})",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = cs.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (unreadCount > 0) {
                        TextButton(
                            onClick = onAcknowledgeAll,
                            modifier = Modifier.testTag("throughput_ack_all_button"),
                        ) {
                            Icon(Icons.Rounded.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Tandai Dibaca")
                        }
                    }
                    if (alerts.isNotEmpty()) {
                        TextButton(
                            onClick = onClearAlerts,
                            modifier = Modifier.testTag("throughput_clear_all_button"),
                        ) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Hapus")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (alerts.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = cs.surfaceContainerHigh,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircleOutline,
                            contentDescription = null,
                            tint = ext.onlineGreen,
                            modifier = Modifier.size(34.dp),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.throughput_empty_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = cs.onSurface,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.throughput_empty_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    alerts.take(10).forEach { event ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("throughput_alert_item_${event.id}"),
                            shape = RoundedCornerShape(16.dp),
                            color = if (event.isAcknowledged) cs.surfaceContainerHigh else ext.offlineContainer,
                            border = BorderStroke(
                                1.dp,
                                if (event.isAcknowledged) cs.outlineVariant.copy(alpha = 0.45f) else ext.offlineRed.copy(alpha = 0.45f),
                            ),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.WarningAmber,
                                    contentDescription = null,
                                    tint = if (event.isAcknowledged) cs.onSurfaceVariant else ext.offlineRed,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = String.format(
                                            Locale.US,
                                            "%.1f Mbps > %.0f Mbps (%s)",
                                            event.totalMbps,
                                            event.thresholdMbps,
                                            event.interfaceName,
                                        ),
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontFamily = JetBrainsMono,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                        ),
                                        color = cs.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = String.format(
                                            Locale.US,
                                            "DL %.1f / UL %.1f Mbps  •  %s",
                                            event.downloadMbps,
                                            event.uploadMbps,
                                            event.timestamp,
                                        ),
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                                        color = cs.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (!event.isAcknowledged) {
                                    TextButton(onClick = { onAcknowledgeAlert(event.id) }) {
                                        Text("OK", fontWeight = FontWeight.Bold)
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
