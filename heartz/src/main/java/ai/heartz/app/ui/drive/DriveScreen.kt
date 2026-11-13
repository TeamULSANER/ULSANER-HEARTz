package ai.heartz.app.ui.drive

import ai.heartz.app.platform.audio.PlaybackUi
import ai.heartz.app.platform.location.LocationStatus
import ai.heartz.app.ui.components.ProfileCircle
import ai.heartz.app.ui.components.Chip
import ai.heartz.app.ui.components.ChipStyle
import ai.heartz.app.ui.components.DarkCard
import ai.heartz.app.ui.components.HeartzWordmark
import ai.heartz.app.ui.components.OutlineButton
import ai.heartz.app.ui.components.PrimaryButton
import ai.heartz.app.ui.components.VoiceHint
import ai.heartz.app.ui.theme.Hz
import ai.heartz.app.ui.theme.colors
import ai.heartz.core.schedule.LocationValidity
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 카드에서 할 수 있는 동작. 주행·주차 화면이 같이 쓴다. */
class CardActions(
    val play: (String) -> Unit,
    val pause: () -> Unit,
    val resume: () -> Unit,
    val stop: () -> Unit,
    val open: () -> Unit,
    val skip: () -> Unit,
    val heart: () -> Unit,
    val noHeart: () -> Unit,
)

/** 주행 중 머리: 글자 로고 + 주행 중 + (위치 상태) */
@Composable
fun DriveHeader(location: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().height(88.dp), verticalAlignment = Alignment.CenterVertically) {
        HeartzWordmark()
        Spacer(Modifier.weight(1f))
        Chip("주행 중", ChipStyle.Ink, icon = Icons.Rounded.DirectionsCar, height = 60.dp)
        if (location != null) {
            Spacer(Modifier.width(12.dp))
            location()
        }
    }
}

@Composable
fun LocationChip(state: DriveUiState) {
    val problem = locationProblem(state)
    Chip(problem ?: "위치 수신", ChipStyle.Soft, dot = if (problem == null) Hz.Green else Hz.InkFaint, height = 60.dp)
}

/** 주행 중 화면: 사연 카드(또는 대기) + 지나면서 남기기. 목록·편집·키보드 없음. */
@Composable
fun DrivingScreen(
    state: DriveUiState,
    actions: CardActions,
    roadsideEnabled: Boolean,
    voiceOn: Boolean,
    onRoadside: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Hz.Bg), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 960.dp).fillMaxSize().padding(horizontal = 38.dp, vertical = 28.dp)) {
            DriveHeader { LocationChip(state) }
            Spacer(Modifier.height(28.dp))
            val card = state.card
            val step = state.voice.takeIf { card != null && it.storyIdOrNull() == card.storyId }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    card == null -> WaitingCard(state, Modifier.fillMaxSize())
                    step is VoiceStep.Closing && step.reason == CloseReason.SKIPPED -> SkippedCard(Modifier.fillMaxSize())
                    else -> StoryCard(card, state.playback, state.voice, driving = true, voiceOn = voiceOn, actions = actions, modifier = Modifier.fillMaxSize())
                }
            }
            if (roadsideEnabled) {
                Spacer(Modifier.height(28.dp))
                OutlineButton(
                    "지나면서 남기기", onRoadside, Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.Mic, height = 112.dp, style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/** 위치가 없거나 오래됐을 때만 짧게 알린다. 정상이면 null */
fun locationProblem(state: DriveUiState): String? = when (val l = state.location) {
    is LocationStatus.Receiving -> when (state.validity) {
        is LocationValidity.Valid -> null
        else -> "위치 확인 중"
    }
    LocationStatus.PermissionMissing -> "위치 권한 필요"
    LocationStatus.Unsupported, is LocationStatus.Failed -> "위치를 받을 수 없음"
    else -> if (l is LocationStatus.Waiting || l == LocationStatus.NotStarted) "위치 확인 중" else null
}

/** 카드가 없을 때: 정적인 길 그림과 두 줄. [plain]이면 회색 바탕 없이(주차 중 지금 이 길) */
@Composable
fun WaitingCard(state: DriveUiState, modifier: Modifier = Modifier, plain: Boolean = false) {
    Box(
        modifier.clip(RoundedCornerShape(48.dp)).then(if (plain) Modifier else Modifier.background(Hz.Soft)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(start = if (plain) 12.dp else 36.dp, end = if (plain) 12.dp else 36.dp, top = if (plain) 56.dp else 36.dp, bottom = if (plain) 12.dp else 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RoadDrawing(if (plain) 170.dp else 300.dp)
            Spacer(Modifier.height(if (plain) 32.dp else 52.dp))
            Text(
                "이 길의 사연을 기다리는 중",
                style = if (plain) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineLarge,
                color = Hz.Ink, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                when (locationProblem(state)) {
                    null -> "지금 이곳에는 아직 도착한 사연이 없어요."
                    "위치 권한 필요" -> "위치 권한을 허용하면 사연을 찾을 수 있어요."
                    "위치를 받을 수 없음" -> "위치를 받을 수 없어 사연을 찾지 못해요."
                    else -> "위치를 확인하는 동안에는 사연을 찾지 않아요."
                },
                style = if (plain) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                color = Hz.InkMuted, textAlign = TextAlign.Center,
            )
        }
    }
}

/** 길 그림: 양쪽 회색 선, 가운데 초록 점선 */
@Composable
fun RoadDrawing(height: Dp) {
    Canvas(Modifier.width(height * 1.55f).height(height)) {
        val w = size.width
        val h = size.height
        val cx = w / 2
        val stroke = 5.dp.toPx()
        val side = Hz.InkFaint.copy(alpha = 0.55f)
        drawLine(side, Offset(cx - w * 0.12f, 0f), Offset(cx - w * 0.5f, h), stroke, StrokeCap.Round)
        drawLine(side, Offset(cx + w * 0.12f, 0f), Offset(cx + w * 0.5f, h), stroke, StrokeCap.Round)
        drawLine(
            Hz.Green, Offset(cx, h * 0.06f), Offset(cx, h), stroke * 1.1f, StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(h * 0.13f, h * 0.17f)),
        )
    }
}

/** 넘긴 뒤 잠깐 보이는 카드 */
@Composable
private fun SkippedCard(modifier: Modifier = Modifier) {
    DarkCard(modifier) {
        Spacer(Modifier.weight(1f))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(150.dp).clip(CircleShape).background(Hz.CardChip), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.SkipNext, null, tint = Hz.OnCard, modifier = Modifier.size(72.dp))
            }
            Spacer(Modifier.height(40.dp))
            Text("넘겼어요", style = MaterialTheme.typography.displaySmall, color = Hz.OnCard)
            Spacer(Modifier.height(14.dp))
            Text("지나온 사연에 남겨 둘게요", style = MaterialTheme.typography.bodyLarge, color = Hz.OnCardMuted)
        }
        Spacer(Modifier.weight(1f))
    }
}

