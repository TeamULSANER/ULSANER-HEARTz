package ai.heartz.app.ui.drive

import ai.heartz.app.ui.components.BoldCheck
import ai.heartz.app.ui.components.DarkCard
import ai.heartz.app.ui.components.OutlineButton
import ai.heartz.app.ui.components.PrimaryButton
import ai.heartz.app.ui.components.clockLabel
import ai.heartz.app.ui.theme.Hz
import ai.heartz.core.model.VoiceMode
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.material3.Surface
import androidx.compose.material.icons.rounded.Stop
import ai.heartz.app.ui.components.ChipStyle
import ai.heartz.app.ui.components.Chip
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * 지나면서 남기기 화면(주행 중에도 보임). 큰 글자 한두 줄과 큰 버튼만 둔다.
 * 말로 답할 수 있고, 같은 답을 버튼으로도 고를 수 있다.
 */
@Composable
fun RoadsideScreen(
    ui: RoadsideUi,
    onFinish: () -> Unit,
    onDiscard: () -> Unit,
    onSubmit: () -> Unit,
    onRerecord: () -> Unit,
    onVoice: (VoiceMode) -> Unit,
    onDismiss: () -> Unit,
    location: @Composable () -> Unit = {},
) {
    Box(Modifier.fillMaxSize().background(Hz.Bg), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 960.dp).fillMaxSize().padding(horizontal = 38.dp, vertical = 28.dp)) {
            // 오른쪽 위: 녹음을 시작한 곳에서 1 km 안의 가장 가까운 랜드마크, 없으면 "위치 정보 없음"
            var placeKnown by remember { mutableStateOf(false) }
            var placeName by remember { mutableStateOf<String?>(null) }
            when (ui) {
                is RoadsideUi.Preparing -> { placeKnown = true; placeName = ui.placeName }
                is RoadsideUi.Recording -> { placeKnown = true; placeName = ui.placeName }
                is RoadsideUi.Asking -> { placeKnown = true; placeName = ui.placeName }
                else -> Unit
            }
            DriveHeader {
                if (placeKnown) {
                    Chip(
                        placeName ?: "위치 정보 없음", ChipStyle.Soft,
                        icon = if (placeName != null) Icons.Rounded.Place else Icons.Rounded.LocationOff,
                        iconTint = if (placeName != null) Hz.Green else Hz.InkFaint, height = 60.dp,
                    )
                } else {
                    location()
                }
            }
            Spacer(Modifier.height(28.dp))
            val glow = when (ui) {
                is RoadsideUi.Preparing, is RoadsideUi.Recording -> Hz.RedGlow
                is RoadsideUi.Asking, RoadsideUi.Paused -> Hz.GreenGlow
                else -> null
            }
            DarkCard(Modifier.weight(1f).fillMaxWidth(), glow = glow, padding = PaddingValues(52.dp)) {
                when (ui) {
                    is RoadsideUi.Preparing -> RecordPanel(recording = false, startedAtMillis = null, maxMs = 60_000, onFinish, onDiscard)
                    is RoadsideUi.Recording -> RecordPanel(recording = true, startedAtMillis = ui.startedAtMillis, maxMs = ui.maxMs, onFinish, onDiscard)
                    RoadsideUi.Paused -> Asked("녹음했어요", null, null, "잠시 멈췄어요", AskKind.DELIVER, onSubmit, onRerecord, onVoice, onDiscard)
                    is RoadsideUi.Asking -> Asked(
                        "녹음했어요", ui.durationMs.takeIf { it > 0 }, ui.placeName, askTitle(ui), ui.kind,
                        onSubmit, onRerecord, onVoice, onDiscard,
                    )
                    RoadsideUi.Saving -> Saving()
                    is RoadsideUi.Finished -> Finished(ui, onDismiss)
                    RoadsideUi.Idle -> Unit
                }
            }
            if (ui !is RoadsideUi.Finished) {
                Spacer(Modifier.height(24.dp))
                Text(
                    "공개 전에 운영자가 먼저 들어요", style = MaterialTheme.typography.bodyMedium, color = Hz.InkMuted,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun askTitle(ui: RoadsideUi.Asking): String {
    val again = ui.question.startsWith("잘 못 들었어요")
    val q = when (ui.kind) {
        AskKind.DELIVER -> "이 사연을\n전달할까요?"
        AskKind.VOICE -> "변조 목소리로\n전달할까요?"
    }
    return if (again) "잘 못 들었어요\n" + q.replace("\n", " ") else q
}

@Composable
private fun TopLine(icon: ImageVector?, dot: Color?, title: String, right: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (dot != null) Box(Modifier.size(22.dp).clip(CircleShape).background(dot))
        if (icon != null) Icon(icon, null, tint = if (icon == Icons.Rounded.Check) Hz.GreenOnCard else Hz.OnCard, modifier = Modifier.size(38.dp))
        Spacer(Modifier.width(14.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = Hz.OnCard, modifier = Modifier.weight(1f))
        if (right != null) Text(right, style = MaterialTheme.typography.titleMedium, color = Hz.OnCardMuted)
    }
}

/** 녹음을 시작한 곳 */
@Composable
private fun PlaceLine(name: String?, detail: Boolean) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(if (name != null) Icons.Rounded.Place else Icons.Rounded.LocationOff, null, tint = Hz.GreenOnCard, modifier = Modifier.size(40.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                when {
                    name == null -> "장소를 아직 못 정했어요"
                    detail -> "${name}에 남겨요"
                    else -> name
                },
                style = MaterialTheme.typography.headlineSmall, color = Hz.OnCard,
            )
            if (detail) {
                Spacer(Modifier.height(6.dp))
                Text(
                    if (name != null) "녹음을 시작한 곳으로 정해져요" else "주차 후 장소를 고를 수 있어요",
                    style = MaterialTheme.typography.bodyLarge, color = Hz.OnCardMuted,
                )
            }
        }
    }
}

/**
 * 주행 중 녹음 화면. 주차 중 남기기의 녹음 단계와 같은 구성(시간 / 큰 원 / 한 줄 안내)을 어두운 카드 위에 둔다.
 * 안내 음성이 끝나기 전([recording]=false)에는 초록 마이크, 녹음 중에는 빨간 멈춤(누르면 끝내기).
 */
@Composable
private fun ColumnScope.RecordPanel(
    recording: Boolean,
    startedAtMillis: Long?,
    maxMs: Long,
    onFinish: () -> Unit,
    onDiscard: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAtMillis) {
        while (startedAtMillis != null) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val elapsed = startedAtMillis?.let { (now - it).coerceAtLeast(0) } ?: 0L
    Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (recording) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(Hz.Red))
                Spacer(Modifier.width(14.dp))
                Text("녹음 중", style = MaterialTheme.typography.titleLarge, color = Hz.OnCard)
            }
            Spacer(Modifier.height(12.dp))
        }
        Text(
            buildAnnotatedString {
                append(clockLabel(elapsed))
                withStyle(SpanStyle(color = Hz.OnCardFaint, fontSize = 48.sp)) { append(" / ${clockLabel(maxMs)}") }
            },
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 116.sp, lineHeight = 128.sp, letterSpacing = (-3).sp),
            color = Hz.OnCard,
        )
        Spacer(Modifier.height(40.dp))
        Surface(
            onClick = onFinish, enabled = recording, shape = CircleShape,
            color = if (recording) Hz.Red else Hz.Green, modifier = Modifier.size(220.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (recording) Icons.Rounded.Stop else Icons.Rounded.Mic,
                    if (recording) "녹음 멈추기" else null,
                    tint = if (recording) Hz.OnCard else Hz.Ink, modifier = Modifier.size(104.dp),
                )
            }
        }
        Spacer(Modifier.height(32.dp))
        Text(
            if (recording) "누르면 녹음을 멈춰요" else "안내가 끝나면 녹음을 시작해요",
            style = MaterialTheme.typography.titleMedium, color = Hz.OnCardMuted,
        )
    }
    OutlineButton("버리기", onDiscard, Modifier.fillMaxWidth(), Icons.Rounded.Delete, 104.dp, onCard = true, style = MaterialTheme.typography.labelMedium)
}

