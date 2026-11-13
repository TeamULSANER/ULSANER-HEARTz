package ai.heartz.app.ui.components

import ai.heartz.app.ui.theme.Hz
import ai.heartz.app.ui.theme.colors
import ai.heartz.core.model.StoryStatus
import ai.heartz.core.model.StoryTone
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/* ---------- 브랜드 ---------- */

/** H에만 초록 그라데이션, 나머지 글자는 단색. */
@Composable
fun HeartzWordmark(modifier: Modifier = Modifier, size: TextUnit = 60.sp, onDark: Boolean = false) {
    val style = MaterialTheme.typography.displaySmall.copy(fontSize = size, lineHeight = size, fontWeight = FontWeight.Black, letterSpacing = (-2).sp)
    Row(modifier.semantics(mergeDescendants = true) { contentDescription = "HEARTz" }) {
        Text(
            "H", modifier = Modifier.alignByBaseline(), maxLines = 1,
            style = style.copy(brush = Brush.horizontalGradient(
                0f to Color(0xFF14CF4B), 0.35f to Color(0xFF8ED974), 0.88f to if (onDark) Color.White else Color(0xFF3CAD37),
            )),
        )
        Text("EARTz", modifier = Modifier.alignByBaseline(), style = style, color = if (onDark) Color.White else Hz.Ink, maxLines = 1)
    }
}

/** 앱 표시: 검은 네모에 초록에서 흰색으로 이어지는 Hz. */
@Composable
fun HzBadge(size: Dp = 64.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.24f)).background(Hz.Ink), contentAlignment = Alignment.Center) {
        Text(
            "Hz",
            style = MaterialTheme.typography.titleLarge.copy(
                brush = Brush.horizontalGradient(0f to Color(0xFF06D541), 0.42f to Color(0xFF9EE7A7), 0.65f to Color.White, 1f to Color.White),
                fontSize = (size.value * 0.55f).sp, lineHeight = (size.value * 0.6f).sp, fontWeight = FontWeight.Black,
            ),
        )
    }
}

/* ---------- 버튼 ---------- */

private val Pill = RoundedCornerShape(percent = 50)

@Composable
private fun ButtonBody(text: String, icon: ImageVector?, color: Color, style: TextStyle) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size((style.fontSize.value * 1.2f).dp))
            Spacer(Modifier.width(14.dp))
        }
        Text(text, color = color, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 초록 주 버튼 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    height: Dp = 84.dp,
    enabled: Boolean = true,
    style: TextStyle = MaterialTheme.typography.labelMedium,
) {
    Surface(
        onClick = onClick, enabled = enabled, shape = Pill, color = Hz.Green,
        modifier = modifier.heightIn(min = height).alpha(if (enabled) 1f else 0.45f),
    ) {
        Box(Modifier.padding(horizontal = 28.dp), contentAlignment = Alignment.Center) { ButtonBody(text, icon, Hz.Ink, style) }
    }
}

/** 테두리 버튼. [onCard]면 어두운 카드 위(흰 글자) */
@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    height: Dp = 84.dp,
    enabled: Boolean = true,
    onCard: Boolean = false,
    style: TextStyle = MaterialTheme.typography.labelMedium,
) {
    val fg = if (onCard) Hz.OnCard else Hz.Ink
    Surface(
        onClick = onClick, enabled = enabled, shape = Pill,
        color = Color.Transparent,
        border = BorderStroke(3.dp, if (onCard) Hz.CardLine else Hz.Ink),
        modifier = modifier.heightIn(min = height).alpha(if (enabled) 1f else 0.45f),
    ) {
        Box(Modifier.padding(horizontal = 28.dp), contentAlignment = Alignment.Center) { ButtonBody(text, icon, fg, style) }
    }
}

/** 회색 버튼(신고·취소 등 덜 중요한 동작) */
@Composable
fun SoftButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    height: Dp = 76.dp,
    enabled: Boolean = true,
    color: Color = Hz.Ink,
    bg: Color = Hz.Soft,
) {
    Surface(onClick = onClick, enabled = enabled, shape = Pill, color = bg, modifier = modifier.heightIn(min = height).alpha(if (enabled) 1f else 0.45f)) {
        Box(Modifier.padding(horizontal = 26.dp), contentAlignment = Alignment.Center) {
            ButtonBody(text, icon, color, MaterialTheme.typography.titleSmall)
        }
    }
}

