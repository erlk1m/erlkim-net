package com.erlkim.mikrotik.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.erlkim.mikrotik.data.model.ThemeMode

private val ErlkimDarkColorScheme = darkColorScheme(
    primary = CoralPeach,
    onPrimary = OnCoralPeach,
    primaryContainer = CoralContainerDark,
    onPrimaryContainer = OnCoralContainerDark,
    inversePrimary = CoralPeachLight,
    secondary = SoftLavender,
    onSecondary = OnSoftLavender,
    secondaryContainer = LavenderContainerDark,
    onSecondaryContainer = OnLavenderContainerDark,
    tertiary = SkyBlue,
    onTertiary = OnSkyBlue,
    tertiaryContainer = SkyContainerDark,
    onTertiaryContainer = OnSkyContainerDark,
    background = ObsidianBackground,
    onBackground = TextPrimaryDark,
    surface = EspressoSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = EspressoSurfaceHigh,
    onSurfaceVariant = TextSecondaryDark,
    surfaceTint = CoralPeach,
    inverseSurface = EspressoSurfaceHighest,
    inverseOnSurface = TextPrimaryDark,
    surfaceDim = ObsidianBackground,
    surfaceBright = EspressoSurfaceHighest,
    surfaceContainerLowest = ObsidianBackground,
    surfaceContainerLow = ObsidianSurfaceLow,
    surfaceContainer = EspressoSurface,
    surfaceContainerHigh = EspressoSurfaceHigh,
    surfaceContainerHighest = EspressoSurfaceHighest,
    outline = WarmOutline,
    outlineVariant = WarmOutlineSubtle,
    error = StatusOfflineRed,
    onError = OnStatusOfflineDark,
    errorContainer = StatusOfflineContainer,
    onErrorContainer = OnStatusOfflineContainerDark,
    scrim = androidx.compose.ui.graphics.Color(0xFF000000),
)

private val ErlkimLightColorScheme = lightColorScheme(
    primary = CoralPeachLight,
    onPrimary = LightSurface,
    primaryContainer = CoralContainerLight,
    onPrimaryContainer = OnCoralContainerLight,
    inversePrimary = CoralPeach,
    secondary = LavenderLight,
    onSecondary = LightSurface,
    secondaryContainer = LavenderContainerLight,
    onSecondaryContainer = TextPrimaryLight,
    tertiary = SkyBlueLight,
    onTertiary = LightSurface,
    tertiaryContainer = SkyContainerLight,
    onTertiaryContainer = TextPrimaryLight,
    background = LightBackground,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    surfaceTint = CoralPeachLight,
    inverseSurface = EspressoSurface,
    inverseOnSurface = TextPrimaryDark,
    surfaceDim = LightSurfaceHigh,
    surfaceBright = LightSurface,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = LightBackground,
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightSurfaceVariant,
    surfaceContainerHighest = LightSurfaceHigh,
    outline = LightOutline,
    outlineVariant = LightSurfaceHigh,
    error = androidx.compose.ui.graphics.Color(0xFFB91C1C),
    onError = LightSurface,
    errorContainer = androidx.compose.ui.graphics.Color(0xFFFEE2E2),
    onErrorContainer = androidx.compose.ui.graphics.Color(0xFF450A0A),
    scrim = androidx.compose.ui.graphics.Color(0xFF000000),
)

private val SakuraAnimeColorScheme = lightColorScheme(
    primary = SakuraPrimary,
    onPrimary = OnSakuraPrimary,
    primaryContainer = SakuraPrimaryContainer,
    onPrimaryContainer = OnSakuraPrimaryContainer,
    inversePrimary = Color(0xFFFFB0CD),
    secondary = SakuraSecondary,
    onSecondary = OnSakuraSecondary,
    secondaryContainer = SakuraSecondaryContainer,
    onSecondaryContainer = OnSakuraSecondaryContainer,
    tertiary = SakuraTertiary,
    onTertiary = OnSakuraTertiary,
    tertiaryContainer = SakuraTertiaryContainer,
    onTertiaryContainer = OnSakuraTertiaryContainer,
    background = SakuraBackground,
    onBackground = SakuraTextPrimary,
    surface = SakuraSurface,
    onSurface = SakuraTextPrimary,
    surfaceVariant = SakuraSurfaceVariant,
    onSurfaceVariant = SakuraTextSecondary,
    surfaceTint = SakuraPrimary,
    inverseSurface = Color(0xFF38202D),
    inverseOnSurface = Color(0xFFFFECF2),
    surfaceDim = SakuraSurfaceHigh,
    surfaceBright = SakuraSurface,
    surfaceContainerLowest = SakuraSurface,
    surfaceContainerLow = SakuraSurfaceLow,
    surfaceContainer = SakuraSurface,
    surfaceContainerHigh = SakuraSurfaceVariant,
    surfaceContainerHighest = SakuraSurfaceHigh,
    outline = SakuraOutline,
    outlineVariant = SakuraOutlineSubtle,
    error = Color(0xFFC9284C),
    onError = SakuraSurface,
    errorContainer = Color(0xFFFFDCE5),
    onErrorContainer = Color(0xFF470415),
    scrim = Color(0xFF1F0C16),
)

val ErlkimShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun ErlkimNetTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val isSakuraAnime = themeMode == ThemeMode.SAKURA_ANIME
    val useDarkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SAKURA_ANIME -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        isSakuraAnime -> SakuraAnimeColorScheme
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        useDarkTheme -> ErlkimDarkColorScheme
        else -> ErlkimLightColorScheme
    }

    val extendedColors = when {
        isSakuraAnime -> SakuraAnimeErlkimColors
        useDarkTheme -> DarkErlkimColors
        else -> LightErlkimColors
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !useDarkTheme
                controller.isAppearanceLightNavigationBars = !useDarkTheme
            }
        }
    }

    CompositionLocalProvider(LocalErlkimColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ErlkimTypography,
            shapes = ErlkimShapes,
            content = content,
        )
    }
}
