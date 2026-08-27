package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = GreenPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2F1),
    onPrimaryContainer = GreenDark,
    secondary = AmberAccent,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFFFFF8E1),
    tertiary = RoseAccent,
    background = SurfaceLight,
    surface = SurfaceContainerLight,
    onBackground = OnSurfaceLight,
    onSurface = OnSurfaceLight
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF80CBC4),
    onPrimary = GreenDark,
    primaryContainer = GreenSecondary,
    onPrimaryContainer = Color(0xFFE0F2F1),
    secondary = AmberAccent,
    onSecondary = Color.Black,
    tertiary = Color(0xFFF48FB1),
    background = GreenDarkBg,
    surface = SurfaceContainerDark,
    onBackground = OnSurfaceDark,
    onSurface = OnSurfaceDark
)

@Composable
fun AnweshaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep consistent brand color palette
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
