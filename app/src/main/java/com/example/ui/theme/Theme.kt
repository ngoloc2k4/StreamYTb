package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = StreamRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3E1219),
    onPrimaryContainer = Color(0xFFFFD9DF),
    secondary = StreamAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2B1B40),
    onSecondaryContainer = Color(0xFFE9D5FF),
    tertiary = StreamCyan,
    onTertiary = Color.Black,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFF2D3348)
)

private val LightColorScheme = lightColorScheme(
    primary = StreamRed,
    onPrimary = Color.White,
    secondary = StreamAccent,
    onSecondary = Color.White,
    tertiary = StreamCyan,
    background = LightBackground,
    onBackground = Color(0xFF1E293B),
    surface = LightSurface,
    onSurface = Color(0xFF1E293B),
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF64748B)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Default to immersive dark mode for media streaming
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
