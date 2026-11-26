package com.erlkim.mikrotik.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.erlkim.mikrotik.R

enum class ErlkimTab(
    val route: String,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    DASHBOARD("dashboard", R.string.nav_dashboard, Icons.Rounded.GridView, Icons.Outlined.GridView),
    USERS("users", R.string.nav_users, Icons.Rounded.Group, Icons.Outlined.Group),
    HOTSPOT("hotspot", R.string.nav_hotspot, Icons.Rounded.WifiTethering, Icons.Outlined.WifiTethering),
    SETTINGS("settings", R.string.nav_settings, Icons.Rounded.Settings, Icons.Outlined.Settings),
}

@Composable
fun ErlkimBottomBar(
    currentTab: ErlkimTab,
    onSelectTab: (ErlkimTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    NavigationBar(
        modifier = modifier.testTag("erlkim_bottom_bar"),
        containerColor = cs.surfaceContainer,
        tonalElevation = 3.dp,
        windowInsets = WindowInsets.navigationBars,
    ) {
        ErlkimTab.entries.forEach { tab ->
            val selected = tab == currentTab
            val label = stringResource(tab.labelRes)
            NavigationBarItem(
                selected = selected,
                onClick = { onSelectTab(tab) },
                modifier = Modifier.testTag("nav_tab_${tab.route}"),
                icon = {
                    Icon(
                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = label,
                        modifier = Modifier.size(23.dp),
                    )
                },
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = if (tab == ErlkimTab.DASHBOARD) cs.onSecondaryContainer else cs.onPrimaryContainer,
                    selectedTextColor = cs.onSurface,
                    indicatorColor = if (tab == ErlkimTab.DASHBOARD) cs.secondaryContainer else cs.primaryContainer,
                    unselectedIconColor = cs.onSurfaceVariant,
                    unselectedTextColor = cs.onSurfaceVariant,
                ),
            )
        }
    }
}

@Composable
fun ErlkimNavRail(
    currentTab: ErlkimTab,
    onSelectTab: (ErlkimTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    NavigationRail(
        modifier = modifier.testTag("erlkim_nav_rail"),
        containerColor = cs.surfaceContainer,
    ) {
        ErlkimTab.entries.forEach { tab ->
            val selected = tab == currentTab
            val label = stringResource(tab.labelRes)
            NavigationRailItem(
                selected = selected,
                onClick = { onSelectTab(tab) },
                modifier = Modifier.testTag("rail_tab_${tab.route}"),
                icon = {
                    Icon(
                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = label,
                    )
                },
                label = { Text(text = label, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = cs.onPrimaryContainer,
                    selectedTextColor = cs.onSurface,
                    indicatorColor = cs.primaryContainer,
                    unselectedIconColor = cs.onSurfaceVariant,
                    unselectedTextColor = cs.onSurfaceVariant,
                ),
            )
        }
    }
}
