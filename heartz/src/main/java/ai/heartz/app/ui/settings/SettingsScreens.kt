package ai.heartz.app.ui.settings

import ai.heartz.app.BuildConfig
import ai.heartz.app.HeartzLog
import ai.heartz.app.data.BackendMode
import ai.heartz.app.data.SyncStatus
import ai.heartz.app.platform.audio.PlaybackUi
import ai.heartz.app.platform.location.LocationStatus
import ai.heartz.app.platform.location.ProximityProbe
import ai.heartz.app.platform.navi.NaviState
import ai.heartz.app.platform.restrictions.UxRestrictionsMonitor
import ai.heartz.app.ui.components.ProfileCircle
import ai.heartz.app.ui.components.Chip
import ai.heartz.app.ui.components.ChipStyle
import ai.heartz.app.ui.components.OutlineButton
import ai.heartz.app.ui.components.PrimaryButton
import ai.heartz.app.ui.components.ScreenHeader
import ai.heartz.app.ui.components.SoftButton
import ai.heartz.app.ui.components.shortName
import ai.heartz.app.ui.library.ParkedPadding
import ai.heartz.app.ui.theme.Hz
import ai.heartz.core.drive.DrivingRecordingGate
import ai.heartz.core.drive.UxState
import ai.heartz.core.drive.VerificationStatus
import ai.heartz.core.model.Place
import ai.heartz.core.model.User
import ai.heartz.core.model.UserRole
import ai.heartz.core.schedule.Eligibility
import ai.heartz.core.schedule.LocationValidity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 화면 머리 오른쪽의 내 프로필(Pleos Connect 프로필과 같은 첫 글자 원). 누르면 설정으로 */
@Composable
fun MyProfileButton(name: String, onClick: () -> Unit) {
    Box(Modifier.size(64.dp).clip(CircleShape).clickable(onClick = onClick)) {
        ProfileCircle(name, 64.dp)
    }
}

