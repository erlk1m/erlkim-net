package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.NetworkPing
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.network.PingDiagnosticState
import com.example.data.network.PingReply
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalErlkimColors
import java.util.Locale

@Composable
fun PingDiagnosticCard(
    pingState: PingDiagnosticState,
    gatewayHost: String,
    isConnectedLive: Boolean,
    onTargetChange: (String) -> Unit,
    onSelectPacketCount: (Int) -> Unit,
    onToggleRouterSource: (Boolean) -> Unit,
    onStartPing: () -> Unit,
    onStopPing: () -> Unit,
    onClearResults: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val ext = LocalErlkimColors.current

    val (badgeLabel, badgeFg, badgeBg) = when {
        pingState.isRunning -> Triple("Pinging…", cs.primary, cs.primaryContainer)
        pingState.replies.isEmpty() -> Triple("Ready", cs.onSurfaceVariant, cs.surfaceContainerHighest)
        pingState.receivedCount == 0 -> Triple("Unreachable", ext.offlineRed, ext.offlineContainer)
        else -> {
            val avg = pingState.avgMs ?: 0.0
            val formatted = String.format(Locale.US, "%.1f ms", avg)
            when {
                avg < 50.0 && pingState.packetLossPercent == 0 ->
                    Triple("$formatted • Good", ext.onlineGreen, ext.onlineContainer)
                avg < 120.0 ->
                    Triple("$formatted • Fair", ext.demoAmber, ext.demoContainer)
                else ->
                    Triple("$formatted • High", ext.offlineRed, ext.offlineContainer)
            }
        }
    }

    val presets = buildList {
        if (gatewayHost.isNotBlank()) add("Gateway ($gatewayHost)" to gatewayHost)
        add("Cloudflare 1.1.1.1" to "1.1.1.1")
        add("Google 8.8.8.8" to "8.8.8.8")
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("dashboard_ping_diagnostic_card"),
        shape = RoundedCornerShape(22.dp),
        color = cs.surfaceContainer,
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.55f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(cs.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.NetworkPing,
                        contentDescription = null,
                        tint = cs.onPrimaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.ping_card_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = cs.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.ping_card_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = badgeBg,
                    modifier = Modifier.testTag("ping_status_badge"),
                ) {
                    Text(
                        text = badgeLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = JetBrainsMono,
                        ),
                        color = badgeFg,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Target Presets
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                presets.forEach { (label, host) ->
                    val selected = pingState.targetHost.trim().equals(host, ignoreCase = true)
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = !pingState.isRunning) { onTargetChange(host) }
                            .testTag("ping_preset_$host"),
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) cs.secondaryContainer else cs.surfaceContainerHigh,
                        border = BorderStroke(
                            1.dp,
                            if (selected) cs.secondary else cs.outlineVariant.copy(alpha = 0.45f),
                        ),
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                fontFamily = JetBrainsMono,
                            ),
                            color = if (selected) cs.onSecondaryContainer else cs.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Host input + Ping/Stop Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = pingState.targetHost,
                    onValueChange = onTargetChange,
                    enabled = !pingState.isRunning,
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.ping_host_placeholder),
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMono),
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = JetBrainsMono,
                        color = cs.onSurface,
                    ),
                    trailingIcon = {
                        if (pingState.targetHost.isNotEmpty() && !pingState.isRunning) {
                            IconButton(onClick = { onTargetChange("") }) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.cd_clear_search),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { if (!pingState.isRunning) onStartPing() }),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ping_target_input"),
                )

                Button(
                    onClick = if (pingState.isRunning) onStopPing else onStartPing,
                    modifier = Modifier
                        .height(54.dp)
                        .testTag("ping_action_button"),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    colors = if (pingState.isRunning) {
                        ButtonDefaults.buttonColors(
                            containerColor = ext.offlineContainer,
                            contentColor = ext.offlineRed,
                        )
                    } else {
                        ButtonDefaults.buttonColors(
                            containerColor = cs.primary,
                            contentColor = cs.onPrimary,
                        )
                    },
                ) {
                    if (pingState.isRunning) {
                        Icon(
                            imageVector = Icons.Rounded.Stop,
                            contentDescription = stringResource(R.string.ping_action_stop),
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.ping_action_stop), fontWeight = FontWeight.SemiBold)
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(R.string.ping_action_run),
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.ping_action_run), fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Options row: packet count + probe source (when ConnectedLive) + clear button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "Packets:",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant,
                    )
                    listOf(1, 4, 8).forEach { count ->
                        val active = pingState.packetCount == count
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(enabled = !pingState.isRunning) { onSelectPacketCount(count) }
                                .testTag("ping_count_$count"),
                            shape = RoundedCornerShape(10.dp),
                            color = if (active) cs.primaryContainer else cs.surfaceContainerHigh,
                        ) {
                            Text(
                                text = "${count}x",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMono,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                ),
                                color = if (active) cs.onPrimaryContainer else cs.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            )
                        }
                    }

                    if (isConnectedLive) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(enabled = !pingState.isRunning) {
                                    onToggleRouterSource(!pingState.useRouterApi)
                                }
                                .testTag("ping_source_toggle"),
                            shape = RoundedCornerShape(10.dp),
                            color = if (pingState.useRouterApi) cs.tertiaryContainer else cs.surfaceContainerHigh,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = if (pingState.useRouterApi) Icons.Rounded.Router else Icons.Rounded.Smartphone,
                                    contentDescription = null,
                                    tint = if (pingState.useRouterApi) cs.onTertiaryContainer else cs.onSurfaceVariant,
                                    modifier = Modifier.size(13.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (pingState.useRouterApi) "Via Router" else "Via Device",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (pingState.useRouterApi) cs.onTertiaryContainer else cs.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                if (pingState.replies.isNotEmpty() && !pingState.isRunning) {
                    IconButton(
                        onClick = onClearResults,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("ping_clear_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Clear ping results",
                            tint = cs.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                } else if (pingState.isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = cs.primary,
                    )
                }
            }

            pingState.lastError?.let { errorMsg ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMsg,
                    style = MaterialTheme.typography.bodySmall,
                    color = ext.offlineRed,
                )
            }

            // Live Results Section
            AnimatedVisibility(visible = pingState.replies.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.45f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Latency Stats Grid (Min / Avg / Max / Loss)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PingStatTile(
                            label = "MIN",
                            value = pingState.minMs?.let { String.format(Locale.US, "%.1f ms", it) } ?: "--",
                            accent = ext.onlineGreen,
                            modifier = Modifier.weight(1f),
                        )
                        PingStatTile(
                            label = "AVG",
                            value = pingState.avgMs?.let { String.format(Locale.US, "%.1f ms", it) } ?: "--",
                            accent = cs.primary,
                            modifier = Modifier.weight(1f),
                        )
                        PingStatTile(
                            label = "MAX",
                            value = pingState.maxMs?.let { String.format(Locale.US, "%.1f ms", it) } ?: "--",
                            accent = ext.demoAmber,
                            modifier = Modifier.weight(1f),
                        )
                        PingStatTile(
                            label = "LOSS",
                            value = "${pingState.packetLossPercent}%",
                            accent = if (pingState.packetLossPercent > 0) ext.offlineRed else ext.onlineGreen,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Mini Visual Latency Bar Chart
                    PingLatencyBars(replies = pingState.replies)

                    Spacer(modifier = Modifier.height(10.dp))

                    // Compact Per-Reply Monospace Console
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = cs.surfaceContainerLowest,
                        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.35f)),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            pingState.replies.forEach { reply ->
                                PingReplyRow(reply = reply)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PingStatTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = cs.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = cs.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Bold,
                ),
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PingLatencyBars(replies: List<PingReply>) {
    val cs = MaterialTheme.colorScheme
    val ext = LocalErlkimColors.current
    val maxLatency = (replies.mapNotNull { it.latencyMs }.maxOrNull() ?: 100.0).coerceAtLeast(20.0)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(cs.surfaceContainerHigh)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        replies.forEach { reply ->
            val ms = reply.latencyMs
            val fraction = if (ms == null) 0.18f else ((ms / maxLatency).toFloat()).coerceIn(0.22f, 1f)
            val barColor = when {
                ms == null -> ext.offlineRed
                ms < 50.0 -> ext.onlineGreen
                ms < 120.0 -> ext.demoAmber
                else -> ext.offlineRed
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(fraction)
                    .clip(RoundedCornerShape(4.dp))
                    .background(barColor),
            )
        }
    }
}

@Composable
private fun PingReplyRow(reply: PingReply) {
    val cs = MaterialTheme.colorScheme
    val ext = LocalErlkimColors.current
    val ms = reply.latencyMs
    val dotColor = when {
        ms == null -> ext.offlineRed
        ms < 50.0 -> ext.onlineGreen
        ms < 120.0 -> ext.demoAmber
        else -> ext.offlineRed
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "#${reply.seq}  ${reply.resolvedIp}",
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = JetBrainsMono,
                fontSize = 11.sp,
            ),
            color = cs.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (reply.ttl != null) {
            Text(
                text = "ttl=${reply.ttl}  ",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = JetBrainsMono,
                    fontSize = 11.sp,
                ),
                color = cs.onSurfaceVariant,
            )
        }
        Text(
            text = if (ms != null) String.format(Locale.US, "%.1f ms", ms) else reply.status,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
            ),
            color = dotColor,
        )
    }
}
