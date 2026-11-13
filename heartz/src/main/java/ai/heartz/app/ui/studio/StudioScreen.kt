package ai.heartz.app.ui.studio

import ai.heartz.app.data.SaveOutcome
import ai.heartz.app.ui.components.Chip
import ai.heartz.app.ui.components.ChipStyle
import ai.heartz.app.ui.components.HzToast
import ai.heartz.app.ui.components.IconMessage
import ai.heartz.app.ui.components.OutlineButton
import ai.heartz.app.ui.components.PrimaryButton
import ai.heartz.app.ui.components.ScreenHeader
import ai.heartz.app.ui.components.SoftRow
import ai.heartz.app.ui.components.clockLabel
import ai.heartz.app.ui.components.forScreen
import ai.heartz.app.ui.components.withObject
import ai.heartz.app.ui.library.ParkedPadding
import ai.heartz.app.ui.settings.MyProfileButton
import ai.heartz.app.ui.theme.Hz
import ai.heartz.core.model.StoryStatus
import ai.heartz.core.model.VoiceMode
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import ai.heartz.app.ui.components.CircleIconButton
import ai.heartz.core.model.PlaceSearch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun StudioScreen(state: StudioState, vm: StudioViewModel, myName: String?, onProfile: () -> Unit, onOpenMine: () -> Unit) {
    val place = state.places.firstOrNull { it.place.id == state.placeId }?.place
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(ParkedPadding)) {
            ScreenHeader(
                "남기기",
                onBack = when (state.step) {
                    StudioStep.RECORD -> vm::backToPlaces
                    StudioStep.REVIEW -> vm::discardClip
                    else -> null
                },
                backEnabled = !state.recording && !state.saving && !state.cleaning,
            ) {
                when (state.step) {
                    StudioStep.RECORD, StudioStep.REVIEW -> if (place != null) Chip(place.shortName, ChipStyle.Soft, icon = Icons.Rounded.Place, height = 60.dp, iconTint = Hz.Green)
                    else -> if (myName != null) MyProfileButton(myName, onProfile)
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (state.step) {
                    StudioStep.PICK_PLACE -> PlacePicker(state, vm::pickPlace)
                    StudioStep.RECORD -> RecordStep(state, vm)
                    StudioStep.REVIEW -> ReviewStep(state, vm)
                    StudioStep.DONE -> DoneStep(state, onOpenMine, onAgain = { vm.begin() })
                }
            }
        }
        state.error?.let { msg ->
            LaunchedEffect(msg) {
                delay(4_000)
                vm.clearError()
            }
            Box(Modifier.align(Alignment.BottomCenter).padding(24.dp).clickable { vm.clearError() }) {
                HzToast(
                    if (msg.startsWith("조금 더 길게")) "조금 더 길게 들려주세요 · 2초 이상" else msg.forScreen(),
                    if (msg.startsWith("조금 더 길게")) Icons.Rounded.Info else Icons.Rounded.Warning,
                )
            }
        }
    }
}

@Composable
private fun PlacePicker(state: StudioState, onPick: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val shown = remember(state.places, query) {
        val byId = state.places.associateBy { it.place.id }
        PlaceSearch.filter(state.places.map { it.place }, query).mapNotNull { byId[it.id] }
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text("어디에 남길까요?", style = MaterialTheme.typography.headlineSmall, color = Hz.Ink)
            Spacer(Modifier.height(6.dp))
            Text("녹음하는 곳과 달라도 괜찮아요", style = MaterialTheme.typography.bodyMedium, color = Hz.InkMuted)
            Spacer(Modifier.height(20.dp))
            PlaceSearchBar(query, { query = it }, onDone = { keyboard?.hide() })
            Spacer(Modifier.height(20.dp))
            when {
                state.places.isEmpty() -> Text("장소를 불러오지 못했어요", color = Hz.InkMuted, style = MaterialTheme.typography.bodyLarge)
                shown.isEmpty() -> Text("‘${query.trim()}’에 맞는 장소가 없어요", color = Hz.InkMuted, style = MaterialTheme.typography.bodyLarge)
            }
        }
        items(shown, key = { it.place.id }) { opt ->
            SoftRow(Modifier.padding(bottom = 16.dp), onClick = { keyboard?.hide(); onPick(opt.place.id) }) {
                Box(Modifier.size(80.dp).clip(RoundedCornerShape(22.dp)).background(Hz.GreenSoft), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Place, null, tint = Hz.Green, modifier = Modifier.size(44.dp))
                }
                Spacer(Modifier.width(24.dp))
                Column(Modifier.weight(1f)) {
                    Text(opt.place.shortName, style = MaterialTheme.typography.titleMedium, color = Hz.Ink)
                    Text(listOf(opt.place.district, opt.place.dong).filter { it.isNotBlank() }.joinToString(" ").ifBlank { opt.place.displayName }, style = MaterialTheme.typography.bodySmall, color = Hz.InkMuted, maxLines = 1)
                }
                Text(opt.distanceMeters?.let { formatDistance(it) } ?: "", style = MaterialTheme.typography.titleSmall, color = Hz.Ink)
            }
        }
    }
}