/** 검은 버튼(대화상자의 확정) */
@Composable
fun InkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = 76.dp, enabled: Boolean = true) {
    Surface(onClick = onClick, enabled = enabled, shape = Pill, color = Hz.Ink, modifier = modifier.heightIn(min = height).alpha(if (enabled) 1f else 0.45f)) {
        Box(Modifier.padding(horizontal = 26.dp), contentAlignment = Alignment.Center) {
            ButtonBody(text, null, Hz.Bg, MaterialTheme.typography.titleSmall)
        }
    }
}

/** 동그란 아이콘 버튼(뒤로·작은 재생) */
@Composable
fun CircleIconButton(icon: ImageVector, description: String?, onClick: () -> Unit, size: Dp = 64.dp, bg: Color = Hz.Soft, fg: Color = Hz.Ink, enabled: Boolean = true) {
    Surface(onClick = onClick, enabled = enabled, shape = CircleShape, color = bg, modifier = Modifier.size(size).alpha(if (enabled) 1f else 0.4f)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, description, tint = fg, modifier = Modifier.size(size * 0.5f)) }
    }
}

/* ---------- 칩 ---------- */

enum class ChipStyle { Soft, Ink, OnCard, OutlineOnCard, GreenOnCard }

@Composable
fun Chip(text: String, style: ChipStyle = ChipStyle.Soft, icon: ImageVector? = null, dot: Color? = null, height: Dp = 52.dp, iconTint: Color? = null) {
    val (bg, fg) = when (style) {
        ChipStyle.Soft -> Hz.Soft to Hz.Ink
        ChipStyle.Ink -> Hz.Ink to Hz.Bg
        ChipStyle.OnCard -> Hz.CardChip to Hz.OnCard
        ChipStyle.OutlineOnCard -> Color.Transparent to Hz.OnCardMuted
        ChipStyle.GreenOnCard -> Hz.GreenOnCardBg to Hz.GreenOnCard
    }
    Row(
        Modifier.heightIn(min = height).clip(Pill).background(bg)
            .then(if (style == ChipStyle.OutlineOnCard) Modifier.border(2.dp, Hz.CardLine, Pill) else Modifier)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(10.dp))
        }
        if (icon != null) {
            Icon(icon, null, tint = iconTint ?: fg, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, color = fg, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

/* ---------- 사연 카드 ---------- */

/** 사연 색 표시(오른쪽 위 네모). 분위기를 뜻하지 않는 같은 그림을 쓴다 */
@Composable
fun ToneBadge(tone: StoryTone, size: Dp = 84.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.24f)).background(tone.colors().badge), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.GraphicEq, null, tint = Hz.Ink, modifier = Modifier.size(size * 0.52f))
    }
}

