package com.jpntech.bizassistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.jpntech.bizassistant.R

/**
 * 제어실 화면 위에서 비서가 말을 거는 느낌.
 * 바탕은 검정이 아니라 또렷한 남청색, 청록은 비서의 목소리와 살아 있는 표시에만 쓴다.
 * 호박색은 주의, 빨강은 긴급, 초록은 돈과 완료.
 */
object BizColors {
    val Ground = Color(0xFF0B1A2E)
    val Panel = Color(0xFF112640)
    val Line = Color(0xFF1E3A5C)
    val LineSoft = Color(0xFF15304D)
    val Text = Color(0xFFE8EFF7)
    val Muted = Color(0xFF8DA2BA)
    val Dim = Color(0xFF6F86A3)
    val Signal = Color(0xFF5CD6E6)
    val SignalSoft = Color(0x265CD6E6)
    val OnSignal = Color(0xFF062030)
    val Amber = Color(0xFFF2B544)
    val AmberSoft = Color(0x24F2B544)
    val Red = Color(0xFFFF6B6B)
    val RedSoft = Color(0x24FF6B6B)
    val Green = Color(0xFF4CD38A)
    val GreenSoft = Color(0x244CD38A)
    val Chip = Color(0x248DA2BA)

    // 이전 화면 코드와의 호환 이름
    val Surface get() = Panel
    val Border get() = Line
    val Ink get() = Text
    val Navy get() = Signal
    val NavySoft get() = SignalSoft
    val Danger get() = Red
    val DangerSoft get() = RedSoft
    val Warn get() = Amber
    val WarnSoft get() = AmberSoft
    val Ok get() = Green
    val OkSoft get() = Color(0x244CD38A)
    val Teal get() = Color(0xFF9AD0B8)
    val TealSoft get() = Color(0x249AD0B8)
}

val Pretendard = FontFamily(
    Font(R.font.pretendard_light, FontWeight.Light),
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
)

private const val TNUM = "tnum"

private val BizTypography = Typography(
    displayMedium = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Light, fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-1).sp, fontFeatureSettings = TNUM),
    displaySmall = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Light, fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = (-0.8).sp, fontFeatureSettings = TNUM),
    headlineSmall = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Light, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.5).sp, fontFeatureSettings = TNUM),
    titleLarge = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 25.sp),
    bodyLarge = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp, fontFeatureSettings = TNUM),
    bodySmall = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Normal, fontSize = 12.5.sp, lineHeight = 18.sp, fontFeatureSettings = TNUM),
    labelLarge = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, fontFeatureSettings = TNUM),
    labelSmall = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 11.5.sp, lineHeight = 16.sp, fontFeatureSettings = TNUM),
)

private val DarkColors = darkColorScheme(
    primary = BizColors.Signal,
    onPrimary = BizColors.OnSignal,
    primaryContainer = BizColors.Panel,
    onPrimaryContainer = BizColors.Signal,
    secondary = BizColors.Amber,
    secondaryContainer = BizColors.Panel,
    onSecondaryContainer = BizColors.Amber,
    error = BizColors.Red,
    errorContainer = BizColors.Panel,
    onErrorContainer = BizColors.Red,
    background = BizColors.Ground,
    onBackground = BizColors.Text,
    surface = BizColors.Ground,
    onSurface = BizColors.Text,
    surfaceVariant = BizColors.Panel,
    onSurfaceVariant = BizColors.Muted,
    surfaceContainer = BizColors.Panel,
    surfaceContainerHigh = BizColors.Panel,
    surfaceContainerHighest = BizColors.Line,
    outline = BizColors.Line,
    outlineVariant = BizColors.LineSoft,
)

@Composable
fun BizTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, typography = BizTypography, content = content)
}
