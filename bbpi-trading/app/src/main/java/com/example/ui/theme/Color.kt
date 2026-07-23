package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// BBPI Trading brand palette - mirrors the CSS custom properties in
// trading-dashboard.html (--case, --bg, --scr, --tx, --up, --dn, ...) so the
// Android app and widget look like the same product as the web dashboard.
val CaseYellow = Color(0xFFF5C518)
val CaseYellowDim = Color(0xFFB8930E)

val BgDark = Color(0xFF06040C)
val ScreenTop = Color(0xFF171029)

val PanelBg = Color(0xFF241537)
val PanelBorder = Color(0x24AA96D7)
val GridLine = Color(0x1FB4A0DC)

val TextColdWhite = Color(0xFFF1EEF7)
val TextSecondary = Color(0xFFA29CB4)
val TextMuted = Color(0xFF645C74)

val BullishYellow = Color(0xFFFFD24A)
val BearishWhite = Color(0xFFECE8DC)

val IndicatorK = Color(0xFFF5C518)
val IndicatorD = Color(0xFFD9D5C9)

// Glass/accent tokens referenced directly by some components - kept as
// separate names for call-site clarity, but resolved to the same brand
// palette above rather than a generic light "Frosted Glass" theme.
val GlassSurface = Color(0xFF140B20)
val GlassCardBg = PanelBg
val GlassCardWhite = PanelBg
val GlassBorder = PanelBorder
val GlassBorderSubtle = Color(0x1AAA96D7)

val FrostedBg = BgDark
val FrostedHeaderTop = ScreenTop

val AccentIndigo = CaseYellow
val AccentPurpleContainer = Color(0x33F5C518)
val AccentPurpleText = CaseYellow
val AccentBluePill = CaseYellow
val AccentBluePillText = BgDark

val TextPrimary = TextColdWhite
val BullishGreen = BullishYellow
val BearishRed = BearishWhite
