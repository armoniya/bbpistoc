package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val TradingTerminalColorScheme = darkColorScheme(
    primary = CaseYellow,
    onPrimary = BgDark,
    primaryContainer = AccentPurpleContainer,
    onPrimaryContainer = AccentPurpleText,
    secondary = BullishYellow,
    onSecondary = BgDark,
    background = BgDark,
    onBackground = TextColdWhite,
    surface = GlassSurface,
    onSurface = TextColdWhite,
    surfaceVariant = PanelBg,
    onSurfaceVariant = TextSecondary,
    outline = PanelBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TradingTerminalColorScheme,
        typography = Typography,
        content = content
    )
}


