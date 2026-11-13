package ai.heartz.app.ui.library

import ai.heartz.app.data.PendingDraft
import ai.heartz.app.platform.audio.PlaybackUi
import ai.heartz.app.startOfTodayMillis
import ai.heartz.app.ui.components.ProfileTile
import ai.heartz.app.ui.components.DarkCard
import ai.heartz.app.ui.components.EmptyState
import ai.heartz.app.ui.components.HzDialog
import ai.heartz.app.ui.components.OutlineButton
import ai.heartz.app.ui.components.PrimaryButton
import ai.heartz.app.ui.components.RadioList
import ai.heartz.app.ui.components.RowSubtitle
import ai.heartz.app.ui.components.ScreenHeader
import ai.heartz.app.ui.components.SectionHeader
import ai.heartz.app.ui.components.SoftButton
import ai.heartz.app.ui.components.SoftRow
import ai.heartz.app.ui.components.clockLabel
import ai.heartz.app.ui.components.dayTime
import ai.heartz.app.ui.components.dot
import ai.heartz.app.ui.components.forScreen
import ai.heartz.app.ui.components.label
import ai.heartz.app.ui.components.relativeTime
import ai.heartz.app.ui.components.shortName
import ai.heartz.app.ui.drive.CardUi
import ai.heartz.app.ui.drive.DriveViewModel
import ai.heartz.app.ui.drive.StoryCardHead
import ai.heartz.app.ui.theme.Hz
import ai.heartz.app.ui.theme.colors
import ai.heartz.app.ui.theme.tone
import ai.heartz.core.geo.Geo
import ai.heartz.core.model.GeoPoint
import ai.heartz.core.model.ListenState
import ai.heartz.core.model.Place
import ai.heartz.core.model.Story
import ai.heartz.core.model.StoryStatus
import ai.heartz.core.model.User
import ai.heartz.core.model.VoiceMode
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

fun Story.letterTitle(places: Map<String, Place>): String = "${places[placeId]?.shortName ?: "어딘가"}에서 온 사연"
/** 카드·상세용: 랜드마크만 */
fun Story.placeShort(places: Map<String, Place>): String = places[placeId]?.shortName ?: "어딘가"
/** 목록용: "구 랜드마크" */
fun Story.placeList(places: Map<String, Place>): String = places[placeId]?.listLabel ?: "어딘가"

/** 주차 화면 공통 안쪽 여백 */
val ParkedPadding = PaddingValues(start = 40.dp, end = 40.dp, top = 28.dp, bottom = 40.dp)

/** 사연 한 줄: 얼굴(사연 색) / 장소 / 둘째 줄 / 오른쪽 */
@Composable
fun StoryRow(
    s: Story,
    places: Map<String, Place>,
    subtitle: String,
    modifier: Modifier = Modifier,
    unread: Boolean = false,
    trailing: @Composable () -> Unit = {},
    onClick: (() -> Unit)? = null,
) {
    SoftRow(modifier.padding(bottom = 16.dp), onClick = onClick) {
        ProfileTile(s.authorName, s.tone, 88.dp)
        Spacer(Modifier.width(24.dp))
        Column(Modifier.weight(1f)) {
            Text(s.placeList(places), style = MaterialTheme.typography.titleMedium, color = Hz.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            RowSubtitle(subtitle, dot = unread)
        }
        trailing()
    }
}

/** 오늘 지나쳤지만 듣지 않은 사연 + 오늘 들은 사연 */
@Composable
fun PassedScreen(
    feed: List<Story>,
    places: Map<String, Place>,
    listen: ListenState,
    onOpen: (String) -> Unit,
    header: @Composable () -> Unit,
) {
    val today = startOfTodayMillis()
    val passed = ai.heartz.core.schedule.PassedLetters.unplayed(feed, listen, today)
    val heard = ai.heartz.core.schedule.PassedLetters.heard(feed, listen, today)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = ParkedPadding) {
        item { header() }
        if (passed.isEmpty() && heard.isEmpty()) {
            item { EmptyState("아직 지나온 사연이 없어요", "길을 지나면 그곳에 남겨진 사연이 여기에 모여요") }
        }
        if (passed.isNotEmpty()) {
            item { SectionHeader("아직 안 들은 사연") { Count(passed.size) } }
            items(passed, key = { "p-" + it.first.id }) { (s, _) ->
                StoryRow(s, places, "보낸 이 ${s.authorName.shortName()}", unread = true, onClick = { onOpen(s.id) })
            }
        }
        if (heard.isNotEmpty()) {
            item { SectionHeader("오늘 들은 사연") { Count(heard.size) } }
            items(heard, key = { "h-" + it.first.id }) { (s, at) ->
                StoryRow(
                    s, places, "${s.authorName.shortName()} · ${relativeTime(at)} 들음",
                    trailing = { if (s.id in listen.hearted) Icon(Icons.Rounded.Favorite, "공감함", tint = Hz.Green, modifier = Modifier.size(40.dp)) },
                    onClick = { onOpen(s.id) },
                )
            }
        }
    }
}