/** 장소 검색 칸: 랜드마크·구·동 이름으로 찾는다(주차 중 화면에서만 보인다) */
@Composable
private fun PlaceSearchBar(query: String, onChange: (String) -> Unit, onDone: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 84.dp).clip(RoundedCornerShape(percent = 50)).background(Hz.Soft).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = Hz.InkMuted, modifier = Modifier.size(36.dp))
        Spacer(Modifier.width(14.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text("장소 이름, 구, 동으로 찾기", style = MaterialTheme.typography.bodyLarge, color = Hz.InkFaint)
            BasicTextField(
                value = query, onValueChange = onChange, singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Hz.Ink),
                cursorBrush = SolidColor(Hz.Green),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onDone() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Spacer(Modifier.width(10.dp))
            CircleIconButton(Icons.Rounded.Close, "지우기", { onChange("") }, size = 56.dp, bg = Hz.Bg)
        }
    }
}

fun formatDistance(m: Double): String = if (m < 1000) "${m.toInt()} m" else String.format(Locale.US, "%.1f km", m / 1000)

@Composable
private fun RecordStep(state: StudioState, vm: StudioViewModel) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (!granted) {
            IconMessage(Icons.Rounded.MicOff, "마이크 권한이 필요해요", "사연을 녹음하려면 마이크를 허용해 주세요.")
            Spacer(Modifier.height(36.dp))
            PrimaryButton("마이크 허용", { launcher.launch(Manifest.permission.RECORD_AUDIO) }, Modifier.widthIn(min = 380.dp), Icons.Rounded.Mic, 92.dp)
            return@Column
        }
        if (state.recording) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(Hz.Red))
                Spacer(Modifier.width(14.dp))
                Text("녹음 중", style = MaterialTheme.typography.titleLarge, color = Hz.Ink)
            }
            Spacer(Modifier.height(12.dp))
        }
        Text(
            buildAnnotatedString {
                append(clockLabel(state.elapsedMs))
                withStyle(SpanStyle(color = Hz.InkFaint, fontSize = 48.sp)) { append(" / ${clockLabel(state.maxDurationMs)}") }
            },
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 116.sp, lineHeight = 128.sp, letterSpacing = (-3).sp),
            color = Hz.Ink,
        )
        Spacer(Modifier.height(40.dp))
        val level = if (state.recording) state.level.coerceIn(0f, 1f) else 0f
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(300.dp)) {
            if (level > 0f) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(Hz.Red.copy(alpha = 0.14f), radius = size.minDimension / 2 * (0.75f + 0.25f * level))
                }
            }
            Surface(
                onClick = { if (state.recording) vm.stopRecording() else if (!state.cleaning) vm.startRecording() },
                shape = CircleShape, color = if (state.recording) Hz.Red else Hz.Green,
                modifier = Modifier.size(220.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (state.recording) Icons.Rounded.Stop else Icons.Rounded.Mic,
                        if (state.recording) "녹음 멈추기" else "녹음 시작",
                        tint = if (state.recording) Hz.Bg else Hz.Ink, modifier = Modifier.size(104.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(32.dp))
        Text(
            when {
                state.cleaning -> "목소리가 잘 들리게 잡음을 줄이는 중"
                state.recording -> "누르면 녹음을 멈춰요"
                else -> "누르면 녹음을 시작해요"
            },
            style = MaterialTheme.typography.titleMedium, color = Hz.InkMuted,
        )
    }
}

