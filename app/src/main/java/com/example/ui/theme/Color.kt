package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Core Dark Palette — Night-friendly Warm Obsidian / Charcoal Espresso (low-glare for dark monitoring)
val ObsidianBackground = Color(0xFF141113)
val ObsidianSurfaceLow = Color(0xFF1C1719)
val EspressoSurface = Color(0xFF231D20)
val EspressoSurfaceHigh = Color(0xFF2C2528)
val EspressoSurfaceHighest = Color(0xFF362E32)
val WarmOutline = Color(0xFF4F4449)
val WarmOutlineSubtle = Color(0xFF352C30)

// Primary Coral-Peach (FAB, Upload line, Active Settings Pill)
val CoralPeach = Color(0xFFF48C68)
val OnCoralPeach = Color(0xFF2C1108)
val CoralContainerDark = Color(0xFF5A372B)
val OnCoralContainerDark = Color(0xFFFFDBD0)

// Secondary Lavender-Periwinkle (CPU/RAM/Storage progress, Avatar, Accent)
val SoftLavender = Color(0xFFBBA8FA)
val OnSoftLavender = Color(0xFF23164A)
val LavenderContainerDark = Color(0xFF43375E)
val OnLavenderContainerDark = Color(0xFFE7DEFF)

// Tertiary Sky Blue (Download graph, Hotspot badge)
val SkyBlue = Color(0xFF64B5F6)
val OnSkyBlue = Color(0xFF0A2538)
val SkyContainerDark = Color(0xFF1E374B)
val OnSkyContainerDark = Color(0xFFCDE8FF)

// Text Colors Dark (soft off-white to minimize night glare while preserving WCAG AA contrast)
val TextPrimaryDark = Color(0xFFEFE9EC)
val TextSecondaryDark = Color(0xFFB3A8AC)

// Light Palette (for "Terang" mode in Tema & Tampilan)
val LightBackground = Color(0xFFF9F6F7)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF1EAEB)
val LightSurfaceHigh = Color(0xFFE8DFE1)
val CoralPeachLight = Color(0xFFC85634)
val CoralContainerLight = Color(0xFFFFDBD0)
val OnCoralContainerLight = Color(0xFF3B1205)
val LavenderLight = Color(0xFF614DB8)
val LavenderContainerLight = Color(0xFFE9E2FF)
val SkyBlueLight = Color(0xFF1967A3)
val SkyContainerLight = Color(0xFFD5E9FF)
val TextPrimaryLight = Color(0xFF1D181A)
val TextSecondaryLight = Color(0xFF61565A)
val LightOutline = Color(0xFFD4C8CC)

// Anime Sakura Palette — Makoto Shinkai twilight sky & blooming cherry blossom pastels
val SakuraBackground = Color(0xFFFFF4F8)
val SakuraSurface = Color(0xFFFFFBFD)
val SakuraSurfaceLow = Color(0xFFFFEEF4)
val SakuraSurfaceVariant = Color(0xFFFDE2EC)
val SakuraSurfaceHigh = Color(0xFFFAD3E3)
val SakuraPrimary = Color(0xFFD83B78)
val OnSakuraPrimary = Color(0xFFFFFFFF)
val SakuraPrimaryContainer = Color(0xFFFFD8E7)
val OnSakuraPrimaryContainer = Color(0xFF3E001D)
val SakuraSecondary = Color(0xFF6358C7)
val OnSakuraSecondary = Color(0xFFFFFFFF)
val SakuraSecondaryContainer = Color(0xFFE6E0FF)
val OnSakuraSecondaryContainer = Color(0xFF1C1260)
val SakuraTertiary = Color(0xFFE66F59)
val OnSakuraTertiary = Color(0xFFFFFFFF)
val SakuraTertiaryContainer = Color(0xFFFFDBD2)
val OnSakuraTertiaryContainer = Color(0xFF3F0F06)
val SakuraTextPrimary = Color(0xFF2B1622)
val SakuraTextSecondary = Color(0xFF6F4D60)
val SakuraOutline = Color(0xFFE2B5C8)
val SakuraOutlineSubtle = Color(0xFFF4D1DF)

// Status Colors
val StatusOnlineGreen = Color(0xFF4ADE80)
val StatusOnlineContainer = Color(0xFF1D3B28)
val StatusDemoAmber = Color(0xFFFBBF24)
val StatusDemoContainer = Color(0xFF3D3018)
val StatusOfflineRed = Color(0xFFF87171)
val StatusOfflineContainer = Color(0xFF422023)
val OnStatusOfflineDark = Color(0xFF3B090C)
val OnStatusOfflineContainerDark = Color(0xFFFFDAD8)

