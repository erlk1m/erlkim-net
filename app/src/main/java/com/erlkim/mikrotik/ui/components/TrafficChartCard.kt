package com.erlkim.mikrotik.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.erlkim.mikrotik.data.model.RouterInterface
import com.erlkim.mikrotik.data.model.TrafficSample
import com.erlkim.mikrotik.ui.theme.JetBrainsMono
import com.erlkim.mikrotik.ui.theme.LocalErlkimColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private enum class ChartTimeWindow(val label: String, val maxPoints: Int) {
    SEC_30("30s", 14),
    MIN_1("1m", 26),
    MIN_2("2m", 50),
}

@Composable
fun TrafficChartCard(
    samples: List<TrafficSample>,
    downloadMbps: Float,
    uploadMbps: Float,
    isLive: Boolean = false,
    interfaces: List<RouterInterface> = emptyList(),
    selectedInterface: String = "",
    onSelectInterface: (String) -> Unit = {},
    throughputThresholdMbps: Float? = null,
    onConfigureThreshold: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val ext = LocalErlkimColors.current
    val cs = MaterialTheme.colorScheme

    var showRx by remember { mutableStateOf(true) }
    var showTx by remember { mutableStateOf(true) }
    var showTotal by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var frozenSamples by remember { mutableStateOf<List<TrafficSample>>(emptyList()) }
    var selectedWindow by remember { mutableStateOf(ChartTimeWindow.SEC_30) }
    var inspectedIndex by remember { mutableStateOf<Int?>(null) }

    val sourceSamples = if (isPaused && frozenSamples.isNotEmpty()) frozenSamples else samples
    val visibleSamples = remember(sourceSamples, selectedWindow) {
        sourceSamples.takeLast(selectedWindow.maxPoints)
    }

    val activeInterfaces = remember(interfaces) { interfaces.filter { !it.disabled } }
    val selectedIfaceObj = remember(interfaces, selectedInterface) {
        interfaces.firstOrNull { it.name == selectedInterface }
            ?: activeInterfaces.firstOrNull()
    }

    val validInspectIdx = inspectedIndex?.takeIf { it in visibleSamples.indices }
    val inspectedSample = validInspectIdx?.let { visibleSamples[it] }

    val displayedRx = inspectedSample?.downloadMbps ?: downloadMbps
    val displayedTx = inspectedSample?.uploadMbps ?: uploadMbps
    val displayedTotal = displayedRx + displayedTx

    val peakRx = remember(visibleSamples, downloadMbps) {
        (visibleSamples.maxOfOrNull { it.downloadMbps } ?: downloadMbps).coerceAtLeast(downloadMbps)
    }
    val peakTx = remember(visibleSamples, uploadMbps) {
        (visibleSamples.maxOfOrNull { it.uploadMbps } ?: uploadMbps).coerceAtLeast(uploadMbps)
    }
    val avgRx = remember(visibleSamples, downloadMbps) {
        if (visibleSamples.isNotEmpty()) visibleSamples.map { it.downloadMbps }.average().toFloat() else downloadMbps
    }
    val avgTx = remember(visibleSamples, uploadMbps) {
        if (visibleSamples.isNotEmpty()) visibleSamples.map { it.uploadMbps }.average().toFloat() else uploadMbps
    }

    val maxScale = remember(visibleSamples, showRx, showTx, showTotal, throughputThresholdMbps) {
        val rawMax = visibleSamples.maxOfOrNull { sample ->
            val rx = if (showRx) sample.downloadMbps else 0f
            val tx = if (showTx) sample.uploadMbps else 0f
            val total = if (showTotal) sample.downloadMbps + sample.uploadMbps else 0f
            maxOf(rx, tx, total)
        } ?: 10f
        val thresholdHint = throughputThresholdMbps?.takeIf { it <= rawMax * 1.8f } ?: 0f
        (maxOf(rawMax, thresholdHint) * 1.18f).coerceAtLeast(10f)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("traffic_chart_card"),
        shape = RoundedCornerShape(24.dp),
        color = cs.surfaceContainer,
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.65f)),
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
            // Top Title + Limit Pill + LIVE / PAUSED Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ShowChart,
                        contentDescription = null,
                        tint = cs.tertiary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "RX / TX Throughput",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = cs.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (throughputThresholdMbps != null) {
                        val totalNow = downloadMbps + uploadMbps
                        val exceeded = isLive && totalNow > throughputThresholdMbps
                        Surface(
                            modifier = Modifier
                                .clip(CircleShape)
                                .then(
                                    if (onConfigureThreshold != null) {
                                        Modifier.clickable(onClick = onConfigureThreshold)
                                    } else {
                                        Modifier
                                    }
                                )
                                .testTag("traffic_threshold_pill"),
                            shape = CircleShape,
                            color = if (exceeded) ext.offlineContainer else cs.surfaceContainerHigh,
                            border = BorderStroke(
                                1.dp,
                                if (exceeded) ext.offlineRed.copy(alpha = 0.55f) else cs.outlineVariant.copy(alpha = 0.45f),
                            ),
                        ) {
                            Text(
                                text = if (exceeded) {
                                    String.format(Locale.US, "%.1f > %.0fM", totalNow, throughputThresholdMbps)
                                } else {
                                    String.format(Locale.US, "Max %.0fM", throughputThresholdMbps)
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMono,
                                    fontWeight = if (exceeded) FontWeight.Bold else FontWeight.Medium,
                                ),
                                color = if (exceeded) ext.offlineRed else cs.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }

                    // Live / Pause Toggle
                    Surface(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                if (!isPaused) {
                                    frozenSamples = samples
                                    isPaused = true
                                } else {
                                    isPaused = false
                                    inspectedIndex = null
                                }
                            }
                            .testTag("traffic_pause_toggle"),
                        shape = CircleShape,
                        color = when {
                            isPaused -> ext.demoContainer
                            isLive -> ext.onlineContainer
                            else -> cs.surfaceContainerHigh
                        },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = if (isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                contentDescription = if (isPaused) "Resume live chart" else "Pause live chart",
                                tint = when {
                                    isPaused -> ext.demoAmber
                                    isLive -> ext.onlineGreen
                                    else -> cs.onSurfaceVariant
                                },
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = when {
                                    isPaused -> "PAUSED"
                                    isLive -> "LIVE"
                                    else -> "IDLE"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMono,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                ),
                                color = when {
                                    isPaused -> ext.demoAmber
                                    isLive -> ext.onlineGreen
                                    else -> cs.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }

            // Selected MikroTik Interface Detail Strip
            if (selectedIfaceObj != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SettingsEthernet,
                            contentDescription = null,
                            tint = if (selectedIfaceObj.running) ext.onlineGreen else cs.onSurfaceVariant,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = buildString {
                                append(selectedIfaceObj.name)
                                append(" (${selectedIfaceObj.type})")
                                if (selectedIfaceObj.macAddress.isNotBlank()) {
                                    append(" • ${selectedIfaceObj.macAddress}")
                                }
                            },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = JetBrainsMono,
                                fontSize = 11.sp,
                            ),
                            color = cs.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // Time Window Pills
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ChartTimeWindow.entries.forEach { win ->
                            val active = selectedWindow == win
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        selectedWindow = win
                                        inspectedIndex = null
                                    }
                                    .testTag("traffic_window_${win.label}"),
                                shape = RoundedCornerShape(8.dp),
                                color = if (active) cs.primaryContainer else cs.surfaceContainerHigh,
                            ) {
                                Text(
                                    text = win.label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMono,
                                        fontSize = 10.sp,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                    ),
                                    color = if (active) cs.onPrimaryContainer else cs.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Interface Selector Chips
            if (activeInterfaces.size > 1) {
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(end = 4.dp),
                ) {
                    items(activeInterfaces, key = { it.id }) { iface ->
                        val isSelected = iface.name == selectedInterface
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    inspectedIndex = null
                                    isPaused = false
                                    onSelectInterface(iface.name)
                                }
                                .testTag("traffic_iface_${iface.name}"),
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) cs.secondaryContainer else cs.surfaceContainerHigh,
                            border = if (isSelected) BorderStroke(1.dp, cs.secondary.copy(alpha = 0.65f)) else null,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (iface.running) ext.onlineGreen else cs.onSurfaceVariant),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = iface.name,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMono,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    ),
                                    color = if (isSelected) cs.onSecondaryContainer else cs.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Interactive Series Legend Pills (RX / TX / Total)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BandwidthLegendChip(
                    label = "RX (Down)",
                    value = formatMbps(displayedRx),
                    accentColor = ext.downloadLine,
                    icon = Icons.Rounded.ArrowDownward,
                    selected = showRx,
                    onClick = { if (showTx || showTotal || !showRx) showRx = !showRx },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("legend_download_toggle"),
                )
                BandwidthLegendChip(
                    label = "TX (Up)",
                    value = formatMbps(displayedTx),
                    accentColor = ext.uploadLine,
                    icon = Icons.Rounded.ArrowUpward,
                    selected = showTx,
                    onClick = { if (showRx || showTotal || !showTx) showTx = !showTx },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("legend_upload_toggle"),
                )
                BandwidthLegendChip(
                    label = "Total",
                    value = formatMbps(displayedTotal),
                    accentColor = cs.primary,
                    icon = Icons.Rounded.SwapVert,
                    selected = showTotal,
                    onClick = { if (showRx || showTx || !showTotal) showTotal = !showTotal },
                    modifier = Modifier
                        .weight(0.95f)
                        .testTag("legend_total_toggle"),
                )
            }

            // Recharts/D3-style Floating Scrub Tooltip Banner when inspecting a point
            if (inspectedSample != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("traffic_scrub_tooltip"),
                    shape = RoundedCornerShape(12.dp),
                    color = cs.surfaceContainerHighest,
                    border = BorderStroke(1.dp, cs.primary.copy(alpha = 0.45f)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "@ ${formatSampleTime(inspectedSample.timestampMs)}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = cs.onSurface,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "RX ${formatMbpsShort(inspectedSample.downloadMbps)}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMono,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = ext.downloadLine,
                            )
                            Text(
                                text = "TX ${formatMbpsShort(inspectedSample.uploadMbps)}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMono,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = ext.uploadLine,
                            )
                            Text(
                                text = "Σ ${formatMbpsShort(inspectedSample.downloadMbps + inspectedSample.uploadMbps)}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMono,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = cs.primary,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main Detailed Line Chart Area (4-Step Y-Axis + Interactive Canvas)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(186.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (visibleSamples.size >= 2) {
                    Column(
                        modifier = Modifier
                            .width(42.dp)
                            .fillMaxHeight()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        listOf(1f, 0.66f, 0.33f, 0f).forEach { frac ->
                            val labelVal = (maxScale * frac).roundToInt()
                            Text(
                                text = "${labelVal}M",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMono,
                                    fontSize = 10.sp,
                                ),
                                color = cs.onSurfaceVariant,
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(14.dp))
                        .background(cs.surfaceContainerLowest.copy(alpha = 0.55f))
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (visibleSamples.size < 2) {
                        Text(
                            text = "Waiting for RX/TX stream on ${selectedInterface.ifBlank { "interface" }}…",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                        )
                    } else {
                        val downloadLineColor = ext.downloadLine
                        val downloadFillTop = ext.downloadFillTop
                        val uploadLineColor = ext.uploadLine
                        val uploadFillTop = ext.uploadLine.copy(alpha = 0.24f)
                        val totalLineColor = cs.primary
                        val thresholdColor = ext.offlineRed
                        val gridColor = cs.outlineVariant.copy(alpha = 0.42f)
                        val vGridColor = cs.outlineVariant.copy(alpha = 0.22f)
                        val crosshairColor = cs.onSurface.copy(alpha = 0.6f)
                        val surfaceBg = cs.surfaceContainer
                        val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f) }
                        val fineDash = remember { PathEffect.dashPathEffect(floatArrayOf(5f, 6f), 0f) }
                        val thresholdDash = remember { PathEffect.dashPathEffect(floatArrayOf(12f, 7f), 0f) }
                        val totalDash = remember { PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f) }

                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("bandwidth_chart_canvas")
                                .pointerInput(visibleSamples.size) {
                                    detectTapGestures(
                                        onTap = { offset ->
                                            val step = size.width.toFloat() / (visibleSamples.size - 1).coerceAtLeast(1)
                                            val idx = (offset.x / step).roundToInt().coerceIn(0, visibleSamples.lastIndex)
                                            inspectedIndex = if (inspectedIndex == idx) null else idx
                                        },
                                    )
                                }
                                .pointerInput(visibleSamples.size) {
                                    detectHorizontalDragGestures(
                                        onDragEnd = { if (!isPaused) inspectedIndex = null },
                                        onDragCancel = { if (!isPaused) inspectedIndex = null },
                                        onHorizontalDrag = { change, _ ->
                                            val step = size.width.toFloat() / (visibleSamples.size - 1).coerceAtLeast(1)
                                            val idx = (change.position.x / step).roundToInt().coerceIn(0, visibleSamples.lastIndex)
                                            inspectedIndex = idx
                                        },
                                    )
                                },
                        ) {
                            val width = size.width
                            val height = size.height
                            val topPad = 10.dp.toPx()
                            val bottomPad = 8.dp.toPx()
                            val usableH = (height - topPad - bottomPad).coerceAtLeast(1f)

                            // 4-step horizontal gridlines
                            for (i in 0..3) {
                                val y = topPad + usableH * (i / 3f)
                                drawLine(
                                    color = gridColor,
                                    start = Offset(0f, y),
                                    end = Offset(width, y),
                                    strokeWidth = 1.dp.toPx(),
                                    pathEffect = if (i < 3) dashEffect else null,
                                )
                            }

                            // Subtle vertical time gridlines
                            for (i in 1..3) {
                                val vx = width * (i / 4f)
                                drawLine(
                                    color = vGridColor,
                                    start = Offset(vx, topPad),
                                    end = Offset(vx, topPad + usableH),
                                    strokeWidth = 1.dp.toPx(),
                                    pathEffect = fineDash,
                                )
                            }

                            // Optional Throughput Threshold Reference Line
                            if (throughputThresholdMbps != null && throughputThresholdMbps <= maxScale) {
                                val thY = topPad + usableH * (1f - (throughputThresholdMbps / maxScale).coerceIn(0f, 1f))
                                drawLine(
                                    color = thresholdColor.copy(alpha = 0.72f),
                                    start = Offset(0f, thY),
                                    end = Offset(width, thY),
                                    strokeWidth = 1.4.dp.toPx(),
                                    pathEffect = thresholdDash,
                                )
                            }

                            val stepX = width / (visibleSamples.size - 1).coerceAtLeast(1)

                            val rxPoints = visibleSamples.mapIndexed { idx, s ->
                                val x = idx * stepX
                                val y = topPad + usableH * (1f - (s.downloadMbps / maxScale).coerceIn(0f, 1f))
                                Offset(x, y)
                            }
                            val txPoints = visibleSamples.mapIndexed { idx, s ->
                                val x = idx * stepX
                                val y = topPad + usableH * (1f - (s.uploadMbps / maxScale).coerceIn(0f, 1f))
                                Offset(x, y)
                            }
                            val totalPoints = visibleSamples.mapIndexed { idx, s ->
                                val x = idx * stepX
                                val y = topPad + usableH * (1f - ((s.downloadMbps + s.uploadMbps) / maxScale).coerceIn(0f, 1f))
                                Offset(x, y)
                            }

                            if (showTotal) {
                                val totalPath = buildSmoothPath(totalPoints)
                                drawPath(
                                    path = totalPath,
                                    color = totalLineColor.copy(alpha = 0.8f),
                                    style = Stroke(
                                        width = 1.8.dp.toPx(),
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round,
                                        pathEffect = totalDash,
                                    ),
                                )
                            }

                            if (showRx) {
                                val rxStrokePath = buildSmoothPath(rxPoints)
                                val rxFillPath = Path().apply {
                                    addPath(rxStrokePath)
                                    lineTo(width, topPad + usableH)
                                    lineTo(0f, topPad + usableH)
                                    close()
                                }
                                drawPath(
                                    path = rxFillPath,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(downloadFillTop, Color.Transparent),
                                        startY = topPad,
                                        endY = topPad + usableH,
                                    ),
                                )
                                drawPath(
                                    path = rxStrokePath,
                                    color = downloadLineColor,
                                    style = Stroke(
                                        width = 2.5.dp.toPx(),
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round,
                                    ),
                                )
                                if (visibleSamples.size <= 20) {
                                    rxPoints.forEach { pt ->
                                        drawCircle(color = surfaceBg, radius = 3.dp.toPx(), center = pt)
                                        drawCircle(color = downloadLineColor, radius = 2.dp.toPx(), center = pt)
                                    }
                                }
                                val lastRx = rxPoints.last()
                                drawCircle(color = downloadLineColor.copy(alpha = 0.25f), radius = 7.dp.toPx(), center = lastRx)
                                drawCircle(color = downloadLineColor, radius = 3.6.dp.toPx(), center = lastRx)
                            }

                            if (showTx) {
                                val txStrokePath = buildSmoothPath(txPoints)
                                val txFillPath = Path().apply {
                                    addPath(txStrokePath)
                                    lineTo(width, topPad + usableH)
                                    lineTo(0f, topPad + usableH)
                                    close()
                                }
                                drawPath(
                                    path = txFillPath,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(uploadFillTop, Color.Transparent),
                                        startY = topPad,
                                        endY = topPad + usableH,
                                    ),
                                )
                                drawPath(
                                    path = txStrokePath,
                                    color = uploadLineColor,
                                    style = Stroke(
                                        width = 2.3.dp.toPx(),
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round,
                                    ),
                                )
                                if (visibleSamples.size <= 20) {
                                    txPoints.forEach { pt ->
                                        drawCircle(color = surfaceBg, radius = 2.8.dp.toPx(), center = pt)
                                        drawCircle(color = uploadLineColor, radius = 1.9.dp.toPx(), center = pt)
                                    }
                                }
                                val lastTx = txPoints.last()
                                drawCircle(color = uploadLineColor.copy(alpha = 0.25f), radius = 6.dp.toPx(), center = lastTx)
                                drawCircle(color = uploadLineColor, radius = 3.2.dp.toPx(), center = lastTx)
                            }

                            // Interactive Crosshair & Highlighted Data Points
                            if (validInspectIdx != null) {
                                val cx = validInspectIdx * stepX
                                drawLine(
                                    color = crosshairColor,
                                    start = Offset(cx, topPad),
                                    end = Offset(cx, topPad + usableH),
                                    strokeWidth = 1.3.dp.toPx(),
                                    pathEffect = dashEffect,
                                )
                                if (showRx) {
                                    val pt = rxPoints[validInspectIdx]
                                    drawCircle(color = surfaceBg, radius = 6.dp.toPx(), center = pt)
                                    drawCircle(color = downloadLineColor, radius = 4.2.dp.toPx(), center = pt)
                                }
                                if (showTx) {
                                    val pt = txPoints[validInspectIdx]
                                    drawCircle(color = surfaceBg, radius = 6.dp.toPx(), center = pt)
                                    drawCircle(color = uploadLineColor, radius = 4.2.dp.toPx(), center = pt)
                                }
                                if (showTotal) {
                                    val pt = totalPoints[validInspectIdx]
                                    drawCircle(color = surfaceBg, radius = 5.5.dp.toPx(), center = pt)
                                    drawCircle(color = totalLineColor, radius = 3.8.dp.toPx(), center = pt)
                                }
                            }
                        }
                    }
                }
            }

            // X-Axis Real Clock Timestamps
            if (visibleSamples.size >= 2) {
                Spacer(modifier = Modifier.height(5.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 42.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val midIdx = visibleSamples.size / 2
                    Text(
                        text = formatSampleTime(visibleSamples.first().timestampMs),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono, fontSize = 10.sp),
                        color = cs.onSurfaceVariant,
                    )
                    Text(
                        text = formatSampleTime(visibleSamples[midIdx].timestampMs),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono, fontSize = 10.sp),
                        color = cs.onSurfaceVariant,
                    )
                    Text(
                        text = formatSampleTime(visibleSamples.last().timestampMs),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono, fontSize = 10.sp),
                        color = cs.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Avg & Peak RX / TX Telemetry Summary Strip
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = cs.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    MiniStatItem("Avg RX", formatMbpsShort(avgRx), ext.downloadLine)
                    MiniStatItem("Peak RX", formatMbpsShort(peakRx), ext.downloadLine)
                    MiniStatItem("Avg TX", formatMbpsShort(avgTx), ext.uploadLine)
                    MiniStatItem("Peak TX", formatMbpsShort(peakTx), ext.uploadLine)
                }
            }
        }
    }
}

