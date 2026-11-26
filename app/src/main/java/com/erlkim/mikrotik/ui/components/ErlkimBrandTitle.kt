package com.erlkim.mikrotik.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.erlkim.mikrotik.R
import com.erlkim.mikrotik.data.model.ConnectionStatus
import com.erlkim.mikrotik.ui.theme.LocalErlkimColors
import com.erlkim.mikrotik.ui.theme.PlusJakartaSans

@Composable
fun ErlkimBrandTitle(
    modifier: Modifier = Modifier,
    compact: Boolean = true,
    connectionStatus: ConnectionStatus? = null,
    onStatusClick: (() -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    val accentBrush = Brush.linearGradient(
        colors = listOf(cs.primary, cs.secondary, cs.tertiary),
    )

    val iconSize = if (compact) 30.dp else 36.dp
    val fontSize = if (compact) 19.sp else 23.sp

    val styledBrand = buildAnnotatedString {
        withStyle(
            SpanStyle(
                color = cs.onBackground,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
            ),
        ) {
            append("Erlkim")
        }
        withStyle(
            SpanStyle(
                brush = accentBrush,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.4.sp,
            ),
        ) {
            append("NET")
        }
    }

    Row(
        modifier = modifier.testTag("erlkim_brand_title"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_erlkim_brand_mark),
            contentDescription = stringResource(R.string.cd_brand_logo),
            modifier = Modifier.size(iconSize),
        )
        Spacer(modifier = Modifier.width(if (compact) 8.dp else 10.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = styledBrand,
                    fontSize = fontSize,
                    lineHeight = if (compact) 22.sp else 26.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (connectionStatus != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    ConnectionStatusDot(
                        status = connectionStatus,
                        compact = compact,
                        onClick = onStatusClick,
                    )
                }
            }
            // Subtle vector signal pulse accent line beneath the brand text
            Canvas(
                modifier = Modifier
                    .width(if (compact) 88.dp else 108.dp)
                    .height(4.dp),
            ) {
                val h = size.height
                val w = size.width
                val wavePath = Path().apply {
                    moveTo(0f, h * 0.65f)
                    lineTo(w * 0.56f, h * 0.65f)
                    lineTo(w * 0.64f, h * 0.15f)
                    lineTo(w * 0.72f, h * 0.92f)
                    lineTo(w * 0.80f, h * 0.45f)
                    lineTo(w, h * 0.45f)
                }
                drawPath(
                    path = wavePath,
                    brush = accentBrush,
                    style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round),
                )
                drawCircle(
                    color = cs.tertiary,
                    radius = 1.8.dp.toPx(),
                    center = Offset(w - 1.5.dp.toPx(), h * 0.45f),
                )
            }
        }
    }
}

@Composable
private fun ConnectionStatusDot(
    status: ConnectionStatus,
    compact: Boolean,
    onClick: (() -> Unit)?,
) {
    val cs = MaterialTheme.colorScheme
    val ext = LocalErlkimColors.current

    val targetColor = when (status) {
        ConnectionStatus.ConnectedLive -> ext.onlineGreen
        ConnectionStatus.ConnectedDemo -> ext.demoAmber
        ConnectionStatus.Connecting -> cs.tertiary
        is ConnectionStatus.Disconnected -> ext.offlineRed
    }
    val dotColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 260),
        label = "brand_connection_dot_color",
    )

    val statusDescription = when (status) {
        ConnectionStatus.ConnectedLive -> stringResource(R.string.cd_connection_online)
        ConnectionStatus.ConnectedDemo -> stringResource(R.string.cd_connection_demo)
        ConnectionStatus.Connecting -> stringResource(R.string.cd_connection_connecting)
        is ConnectionStatus.Disconnected -> stringResource(R.string.cd_connection_offline)
    }

    val isConnecting = status == ConnectionStatus.Connecting
    val pulseScale: Float
    val pulseAlpha: Float
    if (isConnecting) {
        val infiniteTransition = rememberInfiniteTransition(label = "connection_dot_pulse")
        val animatedScale by infiniteTransition.animateFloat(
            initialValue = 0.88f,
            targetValue = 1.24f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 750, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "connection_dot_scale",
        )
        val animatedAlpha by infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 750, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "connection_dot_alpha",
        )
        pulseScale = animatedScale
        pulseAlpha = animatedAlpha
    } else {
        pulseScale = 1f
        pulseAlpha = 1f
    }

    val haloSize = if (compact) 16.dp else 18.dp
    val coreSize = if (compact) 8.dp else 9.dp

    val containerModifier = Modifier
        .testTag("top_bar_connection_status_dot")
        .semantics(mergeDescendants = true) {
            contentDescription = statusDescription
        }
        .clip(CircleShape)
        .let { base ->
            if (onClick != null) {
                base.clickable(
                    role = Role.Button,
                    onClickLabel = statusDescription,
                    onClick = onClick,
                )
            } else {
                base
            }
        }
        .padding(4.dp)

    Box(
        modifier = containerModifier,
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(haloSize)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(dotColor.copy(alpha = 0.22f))
                .border(
                    width = 1.dp,
                    color = dotColor.copy(alpha = 0.45f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(coreSize)
                    .alpha(pulseAlpha)
                    .clip(CircleShape)
                    .background(dotColor),
            )
        }
    }
}
