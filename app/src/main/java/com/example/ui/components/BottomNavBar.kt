package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CoralPrimary
import com.example.ui.viewmodel.AppTab

@Composable
fun SnapTokBottomNavBar(
    currentTab: AppTab,
    historyCount: Int,
    onTabSelected: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .height(58.dp)
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            ),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 4.dp,
        windowInsets = WindowInsets(0.dp)
    ) {
        // Home Tab
        NavigationBarItem(
            modifier = Modifier.testTag("nav_tab_home"),
            selected = currentTab == AppTab.HOME,
            onClick = { onTabSelected(AppTab.HOME) },
            icon = {
                Icon(
                    imageVector = if (currentTab == AppTab.HOME) Icons.Filled.Download else Icons.Outlined.Download,
                    contentDescription = "Home",
                    modifier = Modifier.size(20.dp)
                )
            },
            label = { Text("Downloader", fontSize = 10.sp, fontWeight = if (currentTab == AppTab.HOME) FontWeight.Bold else FontWeight.Normal) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.White,
                selectedTextColor = CoralPrimary,
                indicatorColor = CoralPrimary,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )

        // History Tab
        NavigationBarItem(
            modifier = Modifier.testTag("nav_tab_history"),
            selected = currentTab == AppTab.HISTORY,
            onClick = { onTabSelected(AppTab.HISTORY) },
            icon = {
                BadgedBox(
                    badge = {
                        if (historyCount > 0) {
                            Badge(
                                containerColor = CoralPrimary,
                                contentColor = Color.White
                            ) {
                                Text("$historyCount", fontSize = 9.sp)
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (currentTab == AppTab.HISTORY) Icons.Filled.History else Icons.Outlined.History,
                        contentDescription = "History",
                        modifier = Modifier.size(20.dp)
                    )
                }
            },
            label = { Text("History", fontSize = 10.sp, fontWeight = if (currentTab == AppTab.HISTORY) FontWeight.Bold else FontWeight.Normal) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.White,
                selectedTextColor = CoralPrimary,
                indicatorColor = CoralPrimary,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )

        // Settings Tab
        NavigationBarItem(
            modifier = Modifier.testTag("nav_tab_settings"),
            selected = currentTab == AppTab.SETTINGS,
            onClick = { onTabSelected(AppTab.SETTINGS) },
            icon = {
                Icon(
                    imageVector = if (currentTab == AppTab.SETTINGS) Icons.Filled.Settings else Icons.Outlined.Settings,
                    contentDescription = "Settings",
                    modifier = Modifier.size(20.dp)
                )
            },
            label = { Text("Settings", fontSize = 10.sp, fontWeight = if (currentTab == AppTab.SETTINGS) FontWeight.Bold else FontWeight.Normal) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.White,
                selectedTextColor = CoralPrimary,
                indicatorColor = CoralPrimary,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}
