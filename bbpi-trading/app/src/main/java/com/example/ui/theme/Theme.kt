package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val FrostedGlassColorScheme = lightColorScheme(
    primary = AccentIndigo,
    onPrimary = GlassSurface,
    primaryContainer = AccentPurpleContainer,
    onPrimaryContainer = AccentPurpleText,
    secondary = BullishGreen,
    onSecondary = GlassSurface,
    background = FrostedBg,
    onBackground = TextPrimary,
    surface = GlassSurface,
    onSurface = TextPrimary,
    surfaceVariant = GlassCardBg,
    onSurfaceVariant = TextSecondary,
    outline = GlassBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = FrostedGlassColorScheme,
        typography = Typography,
        content = content
    )
}