@Composable
private fun BandwidthLegendChip(
    label: String,
    value: String,
    accentColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) cs.surfaceContainerHigh else cs.surfaceContainerLow,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) accentColor.copy(alpha = 0.55f) else cs.outlineVariant.copy(alpha = 0.4f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = if (selected) 0.22f else 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) accentColor else cs.onSurfaceVariant,
                    modifier = Modifier.size(13.dp),
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    ),
                    color = if (selected) cs.onSurface else cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MiniStatItem(
    label: String,
    value: String,
    accentColor: Color,
) {
    val cs = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = cs.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.5.sp,
            ),
            color = accentColor,
        )
    }
}

private fun buildSmoothPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points.first().x, points.first().y)
    for (i in 0 until points.lastIndex) {
        val p0 = points[i]
        val p1 = points[i + 1]
        val midX = (p0.x + p1.x) / 2f
        path.cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
    }
    return path
}

private fun formatMbps(value: Float): String =
    String.format(Locale.US, "%.1fM", value.coerceAtLeast(0f))

private fun formatMbpsShort(value: Float): String =
    String.format(Locale.US, "%.1f Mbps", value.coerceAtLeast(0f))

private val sampleTimeFormatter = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue(): SimpleDateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
}

private fun formatSampleTime(timestampMs: Long): String =
    sampleTimeFormatter.get()!!.format(Date(timestampMs))