/** 녹음을 마치고 묻는 중(전달·목소리) */
@Composable
private fun ColumnScope.Asked(
    top: String,
    durationMs: Long?,
    placeName: String?,
    title: String,
    kind: AskKind,
    onSubmit: () -> Unit,
    onRerecord: () -> Unit,
    onVoice: (VoiceMode) -> Unit,
    onDiscard: () -> Unit,
) {
    TopLine(Icons.Rounded.Check, null, top, durationMs?.let(::clockLabel))
    Spacer(Modifier.height(28.dp))
    PlaceLine(placeName, detail = false)
    Spacer(Modifier.height(36.dp))
    Text(title, style = MaterialTheme.typography.displaySmall.copy(fontSize = 72.sp, lineHeight = 84.sp), color = Hz.OnCard)
    Spacer(Modifier.weight(1f))
    val h = 124.dp
    val style = MaterialTheme.typography.labelLarge
    when (kind) {
        AskKind.DELIVER -> {
            PrimaryButton("보내기", onSubmit, Modifier.fillMaxWidth(), Icons.AutoMirrored.Rounded.Send, h, style = style)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                OutlineButton("다시 녹음", onRerecord, Modifier.weight(1f), Icons.Rounded.Refresh, h, onCard = true, style = style)
                OutlineButton("버리기", onDiscard, Modifier.weight(1f), Icons.Rounded.Delete, h, onCard = true, style = style)
            }
        }
        AskKind.VOICE -> {
            PrimaryButton("내 목소리로", { onVoice(VoiceMode.ORIGINAL) }, Modifier.fillMaxWidth(), Icons.Rounded.RecordVoiceOver, h, style = style)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                OutlineButton("변조 목소리", { onVoice(VoiceMode.MODULATED) }, Modifier.weight(1f), Icons.Rounded.GraphicEq, h, onCard = true, style = style)
                OutlineButton("버리기", onDiscard, Modifier.weight(1f), Icons.Rounded.Delete, h, onCard = true, style = style)
            }
        }
    }
}

