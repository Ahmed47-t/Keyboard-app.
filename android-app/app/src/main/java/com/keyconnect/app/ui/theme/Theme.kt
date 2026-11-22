package com.keyconnect.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BgDeep = Color(0xFF070B14)
val BgSurface = Color(0xFF111827)
val BgKeyNormal = Color(0xFF1E293B)
val BgKeySpecial = Color(0xFF0F172A)
val BgKeyPressed = Color(0xFF38BDF8)
val AccentCyan = Color(0xFF38BDF8)
val AccentEmerald = Color(0xFF10B981)
val AccentIndigo = Color(0xFF818CF8)
val TextPrimary = Color(0xFFF8FAFC)
val TextMuted = Color(0xFF94A3B8)

private val KeyConnectDarkColors = darkColorScheme(
    primary = AccentCyan,
    onPrimary = Color(0xFF04131F),
    secondary = AccentEmerald,
    tertiary = AccentIndigo,
    background = BgDeep,
    surface = BgSurface,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = TextMuted,
)

@Composable
fun KeyConnectTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KeyConnectDarkColors,
        content = content
    )
}
