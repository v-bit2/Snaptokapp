package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CoralPrimary,
    onPrimary = Color.White,
    primaryContainer = CoralVariant,
    onPrimaryContainer = Color.White,
    secondary = TealAccent,
    onSecondary = Color(0xFF00363A),
    secondaryContainer = TealDark,
    onSecondaryContainer = Color.White,
    background = DarkCanvas,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    error = ErrorRed,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = CoralPrimary,
    onPrimary = Color.White,
    primaryContainer = CoralVariant,
    onPrimaryContainer = Color.White,
    secondary = TealDark,
    onSecondary = Color.White,
    secondaryContainer = TealAccent,
    onSecondaryContainer = Color(0xFF00363A),
    background = LightCanvas,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    error = ErrorRed,
    onError = Color.White
)

@Composable
fun SnapTokTheme(
    darkTheme: Boolean = true, // Default to sleek dark mode as requested
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
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    SnapTokTheme(darkTheme = darkTheme, content = content)
}