@Immutable
data class ErlkimExtendedColors(
    val onlineGreen: Color,
    val onlineContainer: Color,
    val demoAmber: Color,
    val demoContainer: Color,
    val offlineRed: Color,
    val offlineContainer: Color,
    val downloadLine: Color,
    val downloadFillTop: Color,
    val uploadLine: Color,
    val tileHotspotBg: Color,
    val tileHotspotBadge: Color,
    val tileInterfacesBg: Color,
    val tileInterfacesBadge: Color,
    val tileDhcpBg: Color,
    val tileDhcpBadge: Color,
    val tileWirelessBg: Color,
    val tileWirelessBadge: Color,
    val tileIpBg: Color,
    val tileIpBadge: Color,
    val tileSystemBg: Color,
    val tileSystemBadge: Color,
    // Hotspot screen themed surfaces (dark-first for night monitoring)
    val hotspotScreenBg: Color,
    val hotspotCardBg: Color,
    val hotspotCardText: Color,
    val hotspotCardSubtext: Color,
    val hotspotActivePillBg: Color,
    val hotspotWifiOrange: Color,
    // User Avatar badges
    val avatarActiveBg: Color,
    val avatarActiveIcon: Color,
    val avatarVipBg: Color,
    val avatarVipIcon: Color,
    val avatarInactiveBg: Color,
    val avatarInactiveIcon: Color,
    // Settings Circular Badges
    val badgeIp: Color,
    val badgeWireless: Color,
    val badgeHotspot: Color,
    val badgeIdentity: Color,
    val badgeSecurity: Color,
    val badgeBackup: Color,
    val badgeReboot: Color,
    val badgeTheme: Color,
    val badgeAbout: Color,
)

val DarkErlkimColors = ErlkimExtendedColors(
    onlineGreen = StatusOnlineGreen,
    onlineContainer = StatusOnlineContainer,
    demoAmber = StatusDemoAmber,
    demoContainer = StatusDemoContainer,
    offlineRed = StatusOfflineRed,
    offlineContainer = StatusOfflineContainer,
    downloadLine = Color(0xFF63B3ED),
    downloadFillTop = Color(0x773889C4),
    uploadLine = Color(0xFFF48C68),
    tileHotspotBg = Color(0xFF1F2D3B),
    tileHotspotBadge = Color(0xFF60B2F5),
    tileInterfacesBg = Color(0xFF35281F),
    tileInterfacesBadge = Color(0xFFF39C52),
    tileDhcpBg = Color(0xFF252434),
    tileDhcpBadge = Color(0xFF8B92EA),
    tileWirelessBg = Color(0xFF2E2233),
    tileWirelessBadge = Color(0xFFD293F2),
    tileIpBg = Color(0xFF232533),
    tileIpBadge = Color(0xFF8C92EA),
    tileSystemBg = Color(0xFF2E2131),
    tileSystemBadge = Color(0xFFD293F2),
    hotspotScreenBg = ObsidianBackground,
    hotspotCardBg = EspressoSurface,
    hotspotCardText = TextPrimaryDark,
    hotspotCardSubtext = TextSecondaryDark,
    hotspotActivePillBg = StatusOnlineContainer,
    hotspotWifiOrange = CoralPeach,
    avatarActiveBg = Color(0xFF2E5034),
    avatarActiveIcon = Color(0xFF86E08D),
    avatarVipBg = Color(0xFF533357),
    avatarVipIcon = Color(0xFFDF9AE8),
    avatarInactiveBg = Color(0xFF593D2D),
    avatarInactiveIcon = Color(0xFFF2AA7E),
    badgeIp = Color(0xFF67A8E8),
    badgeWireless = Color(0xFFF49A48),
    badgeHotspot = Color(0xFFB572B8),
    badgeIdentity = Color(0xFF9F8CE0),
    badgeSecurity = Color(0xFF7D491F),
    badgeBackup = Color(0xFF2A456B),
    badgeReboot = Color(0xFF2D5534),
    badgeTheme = Color(0xFF5A3F35),
    badgeAbout = Color(0xFF2D4659),
)

