package com.leon.weather.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF176B8C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0EFF4),
    onPrimaryContainer = Color(0xFF124B63),
    secondaryContainer = Color(0xFFE0EFF4),
    onSecondaryContainer = Color(0xFF124B63),
    background = Color(0xFFF5F7FA),
    surface = Color.White,
    surfaceVariant = Color(0xFFEBF0F4),
    onSurface = Color(0xFF172E3F),
    onSurfaceVariant = Color(0xFF617380),
    outline = Color(0xFF9FAFB9),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF94D5EF),
    primaryContainer = Color(0xFF204D63),
    onPrimaryContainer = Color(0xFFCEEFFF),
    secondaryContainer = Color(0xFF204D63),
    onSecondaryContainer = Color(0xFFCEEFFF),
    background = Color(0xFF101B25),
    surface = Color(0xFF192A36),
    surfaceVariant = Color(0xFF283B48),
    onSurface = Color(0xFFE2EDF3),
    onSurfaceVariant = Color(0xFFB1C2CE),
)

@Composable
fun WeatherTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
