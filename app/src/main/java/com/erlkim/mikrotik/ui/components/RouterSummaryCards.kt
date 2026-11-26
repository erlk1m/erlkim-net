package com.erlkim.mikrotik.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.erlkim.mikrotik.R
import com.erlkim.mikrotik.ui.theme.JetBrainsMono
import com.erlkim.mikrotik.ui.theme.LocalErlkimColors

@Composable
fun RouterSummaryCardsSection(
    uptime: String,
    cpuLoadPercent: Int,
    activeUsersCount: Int,
    isConnected: Boolean,
    onUptimeClick: () -> Unit,
    onCpuClick: () -> Unit,
    onActiveUsersClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val ext = LocalErlkimColors.current

    val cpuAccent = when {
        !isConnected -> cs.onSurfaceVariant
        cpuLoadPercent >= 80 -> ext.offlineRed
        cpuLoadPercent >= 50 -> ext.demoAmber
        else -> cs.primary
    }

    val cpuProgress by animateFloatAsState(
        targetValue = (cpuLoadPercent / 100f).coerceIn(0f, 1f),
        animationSpec = tween(500),
        label = "summary_cpu_progress",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.summary_section_title),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SummaryMetricCard(
                label = stringResource(R.string.summary_uptime),
                value = if (isConnected) uptime else "0d 00h",
                subtitle = if (isConnected) "Router aktif" else "Offline",
                icon = Icons.Rounded.Schedule,
                iconContainerColor = cs.secondaryContainer,
                iconTint = cs.secondary,
                onClick = onUptimeClick,
                modifier = Modifier
                    .weight(1f)
                    .testTag("summary_card_uptime"),
            )

            SummaryMetricCard(
                label = stringResource(R.string.summary_cpu_load),
                value = "$cpuLoadPercent%",
                subtitle = when {
                    !isConnected -> "Menunggu"
                    cpuLoadPercent >= 80 -> "Tinggi"
                    cpuLoadPercent >= 50 -> "Sedang"
                    else -> "Normal"
                },
                icon = Icons.Rounded.Memory,
                iconContainerColor = cs.primaryContainer,
                iconTint = cpuAccent,
                progressBar = cpuProgress,
                progressColor = cpuAccent,
                onClick = onCpuClick,
                modifier = Modifier
                    .weight(1f)
                    .testTag("summary_card_cpu"),
            )

            SummaryMetricCard(
                label = stringResource(R.string.summary_active_users),
                value = "$activeUsersCount",
                subtitle = "Hotspot online",
                icon = Icons.Rounded.Group,
                iconContainerColor = cs.tertiaryContainer,
                iconTint = cs.tertiary,
                showLiveDot = isConnected && activeUsersCount > 0,
                liveDotColor = ext.onlineGreen,
                onClick = onActiveUsersClick,
                modifier = Modifier
                    .weight(1f)
                    .testTag("summary_card_active_users"),
            )
        }
    }
}

@Composable
private fun SummaryMetricCard(
    label: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    iconContainerColor: Color,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progressBar: Float? = null,
    progressColor: Color = iconTint,
    showLiveDot: Boolean = false,
    liveDotColor: Color = iconTint,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = cs.surfaceContainer,
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.65f)),
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(iconContainerColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = iconTint,
                        modifier = Modifier.size(19.dp),
                    )
                }

                if (showLiveDot) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(liveDotColor),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                ),
                color = cs.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(6.dp))

            if (progressBar != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(cs.surfaceContainerHighest),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressBar)
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(progressColor),
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = cs.onSurfaceVariant.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