@Composable
private fun ColumnScope.Saving() {
    Spacer(Modifier.weight(1f))
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = Hz.Green, strokeWidth = 8.dp, modifier = Modifier.size(120.dp))
        Spacer(Modifier.height(40.dp))
        Text("보내는 중", style = MaterialTheme.typography.headlineLarge, color = Hz.OnCard)
    }
    Spacer(Modifier.weight(1f))
}

private data class ResultLook(val icon: ImageVector, val good: Boolean, val title: String, val body: String)

private fun look(ui: RoadsideUi.Finished): ResultLook {
    val parts = ui.message.split(". ", limit = 2)
    val head = parts[0].removeSuffix(".")
    val rest = parts.getOrNull(1)?.let { if (it.endsWith(".")) it else "$it." } ?: ""
    return when (ui.kind) {
        FinishKind.SENT -> ResultLook(Icons.Rounded.Check, true, "검수에 보냈어요", "")
        FinishKind.SENT_MODULATED -> ResultLook(Icons.Rounded.Check, true, "변조 목소리로\n검수에 보냈어요", "")
        FinishKind.DRAFT_NO_ANSWER -> ResultLook(Icons.Rounded.Inbox, false, "답을 듣지 못해\n초안으로 보관했어요", "주차 후 내 사연함에서 확인해 주세요.")
        FinishKind.DRAFT_NO_PLACE -> ResultLook(Icons.Rounded.LocationOff, false, "장소를 찾지 못해\n초안으로 보관했어요", "주차 후 내 사연함에서 장소를 골라 주세요.")
        FinishKind.DRAFT -> ResultLook(Icons.Rounded.Inbox, false, head, rest)
        FinishKind.DISCARDED -> ResultLook(Icons.Rounded.Delete, false, head, rest.ifBlank { "녹음은 남기지 않았어요." })
        FinishKind.NO_SPEECH -> ResultLook(Icons.Rounded.MicOff, false, "말소리가 들리지 않아\n녹음을 멈췄어요", "녹음은 남기지 않았어요.")
        FinishKind.TOO_SHORT -> ResultLook(Icons.Rounded.Timer, false, head, "녹음은 남기지 않았어요.")
        FinishKind.NEED_MIC -> ResultLook(Icons.Rounded.MicOff, false, "마이크 권한이 필요해요", "주차 후 설정에서 허용해 주세요.")
        FinishKind.CANNOT_START -> ResultLook(
            Icons.Rounded.Block, false, head,
            when {
                "꺼져" in ui.message -> "주차 후 설정에서 켤 수 있어요."
                "로그인" in ui.message -> "주차 후 설정에서 계정을 골라 주세요."
                else -> rest
            },
        )
        FinishKind.CANCELLED -> ResultLook(Icons.Rounded.Close, false, head, "녹음은 남기지 않았어요.")
        FinishKind.FAILED -> ResultLook(Icons.Rounded.Warning, false, head, rest)
    }
}

@Composable
private fun ColumnScope.Finished(ui: RoadsideUi.Finished, onDismiss: () -> Unit) {
    LaunchedEffect(ui) {
        delay(6_000)
        onDismiss()
    }
    val l = look(ui)
    Spacer(Modifier.weight(1f))
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(156.dp).clip(CircleShape).background(if (l.good) Hz.Green else Hz.CardChip),
            contentAlignment = Alignment.Center,
        ) {
            if (l.good) BoldCheck(76.dp, Hz.Ink)
            else Icon(l.icon, null, tint = Hz.OnCard, modifier = Modifier.size(80.dp))
        }
        Spacer(Modifier.height(40.dp))
        Text(l.title, style = MaterialTheme.typography.headlineLarge.copy(fontSize = 58.sp, lineHeight = 70.sp), color = Hz.OnCard, textAlign = TextAlign.Center)
        if (l.body.isNotBlank()) {
            Spacer(Modifier.height(18.dp))
            Text(l.body, style = MaterialTheme.typography.bodyLarge, color = Hz.OnCardMuted, textAlign = TextAlign.Center)
        }
    }
    Spacer(Modifier.weight(1f))
    PrimaryButton("확인", onDismiss, Modifier.fillMaxWidth(), null, 124.dp, style = MaterialTheme.typography.labelLarge)
}