@Composable
fun Count(n: Int) {
    Text("$n", style = MaterialTheme.typography.titleSmall, color = Hz.InkMuted)
}

/** 상세 화면 카드에 쓰는 값 */
fun Story.cardUi(places: Map<String, Place>, hearted: Boolean): CardUi = CardUi(
    storyId = id,
    title = letterTitle(places),
    placeShort = placeShort(places),
    placeName = places[placeId]?.displayName ?: placeShort(places),
    durationLabel = DriveViewModel.durationLabel(durationMs),
    tone = tone,
    authorName = authorName.shortName(),
    sampleLabel = sampleLabel,
    playing = false,
    playedToEnd = false,
    preparing = false,
    hearted = hearted,
    modulated = voiceMode == VoiceMode.MODULATED,
)

/** 사연 상세(주차 중): 듣기, 공감 하트, 신고·숨기기 / 내 사연이면 상태·검수에 보내기·삭제 */
@Composable
fun StoryDetailScreen(
    detail: DetailState,
    places: Map<String, Place>,
    me: User?,
    playback: PlaybackUi,
    busy: Boolean,
    onBack: () -> Unit,
    onPlay: (Story) -> Unit,
    onPause: () -> Unit,
    hearted: Set<String>,
    onHeart: (String, Boolean) -> Unit,
    onReport: (String, String) -> Unit,
    onHideAuthor: (String) -> Unit,
    onDelete: (String) -> Unit,
    onSubmit: (String) -> Unit,
) {
    var reportFor by remember { mutableStateOf<String?>(null) }
    var reportReason by remember { mutableStateOf<String?>(null) }
    var confirmHide by remember { mutableStateOf<Story?>(null) }
    var confirmDelete by remember { mutableStateOf<Story?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ParkedPadding)) {
        ScreenHeader("사연 상세", onBack = onBack)
        Spacer(Modifier.height(120.dp))
        val s = detail.story
        if (s == null) {
            if (detail.loading) EmptyState("불러오는 중") else EmptyState("사연을 열 수 없어요", detail.error ?: "")
            return@Column
        }
        val mine = me?.id == s.authorId
        val playingThis = playback.storyId == s.id && playback.isPlaying
        val isHearted = s.id in hearted
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
                DarkCard(Modifier.fillMaxWidth(), glow = s.tone.colors().glow, padding = PaddingValues(start = 40.dp, end = 40.dp, top = 40.dp, bottom = 40.dp)) {
                    StoryCardHead(
                        s.cardUi(places, isHearted), big = false,
                        label = if (mine) "내가 남긴 사연" else "이 길에 남겨진 사연",
                        authorSuffix = if (mine) " (나)" else "",
                    )
                    Spacer(Modifier.height(32.dp))
                    if (playingThis) PrimaryButton("일시정지", onPause, Modifier.fillMaxWidth(), Icons.Rounded.Pause, 100.dp)
                    else PrimaryButton("듣기", { onPlay(s) }, Modifier.fillMaxWidth(), Icons.Rounded.PlayArrow, 100.dp)
                }
                Spacer(Modifier.height(24.dp))
                if (mine) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (s.status == StoryStatus.PRIVATE || s.status == StoryStatus.DRAFT) {
                            PrimaryButton("검수에 보내기", { onSubmit(s.id) }, Modifier.weight(1f), Icons.AutoMirrored.Rounded.Send, 88.dp, enabled = !busy)
                        } else {
                            Surface(shape = RoundedCornerShape(percent = 50), color = Hz.Soft, modifier = Modifier.weight(1f).height(88.dp)) {
                                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(14.dp).clip(CircleShape).background(s.status.dot()))
                                    Spacer(Modifier.width(12.dp))
                                    Text(s.status.label(), style = MaterialTheme.typography.labelMedium, color = Hz.Ink)
                                }
                            }
                        }
                        OutlineButton("삭제", { confirmDelete = s }, Modifier.weight(1f), null, 88.dp, enabled = !busy)
                    }
                } else if (s.status == StoryStatus.APPROVED) {
                    OutlineButton(
                        "공감", { onHeart(s.id, !isHearted) }, Modifier.fillMaxWidth(),
                        if (isHearted) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, 88.dp, enabled = !busy,
                        heartGreen = isHearted,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        SoftButton("신고", { reportFor = s.id; reportReason = null }, Modifier.weight(0.8f), Icons.Rounded.Flag, enabled = !busy)
                        SoftButton("이 사람 사연 숨기기", { confirmHide = s }, Modifier.weight(1.4f), Icons.Rounded.VisibilityOff, enabled = !busy)
                    }
                }
            }
        }
    }

    reportFor?.let { id ->
        val reasons = listOf("장소와 관련 없음", "개인정보 노출", "괴롭힘·불쾌감", "갑작스러운 큰 소리", "장난·반복 음성")
        HzDialog(
            title = "신고 사유를 골라 주세요", confirm = "신고",
            onConfirm = { reportReason?.let { onReport(id, it) }; reportFor = null },
            onDismiss = { reportFor = null }, confirmEnabled = reportReason != null,
        ) { RadioList(reasons, reportReason) { reportReason = it } }
    }
    confirmHide?.let { s ->
        HzDialog(
            title = "${s.authorName.shortName()} 님의 사연을 숨길까요?", confirm = "숨기기",
            body = "이 사람의 사연이 더 오지 않아요.\n설정에서 다시 볼 수 있어요.",
            onConfirm = { onHideAuthor(s.authorId); confirmHide = null }, onDismiss = { confirmHide = null },
        )
    }
    confirmDelete?.let { s ->
        HzDialog(
            title = "이 사연을 삭제할까요?", confirm = "삭제", body = "삭제하면 되돌릴 수 없어요.",
            onConfirm = { onDelete(s.id); confirmDelete = null }, onDismiss = { confirmDelete = null },
        )
    }
}