/** 어두운 카드. [glow]가 있으면 왼쪽 위에서 번지는 색 */
@Composable
fun DarkCard(
    modifier: Modifier = Modifier,
    glow: Color? = null,
    border: Color? = null,
    padding: PaddingValues = PaddingValues(start = 52.dp, end = 52.dp, top = 48.dp, bottom = 48.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(48.dp)
    Column(
        modifier
            .clip(shape)
            .background(Hz.Card)
            .drawBehind {
                if (glow != null) {
                    drawRect(
                        Brush.linearGradient(
                            0f to glow, 0.5f to glow.copy(alpha = 0f),
                            start = Offset.Zero, end = Offset(size.width * 0.85f, size.height * 0.55f),
                        ),
                    )
                }
            }
            .then(if (border != null) Modifier.border(4.dp, border, shape) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** "말로 답해도 돼요" 같은 초록 안내(어두운 카드 위) */
@Composable
fun VoiceHint(text: String, icon: ImageVector? = Icons.Rounded.GraphicEq) {
    Chip(text, ChipStyle.GreenOnCard, icon = icon, height = 54.dp)
}

/* ---------- 화면 틀 ---------- */

/** 화면 머리: (뒤로) 제목 … 오른쪽 */
@Composable
fun ScreenHeader(title: String, modifier: Modifier = Modifier, onBack: (() -> Unit)? = null, backEnabled: Boolean = true, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().heightIn(min = 80.dp).padding(bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "뒤로", onBack, size = 64.dp, enabled = backEnabled)
            Spacer(Modifier.width(20.dp))
        }
        Text(title, style = MaterialTheme.typography.headlineMedium.copy(fontSize = 44.sp, fontWeight = FontWeight.Black), color = Hz.Ink, modifier = Modifier.weight(1f), maxLines = 1)
        trailing()
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 28.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Hz.Ink, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** 회색 목록 줄 */
@Composable
fun SoftRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 22.dp),
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Hz.Soft)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** 목록의 둘째 줄: (초록 점) 글 */
@Composable
fun RowSubtitle(text: String, dot: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (dot) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(Hz.Green))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.bodySmall, color = Hz.InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 큰 동그라미 아이콘 + 제목 + 설명(빈 상태·권한 등) */
@Composable
fun IconMessage(
    icon: ImageVector,
    title: String,
    body: String = "",
    modifier: Modifier = Modifier,
    onCard: Boolean = false,
    circle: Color = if (onCard) Hz.CardChip else Hz.Soft,
    iconTint: Color = if (onCard) Hz.OnCard else Hz.InkMuted,
    circleSize: Dp = 156.dp,
) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(circleSize).clip(CircleShape).background(circle), contentAlignment = Alignment.Center) {
            if (icon == Icons.Rounded.Check) BoldCheck(circleSize * 0.46f, iconTint)
            else Icon(icon, null, tint = iconTint, modifier = Modifier.size(circleSize * 0.46f))
        }
        Spacer(Modifier.height(36.dp))
        Text(
            title, style = MaterialTheme.typography.headlineMedium, color = if (onCard) Hz.OnCard else Hz.Ink,
            textAlign = TextAlign.Center,
        )
        if (body.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = if (onCard) Hz.OnCardMuted else Hz.InkMuted, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun EmptyState(title: String, body: String = "", modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Hz.InkMuted, textAlign = TextAlign.Center)
        if (body.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = Hz.InkFaint, textAlign = TextAlign.Center)
        }
    }
}

/* ---------- 대화상자 ---------- */

/** 흰 대화상자: 제목, 내용, [취소][확정] */
@Composable
fun HzDialog(
    title: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmEnabled: Boolean = true,
    body: String? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.widthIn(max = 760.dp).fillMaxWidth().padding(horizontal = 32.dp)
                .clip(RoundedCornerShape(40.dp)).background(Hz.Bg).padding(40.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Hz.Ink)
            if (body != null) {
                Spacer(Modifier.height(14.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = Hz.InkMuted)
            }
            content()
            Spacer(Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SoftButton("취소", onDismiss, Modifier.weight(1f), height = 84.dp)
                InkButton(confirm, onConfirm, Modifier.weight(1f), height = 84.dp, enabled = confirmEnabled)
            }
        }
    }
}

/** 대화상자 안의 하나 고르기 목록 */
@Composable
fun RadioList(options: List<String>, selected: String?, onSelect: (String) -> Unit) {
    Column(Modifier.padding(top = 16.dp)) {
        options.forEachIndexed { i, o ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 76.dp).clickable { onSelect(o) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val on = o == selected
                Box(
                    Modifier.size(36.dp).clip(CircleShape).border(3.dp, if (on) Hz.Green else Hz.InkFaint, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { if (on) Box(Modifier.size(20.dp).clip(CircleShape).background(Hz.Green)) }
                Spacer(Modifier.width(22.dp))
                Text(o, style = MaterialTheme.typography.titleMedium, color = Hz.Ink)
            }
            if (i < options.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(Hz.Line))
        }
    }
}

/* ---------- 알림 띠 ---------- */

@Composable
fun HzToast(text: String, icon: ImageVector) {
    Row(
        Modifier.widthIn(max = 900.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Hz.Ink)
            .padding(horizontal = 28.dp, vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Hz.GreenOnCard, modifier = Modifier.size(34.dp))
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.titleSmall, color = Hz.OnCard)
    }
}