val LightErlkimColors = ErlkimExtendedColors(
    onlineGreen = Color(0xFF15803D),
    onlineContainer = Color(0xFFDCFCE7),
    demoAmber = Color(0xFFB45309),
    demoContainer = Color(0xFFFEF3C7),
    offlineRed = Color(0xFFB91C1C),
    offlineContainer = Color(0xFFFEE2E2),
    downloadLine = Color(0xFF1D74B8),
    downloadFillTop = Color(0x553B9AE1),
    uploadLine = Color(0xFFD9643A),
    tileHotspotBg = Color(0xFFE3F0FC),
    tileHotspotBadge = Color(0xFF3B95E0),
    tileInterfacesBg = Color(0xFFFCEDE1),
    tileInterfacesBadge = Color(0xFFE68432),
    tileDhcpBg = Color(0xFFEAECFA),
    tileDhcpBadge = Color(0xFF646ED6),
    tileWirelessBg = Color(0xFFF5E8FA),
    tileWirelessBadge = Color(0xFFA855D1),
    tileIpBg = Color(0xFFE9ECFA),
    tileIpBadge = Color(0xFF5E68D4),
    tileSystemBg = Color(0xFFF5E7F7),
    tileSystemBadge = Color(0xFFAA54C9),
    hotspotScreenBg = Color(0xFFF3F6FB),
    hotspotCardBg = Color(0xFFFFFFFF),
    hotspotCardText = Color(0xFF121826),
    hotspotCardSubtext = Color(0xFF4B5568),
    hotspotActivePillBg = Color(0xFFD9EDFF),
    hotspotWifiOrange = Color(0xFFE47235),
    avatarActiveBg = Color(0xFFD9F3DC),
    avatarActiveIcon = Color(0xFF1E7B34),
    avatarVipBg = Color(0xFFF3DEF7),
    avatarVipIcon = Color(0xFF8A3599),
    avatarInactiveBg = Color(0xFFF8E3D5),
    avatarInactiveIcon = Color(0xFFB55D2B),
    badgeIp = Color(0xFF3B8CE0),
    badgeWireless = Color(0xFFE5822E),
    badgeHotspot = Color(0xFF9C54A1),
    badgeIdentity = Color(0xFF7B65CA),
    badgeSecurity = Color(0xFF8C5320),
    badgeBackup = Color(0xFF2E598F),
    badgeReboot = Color(0xFF2E6B3A),
    badgeTheme = Color(0xFF754E41),
    badgeAbout = Color(0xFF375C78),
)

val SakuraAnimeErlkimColors = ErlkimExtendedColors(
    onlineGreen = Color(0xFF168A57),
    onlineContainer = Color(0xFFD9F7E8),
    demoAmber = Color(0xFFC26410),
    demoContainer = Color(0xFFFFEBD4),
    offlineRed = Color(0xFFC9284C),
    offlineContainer = Color(0xFFFFDCE5),
    downloadLine = Color(0xFF6358C7),
    downloadFillTop = Color(0x557B70E0),
    uploadLine = Color(0xFFE0407B),
    tileHotspotBg = Color(0xFFFFE4EE),
    tileHotspotBadge = Color(0xFFD83B78),
    tileInterfacesBg = Color(0xFFFFE9E2),
    tileInterfacesBadge = Color(0xFFE66F59),
    tileDhcpBg = Color(0xFFECE9FF),
    tileDhcpBadge = Color(0xFF6358C7),
    tileWirelessBg = Color(0xFFFCE5F8),
    tileWirelessBadge = Color(0xFFB847A8),
    tileIpBg = Color(0xFFEBE8FF),
    tileIpBadge = Color(0xFF5D53C2),
    tileSystemBg = Color(0xFFFFE5F1),
    tileSystemBadge = Color(0xFFC93B82),
    hotspotScreenBg = SakuraBackground,
    hotspotCardBg = SakuraSurface,
    hotspotCardText = SakuraTextPrimary,
    hotspotCardSubtext = SakuraTextSecondary,
    hotspotActivePillBg = Color(0xFFFFD8E7),
    hotspotWifiOrange = SakuraTertiary,
    avatarActiveBg = Color(0xFFD9F7E8),
    avatarActiveIcon = Color(0xFF168A57),
    avatarVipBg = Color(0xFFFFD8EA),
    avatarVipIcon = Color(0xFFB82463),
    avatarInactiveBg = Color(0xFFF6E2EC),
    avatarInactiveIcon = Color(0xFF8C5A72),
    badgeIp = Color(0xFF7468D9),
    badgeWireless = Color(0xFFEB7C65),
    badgeHotspot = Color(0xFFE0528B),
    badgeIdentity = Color(0xFF9E6FD9),
    badgeSecurity = Color(0xFFB55D7E),
    badgeBackup = Color(0xFF5D55B8),
    badgeReboot = Color(0xFF288965),
    badgeTheme = Color(0xFFD83B78),
    badgeAbout = Color(0xFF6B5BC7),
)

val LocalErlkimColors = staticCompositionLocalOf { DarkErlkimColors }