/** 둥근 칸 여러 개 중 하나(계정·데이터) */
@Composable
private fun Segmented(options: List<Pair<String, String>>, selected: String?, enabled: Boolean, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(percent = 50)).background(Hz.Soft).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { (id, label) ->
            val on = id == selected
            Surface(
                onClick = { if (!on) onSelect(id) }, enabled = enabled || on, shape = RoundedCornerShape(percent = 50),
                color = if (on) Hz.Ink else Color.Transparent, modifier = Modifier.weight(1f).height(72.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(label, style = MaterialTheme.typography.titleSmall, color = if (on) Hz.Bg else Hz.InkMuted, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = Hz.InkMuted, modifier = Modifier.padding(top = 32.dp, bottom = 14.dp))
}

@Composable
private fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(Hz.Line))

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    me: User?,
    users: List<User>,
    backend: Pair<String, BackendMode>,
    devServerAvailable: Boolean,
    sync: SyncStatus,
    hiddenAuthors: Set<String>,
    locationGranted: Boolean,
    micGranted: Boolean,
    busy: Boolean,
    onSignIn: (String) -> Unit,
    onBackend: (BackendMode) -> Unit,
    onRefresh: () -> Unit,
    onRequestLocation: () -> Unit,
    onRequestMic: () -> Unit,
    onUnhide: (String) -> Unit,
    onDiagnostics: () -> Unit,
    roadsideEnabled: Boolean,
    onRoadside: (Boolean) -> Unit,
    voiceGuideEnabled: Boolean,
    onVoiceGuide: (Boolean) -> Unit,
    authorNames: Map<String, String> = emptyMap(),
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ParkedPadding)) {
        ScreenHeader("설정") { if (me != null) ProfileCircle(me.displayName, 64.dp) }
        if (me != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfileCircle(me.displayName, 96.dp)
                Spacer(Modifier.width(24.dp))
                Column(Modifier.weight(1f)) {
                    Text(me.displayName.shortName(), style = MaterialTheme.typography.headlineSmall, color = Hz.Ink, maxLines = 1)
                    Text(
                        if (me.role == UserRole.MODERATOR) "테스트 운영자" else "테스트 운전자",
                        style = MaterialTheme.typography.bodyMedium, color = Hz.InkMuted,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Divider()
        }
        Label("계정")
        if (users.isEmpty()) Text("계정 목록을 불러오는 중", style = MaterialTheme.typography.bodyMedium, color = Hz.InkFaint)
        else Segmented(accountLabels(users, me), me?.id, !busy, onSignIn)

        Spacer(Modifier.height(20.dp))
        ToggleRow("사연이 오면 바로 읽어 주기", "도착하면 바로 읽고, 다 들으면 공감을 물어요", voiceGuideEnabled, onVoiceGuide)
        Divider()
        ToggleRow("지나면서 남기기", "주행 중 큰 버튼과 Gleo 녹음 시작", roadsideEnabled, onRoadside)
        Divider()
        if (!locationGranted || !micGranted) {
            Label("권한")
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (!locationGranted) PrimaryButton("위치 허용", onRequestLocation, icon = Icons.Rounded.MyLocation, height = 80.dp)
                if (!micGranted) PrimaryButton("마이크 허용", onRequestMic, icon = Icons.Rounded.Mic, height = 80.dp)
            }
        }
        hiddenAuthors.forEach { a ->
            Row(Modifier.fillMaxWidth().heightIn(min = 112.dp), verticalAlignment = Alignment.CenterVertically) {
                ProfileCircle(authorNames[a] ?: a, 72.dp)
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Text("숨긴 사람", style = MaterialTheme.typography.titleMedium, color = Hz.Ink)
                    Text(authorNames[a] ?: a, style = MaterialTheme.typography.bodySmall, color = Hz.InkMuted)
                }
                SoftButton("다시 보기", { onUnhide(a) }, height = 72.dp, enabled = !busy)
            }
            Divider()
        }

        Label("데이터")
        if (devServerAvailable) {
            Segmented(
                listOf(BackendMode.LOCAL_DEMO.name to "기기 안", BackendMode.DEV_SERVER.name to "개발 서버"),
                backend.second.name, !busy,
            ) { onBackend(BackendMode.valueOf(it)) }
        } else {
            Text(backend.first, style = MaterialTheme.typography.bodyMedium, color = Hz.InkMuted)
        }
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        sync.loading -> "동기화 중"
                        sync.lastOkMillis != null -> "마지막 동기화 ${hm.format(Date(sync.lastOkMillis))}"
                        else -> "아직 동기화하지 않았어요"
                    },
                    style = MaterialTheme.typography.titleMedium, color = Hz.Ink,
                )
                Text(sync.error ?: "오류 없음", style = MaterialTheme.typography.bodySmall, color = if (sync.error != null) Hz.Red else Hz.InkMuted, maxLines = 1)
            }
            SoftButton("동기화", onRefresh, icon = Icons.Rounded.Refresh, height = 72.dp, enabled = !busy)
        }
        Spacer(Modifier.height(16.dp))
        Divider()
        Row(
            Modifier.fillMaxWidth().heightIn(min = 112.dp).clickable(onClick = onDiagnostics),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("정보 · 기술 진단", style = MaterialTheme.typography.titleMedium, color = Hz.Ink)
                Text("버전 ${BuildConfig.VERSION_NAME} · 샘플 음원 안내", style = MaterialTheme.typography.bodySmall, color = Hz.InkMuted)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Hz.InkMuted, modifier = Modifier.size(44.dp))
        }
        Divider()
    }
}

@Composable
private fun ToggleRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 112.dp).clickable { onChange(!checked) }.padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Hz.Ink)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = Hz.InkMuted)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            modifier = Modifier.scale(1.45f).padding(end = 14.dp),
            colors = SwitchDefaults.colors(
                checkedTrackColor = Hz.Green, checkedThumbColor = Hz.Bg, checkedBorderColor = Hz.Green,
                uncheckedTrackColor = Hz.SoftHigh, uncheckedThumbColor = Hz.Bg, uncheckedBorderColor = Hz.SoftHigh,
            ),
        )
    }
}

/** 계정 고르기 이름: 계정 이름(지금 계정은 최신 이름), 운영자는 "운영자" */
fun accountLabels(users: List<User>, me: User?): List<Pair<String, String>> =
    users.map { u -> u.id to if (u.role == UserRole.MODERATOR) "운영자" else (if (u.id == me?.id) me.displayName else u.displayName).shortName() }

private val hm = SimpleDateFormat("HH:mm", Locale.KOREA)
private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.KOREA)