@Composable
private fun ReviewStep(state: StudioState, vm: StudioViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
        Spacer(Modifier.height(40.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(36.dp)).background(Hz.Card).padding(horizontal = 28.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(onClick = vm::togglePreview, shape = CircleShape, color = Hz.Green, modifier = Modifier.size(92.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (state.previewPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (state.previewPlaying) "미리 듣기 멈춤" else "미리 듣기", tint = Hz.Ink, modifier = Modifier.size(56.dp),
                    )
                }
            }
            Spacer(Modifier.width(24.dp))
            Column(Modifier.weight(1f)) {
                Text("들어 보기", style = MaterialTheme.typography.titleLarge, color = Hz.OnCard)
                Text(clockLabel(state.clipDurationMs), style = MaterialTheme.typography.bodyMedium, color = Hz.OnCardMuted)
            }
            OutlineButton("다시 녹음", vm::discardClip, icon = Icons.Rounded.Refresh, height = 76.dp, onCard = true, style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.height(40.dp))
        Text("어떤 목소리로 전할까요", style = MaterialTheme.typography.headlineSmall, color = Hz.Ink)
        Spacer(Modifier.height(6.dp))
        Text("기본은 내 목소리예요", style = MaterialTheme.typography.bodySmall, color = Hz.InkMuted)
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            VoiceTile("내 목소리", Icons.Rounded.RecordVoiceOver, Hz.Green, state.voiceMode == VoiceMode.ORIGINAL, Modifier.weight(1f)) {
                vm.selectVoice(VoiceMode.ORIGINAL)
            }
            VoiceTile("변조 목소리", Icons.Rounded.GraphicEq, Color(0xFF5C8CF2), state.voiceMode == VoiceMode.MODULATED, Modifier.weight(1f)) {
                vm.selectVoice(VoiceMode.MODULATED)
            }
        }
        Spacer(Modifier.height(36.dp))
        PrimaryButton(
            if (state.saving) "보내는 중" else "보내기", vm::save, Modifier.fillMaxWidth(),
            Icons.AutoMirrored.Rounded.Send, 96.dp, enabled = !state.saving,
        )
        Spacer(Modifier.height(16.dp))
        Text("보낸 사연은 검수 후 공개돼요", style = MaterialTheme.typography.bodySmall, color = Hz.InkMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun VoiceTile(title: String, icon: ImageVector, tint: Color, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.heightIn(min = 112.dp).clip(RoundedCornerShape(28.dp)).background(Hz.Soft)
            .then(if (selected) Modifier.border(4.dp, Hz.Green, RoundedCornerShape(28.dp)) else Modifier)
            .clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(68.dp).clip(RoundedCornerShape(18.dp)).background(tint), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Hz.Bg, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.width(18.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = Hz.Ink, modifier = Modifier.weight(1f))
        if (selected) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Hz.Green), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, null, tint = Hz.Bg, modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
private fun DoneStep(state: StudioState, onOpenMine: () -> Unit, onAgain: () -> Unit) {
    val r = state.result
    val ok = r is SaveOutcome.Saved
    val title = when {
        r is SaveOutcome.Saved && r.status == StoryStatus.PENDING_REVIEW -> "검수에 보냈어요"
        r is SaveOutcome.Saved -> "저장했어요"
        r is SaveOutcome.Deferred -> "아직 보내지 못했어요"
        else -> "완료"
    }
    val body = when (r) {
        is SaveOutcome.Saved -> "운영자가 들은 뒤\n${(state.places.firstOrNull { it.place.id == state.placeId }?.place?.shortName ?: r.placeName).withObject()} 지나는 사람에게 전해져요"
        is SaveOutcome.Deferred -> "내 사연함에서 다시 보낼 수 있어요\n(${r.reason})"
        null -> ""
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            IconMessage(
                if (ok) Icons.Rounded.Check else Icons.Rounded.Warning, title, body,
                circle = if (ok) Hz.Green else Hz.Soft, iconTint = if (ok) Hz.Ink else Hz.Warn,
            )
            Spacer(Modifier.height(48.dp))
            Row(Modifier.widthIn(max = 760.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlineButton("하나 더", onAgain, Modifier.weight(1f), null, 92.dp)
                PrimaryButton("내 사연함 보기", onOpenMine, Modifier.weight(1f), null, 92.dp)
            }
        }
    }
}
