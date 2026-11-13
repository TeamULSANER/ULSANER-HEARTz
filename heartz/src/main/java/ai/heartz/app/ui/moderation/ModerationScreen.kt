package ai.heartz.app.ui.moderation

import ai.heartz.app.platform.audio.PlaybackUi
import ai.heartz.app.ui.components.Chip
import ai.heartz.app.ui.components.ChipStyle
import ai.heartz.app.ui.components.CircleIconButton
import ai.heartz.app.ui.components.EmptyState
import ai.heartz.app.ui.components.HzDialog
import ai.heartz.app.ui.components.OutlineButton
import ai.heartz.app.ui.components.PrimaryButton
import ai.heartz.app.ui.components.RadioList
import ai.heartz.app.ui.components.ScreenHeader
import ai.heartz.app.ui.components.SoftRow
import ai.heartz.app.ui.components.ToneBadge
import ai.heartz.app.ui.components.clockLabel
import ai.heartz.app.ui.components.relativeTime
import ai.heartz.app.ui.components.shortName
import ai.heartz.app.ui.library.ParkedPadding
import ai.heartz.app.ui.library.letterTitle
import ai.heartz.app.ui.library.placeList
import ai.heartz.app.ui.theme.Hz
import ai.heartz.app.ui.theme.tone
import ai.heartz.core.model.Place
import ai.heartz.core.model.Story
import ai.heartz.core.model.VoiceMode
import ai.heartz.core.moderation.TranscriptStatus
import ai.heartz.core.service.ModerationAction
import ai.heartz.core.service.ModerationOverview
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val rejectReasons = listOf("장소와 관련 없음", "음질이 나쁨", "갑작스러운 큰 소리", "장난·반복 음성", "개인정보 노출", "괴롭힘·불쾌감")