/** 공감 버튼: 남겼으면 하트만 초록 */
@Composable
private fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    height: androidx.compose.ui.unit.Dp,
    enabled: Boolean,
    heartGreen: Boolean,
) {
    Surface(
        onClick = onClick, enabled = enabled, shape = RoundedCornerShape(percent = 50), color = Hz.Bg,
        border = androidx.compose.foundation.BorderStroke(3.dp, Hz.Ink),
        modifier = modifier.height(height),
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (heartGreen) Hz.Green else Hz.Ink, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(14.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, color = Hz.Ink)
        }
    }
}

/** 내 사연함: 보내지 못한 초안 + 내가 남긴 사연(상태·하트 수) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MineScreen(
    mine: List<Story>,
    pending: List<PendingDraft>,
    places: Map<String, Place>,
    myName: String,
    onOpen: (String) -> Unit,
    onRetry: (PendingDraft) -> Unit,
    onDiscard: (PendingDraft) -> Unit,
    onCompose: () -> Unit,
    header: @Composable () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = ParkedPadding) {
        item {
            header()
            PrimaryButton("남기기", onCompose, Modifier.fillMaxWidth(), Icons.Rounded.Mic, 92.dp)
        }
        if (pending.isNotEmpty()) {
            item { SectionHeader("보내지 못한 사연") { Count(pending.size) } }
            items(pending, key = { it.clientDraftId }) { d -> PendingCard(d, places, onRetry, onDiscard) }
        }
        item { SectionHeader("남긴 사연") { if (mine.isNotEmpty()) Count(mine.size) } }
        if (mine.isEmpty()) {
            item { EmptyState("아직 남긴 사연이 없어요") }
        }
        items(mine, key = { it.id }) { s ->
            SoftRow(Modifier.padding(bottom = 16.dp), onClick = { onOpen(s.id) }) {
                ProfileTile(myName, s.tone, 88.dp)
                Spacer(Modifier.width(24.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.placeList(places), style = MaterialTheme.typography.titleMedium, color = Hz.Ink, maxLines = 1)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${dayTime(s.createdAtMillis)} · ", style = MaterialTheme.typography.bodySmall, color = Hz.InkMuted)
                        Icon(Icons.Rounded.Favorite, null, tint = if (s.heartCount > 0) Hz.Green else Hz.InkFaint, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("${s.heartCount}", style = MaterialTheme.typography.bodySmall, color = Hz.InkMuted)
                    }
                }
                Box(Modifier.size(14.dp).clip(CircleShape).background(s.status.dot()))
                Spacer(Modifier.width(10.dp))
                Text(s.status.label(), style = MaterialTheme.typography.bodySmall, color = Hz.InkMuted)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PendingCard(d: PendingDraft, places: Map<String, Place>, onRetry: (PendingDraft) -> Unit, onDiscard: (PendingDraft) -> Unit) {
    var chosen by remember(d.clientDraftId) { mutableStateOf(d.placeId) }
    Column(
        Modifier.fillMaxWidth().padding(bottom = 16.dp).clip(RoundedCornerShape(32.dp)).background(Hz.Soft).padding(28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(Hz.Bg), contentAlignment = Alignment.Center) {
                Icon(if (d.placeId == null) Icons.Rounded.LocationOff else Icons.Rounded.Inbox, null, tint = Hz.Ink, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    d.placeId?.let { places[it]?.listLabel ?: it } ?: "장소 없음",
                    style = MaterialTheme.typography.titleMedium, color = Hz.Ink,
                )
                Text(
                    listOfNotNull(
                        if (d.recordedWhileDriving) "지나면서 남김" else null,
                        clockLabel(d.durationMs),
                        dayTime(d.createdAtMillis),
                        if (d.voiceMode == VoiceMode.MODULATED) "변조 목소리" else null,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = Hz.InkMuted,
                )
            }
        }
        d.lastError?.let {
            Spacer(Modifier.height(16.dp))
            Text(it.forScreen(), style = MaterialTheme.typography.bodyMedium, color = Hz.Ink)
        }
        if (d.placeId == null) {
            Spacer(Modifier.height(18.dp))
            val sorted = places.values.map { p ->
                p to (if (d.anchorLat != null && d.anchorLng != null) Geo.distanceMeters(GeoPoint(d.anchorLat, d.anchorLng), p.publicLocation) else null)
            }.sortedBy { it.second ?: 0.0 }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                sorted.forEach { (p, m) ->
                    val on = chosen == p.id
                    Surface(
                        onClick = { chosen = p.id }, shape = RoundedCornerShape(percent = 50),
                        color = if (on) Hz.Ink else Hz.Bg, modifier = Modifier.height(64.dp),
                    ) {
                        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Place, null, tint = Hz.Green, modifier = Modifier.size(30.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                p.shortName + (m?.let { " · " + ai.heartz.app.ui.studio.formatDistance(it) } ?: ""),
                                style = MaterialTheme.typography.labelSmall, color = if (on) Hz.Bg else Hz.Ink,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            SoftButton("버리기", { onDiscard(d) }, Modifier.weight(1f), Icons.Rounded.Delete, height = 80.dp, bg = Hz.Bg)
            PrimaryButton("검수에 보내기", { onRetry(d.copy(placeId = chosen)) }, Modifier.weight(1.4f), null, 80.dp, enabled = chosen != null)
        }
    }
}
