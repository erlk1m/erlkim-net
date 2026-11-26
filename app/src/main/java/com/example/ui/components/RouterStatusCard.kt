package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.ConnectionStatus
import com.example.data.model.RouterResource
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalErlkimColors

@Composable
fun RouterStatusCard(
    resource: RouterResource,
    connectionStatus: ConnectionStatus,
    routerAddress: String,
    onCardClick: () -> Unit,
    onOpenConnection: () -> Unit,
    onEnableDemo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ext = LocalErlkimColors.current
    val cs = MaterialTheme.colorScheme

    val (statusLabel, statusColor) = when (connectionStatus) {
        ConnectionStatus.ConnectedLive -> stringResource(R.string.status_connected_online) to ext.onlineGreen
        ConnectionStatus.ConnectedDemo -> stringResource(R.string.status_connected_demo) to ext.demoAmber
        ConnectionStatus.Connecting -> stringResource(R.string.status_connecting) to cs.tertiary
        is ConnectionStatus.Disconnected -> stringResource(R.string.status_offline) to ext.offlineRed
    }

    val cpuAnim by animateFloatAsState(
        targetValue = (resource.cpuLoadPercent / 100f).coerceIn(0f, 1f),
        animationSpec = tween(550),
        label = "cpu_anim",
    )
    val ramAnim by animateFloatAsState(
        targetValue = (resource.ramUsagePercent / 100f).coerceIn(0f, 1f),
        animationSpec = tween(550),
        label = "ram_anim",
    )
    val storageAnim by animateFloatAsState(
        targetValue = (resource.storageUsagePercent / 100f).coerceIn(0f, 1f),
        animationSpec = tween(550),
        label = "storage_anim",
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("router_status_card")
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onCardClick),
        shape = RoundedCornerShape(24.dp),
        color = cs.surfaceContainer,
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.65f)),
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = resource.version,
                        style = MaterialTheme.typography.titleLarge,
                        color = cs.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Status: ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant,
                        )
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = statusColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Uptime: ${resource.uptime}  •  $routerAddress",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                if (connectionStatus is ConnectionStatus.Connecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = cs.secondary,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(statusColor),
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Circular CPU Load Gauge (left)
                Box(
                    modifier = Modifier.size(72.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val trackColor = cs.surfaceContainerHighest
                    val ringColor = cs.secondary
                    Canvas(modifier = Modifier.size(72.dp)) {
                        val stroke = 7.dp.toPx()
                        drawArc(
                            color = trackColor,
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = stroke, cap = StrokeCap.Round),
                        )
                        drawArc(
                            color = ringColor,
                            startAngle = -90f,
                            sweepAngle = (cpuAnim * 360f).coerceAtLeast(if (connectionStatus.isConnected) 18f else 0f),
                            useCenter = false,
                            style = Stroke(width = stroke, cap = StrokeCap.Round),
                        )
                    }
                    Text(
                        text = "${resource.cpuLoadPercent}%",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = JetBrainsMono,
                            fontSize = 15.sp,
                        ),
                        color = cs.onSurface,
                    )
                }

                Spacer(modifier = Modifier.width(20.dp))

                // Horizontal Progress Bars: CPU / RAM / Storage
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MetricBarColumn(
                        label = stringResource(R.string.label_cpu),
                        percent = resource.cpuLoadPercent,
                        progress = cpuAnim,
                        modifier = Modifier.weight(1f),
                    )
                    MetricBarColumn(
                        label = stringResource(R.string.label_ram),
                        percent = resource.ramUsagePercent,
                        progress = ramAnim,
                        modifier = Modifier.weight(1f),
                    )
                    MetricBarColumn(
                        label = stringResource(R.string.label_storage),
                        percent = resource.storageUsagePercent,
                        progress = storageAnim,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Honest Disconnected Action Row
            if (connectionStatus is ConnectionStatus.Disconnected) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ext.offlineContainer.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Router $routerAddress tidak terjangkau. Hubungkan MikroTik RouterOS v7 (REST API) atau aktifkan Mode Demo untuk pratinjau UI.",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurface,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Button(
                                onClick = onOpenConnection,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("connect_router_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = cs.primary,
                                    contentColor = cs.onPrimary,
                                ),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Router,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.action_connect_router),
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            OutlinedButton(
                                onClick = onEnableDemo,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("enable_demo_button"),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, cs.outline),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.action_demo_mode),
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricBarColumn(
    label: String,
    percent: Int,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(CircleShape)
                .background(cs.surfaceContainerHighest),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(7.dp)
                    .clip(CircleShape)
                    .background(cs.secondary),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "$percent%",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Medium,
            ),
            color = cs.onSurface,
        )
    }
}
