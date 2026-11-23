package com.jpntech.bizassistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 시안의 색. 남색 기준색, 긴급 빨강, 대금 초록, 주의 황갈색. */
object BizColors {
    val Navy = Color(0xFF1D4F91)
    val NavySoft = Color(0xFFE7EEF8)
    val Ink = Color(0xFF141A22)
    val Muted = Color(0xFF5B6572)
    val Border = Color(0xFFE3E6EA)
    val Ground = Color(0xFFF4F5F7)
    val Surface = Color(0xFFFFFFFF)
    val Danger = Color(0xFFB42318)
    val DangerSoft = Color(0xFFFCEBE9)
    val Warn = Color(0xFF8A5A00)
    val WarnSoft = Color(0xFFFFF4D6)
    val Ok = Color(0xFF1B6B3A)
    val OkSoft = Color(0xFFE3F3E8)
    val Teal = Color(0xFF0F6E6E)
    val TealSoft = Color(0xFFE0F2F1)
    val Chip = Color(0xFFF1F3F5)
}

private val LightColors = lightColorScheme(
    primary = BizColors.Navy,
    onPrimary = Color.White,
    primaryContainer = BizColors.NavySoft,
    onPrimaryContainer = BizColors.Navy,
    secondary = BizColors.Teal,
    secondaryContainer = BizColors.TealSoft,
    onSecondaryContainer = BizColors.Teal,
    error = BizColors.Danger,
    errorContainer = BizColors.DangerSoft,
    onErrorContainer = BizColors.Danger,
    background = BizColors.Ground,
    onBackground = BizColors.Ink,
    surface = BizColors.Surface,
    onSurface = BizColors.Ink,
    surfaceVariant = BizColors.Chip,
    onSurfaceVariant = BizColors.Muted,
    outline = BizColors.Border,
    outlineVariant = BizColors.Border,
)

@Composable
fun BizTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, content = content)
}
