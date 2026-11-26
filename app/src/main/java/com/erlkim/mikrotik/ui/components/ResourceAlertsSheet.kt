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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.erlkim.mikrotik.R
import com.erlkim.mikrotik.data.model.ResourceAlertConfig
import com.erlkim.mikrotik.data.model.ResourceAlertEvent
import com.erlkim.mikrotik.data.model.ResourceMetricType
import com.erlkim.mikrotik.data.model.RouterResource
import com.erlkim.mikrotik.notifications.QuotaNotificationHelper
import com.erlkim.mikrotik.ui.theme.JetBrainsMono
import com.erlkim.mikrotik.ui.theme.LocalErlkimColors
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResourceAlertsSheet(
    resource: RouterResource,
    config: ResourceAlertConfig,
    alerts: List<ResourceAlertEvent>,
    onSaveConfig: (ResourceAlertConfig) -> Unit,
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
    var cpuThreshold by remember(config) { mutableIntStateOf(config.cpuThresholdPercent.coerceIn(10, 99)) }
    var memThreshold by remember(config) { mutableIntStateOf(config.memoryThresholdPercent.coerceIn(10, 99)) }
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

    val isCpuExceededNow = enabled && resource.cpuLoadPercent > cpuThreshold
    val isMemExceededNow = enabled && resource.ramUsagePercent > memThreshold
    val isAnyExceededNow = isCpuExceededNow || isMemExceededNow
    val unreadCount = alerts.count { !it.isAcknowledged }

    val percentPresets = listOf(50, 70, 80, 90)
    val cooldownPresets = listOf(15 to "15d", 30 to "30d", 60 to "1m", 300 to "5m")
    val usedRamMb = (resource.totalMemoryMb - resource.freeMemoryMb).coerceAtLeast(0L)

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
                        .background(if (isAnyExceededNow) ext.offlineContainer else cs.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Memory,
                        contentDescription = null,
                        tint = if (isAnyExceededNow) ext.offlineRed else cs.onPrimaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.resource_alerts_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = cs.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.resource_alerts_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Live Router Resource Meter Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("resource_live_meter_card"),
                shape = RoundedCornerShape(18.dp),
                color = if (isAnyExceededNow) ext.offlineContainer else cs.surfaceContainerHigh,
                border = BorderStroke(
                    1.dp,
                    if (isAnyExceededNow) ext.offlineRed.copy(alpha = 0.55f) else cs.outlineVariant.copy(alpha = 0.5f),
                ),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "RESOURCE ROUTER LANGSUNG",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isAnyExceededNow) ext.offlineRed else cs.primary,
                        )
                        Text(
                            text = resource.identity,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                            color = cs.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // CPU Gauge
                    ResourceLiveGaugeRow(
                        label = "Beban CPU",
                        currentPercent = resource.cpuLoadPercent,
                        thresholdPercent = cpuThreshold,
                        isExceeded = isCpuExceededNow,
                        sublabel = "${resource.boardName} • ${resource.version}",
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Memory Gauge
                    ResourceLiveGaugeRow(
                        label = "Penggunaan Memori",
                        currentPercent = resource.ramUsagePercent,
                        thresholdPercent = memThreshold,
                        isExceeded = isMemExceededNow,
                        sublabel = if (resource.totalMemoryMb > 0L) {
                            "Terpakai $usedRamMb / ${resource.totalMemoryMb} MB"
                        } else {
                            "Telemetri RAM"
                        },
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
                                text = "Aktifkan Monitoring CPU & Memori",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = cs.onSurface,
                            )
                            Text(
                                text = "Pantau beban router pada setiap pembaruan telemetri",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = { enabled = it },
                            modifier = Modifier.testTag("resource_enabled_switch"),
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(10.dp))

                    // CPU Threshold Slider + Presets
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Batas CPU",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                            color = cs.onSurface,
                        )
                        Text(
                            text = "$cpuThreshold%",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = if (isCpuExceededNow) ext.offlineRed else cs.primary,
                        )
                    }
                    Slider(
                        value = cpuThreshold.toFloat(),
                        onValueChange = { cpuThreshold = it.roundToInt().coerceIn(10, 99) },
                        valueRange = 10f..99f,
                        enabled = enabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("resource_cpu_slider"),
                    )
                    PercentPresetRow(
                        presets = percentPresets,
                        selectedValue = cpuThreshold,
                        enabled = enabled,
                        tagPrefix = "resource_cpu_preset",
                        onSelect = { cpuThreshold = it },
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Memory Threshold Slider + Presets
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Batas Memori",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                            color = cs.onSurface,
                        )
                        Text(
                            text = "$memThreshold%",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = if (isMemExceededNow) ext.offlineRed else cs.secondary,
                        )
                    }
                    Slider(
                        value = memThreshold.toFloat(),
                        onValueChange = { memThreshold = it.roundToInt().coerceIn(10, 99) },
                        valueRange = 10f..99f,
                        enabled = enabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("resource_mem_slider"),
                    )
                    PercentPresetRow(
                        presets = percentPresets,
                        selectedValue = memThreshold,
                        enabled = enabled,
                        tagPrefix = "resource_mem_preset",
                        onSelect = { memThreshold = it },
                    )

                    Spacer(modifier = Modifier.height(14.dp))
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
                                    .clickable(enabled = enabled) { cooldownSec = seconds }
                                    .testTag("resource_cooldown_$seconds"),
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

                    Spacer(modifier = Modifier.height(12.dp))
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
                                    "Notifikasi push diizinkan"
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
                            modifier = Modifier.testTag("resource_sys_notif_switch"),
                        )
                    }

                    if (systemNotifEnabled && !hasNotifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("resource_allow_notif_button"),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Rounded.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Izinkan Notifikasi Push")
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            onSaveConfig(
                                ResourceAlertConfig(
                                    enabled = enabled,
                                    cpuThresholdPercent = cpuThreshold,
                                    memoryThresholdPercent = memThreshold,
                                    cooldownSeconds = cooldownSec,
                                    systemNotificationsEnabled = systemNotifEnabled,
                                ),
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("resource_save_button"),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(stringResource(R.string.resource_save_settings))
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
                            modifier = Modifier.testTag("resource_ack_all_button"),
                        ) {
                            Icon(Icons.Rounded.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Tandai Dibaca")
                        }
                    }
                    if (alerts.isNotEmpty()) {
                        TextButton(
                            onClick = onClearAlerts,
                            modifier = Modifier.testTag("resource_clear_all_button"),
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
                            text = stringResource(R.string.resource_empty_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = cs.onSurface,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.resource_empty_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    alerts.take(10).forEach { event ->
                        val metricIcon = when (event.metricType) {
                            ResourceMetricType.CPU -> Icons.Rounded.Memory
                            ResourceMetricType.MEMORY -> Icons.Rounded.Storage
                        }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("resource_alert_item_${event.id}"),
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
                                    imageVector = if (event.isAcknowledged) metricIcon else Icons.Rounded.WarningAmber,
                                    contentDescription = null,
                                    tint = if (event.isAcknowledged) cs.onSurfaceVariant else ext.offlineRed,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${event.metricType.label} ${event.currentPercent}% > ${event.thresholdPercent}%",
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
                                        text = "${event.detail}  •  ${event.timestamp}",
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

@Composable
private fun ResourceLiveGaugeRow(
    label: String,
    currentPercent: Int,
    thresholdPercent: Int,
    isExceeded: Boolean,
    sublabel: String,
) {
    val cs = MaterialTheme.colorScheme
    val ext = LocalErlkimColors.current
    val progress = (currentPercent / 100f).coerceIn(0f, 1f)
    val barColor = when {
        isExceeded -> ext.offlineRed
        currentPercent >= (thresholdPercent - 10).coerceAtLeast(0) -> ext.demoAmber
        else -> ext.onlineGreen
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = cs.onSurface,
            )
            Text(
                text = "$currentPercent% / Batas $thresholdPercent%",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Bold,
                ),
                color = if (isExceeded) ext.offlineRed else cs.onSurface,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = barColor,
            trackColor = cs.surfaceContainerHighest,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = sublabel,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
            color = cs.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PercentPresetRow(
    presets: List<Int>,
    selectedValue: Int,
    enabled: Boolean,
    tagPrefix: String,
    onSelect: (Int) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        presets.forEach { preset ->
            val selected = selectedValue == preset
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = enabled) { onSelect(preset) }
                    .testTag("${tagPrefix}_$preset"),
                shape = RoundedCornerShape(10.dp),
                color = if (selected) cs.primaryContainer else cs.surfaceContainer,
                border = BorderStroke(
                    1.dp,
                    if (selected) cs.primary else cs.outlineVariant.copy(alpha = 0.45f),
                ),
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "$preset%",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMono,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        ),
                        color = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
