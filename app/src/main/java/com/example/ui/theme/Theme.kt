package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class NovaThemeMode {
    SYSTEM,
    DARK,
    LIGHT
}

private val NovaDarkColorScheme = darkColorScheme(
    primary = NovaCyan,
    onPrimary = NovaObsidian,
    primaryContainer = NovaCyanDeep,
    onPrimaryContainer = Color.White,
    secondary = NovaViolet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2E1E5C),
    onSecondaryContainer = NovaVioletSoft,
    tertiary = NovaEmerald,
    onTertiary = NovaObsidian,
    background = NovaObsidian,
    onBackground = Color(0xFFF1F5F9),
    surface = NovaMidnight,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = NovaSlateSurface,
    onSurfaceVariant = Color(0xFFB6C2D9),
    surfaceContainerHigh = NovaSlateElevated,
    outline = Color(0xFF334155),
    error = NovaCoral,
    onError = Color.White
)

private val NovaLightColorScheme = lightColorScheme(
    primary = NovaLightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6F0FF),
    onPrimaryContainer = Color(0xFF002B45),
    secondary = NovaLightSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE9FE),
    onSecondaryContainer = Color(0xFF2E1065),
    tertiary = Color(0xFF059669),
    onTertiary = Color.White,
    background = NovaLightBackground,
    onBackground = NovaLightOnSurface,
    surface = NovaLightSurface,
    onSurface = NovaLightOnSurface,
    surfaceVariant = NovaLightSurfaceVariant,
    onSurfaceVariant = Color(0xFF475569),
    surfaceContainerHigh = Color(0xFFE2E8F0),
    outline = NovaLightOutline,
    error = Color(0xFFDC2626),
    onError = Color.White
)

@Composable
fun NovaVoiceTheme(
    themeMode: NovaThemeMode = NovaThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        NovaThemeMode.DARK -> true
        NovaThemeMode.LIGHT -> false
        NovaThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (useDark) NovaDarkColorScheme else NovaLightColorScheme,
        typography = NovaTypography,
        content = content
    )
}
