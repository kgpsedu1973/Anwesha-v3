package com.example.ui.theme

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class AppThemeMode(val labelBn: String, val labelEn: String) {
    SYSTEM("সিস্টেম অনুযায়ী", "Follow System"),
    LIGHT("লাইট মোড", "Light Mode"),
    DARK("ডার্ক মোড", "Dark Mode")
}

enum class AppColorPalette(val labelBn: String, val labelEn: String, val previewColor: Color) {
    GREEN("সবুজ (প্রাথমিক বিদ্যালয়)", "Emerald Green", Color(0xFF00695C)),
    BLUE("ওশান ব্লু", "Ocean Blue", Color(0xFF0D47A1)),
    PURPLE("রয়্যাল পার্পল", "Royal Purple", Color(0xFF4A148C)),
    AMBER("সোনালী সানসেট", "Warm Amber", Color(0xFFBF360C)),
    CRIMSON("ক্রিমসন রোজ", "Crimson Rose", Color(0xFF880E4F))
}

object ThemePreferences {
    private const val PREFS_NAME = "anwesha_theme_prefs"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_COLOR_PALETTE = "color_palette"

    fun getSavedThemeMode(context: Context): AppThemeMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name
        return try {
            AppThemeMode.valueOf(name)
        } catch (e: Exception) {
            AppThemeMode.SYSTEM
        }
    }

    fun saveThemeMode(context: Context, mode: AppThemeMode) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun getSavedColorPalette(context: Context): AppColorPalette {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_COLOR_PALETTE, AppColorPalette.GREEN.name) ?: AppColorPalette.GREEN.name
        return try {
            AppColorPalette.valueOf(name)
        } catch (e: Exception) {
            AppColorPalette.GREEN
        }
    }

    fun saveColorPalette(context: Context, palette: AppColorPalette) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_COLOR_PALETTE, palette.name).apply()
    }
}

