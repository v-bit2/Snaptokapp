package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = NeoCoral,
    onPrimary = Color.White,
    primaryContainer = NeoCoral.copy(alpha = 0.2f),
    onPrimaryContainer = Color.White,
    secondary = NeoYellow,
    onSecondary = Color(0xFF141416),
    secondaryContainer = NeoCyan,
    onSecondaryContainer = Color(0xFF141416),
    tertiary = NeoMint,
    onTertiary = Color(0xFF141416),
    background = PaperBgDark,
    onBackground = DarkOnSurface,
    surface = PaperSurfaceDark,
    onSurface = DarkOnSurface,
    surfaceVariant = PaperSurfaceAltDark,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = PaperBorderDark,
    error = ErrorRed,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = NeoCoral,
    onPrimary = Color.White,
    primaryContainer = NeoCoral.copy(alpha = 0.15f),
    onPrimaryContainer = Color(0xFF141416),
    secondary = NeoYellow,
    onSecondary = Color(0xFF141416),
    secondaryContainer = NeoCyan,
    onSecondaryContainer = Color(0xFF141416),
    tertiary = NeoMint,
    onTertiary = Color(0xFF141416),
    background = PaperBgLight,
    onBackground = LightOnSurface,
    surface = PaperSurfaceLight,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = PaperBorderLight,
    error = ErrorRed,
    onError = Color.White
)

@Composable
fun SnapTokTheme(
    darkTheme: Boolean = false, // Default to iconic paper light neobrutalism
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Keep alias for compatibility
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    SnapTokTheme(darkTheme = darkTheme, content = content)
}