/* ---------- 공통 값 ---------- */

/** 화면용 한 문장은 끝 마침표를 뺀다(음성 문장은 그대로) */
fun String.forScreen(): String = if (count { it == '.' } == 1 && endsWith(".")) dropLast(1) else this

fun StoryStatus.label(): String = when (this) {
    // 나만 보기는 없앴다. 예전 데이터의 초안·나만 듣기는 "보내지 않음"으로 보여 주고 검수에 보낼 수 있게 한다
    StoryStatus.DRAFT -> "보내지 않음"
    StoryStatus.PRIVATE -> "보내지 않음"
    StoryStatus.PENDING_REVIEW -> "검수 중"
    StoryStatus.APPROVED -> "공개"
    StoryStatus.REJECTED -> "반려됨"
    StoryStatus.HIDDEN -> "숨김"
}

fun StoryStatus.dot(): Color = when (this) {
    StoryStatus.APPROVED -> Hz.Green
    StoryStatus.REJECTED -> Hz.Red
    else -> Hz.InkFaint
}

/** 상대 시간 (예: 3분 전) */
fun relativeTime(atMillis: Long, now: Long = System.currentTimeMillis()): String {
    val diff = now - atMillis
    return when {
        diff < 60_000 -> "방금"
        diff < 3_600_000 -> "${diff / 60_000}분 전"
        diff < 86_400_000 -> "${diff / 3_600_000}시간 전"
        else -> "${diff / 86_400_000}일 전"
    }
}

/** 오늘이면 "오늘 13:02", 아니면 "11월 9일 13:02" */
fun dayTime(atMillis: Long, now: Long = System.currentTimeMillis()): String {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = atMillis }
    val n = java.util.Calendar.getInstance().apply { timeInMillis = now }
    val hm = String.format(java.util.Locale.KOREA, "%02d:%02d", c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE))
    val today = c.get(java.util.Calendar.YEAR) == n.get(java.util.Calendar.YEAR) &&
        c.get(java.util.Calendar.DAY_OF_YEAR) == n.get(java.util.Calendar.DAY_OF_YEAR)
    return if (today) "오늘 $hm" else "${c.get(java.util.Calendar.MONTH) + 1}월 ${c.get(java.util.Calendar.DAY_OF_MONTH)}일 $hm"
}

/** 재생 시간 "0:12" */
fun clockLabel(ms: Long): String {
    val s = (ms + 500) / 1000
    return String.format(java.util.Locale.US, "%d:%02d", s / 60, s % 60)
}

/** 화면 가운데 놓이는 내용 폭 */
@Composable
fun Centered(maxWidth: Dp, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = maxWidth).fillMaxWidth(), content = content)
    }
}

/** 받침에 맞춘 목적격 조사: "은행나무길을", "사거리를" */
fun String.withObject(): String {
    val c = lastOrNull() ?: return this
    if (c !in '가'..'힣') return "${this}를"
    return if ((c - '가') % 28 != 0) "${this}을" else "${this}를"
}

/** 굵은 확인 표시(결과 화면의 큰 동그라미 안) */
@Composable
fun BoldCheck(size: Dp, color: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(size)) {
        val w = this.size.width
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.16f, w * 0.54f)
            lineTo(w * 0.40f, w * 0.76f)
            lineTo(w * 0.86f, w * 0.26f)
        }
        drawPath(
            path, color,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                w * 0.15f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round,
            ),
        )
    }
}

/** 기존 테스트 계정의 화면용 짧은 이름: "은행나무 (시드 작성자)" → "은행나무". 원본 데이터는 유지한다. */
fun String.shortName(): String = removeSuffix(" (테스트 청취자)").removeSuffix(" (시드 작성자)").removeSuffix(" (테스트)").ifBlank { this }
