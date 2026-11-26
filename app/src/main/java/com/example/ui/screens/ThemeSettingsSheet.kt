package com.example.ui.screens

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.ThemeMode
import com.example.ui.theme.CoralPeach
import com.example.ui.theme.CoralPeachLight
import com.example.ui.theme.EspressoSurface
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LightBackground
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.SakuraBackground
import com.example.ui.theme.SakuraPrimary
import com.example.ui.theme.SakuraSecondary
import com.example.ui.theme.SakuraTertiary
import com.example.ui.theme.SkyBlue
import com.example.ui.theme.SoftLavender
import kotlin.math.sin

private data class ThemeOptionMeta(
    val mode: ThemeMode,
    val subtitle: String,
    val icon: ImageVector,
    val swatches: List<Color>,
    val previewBg: Color,
    val badgeLabel: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsSheet(
    currentMode: ThemeMode,
    dynamicColor: Boolean,
    onUpdateTheme: (ThemeMode, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isSakuraSelected = currentMode == ThemeMode.SAKURA_ANIME

    val themeOptions = listOf(
        ThemeOptionMeta(
            mode = ThemeMode.DARK,
            subtitle = "Nyaman untuk monitoring malam",
            icon = Icons.Rounded.DarkMode,
            swatches = listOf(ObsidianBackground, CoralPeach, SoftLavender),
            previewBg = EspressoSurface,
        ),
        ThemeOptionMeta(
            mode = ThemeMode.LIGHT,
            subtitle = "Cerahkan layar di siang hari",
            icon = Icons.Rounded.LightMode,
            swatches = listOf(LightBackground, CoralPeachLight, SkyBlue),
            previewBg = LightBackground,
        ),
        ThemeOptionMeta(
            mode = ThemeMode.SAKURA_ANIME,
            subtitle = stringResource(R.string.theme_sakura_desc),
            icon = Icons.Rounded.LocalFlorist,
            swatches = listOf(SakuraBackground, SakuraPrimary, SakuraSecondary),
            previewBg = SakuraBackground,
            badgeLabel = "Anime",
        ),
        ThemeOptionMeta(
            mode = ThemeMode.SYSTEM,
            subtitle = "Otomatis ikuti sistem Android",
            icon = Icons.Rounded.BrightnessAuto,
            swatches = listOf(ObsidianBackground, LightBackground, CoralPeach),
            previewBg = cs.surfaceContainerHigh,
        ),
    )

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
                .padding(bottom = 32.dp)
                .testTag("theme_settings_sheet"),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(cs.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isSakuraSelected) Icons.Rounded.LocalFlorist else Icons.Rounded.Palette,
                        contentDescription = null,
                        tint = cs.onPrimaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.setting_theme),
                        style = MaterialTheme.typography.titleLarge,
                        color = cs.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.theme_night_comfort_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Live Theme Visual Preview Card
            ThemeLivePreviewBanner(
                mode = currentMode,
                dynamicColor = dynamicColor,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "PILIH GAYA TEMA",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                ),
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )

            // 2x2 Grid of Visual Theme Cards
            val rows = themeOptions.chunked(2)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        rowItems.forEach { meta ->
                            ThemeOptionCard(
                                meta = meta,
                                selected = currentMode == meta.mode,
                                onClick = { onUpdateTheme(meta.mode, dynamicColor) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            // Material You Dynamic Color Toggle (Android 12+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = cs.surfaceContainerHigh,
                    border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.45f)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Material You (Dynamic Color)",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = if (isSakuraSelected) cs.onSurfaceVariant else cs.onSurface,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isSakuraSelected) {
                                    stringResource(R.string.theme_sakura_dynamic_note)
                                } else {
                                    "Sesuaikan warna aksen dengan wallpaper Android 12+"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant,
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = dynamicColor && !isSakuraSelected,
                            enabled = !isSakuraSelected,
                            onCheckedChange = { onUpdateTheme(currentMode, it) },
                            modifier = Modifier.testTag("theme_dynamic_color_switch"),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeLivePreviewBanner(
    mode: ThemeMode,
    dynamicColor: Boolean,
) {
    val cs = MaterialTheme.colorScheme
    val isSakura = mode == ThemeMode.SAKURA_ANIME

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(148.dp)
            .testTag("theme_live_preview_banner"),
        shape = RoundedCornerShape(22.dp),
        color = cs.surfaceContainerHigh,
        border = BorderStroke(1.dp, cs.primary.copy(alpha = 0.35f)),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isSakura) {
                Image(
                    painter = painterResource(id = R.drawable.img_sakura_anime_banner),
                    contentDescription = "Ilustrasi tema Anime Sakura",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0x332B1622),
                                    Color(0x992B1622),
                                ),
                            ),
                        ),
                )
                SakuraPetalCanvas(
                    modifier = Modifier.fillMaxSize(),
                    petalAlpha = 0.72f,
                    petalCount = 12,
                )
            } else {
                val gradientColors = when (mode) {
                    ThemeMode.DARK -> listOf(
                        Color(0xFF2B1C22),
                        Color(0xFF1D1824),
                        ObsidianBackground,
                    )
                    ThemeMode.LIGHT -> listOf(
                        Color(0xFFFFE5DB),
                        Color(0xFFEBE4FF),
                        LightBackground,
                    )
                    ThemeMode.SYSTEM -> listOf(
                        cs.primaryContainer.copy(alpha = 0.7f),
                        cs.secondaryContainer.copy(alpha = 0.6f),
                        cs.surfaceContainerHigh,
                    )
                    ThemeMode.SAKURA_ANIME -> listOf(SakuraPrimary, SakuraSecondary)
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(gradientColors)),
                )
            }

            // Overlay preview badges & mini dashboard mockup
            val previewTextColor = when {
                isSakura -> Color.White
                mode == ThemeMode.DARK -> Color(0xFFF5EEF1)
                mode == ThemeMode.LIGHT -> Color(0xFF1D181A)
                else -> cs.onSurface
            }
            val previewSubColor = previewTextColor.copy(alpha = 0.82f)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (isSakura) Color(0xCC3B1328) else cs.surface.copy(alpha = 0.82f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = if (isSakura) Icons.Rounded.AutoAwesome else Icons.Rounded.Palette,
                                contentDescription = null,
                                tint = if (isSakura) Color(0xFFFFB4D1) else cs.primary,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Pratinjau • ${mode.label}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = if (isSakura) Color.White else cs.onSurface,
                            )
                        }
                    }

                    // Active palette dots
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf(cs.primary, cs.secondary, cs.tertiary).forEach { swatch ->
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(swatch)
                                    .border(1.5.dp, Color.White.copy(alpha = 0.75f), CircleShape),
                            )
                        }
                    }
                }

                Column {
                    Text(
                        text = if (isSakura) "ErlkimNET • Gaya Anime Sakura" else "ErlkimNET Dashboard",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = previewTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = when (mode) {
                            ThemeMode.SAKURA_ANIME -> "Nuansa kelopak sakura & langit senja anime yang lembut di mata"
                            ThemeMode.DARK -> "Palet gelap kontras nyaman untuk pemantauan router 24 jam"
                            ThemeMode.LIGHT -> "Tampilan cerah dan bersih untuk penggunaan di luar ruangan"
                            ThemeMode.SYSTEM -> if (dynamicColor) "Mengikuti mode sistem & warna Material You" else "Mengikuti mode Gelap/Terang perangkat Android"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = previewSubColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PreviewChip(label = "CPU 24%", accent = cs.primary, darkOverlay = isSakura)
                        PreviewChip(label = "45.3 Mbps", accent = cs.secondary, darkOverlay = isSakura)
                        PreviewChip(label = "Online", accent = Color(0xFF4ADE80), darkOverlay = isSakura)
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewChip(
    label: String,
    accent: Color,
    darkOverlay: Boolean,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (darkOverlay) Color(0xAA29101E) else cs.surface.copy(alpha = 0.86f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMono,
                    fontSize = 10.sp,
                ),
                color = if (darkOverlay) Color.White else cs.onSurface,
            )
        }
    }
}

