package com.example.pathsense.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

// High contrast color scheme for accessibility
private val HighContrastColorScheme = darkColorScheme(
    primary = HighContrastYellow,
    onPrimary = HighContrastBlack,
    primaryContainer = HighContrastYellow,
    onPrimaryContainer = HighContrastBlack,
    secondary = HighContrastCyan,
    onSecondary = HighContrastBlack,
    secondaryContainer = HighContrastCyan,
    onSecondaryContainer = HighContrastBlack,
    tertiary = HighContrastOrange,
    onTertiary = HighContrastBlack,
    tertiaryContainer = HighContrastOrange,
    onTertiaryContainer = HighContrastBlack,
    background = HighContrastBlack,
    onBackground = HighContrastWhite,
    surface = HighContrastBlack,
    onSurface = HighContrastWhite,
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = HighContrastWhite,
    error = Color(0xFFFF6B6B),
    onError = HighContrastBlack,
    outline = HighContrastYellow,
    outlineVariant = HighContrastWhite
)

/**
 * Accessibility settings available throughout the app via CompositionLocal.
 */
data class AccessibilitySettings(
    val highContrast: Boolean = false,
    val largeText: Boolean = false
)

val LocalAccessibilitySettings = staticCompositionLocalOf { AccessibilitySettings() }

/**
 * PathSense theme with accessibility support.
 *
 * @param darkTheme Whether to use dark theme
 * @param dynamicColor Whether to use dynamic colors (Android 12+)
 * @param highContrast Whether to use high contrast colors for accessibility
 * @param largeText Whether to use larger text for accessibility
 * @param content The composable content
 */
@Composable
fun PathSenseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    highContrast: Boolean = false,
    largeText: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // High contrast takes priority
        highContrast -> HighContrastColorScheme

        // Dynamic color on Android 12+
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        // Standard dark/light themes
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // Select typography based on accessibility settings
    val typography = if (largeText) LargeTypography else Typography

    // Provide accessibility settings to child composables
    val accessibilitySettings = AccessibilitySettings(
        highContrast = highContrast,
        largeText = largeText
    )

    CompositionLocalProvider(LocalAccessibilitySettings provides accessibilitySettings) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}