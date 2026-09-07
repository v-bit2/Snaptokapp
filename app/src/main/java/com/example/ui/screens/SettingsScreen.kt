package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CoralPrimary
import com.example.ui.theme.TealAccent

@Composable
fun SettingsScreen(
    preferHd: Boolean,
    isDarkTheme: Boolean,
    autoFetchClipboard: Boolean,
    onTogglePreferHd: () -> Unit,
    onToggleTheme: () -> Unit,
    onToggleAutoFetchClipboard: () -> Unit,
    onClearCache: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header
        Column {
            Text(
                text = "Settings",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Preferences & App Configurations",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Section: Download Preferences
        SettingsSection(title = "Download Preferences") {
            SettingsToggleRow(
                icon = Icons.Default.Hd,
                iconTint = CoralPrimary,
                title = "High Definition (HD) Priority",
                subtitle = "Download highest available resolution without watermark",
                checked = preferHd,
                onCheckedChange = { onTogglePreferHd() },
                testTag = "setting_toggle_hd"
            )

            SettingsToggleRow(
                icon = Icons.Default.ContentPaste,
                iconTint = TealAccent,
                title = "Auto-Detect Clipboard Links",
                subtitle = "Suggest download when a TikTok link is copied",
                checked = autoFetchClipboard,
                onCheckedChange = { onToggleAutoFetchClipboard() },
                testTag = "setting_toggle_clipboard"
            )

            SettingsClickableRow(
                icon = Icons.Default.Folder,
                iconTint = Color(0xFFFFB703),
                title = "Save Location",
                subtitle = "Device Gallery > Movies / SnapTok",
                onClick = {
                    Toast.makeText(context, "Saved under Movies/SnapTok in your Gallery", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Section: Appearance
        SettingsSection(title = "Appearance") {
            SettingsToggleRow(
                icon = Icons.Default.DarkMode,
                iconTint = Color(0xFFA855F7),
                title = "Dark Theme",
                subtitle = "Sleek high-contrast obsidian dark palette",
                checked = isDarkTheme,
                onCheckedChange = { onToggleTheme() },
                testTag = "setting_toggle_theme"
            )
        }

        // Section: Storage & Maintenance
        SettingsSection(title = "Storage & Maintenance") {
            SettingsClickableRow(
                icon = Icons.Default.CleaningServices,
                iconTint = Color(0xFF06D6A0),
                title = "Clear Temporary Cache",
                subtitle = "Free up network thumbnail cache",
                onClick = {
                    onClearCache()
                    Toast.makeText(context, "App cache cleared successfully!", Toast.LENGTH_SHORT).show()
                },
                testTag = "setting_clear_cache"
            )
        }

        // Section: About & Community
        SettingsSection(title = "About SnapTok") {
            SettingsClickableRow(
                icon = Icons.Default.Star,
                iconTint = Color(0xFFFFC107),
                title = "Rate SnapTok",
                subtitle = "Support us with 5 stars on Google Play",
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}"))
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "SnapTok v1.0 • Ready for Play Store", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            SettingsClickableRow(
                icon = Icons.Default.Share,
                iconTint = TealAccent,
                title = "Share SnapTok with Friends",
                subtitle = "Help others save TikTok videos without watermark",
                onClick = {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, "Download TikTok videos without watermark using SnapTok!")
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Share SnapTok"))
                }
            )

            SettingsClickableRow(
                icon = Icons.Default.Lock,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                title = "Privacy & Terms",
                subtitle = "No login required • All processing is strictly client-side",
                onClick = {
                    Toast.makeText(context, "SnapTok does not collect personal data.", Toast.LENGTH_LONG).show()
                }
            )

            SettingsClickableRow(
                icon = Icons.Default.Info,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                title = "Version",
                subtitle = "1.0.0 (Production Release)",
                onClick = {}
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = CoralPrimary
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp)
                ),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(vertical = 2.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String = ""
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, fontSize = 10.sp, lineHeight = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = CoralPrimary
            )
        )
    }
}

@Composable
private fun SettingsClickableRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String = ""
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, fontSize = 10.sp, lineHeight = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