/** 기술 진단: 위에 요약 카드, 아래에 실제 SDK에서 받은 값 자세히(초기화 반환과 콜백 수신을 구분) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiagnosticsScreen(
    ux: UxState,
    uxChanges: Int,
    location: LocationStatus,
    validity: LocationValidity,
    proximity: ProximityProbe,
    navi: NaviState,
    playback: PlaybackUi,
    places: List<Place>,
    logs: List<HeartzLog.Entry>,
    onProbeProximity: (Place) -> Unit,
    onNaviLocation: () -> Unit,
    onTestRoute: (Place) -> Unit,
    onCancelRoute: () -> Unit,
    onBack: () -> Unit,
    speech: Pair<String, String> = "-" to "-",
    onClearListens: (() -> Unit)? = null,
) {
    val now = System.currentTimeMillis()
    var details by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = ParkedPadding) {
        item {
            ScreenHeader("기술 진단", onBack = onBack) { Chip("개발용", ChipStyle.Soft) }
            SummaryCard(
                "주행 제한",
                when (ux) {
                    is UxState.Known -> if (ux.requiresDistractionOptimization) "주행 · 제한 있음" else "주차 · 제한 없음"
                    is UxState.Unknown -> "알 수 없음"
                },
                "CarUxRestrictions · 변경 ${uxChanges}회",
            )
            SummaryCard(
                "위치",
                when (location) {
                    is LocationStatus.Receiving -> HeartzLog.coord(location.fix.lat, location.fix.lng) +
                        (if (validity is LocationValidity.Valid) " · 사용 가능" else " · 오래됨")
                    LocationStatus.PermissionMissing -> "권한 없음"
                    else -> "받는 중 아님"
                },
                if (location is LocationStatus.Receiving) "FusedLocation · ${(now - location.receivedAtMillis) / 100 / 10.0}초 전" else location.toString(),
            )
            SummaryCard("내비", navi.navigationStatus ?: "상태 없음", "NaviHelper · ${navi.connection.name}")
            SummaryCard(
                "재생 · 오디오 포커스",
                if (playback.isPlaying) "재생 중" else "재생 없음",
                playback.lastFocusEvent ?: playback.error ?: "포커스 이벤트 없음",
            )
            SummaryCard("음성 엔진", "TTS · STT 기기 내", "TTS ${speech.first} · STT ${speech.second}")
            // 2초마다 오는 위치 콜백 말고 마지막으로 일어난 일을 보여 준다
            logs.lastOrNull { it.area != "location" }?.let { e ->
                SummaryCard("최근 로그", "${timeFmt.format(Date(e.atMillis))} ${e.area}", e.message)
            }
            if (BuildConfig.DEBUG && onClearListens != null) {
                Spacer(Modifier.height(8.dp))
                OutlineButton("받은 사연 기록 지우기", onClearListens, Modifier.fillMaxWidth(), Icons.Rounded.Delete, 88.dp)
                Spacer(Modifier.height(12.dp))
                Text(
                    "debug 빌드 전용 · 하트와 숨긴 사람은 그대로예요", style = MaterialTheme.typography.bodySmall, color = Hz.InkMuted,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(24.dp))
            SoftButton(if (details) "자세히 닫기" else "자세히 보기", { details = !details }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
        }
        if (details) {
            item { Details(ux, uxChanges, location, validity, proximity, navi, playback, places, speech, now, onProbeProximity, onNaviLocation, onTestRoute, onCancelRoute) }
            items(logs.takeLast(80).reversed()) { e ->
                Text(
                    "${timeFmt.format(Date(e.atMillis))} [${e.area}] ${e.message}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = Hz.InkMuted, modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(label: String, value: String, detail: String) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 16.dp).clip(RoundedCornerShape(28.dp)).background(Hz.Soft).padding(horizontal = 28.dp, vertical = 22.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Hz.InkMuted)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, color = Hz.Ink, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(detail, style = MaterialTheme.typography.bodySmall, color = Hz.InkMuted, maxLines = 2)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Details(
    ux: UxState,
    uxChanges: Int,
    location: LocationStatus,
    validity: LocationValidity,
    proximity: ProximityProbe,
    navi: NaviState,
    playback: PlaybackUi,
    places: List<Place>,
    speech: Pair<String, String>,
    now: Long,
    onProbeProximity: (Place) -> Unit,
    onNaviLocation: () -> Unit,
    onTestRoute: (Place) -> Unit,
    onCancelRoute: () -> Unit,
) {
    Column {
        Text(
            "앱이 실제 SDK에서 받은 값만 표시해요. 에뮬레이터 위치 입력(geo fix)은 테스트 입력이고, 여기 보이는 콜백이 앱 수신 증거예요.",
            style = MaterialTheme.typography.bodySmall, color = Hz.InkMuted,
        )
        Spacer(Modifier.height(16.dp))
        DiagPanel("주행 제한 (CarUxRestrictions)") {
            when (ux) {
                is UxState.Known -> {
                    Line("requiresDistractionOptimization", ux.requiresDistractionOptimization.toString())
                    Line("activeRestrictions", "0x${Integer.toHexString(ux.activeRestrictions)} " + UxRestrictionsMonitor.describe(ux.activeRestrictions).joinToString(","))
                }
                is UxState.Unknown -> Line("state", "Unknown (${ux.reason})")
            }
            Line("callbacks", uxChanges.toString())
        }
        DiagPanel("FusedLocation SDK") {
            when (location) {
                is LocationStatus.Receiving -> {
                    Line("status", "Receiving (callback)")
                    Line("fix", HeartzLog.coord(location.fix.lat, location.fix.lng) + " heading=${location.fix.headingDeg}")
                    Line("timestamp age", "${now - location.fix.timestampMillis} ms (received ${now - location.receivedAtMillis} ms ago)")
                    Line("callbacks / null", "${location.callbacks} / ${location.nullCallbacks}")
                    Line("confidence", location.confidence.toString())
                }
                else -> Line("status", location.toString())
            }
            Line("validity", validity.toString())
            if (location is LocationStatus.Receiving) {
                val inside = places.filter { Eligibility.inArea(it, location.fix) }.joinToString { it.listLabel }
                Line("in 1 km", inside.ifEmpty { "(none)" })
            }
        }
        DiagPanel("근접 알림 시험 (addProximityAlert, 편성에 미사용)") {
            Line("result", proximity.toString())
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 장소가 서울 전역(100여 곳)이라 시드 사연이 있는 용산구 랜드마크만 버튼으로 둔다
                places.filter { it.district == "용산구" }.forEach { p -> SoftButton(p.shortName, { onProbeProximity(p) }, height = 64.dp, bg = Hz.Bg) }
            }
        }
        DiagPanel("NaviHelper (비교용)") {
            Line("connection", navi.connection.name)
            Line("navigationStatus", (navi.navigationStatus ?: "(callback 없음)") + (navi.navigationStatusAtMillis?.let { " @${timeFmt.format(Date(it))}" } ?: ""))
            Line("current location", navi.lastLocation?.let { HeartzLog.coord(it.lat, it.lng) + " heading=${it.heading} @${timeFmt.format(Date(it.atMillis))}" } ?: "(요청 전 또는 응답 없음)")
            Line("route event", navi.lastRouteEvent ?: "-")
            Line("error", navi.lastError ?: "-")
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SoftButton("getCurrentLocationInfo()", onNaviLocation, height = 64.dp, bg = Hz.Bg)
                if (BuildConfig.DEBUG) {
                    places.lastOrNull { it.id == "p-seobinggo" }?.let { p -> SoftButton("시험 경로 → ${p.shortName} (debug)", { onTestRoute(p) }, height = 64.dp, bg = Hz.Bg) }
                    SoftButton("경로 취소 (debug)", onCancelRoute, height = 64.dp, bg = Hz.Bg)
                }
            }
        }
        DiagPanel("재생·오디오 포커스") {
            Line("controller", if (playback.connected) "connected" else "not connected")
            Line("story", playback.storyId ?: "-")
            Line("playing / buffering / ended", "${playback.isPlaying} / ${playback.isBuffering} / ${playback.ended}")
            Line("suppressed by focus", playback.suppressedByFocus.toString())
            Line("last focus event", playback.lastFocusEvent ?: "-")
            Line("error", playback.error ?: "-")
        }
        DiagPanel("Pleos TTS · STT (ON_DEVICE)") {
            Line("TextToSpeech", speech.first)
            Line("SpeechToText", speech.second)
        }
        DiagPanel("지나면서 녹음·전송 (기획서 6절)") {
            Line("all verified", DrivingRecordingGate.isEnabled().toString())
            DrivingRecordingGate.current.forEach { (c, s) -> Line(c.label, s.name, if (s == VerificationStatus.VERIFIED) Hz.Green else Hz.Warn) }
        }
        Text("최근 로그", style = MaterialTheme.typography.titleMedium, color = Hz.Ink, modifier = Modifier.padding(vertical = 12.dp))
    }
}

@Composable
private fun DiagPanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp).clip(RoundedCornerShape(28.dp)).background(Hz.Soft).padding(24.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Hz.Ink)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun Line(k: String, v: String, color: Color = Hz.Ink) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(k, style = MaterialTheme.typography.bodySmall, color = Hz.InkMuted, modifier = Modifier.weight(0.42f))
        Spacer(Modifier.width(16.dp))
        Text(v, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = color, modifier = Modifier.weight(0.58f))
    }
}