fun getColorScheme(palette: AppColorPalette, isDark: Boolean): ColorScheme {
    return when (palette) {
        AppColorPalette.GREEN -> if (isDark) {
            darkColorScheme(
                primary = GreenPrimaryDark,
                onPrimary = GreenDark,
                primaryContainer = GreenSecondary,
                onPrimaryContainer = Color(0xFFE0F2F1),
                secondary = AmberAccent,
                onSecondary = Color.Black,
                secondaryContainer = Color(0xFF332300),
                onSecondaryContainer = Color(0xFFFFE082),
                tertiary = RoseAccent,
                background = GreenDarkBg,
                surface = GreenDarkSurface,
                surfaceVariant = Color(0xFF243B37),
                onBackground = OnSurfaceDark,
                onSurface = OnSurfaceDark,
                onSurfaceVariant = Color(0xFFB0CCC7)
            )
        } else {
            lightColorScheme(
                primary = GreenPrimary,
                onPrimary = Color.White,
                primaryContainer = GreenPrimaryContainerLight,
                onPrimaryContainer = GreenDark,
                secondary = AmberAccent,
                onSecondary = Color.Black,
                secondaryContainer = Color(0xFFFFF8E1),
                onSecondaryContainer = Color(0xFF5D4037),
                tertiary = RoseAccent,
                background = SurfaceLight,
                surface = SurfaceContainerLight,
                surfaceVariant = Color(0xFFE8F5E9),
                onBackground = OnSurfaceLight,
                onSurface = OnSurfaceLight,
                onSurfaceVariant = Color(0xFF4A6572)
            )
        }

        AppColorPalette.BLUE -> if (isDark) {
            darkColorScheme(
                primary = BluePrimaryDark,
                onPrimary = BlueDark,
                primaryContainer = BlueSecondary,
                onPrimaryContainer = Color(0xFFE3F2FD),
                secondary = CyanAccent,
                onSecondary = Color.Black,
                tertiary = AmberAccent,
                background = BlueDarkBg,
                surface = BlueDarkSurface,
                surfaceVariant = Color(0xFF1E3250),
                onBackground = OnSurfaceDark,
                onSurface = OnSurfaceDark,
                onSurfaceVariant = Color(0xFFB3C5D7)
            )
        } else {
            lightColorScheme(
                primary = BluePrimary,
                onPrimary = Color.White,
                primaryContainer = BluePrimaryContainerLight,
                onPrimaryContainer = BlueDark,
                secondary = CyanAccent,
                onSecondary = Color.Black,
                secondaryContainer = Color(0xFFE0F7FA),
                tertiary = AmberAccent,
                background = SurfaceLight,
                surface = SurfaceContainerLight,
                surfaceVariant = Color(0xFFE3F2FD),
                onBackground = OnSurfaceLight,
                onSurface = OnSurfaceLight,
                onSurfaceVariant = Color(0xFF455A64)
            )
        }

        AppColorPalette.PURPLE -> if (isDark) {
            darkColorScheme(
                primary = PurplePrimaryDark,
                onPrimary = PurpleDark,
                primaryContainer = PurpleSecondary,
                onPrimaryContainer = Color(0xFFF3E5F5),
                secondary = RoseAccent,
                onSecondary = Color.White,
                tertiary = AmberAccent,
                background = PurpleDarkBg,
                surface = PurpleDarkSurface,
                surfaceVariant = Color(0xFF33204D),
                onBackground = OnSurfaceDark,
                onSurface = OnSurfaceDark,
                onSurfaceVariant = Color(0xFFCEB8E0)
            )
        } else {
            lightColorScheme(
                primary = PurplePrimary,
                onPrimary = Color.White,
                primaryContainer = PurplePrimaryContainerLight,
                onPrimaryContainer = PurpleDark,
                secondary = RoseAccent,
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFFCE4EC),
                tertiary = AmberAccent,
                background = SurfaceLight,
                surface = SurfaceContainerLight,
                surfaceVariant = Color(0xFFF3E5F5),
                onBackground = OnSurfaceLight,
                onSurface = OnSurfaceLight,
                onSurfaceVariant = Color(0xFF5D4037)
            )
        }

        AppColorPalette.AMBER -> if (isDark) {
            darkColorScheme(
                primary = AmberPrimaryDark,
                onPrimary = AmberDark,
                primaryContainer = AmberSecondary,
                onPrimaryContainer = Color(0xFFFBE9E7),
                secondary = AmberAccent,
                onSecondary = Color.Black,
                tertiary = Color(0xFFFF7043),
                background = AmberDarkBg,
                surface = AmberDarkSurface,
                surfaceVariant = Color(0xFF42271D),
                onBackground = OnSurfaceDark,
                onSurface = OnSurfaceDark,
                onSurfaceVariant = Color(0xFFDFC2B8)
            )
        } else {
            lightColorScheme(
                primary = AmberPrimary,
                onPrimary = Color.White,
                primaryContainer = AmberPrimaryContainerLight,
                onPrimaryContainer = AmberDark,
                secondary = AmberAccent,
                onSecondary = Color.Black,
                secondaryContainer = Color(0xFFFFF8E1),
                tertiary = Color(0xFFD84315),
                background = SurfaceLight,
                surface = SurfaceContainerLight,
                surfaceVariant = Color(0xFFFBE9E7),
                onBackground = OnSurfaceLight,
                onSurface = OnSurfaceLight,
                onSurfaceVariant = Color(0xFF5D4037)
            )
        }

        AppColorPalette.CRIMSON -> if (isDark) {
            darkColorScheme(
                primary = CrimsonPrimaryDark,
                onPrimary = CrimsonDark,
                primaryContainer = CrimsonSecondary,
                onPrimaryContainer = Color(0xFFFCE4EC),
                secondary = AmberAccent,
                onSecondary = Color.Black,
                tertiary = Color(0xFFFF80AB),
                background = CrimsonDarkBg,
                surface = CrimsonDarkSurface,
                surfaceVariant = Color(0xFF3F1D30),
                onBackground = OnSurfaceDark,
                onSurface = OnSurfaceDark,
                onSurfaceVariant = Color(0xFFDABECD)
            )
        } else {
            lightColorScheme(
                primary = CrimsonPrimary,
                onPrimary = Color.White,
                primaryContainer = CrimsonPrimaryContainerLight,
                onPrimaryContainer = CrimsonDark,
                secondary = AmberAccent,
                onSecondary = Color.Black,
                secondaryContainer = Color(0xFFFFF3E0),
                tertiary = Color(0xFFC2185B),
                background = SurfaceLight,
                surface = SurfaceContainerLight,
                surfaceVariant = Color(0xFFFCE4EC),
                onBackground = OnSurfaceLight,
                onSurface = OnSurfaceLight,
                onSurfaceVariant = Color(0xFF5D4037)
            )
        }
    }
}

@Composable
fun AnweshaTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    colorPalette: AppColorPalette = AppColorPalette.GREEN,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        AppThemeMode.SYSTEM -> systemDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> getColorScheme(colorPalette, isDark)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