/**
 * 사연 카드(주행·주차 공통). 위: 이 길에 남겨진 사연 / 장소 / 길이 / 보낸 이. 아래: 지금 할 수 있는 동작.
 * [driving]이면 화면을 채우고 글자·버튼을 크게, 진행 막대 없음.
 */
@Composable
fun StoryCard(
    card: CardUi,
    playback: PlaybackUi,
    voice: VoiceStep,
    driving: Boolean,
    voiceOn: Boolean,
    actions: CardActions,
    modifier: Modifier = Modifier,
) {
    val isCurrent = playback.storyId == card.storyId
    val playingNow = isCurrent && playback.isPlaying
    val paused = isCurrent && !playback.isPlaying && !playback.ended && !playback.idle
    val step = voice.takeIf { it.storyIdOrNull() == card.storyId }
    DarkCard(
        modifier,
        glow = card.tone.colors().glow,
        border = if (playingNow) Hz.Green else null,
        padding = if (driving) PaddingValues(52.dp) else PaddingValues(start = 44.dp, end = 44.dp, top = 40.dp, bottom = 40.dp),
    ) {
        StoryCardHead(card, driving)
        if (driving) Spacer(Modifier.weight(1f)) else Spacer(Modifier.height(36.dp))
        StoryCardActions(card, playback, step, driving, voiceOn, isCurrent, playingNow, paused, actions)
    }
}