@Composable
private fun ThemeOptionCard(
    meta: ThemeOptionMeta,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val isSakura = meta.mode == ThemeMode.SAKURA_ANIME
    val borderColor by animateColorAsState(
        targetValue = if (selected) cs.primary else cs.outlineVariant.copy(alpha = 0.55f),
        animationSpec = tween(220),
        label = "theme_card_border_${meta.mode.name}",
    )

    Surface(
        modifier = modifier
            .testTag("theme_option_${meta.mode.name.lowercase()}")
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) cs.primaryContainer.copy(alpha = 0.34f) else cs.surfaceContainerHigh,
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Mini visual swatch header strip
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                if (isSakura) {
                    Image(
                        painter = painterResource(id = R.drawable.img_sakura_anime_banner),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x382B1622)),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        meta.swatches.first(),
                                        meta.swatches.last().copy(alpha = 0.45f),
                                    ),
                                ),
                            ),
                    )
                }

                // Icon badge + optional featured tag
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSakura) Color(0xCC3B1328) else cs.surface.copy(alpha = 0.88f),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = meta.icon,
                            contentDescription = null,
                            tint = if (isSakura) Color(0xFFFFB4D1) else cs.primary,
                            modifier = Modifier.size(17.dp),
                        )
                    }

                    if (selected) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Terpilih",
                            tint = if (isSakura) Color(0xFFFFD5E6) else cs.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    } else if (meta.badgeLabel != null) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = SakuraPrimary,
                        ) {
                            Text(
                                text = meta.badgeLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                ),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    text = meta.mode.label,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    ),
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = meta.subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = cs.onSurfaceVariant,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 14.sp,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    meta.swatches.forEach { dotColor ->
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                                .border(1.dp, cs.outline.copy(alpha = 0.45f), CircleShape),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Delicate, non-touch-blocking drifting cherry blossom petals overlay
 * rendered subtly along the top atmosphere when ThemeMode.SAKURA_ANIME is active.
 */
@Composable
fun SakuraAmbientOverlay(
    modifier: Modifier = Modifier,
) {
    SakuraPetalCanvas(
        modifier = modifier
            .fillMaxWidth()
            .height(170.dp),
        petalAlpha = 0.28f,
        petalCount = 9,
    )
}

@Composable
private fun SakuraPetalCanvas(
    modifier: Modifier = Modifier,
    petalAlpha: Float = 0.35f,
    petalCount: Int = 10,
) {
    val transition = rememberInfiniteTransition(label = "sakura_petal_drift")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sakura_petal_progress",
    )

    val petalColors = listOf(
        Color(0xFFFFB7D5),
        Color(0xFFFF90BC),
        Color(0xFFFFD8E8),
        SakuraTertiary.copy(alpha = 0.85f),
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        for (i in 0 until petalCount) {
            val seed = (i * 0.137f + 0.08f) % 1f
            val speed = 0.75f + (i % 3) * 0.22f
            val yFrac = (progress * speed + seed) % 1f
            val sway = sin((yFrac * 6.283f) + i * 1.4f) * (18.dp.toPx())
            val x = ((seed * w * 1.15f) + sway + (progress * 26.dp.toPx() * (if (i % 2 == 0) 1f else -0.6f)))
                .let { ((it % w) + w) % w }
            val y = yFrac * h
            val fadeEdge = (1f - (yFrac * 0.85f)).coerceIn(0.15f, 1f)
            val radius = (4.5f + (i % 4) * 1.4f).dp.toPx()
            val angle = (progress * 360f * (if (i % 2 == 0) 1f else -1f) + i * 47f) % 360f
            val color = petalColors[i % petalColors.size].copy(alpha = petalAlpha * fadeEdge)

            rotate(degrees = angle, pivot = Offset(x, y)) {
                val path = Path().apply {
                    moveTo(x, y - radius)
                    cubicTo(
                        x + radius * 0.9f,
                        y - radius * 0.45f,
                        x + radius * 0.75f,
                        y + radius * 0.65f,
                        x,
                        y + radius,
                    )
                    cubicTo(
                        x - radius * 0.75f,
                        y + radius * 0.65f,
                        x - radius * 0.9f,
                        y - radius * 0.45f,
                        x,
                        y - radius,
                    )
                    close()
                }
                drawPath(path = path, color = color)
            }
        }
    }
}
