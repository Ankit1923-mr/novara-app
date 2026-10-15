package com.example.novara.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = TerracottaPrimary,
    onPrimary = SurfaceWhite,
    primaryContainer = TerracottaLight,
    onPrimaryContainer = TextSlatePrimary,
    secondary = OliveSecondary,
    onSecondary = SurfaceWhite,
    secondaryContainer = OliveLight,
    onSecondaryContainer = TextSlatePrimary,
    tertiary = OchreAccent,
    onTertiary = TextSlatePrimary,
    background = BackgroundCream,
    onBackground = TextSlatePrimary,
    surface = SurfaceWhite,
    onSurface = TextSlatePrimary,
    surfaceVariant = SurfaceVariantWarm,
    onSurfaceVariant = TextSlateSecondary
)

private val DarkColorScheme = darkColorScheme(
    primary = TerracottaPrimary,
    onPrimary = SurfaceWhite,
    primaryContainer = TerracottaDark,
    onPrimaryContainer = SurfaceWhite,
    secondary = OliveLight,
    onSecondary = TextSlatePrimary,
    secondaryContainer = OliveSecondary,
    onSecondaryContainer = SurfaceWhite,
    tertiary = WarmOrange,
    onTertiary = TextSlatePrimary,
    background = TextSlatePrimary,
    onBackground = BackgroundCream,
    surface = Color(0xFF24344D),
    onSurface = BackgroundCream,
    surfaceVariant = Color(0xFF2D3F5B),
    onSurfaceVariant = Color(0xFFE2E8F0)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