/** 사연 카드 위쪽(상세 화면도 같이 쓴다) */
@Composable
fun StoryCardHead(
    card: CardUi,
    big: Boolean,
    label: String = "이 길에 남겨진 사연",
    authorSuffix: String = "",
) {
    Row(verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = Hz.OnCardMuted, modifier = Modifier.weight(1f).padding(top = 8.dp))
    }
    Spacer(Modifier.height(if (big) 26.dp else 16.dp))
    Text(
        card.placeShort,
        style = if (big) MaterialTheme.typography.displayLarge else MaterialTheme.typography.displayMedium,
        color = Hz.OnCard, maxLines = 2, overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(if (big) 24.dp else 18.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Chip(card.durationLabel, ChipStyle.OnCard)
    }
    Spacer(Modifier.height(if (big) 22.dp else 18.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("보낸 이", style = MaterialTheme.typography.bodyLarge, color = Hz.OnCardMuted)
        Spacer(Modifier.width(14.dp))
        ProfileCircle(card.authorName, if (big) 64.dp else 56.dp)
        Spacer(Modifier.width(14.dp))
        Text(card.authorName, style = MaterialTheme.typography.titleMedium, color = Hz.OnCard, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (authorSuffix.isNotBlank()) Text(authorSuffix, style = MaterialTheme.typography.bodyLarge, color = Hz.OnCardMuted, maxLines = 1)
    }
}

@Composable
private fun ColumnScope.StoryCardActions(
    card: CardUi,
    playback: PlaybackUi,
    step: VoiceStep?,
    driving: Boolean,
    voiceOn: Boolean,
    isCurrent: Boolean,
    playingNow: Boolean,
    paused: Boolean,
    actions: CardActions,
) {
    val h = if (driving) 136.dp else 100.dp
    val style = if (driving) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium
    val gap = if (driving) 20.dp else 16.dp
    when {
        step is VoiceStep.Closing && step.reason == CloseReason.HEARTED -> {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.Favorite, null, tint = Hz.Green, modifier = Modifier.size(if (driving) 112.dp else 84.dp))
                Spacer(Modifier.height(24.dp))
                Text("하트를 남겼어요", style = MaterialTheme.typography.headlineLarge, color = Hz.OnCard)
                Spacer(Modifier.height(10.dp))
                Text("${card.authorName} 님에게 공감이 전해져요", style = MaterialTheme.typography.bodyLarge, color = Hz.OnCardMuted, textAlign = TextAlign.Center)
            }
        }
        step is VoiceStep.Closing -> {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("다음 사연을 기다릴게요", style = MaterialTheme.typography.headlineLarge, color = Hz.OnCard, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Text("지나온 사연에서 다시 들을 수 있어요", style = MaterialTheme.typography.bodyLarge, color = Hz.OnCardMuted, textAlign = TextAlign.Center)
            }
        }
        step is VoiceStep.Announcing -> {
            VoiceHint("곧 읽어드릴게요", Icons.AutoMirrored.Rounded.VolumeUp)
            Spacer(Modifier.height(gap))
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                PrimaryButton("듣기", actions.open, Modifier.weight(1.3f), Icons.Rounded.PlayArrow, h, style = style)
                OutlineButton("넘기기", actions.skip, Modifier.weight(1f), Icons.Rounded.SkipNext, h, onCard = true, style = style)
            }
        }
        step is VoiceStep.AskingHeart || (driving && card.playedToEnd && !isCurrentlyPlaying(isCurrent, playback) && !card.hearted) -> {
            Text("이번 사연은 어떠셨어요?", style = MaterialTheme.typography.headlineMedium, color = Hz.OnCard)
            Spacer(Modifier.height(gap))
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                PrimaryButton("공감", actions.heart, Modifier.weight(1.3f), Icons.Rounded.FavoriteBorder, h, style = style)
                OutlineButton("괜찮아요", actions.noHeart, Modifier.weight(1f), null, h, onCard = true, style = style)
            }
        }
        card.preparing -> PrimaryButton("여는 중", {}, Modifier.fillMaxWidth(), Icons.Rounded.PlayArrow, h, enabled = false, style = style)
        playingNow -> {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                PrimaryButton("일시정지", actions.pause, Modifier.weight(1.3f), Icons.Rounded.Pause, h, style = style)
                OutlineButton("그만 듣기", actions.stop, Modifier.weight(1f), Icons.Rounded.Stop, h, onCard = true, style = style)
            }
        }
        paused -> {
            Text(
                if (playback.suppressedByFocus) "다른 소리 때문에 잠시 멈췄어요" else "잠시 멈췄어요",
                style = MaterialTheme.typography.titleLarge, color = Hz.OnCardMuted,
            )
            Spacer(Modifier.height(gap))
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                PrimaryButton("이어 듣기", actions.resume, Modifier.weight(1.3f), Icons.Rounded.PlayArrow, h, style = style)
                OutlineButton("그만 듣기", actions.stop, Modifier.weight(1f), Icons.Rounded.Stop, h, onCard = true, style = style)
            }
        }
        card.playedToEnd -> Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            OutlineButton("다시 듣기", { actions.play(card.storyId) }, Modifier.weight(1f), Icons.Rounded.Replay, h, onCard = true, style = style)
            if (card.hearted) OutlineButton("공감함", {}, Modifier.weight(1f), Icons.Rounded.Favorite, h, enabled = false, onCard = true, style = style)
            else PrimaryButton("공감", actions.heart, Modifier.weight(1f), Icons.Rounded.FavoriteBorder, h, style = style)
        }
        else -> {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                PrimaryButton("듣기", { actions.play(card.storyId) }, Modifier.weight(1.3f), Icons.Rounded.PlayArrow, h, style = style)
                OutlineButton("넘기기", actions.skip, Modifier.weight(1f), Icons.Rounded.SkipNext, h, onCard = true, style = style)
            }
        }
    }
}

private fun isCurrentlyPlaying(isCurrent: Boolean, p: PlaybackUi) = isCurrent && (p.isPlaying || (!p.idle && !p.ended))

internal fun VoiceStep.storyIdOrNull(): String? = when (this) {
    VoiceStep.Idle -> null
    is VoiceStep.Announcing -> storyId
    is VoiceStep.AskingHeart -> storyId
    is VoiceStep.Closing -> storyId
}
