package com.airbombal.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF060B09)
val Panel = Color(0xFF0E1714)
val PanelHigh = Color(0xFF13221D)
val Line = Color(0xFF1F352E)
val Neon = Color(0xFF3BFFA9)
val Cyan = Color(0xFF52E5FF)
val Danger = Color(0xFFFF5C6C)
val Amber = Color(0xFFFFC857)
val Mist = Color(0xFFE6FFF5)
val MistDim = Color(0xFF7FA396)
val OnNeon = Color(0xFF00251A)
val Mono = FontFamily.Monospace

private val Scheme = darkColorScheme(
    primary = Neon,
    onPrimary = OnNeon,
    primaryContainer = Color(0xFF0B3A2A),
    onPrimaryContainer = Neon,
    secondary = Cyan,
    onSecondary = Color(0xFF00202A),
    background = Ink,
    onBackground = Mist,
    surface = Panel,
    onSurface = Mist,
    surfaceVariant = PanelHigh,
    onSurfaceVariant = MistDim,
    error = Danger,
    onError = Color(0xFF2A0006),
    errorContainer = Color(0xFF3A0A10),
    onErrorContainer = Danger,
    outline = Line,
    outlineVariant = Line,
)

private val AppTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        letterSpacing = 0.5.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
    ),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelSmall = TextStyle(
        fontFamily = Mono,
        fontSize = 10.sp,
        letterSpacing = 0.8.sp,
    ),
)

@Composable
fun AirbombTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = AppTypography, content = content)
}
