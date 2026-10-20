package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NeoCoral
import com.example.ui.theme.NeoCyan
import com.example.ui.theme.NeoYellow
import com.example.ui.viewmodel.AppTab

/**
 * Paper Neobrutalism Floating Navigation Dock
 */
@Composable
fun SnapTokBottomNavBar(
    currentTab: AppTab,
    historyCount: Int,
    onTabSelected: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        val shape = RoundedCornerShape(14.dp)
        val shadowOffset = 3.5.dp

        // Neobrutalist Hard Paper Shadow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .offset(x = shadowOffset, y = shadowOffset)
                .clip(shape)
                .background(MaterialTheme.colorScheme.outline)
        )

        // Dock Foreground
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .border(2.dp, MaterialTheme.colorScheme.outline, shape)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            NeoNavTabItem(
                selected = currentTab == AppTab.HOME,
                onClick = { onTabSelected(AppTab.HOME) },
                iconActive = Icons.Filled.Download,
                iconInactive = Icons.Outlined.Download,
                label = "Downloader",
                activeColor = NeoCoral,
                testTag = "nav_tab_home",
                modifier = Modifier.weight(1f)
            )

            NeoNavTabItem(
                selected = currentTab == AppTab.HISTORY,
                onClick = { onTabSelected(AppTab.HISTORY) },
                iconActive = Icons.Filled.History,
                iconInactive = Icons.Outlined.History,
                label = "Library",
                badgeCount = historyCount,
                activeColor = NeoYellow,
                activeTextColor = Color(0xFF141416),
                testTag = "nav_tab_history",
                modifier = Modifier.weight(1f)
            )

            NeoNavTabItem(
                selected = currentTab == AppTab.SETTINGS,
                onClick = { onTabSelected(AppTab.SETTINGS) },
                iconActive = Icons.Filled.Settings,
                iconInactive = Icons.Outlined.Settings,
                label = "Settings",
                activeColor = NeoCyan,
                activeTextColor = Color(0xFF141416),
                testTag = "nav_tab_settings",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun NeoNavTabItem(
    selected: Boolean,
    onClick: () -> Unit,
    iconActive: ImageVector,
    iconInactive: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    badgeCount: Int = 0,
    activeColor: Color = NeoCoral,
    activeTextColor: Color = Color.White,
    testTag: String? = null
) {
    val interactionSource = remember { MutableInteractionSource() }

    val bgColor by animateColorAsState(
        targetValue = if (selected) activeColor else Color.Transparent,
        animationSpec = tween(durationMillis = 100),
        label = "TabBg"
    )

    Box(
        modifier = modifier
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .then(
                if (selected) {
                    Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                } else Modifier
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .then(
                if (testTag != null) Modifier.testTag(testTag) else Modifier
            )
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box {
                Icon(
                    imageVector = if (selected) iconActive else iconInactive,
                    contentDescription = label,
                    tint = if (selected) activeTextColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp)
                )

                if (badgeCount > 0) {
                    Box(
                        modifier = Modifier
                            .offset(x = 10.dp, y = (-4).dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(NeoCoral)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                            .padding(horizontal = 3.dp, vertical = 0.5.dp)
                    ) {
                        Text(
                            text = if (badgeCount > 99) "99+" else "$badgeCount",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                }
            }

            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
                color = if (selected) activeTextColor else MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.2.sp
            )
        }
    }
}