/** 운영자 검수: 원본 음성을 듣고 승인·반려, 공개 중 사연 숨김, 신고 확인 */
@Composable
fun ModerationScreen(
    overview: ModerationOverview?,
    loading: Boolean,
    error: String?,
    places: Map<String, Place>,
    playback: PlaybackUi,
    busy: Boolean,
    onRefresh: () -> Unit,
    onPlay: (Story) -> Unit,
    onPause: () -> Unit,
    onModerate: (String, ModerationAction, String?) -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    var rejectFor by remember { mutableStateOf<Story?>(null) }
    var rejectReason by remember { mutableStateOf<String?>(null) }
    var hideFor by remember { mutableStateOf<Story?>(null) }

    // 확인할 표시(검수 보조)가 있는 사연을 위로. 우선순위만 바꾸고 승인·반려는 사람이 한다
    val pending = overview?.pending.orEmpty().sortedByDescending { it.assist?.flags?.isNotEmpty() == true }
    val approved = overview?.approved.orEmpty()
    val reports = overview?.reports.orEmpty()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = ParkedPadding) {
        item {
            ScreenHeader("검수") {
                CircleIconButton(Icons.Rounded.Refresh, "새로고침", onRefresh, size = 64.dp, enabled = !loading)
                Spacer(Modifier.width(14.dp))
                Box(Modifier.size(64.dp).clip(CircleShape).background(Hz.Ink), contentAlignment = Alignment.Center) {
                    Text("운영", style = MaterialTheme.typography.labelSmall, color = Hz.Bg)
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("검수 대기 ${pending.size}", "공개 중 ${approved.size}", "신고 ${reports.size}").forEachIndexed { i, t ->
                    Surface(onClick = { tab = i }, shape = RoundedCornerShape(percent = 50), color = if (tab == i) Hz.Ink else Hz.Soft, modifier = Modifier.height(64.dp)) {
                        Box(Modifier.padding(horizontal = 26.dp), contentAlignment = Alignment.Center) {
                            Text(t, style = MaterialTheme.typography.titleSmall, color = if (tab == i) Hz.Bg else Hz.Ink)
                        }
                    }
                }
            }
            if (tab == 0) {
                Spacer(Modifier.height(16.dp))
                Text("확인할 표시가 있는 사연을 위로 올렸어요", style = MaterialTheme.typography.bodyMedium, color = Hz.InkMuted)
            }
            Spacer(Modifier.height(20.dp))
        }
        when {
            overview == null && loading -> item { EmptyState("불러오는 중") }
            overview == null && error != null -> item { EmptyState("검수 목록을 열 수 없어요", error) }
            tab == 0 -> {
                if (pending.isEmpty()) item { EmptyState("대기 중인 사연이 없어요") }
                items(pending, key = { it.id }) { s ->
                    PendingItem(s, places, playback, busy, onPlay, onPause,
                        onApprove = { onModerate(s.id, ModerationAction.APPROVE, null) },
                        onReject = { rejectFor = s; rejectReason = null })
                }
            }
            tab == 1 -> {
                if (approved.isEmpty()) item { EmptyState("공개 중인 사연이 없어요") }
                items(approved, key = { it.id }) { s ->
                    SoftRow(Modifier.padding(bottom = 14.dp)) {
                        ToneBadge(s.tone, 76.dp)
                        Spacer(Modifier.width(22.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.placeList(places), style = MaterialTheme.typography.titleMedium, color = Hz.Ink, maxLines = 1)
                            Text(
                                listOfNotNull(s.authorName.shortName(), clockLabel(s.durationMs), flagSummary(s)).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = Hz.InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        PlayCircle(s, playback, onPlay, onPause)
                        Spacer(Modifier.width(14.dp))
                        OutlineButton("숨기기", { hideFor = s }, height = 68.dp, enabled = !busy, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
            else -> {
                if (reports.isEmpty()) item { EmptyState("접수된 신고가 없어요") }
                items(reports, key = { it.id }) { r ->
                    val s = approved.firstOrNull { it.id == r.storyId }
                    SoftRow(Modifier.padding(bottom = 14.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(r.reason, style = MaterialTheme.typography.titleMedium, color = Hz.Ink)
                            Text(
                                (s?.letterTitle(places) ?: "공개 목록에 없는 사연") + " · ${relativeTime(r.createdAtMillis)}",
                                style = MaterialTheme.typography.bodySmall, color = Hz.InkMuted,
                            )
                        }
                        if (s != null) {
                            PlayCircle(s, playback, onPlay, onPause)
                            Spacer(Modifier.width(14.dp))
                            OutlineButton("숨기기", { hideFor = s }, height = 68.dp, enabled = !busy, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        }
    }

    rejectFor?.let { s ->
        HzDialog(
            title = "반려 사유를 골라 주세요", confirm = "반려", confirmEnabled = rejectReason != null,
            onConfirm = { onModerate(s.id, ModerationAction.REJECT, rejectReason); rejectFor = null },
            onDismiss = { rejectFor = null },
        ) { RadioList(rejectReasons, rejectReason) { rejectReason = it } }
    }
    hideFor?.let { s ->
        HzDialog(
            title = "이 사연을 숨길까요?", confirm = "숨기기", body = "공개 목록에서 바로 빠져요.",
            onConfirm = { onModerate(s.id, ModerationAction.HIDE, "운영자 숨김"); hideFor = null },
            onDismiss = { hideFor = null },
        )
    }
}

private fun flagSummary(s: Story): String? =
    s.assist?.flags?.firstOrNull()?.let { f -> f.kind.label + (f.atMs?.let { " ${it / 1000}초" } ?: "") }

@Composable
private fun PlayCircle(s: Story, playback: PlaybackUi, onPlay: (Story) -> Unit, onPause: () -> Unit) {
    val playing = playback.storyId == s.id && playback.isPlaying
    CircleIconButton(
        if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "멈춤" else "원본 듣기",
        { if (playing) onPause() else onPlay(s) }, size = 68.dp, bg = Hz.Bg,
    )
}

/** 검수 대기 한 건: 사연 색 / 장소 / 원본 듣기 / 받아쓰기 / 확인할 표시 / 승인·반려 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PendingItem(
    s: Story,
    places: Map<String, Place>,
    playback: PlaybackUi,
    busy: Boolean,
    onPlay: (Story) -> Unit,
    onPause: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    val playing = playback.storyId == s.id && playback.isPlaying
    Column(Modifier.fillMaxWidth().padding(bottom = 18.dp).clip(RoundedCornerShape(36.dp)).background(Hz.Soft).padding(28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ToneBadge(s.tone, 76.dp)
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(s.placeList(places), style = MaterialTheme.typography.titleLarge, color = Hz.Ink, maxLines = 1)
                Text(
                    listOfNotNull(
                        s.authorName.shortName(), clockLabel(s.durationMs),
                        if (s.recordedWhileDriving) "지나면서 남김" else null,
                        "제출 ${relativeTime(s.submittedAtMillis ?: s.createdAtMillis)}",
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = Hz.InkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            Surface(onClick = { if (playing) onPause() else onPlay(s) }, shape = RoundedCornerShape(percent = 50), color = Hz.Bg, modifier = Modifier.height(68.dp)) {
                Row(Modifier.padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Hz.Ink, modifier = Modifier.size(34.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (playing) "멈춤" else "원본 듣기", style = MaterialTheme.typography.titleSmall, color = Hz.Ink)
                }
            }
        }
        if (s.voiceMode == VoiceMode.MODULATED) {
            Spacer(Modifier.height(14.dp))
            Chip("변조 목소리 · 받아쓰기를 안내 목소리로 읽어 줘요", ChipStyle.Soft, dot = Hz.Warn)
        }
        Spacer(Modifier.height(16.dp))
        val a = s.assist
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Hz.Bg).padding(22.dp)) {
            Text(
                when {
                    a == null -> "받아쓰기 없음"
                    a.transcript.status == TranscriptStatus.DONE -> "받아쓰기 “${a.transcript.text}”"
                    a.transcript.status == TranscriptStatus.NO_SPEECH -> "받아쓰기: 말소리를 찾지 못했어요"
                    else -> "받아쓰기 실패 (${a.transcript.reason ?: "원인 미상"})"
                },
                style = MaterialTheme.typography.bodyMedium, color = if (a == null) Hz.InkFaint else Hz.Ink,
            )
        }
        Spacer(Modifier.height(14.dp))
        val flags = a?.flags.orEmpty()
        if (flags.isEmpty()) {
            Chip("확인할 표시 없음", ChipStyle.Soft, height = 48.dp)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                flags.forEach { f -> Chip("! " + f.kind.label + (f.atMs?.let { " · ${it / 1000}초" } ?: ""), ChipStyle.Ink, height = 48.dp) }
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PrimaryButton("승인", onApprove, Modifier.weight(1f), null, 84.dp, enabled = !busy)
            OutlineButton("반려", onReject, Modifier.weight(1f), null, 84.dp, enabled = !busy)
        }
    }
}
