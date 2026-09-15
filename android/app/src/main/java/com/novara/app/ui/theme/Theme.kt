package com.novara.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BgPrimary = Color(0xFF07090C)
val BgSurface = Color(0xFF0F1318)
val BgElevated = Color(0xFF161C24)
val AccentEmerald = Color(0xFF10B981)
val AccentCyan = Color(0xFF06B6D4)
val AccentGold = Color(0xFFF59E0B)
val TextPrimary = Color(0xFFF1F5F9)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)
val BorderColor = Color(0xFF1E293B)
val ErrorColor = Color(0xFFEF4444)

private val DarkColorScheme = darkColorScheme(
    primary = AccentEmerald,
    onPrimary = Color.White,
    secondary = AccentCyan,
    onSecondary = Color.White,
    tertiary = AccentGold,
    background = BgPrimary,
    onBackground = TextPrimary,
    surface = BgSurface,
    onSurface = TextPrimary,
    error = ErrorColor
)

@Composable
fun NovaraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
