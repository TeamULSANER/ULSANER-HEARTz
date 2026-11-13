package ai.heartz.app.ui.theme

import ai.heartz.core.model.StoryTone
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * HEARTz v8 팔레트(사용자 시안 heartz_v8_screens 기준): 흰 바탕, 검은 글자, 초록 강조, 사연은 검은 카드.
 * 사연 카드는 사연 색(파랑·주황·노랑·초록)을 왼쪽 위에서 번지는 빛과 오른쪽 위 표시에만 쓴다.
 */
object Hz {
    val Bg = Color(0xFFFFFFFF)
    /** 대기 카드·목록 줄·회색 버튼 */
    val Soft = Color(0xFFF4F5F7)
    val SoftHigh = Color(0xFFEBEDF0)
    val Line = Color(0xFFE4E6EA)
    val Ink = Color(0xFF111111)
    val InkMuted = Color(0xFF5F646C)
    val InkFaint = Color(0xFF9A9FA7)

    /** 사연·지나면서 남기기 카드(어두운 면) */
    val Card = Color(0xFF121212)
    val CardChip = Color(0xFF2A2C30)
    val CardLine = Color(0xFF45484E)
    val OnCard = Color(0xFFFFFFFF)
    val OnCardMuted = Color(0xFFB9BCC2)
    val OnCardFaint = Color(0xFF7E8289)

    val Green = Color(0xFF1DB954)
    val GreenSoft = Color(0xFFE3F6EA)
    /** 어두운 카드 위 "말로 답해도 돼요" 같은 안내 */
    val GreenOnCard = Color(0xFF22C55E)
    val GreenOnCardBg = Color(0xFF16301F)
    val Logo = Color(0xFF34CC15)

    val Red = Color(0xFFE5243B)
    val RedGlow = Color(0xFF5A1219)
    val GreenGlow = Color(0xFF163921)
    val Warn = Color(0xFFE59A1A)
}

/** 사연 색. [badge]: 오른쪽 위 표시·목록 얼굴 바탕, [glow]: 카드 왼쪽 위에서 번지는 빛 */
@Immutable
data class ToneColors(val badge: Color, val glow: Color)

fun StoryTone.colors(): ToneColors = when (this) {
    StoryTone.BLUE -> ToneColors(Color(0xFF5C8CF2), Color(0xFF3C5991))
    StoryTone.ORANGE -> ToneColors(Color(0xFFFF8A3D), Color(0xFF99562C))
    StoryTone.YELLOW -> ToneColors(Color(0xFFFFC92E), Color(0xFF846721))
    StoryTone.GREEN -> ToneColors(Color(0xFF8EC3A7), Color(0xFF4D6B5B))
}

/** 사연의 화면 색(감정 분석 전까지 사연마다 임의로 고정) */
val ai.heartz.core.model.Story.tone: StoryTone get() = StoryTone.of(id)

/**
 * 글꼴: 기기에 Pretendard 파일(/product/fonts, Pleos 이미지에 포함)이 있으면 그것을 쓰고(시안과 같은 글꼴),
 * 없으면 Pleos 시스템 이름 글꼴 "pretendard"(에뮬레이터에서는 AstaSans), 그것도 없으면 기본 글꼴.
 */
private val pretendardFiles = listOf(
    FontWeight.Normal to "Regular", FontWeight.Medium to "Medium", FontWeight.SemiBold to "SemiBold",
    FontWeight.Bold to "Bold", FontWeight.ExtraBold to "ExtraBold", FontWeight.Black to "Black",
).map { (w, n) -> w to java.io.File("/product/fonts/Pretendard-$n.otf") }

private val device = DeviceFontFamilyName("pretendard")
val HzFont: FontFamily = runCatching {
    if (pretendardFiles.all { it.second.canRead() }) FontFamily(pretendardFiles.map { (w, f) -> Font(f, w) })
    else null
}.getOrNull() ?: FontFamily(
    Font(device, FontWeight.Normal),
    Font(device, FontWeight.Medium),
    Font(device, FontWeight.SemiBold),
    Font(device, FontWeight.Bold),
    Font(device, FontWeight.ExtraBold),
    Font(device, FontWeight.Black),
)

private val scheme = lightColorScheme(
    primary = Hz.Green,
    onPrimary = Hz.Ink,
    primaryContainer = Hz.GreenSoft,
    onPrimaryContainer = Hz.Ink,
    secondary = Hz.Ink,
    onSecondary = Hz.Bg,
    tertiary = Hz.InkMuted,
    background = Hz.Bg,
    onBackground = Hz.Ink,
    surface = Hz.Bg,
    onSurface = Hz.Ink,
    surfaceVariant = Hz.Soft,
    onSurfaceVariant = Hz.InkMuted,
    surfaceTint = Color.Transparent,
    surfaceContainer = Hz.Bg,
    surfaceContainerHigh = Hz.Bg,
    surfaceContainerHighest = Hz.Soft,
    outline = Hz.Line,
    outlineVariant = Hz.Line,
    error = Hz.Red,
)

private fun s(size: Int, line: Int, w: FontWeight, track: Double = 0.0) =
    TextStyle(fontFamily = HzFont, fontSize = size.sp, lineHeight = line.sp, fontWeight = w, letterSpacing = track.sp)

/**
 * 차량 화면 글자(1dp ≈ 1px, 약 160 dpi). 시안의 크기를 따르되 본문 26sp 이상.
 * 큰 제목은 굵게(ExtraBold) 좁은 자간, 본문은 Medium.
 */
private val typography = Typography(
    // 시안 heartz_v8_screens의 글자 폭으로 잰 크기(1dp ≈ 1px)
    displayLarge = s(88, 100, FontWeight.ExtraBold, -2.0),
    displayMedium = s(76, 88, FontWeight.ExtraBold, -1.6),
    displaySmall = s(60, 72, FontWeight.ExtraBold, -1.2),
    headlineLarge = s(46, 58, FontWeight.ExtraBold, -0.9),
    headlineMedium = s(42, 54, FontWeight.ExtraBold, -0.8),
    headlineSmall = s(36, 46, FontWeight.ExtraBold, -0.6),
    titleLarge = s(30, 40, FontWeight.ExtraBold, -0.4),
    titleMedium = s(29, 38, FontWeight.ExtraBold, -0.3),
    titleSmall = s(27, 36, FontWeight.Bold, -0.2),
    bodyLarge = s(29, 40, FontWeight.Medium),
    bodyMedium = s(27, 38, FontWeight.Medium),
    bodySmall = s(26, 34, FontWeight.Medium),
    labelLarge = s(42, 50, FontWeight.ExtraBold, -0.6),
    labelMedium = s(32, 40, FontWeight.ExtraBold, -0.4),
    labelSmall = s(26, 32, FontWeight.Bold),
)

@Composable
fun HeartzTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
