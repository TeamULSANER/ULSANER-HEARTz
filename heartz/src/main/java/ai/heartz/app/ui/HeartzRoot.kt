package ai.heartz.app.ui

import ai.heartz.app.AppGraph
import ai.heartz.app.HeartzLog
import ai.heartz.app.data.SessionState
import ai.heartz.app.platform.audio.PlaybackUi
import ai.heartz.app.platform.location.PERMISSION_FUSED_LOCATION
import ai.heartz.app.startOfTodayMillis
import ai.heartz.app.ui.components.ProfileCircle
import ai.heartz.app.ui.components.Chip
import ai.heartz.app.ui.components.ChipStyle
import ai.heartz.app.ui.components.HzBadge
import ai.heartz.app.ui.components.HzToast
import ai.heartz.app.ui.components.OutlineButton
import ai.heartz.app.ui.components.PrimaryButton
import ai.heartz.app.ui.components.ScreenHeader
import ai.heartz.app.ui.components.SectionHeader
import ai.heartz.app.ui.components.shortName
import ai.heartz.app.ui.drive.CardActions
import ai.heartz.app.ui.drive.DriveUiState
import ai.heartz.app.ui.drive.DriveViewModel
import ai.heartz.app.ui.drive.DrivingScreen
import ai.heartz.app.ui.drive.InputSource
import ai.heartz.app.ui.drive.LocationChip
import ai.heartz.app.ui.drive.RoadsideScreen
import ai.heartz.app.ui.drive.RoadsideUi
import ai.heartz.app.ui.drive.StoryCard
import ai.heartz.app.ui.drive.WaitingCard
import ai.heartz.app.ui.library.LibraryViewModel
import ai.heartz.app.ui.library.MineScreen
import ai.heartz.app.ui.library.ParkedPadding
import ai.heartz.app.ui.library.PassedScreen
import ai.heartz.app.ui.library.StoryDetailScreen
import ai.heartz.app.ui.library.StoryRow
import ai.heartz.app.ui.moderation.ModerationScreen
import ai.heartz.app.ui.settings.DiagnosticsScreen
import ai.heartz.app.ui.settings.MyProfileButton
import ai.heartz.app.ui.settings.SettingsScreen
import ai.heartz.app.ui.studio.StudioScreen
import ai.heartz.app.ui.studio.StudioViewModel
import ai.heartz.app.ui.theme.Hz
import ai.heartz.app.ui.theme.tone
import ai.heartz.core.drive.StoryChoice
import ai.heartz.core.model.Place
import ai.heartz.core.model.Story
import ai.heartz.core.model.StoryStatus
import ai.heartz.core.model.UserRole
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Tab(val label: String, val icon: ImageVector) {
    NOW("지금 이 길", Icons.Rounded.Route),
    PASSED("지나온 사연", Icons.Rounded.History),
    COMPOSE("남기기", Icons.Rounded.Mic),
    MINE("내 사연함", Icons.Rounded.Inbox),
    REVIEW("검수", Icons.Rounded.VerifiedUser),
    SETTINGS("설정", Icons.Rounded.Settings),
}

@Composable
fun HeartzRoot(graph: AppGraph, drive: DriveViewModel, library: LibraryViewModel, studio: StudioViewModel) {
    val d by drive.state.collectAsStateWithLifecycle()

    // 주행 중 화면에서는 진행 위치를 갱신하지 않는다(움직이는 요소 최소화)
    LaunchedEffect(d.playback.isPlaying, d.drivingView) {
        while (d.playback.isPlaying && !d.drivingView) {
            graph.player.refreshPosition()
            delay(500)
        }
    }

    val roadside by graph.roadside.state.collectAsStateWithLifecycle()
    val roadsideEnabled by graph.roadsideEnabled.collectAsStateWithLifecycle()
    val voiceGuideEnabled by graph.voiceGuideEnabled.collectAsStateWithLifecycle()
    val actions = cardActions(graph, drive)

    Box(Modifier.fillMaxSize().background(Hz.Bg)) {
        if (roadside != RoadsideUi.Idle) {
            RoadsideScreen(
                roadside,
                onFinish = { graph.roadside.finish() },
                onDiscard = graph.roadside::cancel,
                onSubmit = { graph.roadside.answer(StoryChoice.SUBMIT, viaGleo = false) },
                onRerecord = { graph.roadside.rerecord(viaGleo = false) },
                onVoice = { graph.roadside.chooseVoice(it, viaGleo = false) },
                onDismiss = graph.roadside::dismiss,
                location = { LocationChip(d) },
            )
        } else if (d.drivingView) {
            DrivingScreen(
                d, actions,
                roadsideEnabled = roadsideEnabled,
                voiceOn = voiceGuideEnabled,
                onRoadside = {
                    drive.cancelVoice()
                    graph.roadside.start(InputSource.TOUCH)
                },
            )
        } else {
            ParkedShell(graph, d, drive, library, studio, actions)
        }
    }
}

private fun cardActions(graph: AppGraph, drive: DriveViewModel) = CardActions(
    play = { id -> graph.repository.feed.value.firstOrNull { it.id == id }?.let(drive::play) },
    pause = drive::pause,
    resume = drive::resume,
    stop = drive::stopPlayback,
    open = { drive.openCard(viaGleo = false) },
    skip = { drive.skipCard() },
    heart = { drive.heartCard(viaGleo = false) },
    noHeart = { drive.noHeart(viaGleo = false) },
)

@Composable
private fun ParkedShell(
    graph: AppGraph,
    d: DriveUiState,
    drive: DriveViewModel,
    library: LibraryViewModel,
    studio: StudioViewModel,
    actions: CardActions,
) {
    val context = LocalContext.current
    val repo = graph.repository
    val session by repo.session.collectAsStateWithLifecycle()
    val places by repo.places.collectAsStateWithLifecycle()
    val feed by repo.feed.collectAsStateWithLifecycle()
    val listen by repo.listenState.collectAsStateWithLifecycle()
    val mine by repo.myStories.collectAsStateWithLifecycle()
    val pending by repo.pendingDrafts.collectAsStateWithLifecycle()
    val sync by repo.sync.collectAsStateWithLifecycle()
    val backend by repo.backendInfo.collectAsStateWithLifecycle()
    val lib by library.state.collectAsStateWithLifecycle()
    val st by studio.state.collectAsStateWithLifecycle()
    val navi by graph.navi.state.collectAsStateWithLifecycle()
    val proximity by graph.location.proximity.collectAsStateWithLifecycle()
    val uxChanges by graph.ux.changes.collectAsStateWithLifecycle()
    val logs by HeartzLog.entries.collectAsStateWithLifecycle()
    val roadsideEnabled by graph.roadsideEnabled.collectAsStateWithLifecycle()
    val voiceGuideEnabled by graph.voiceGuideEnabled.collectAsStateWithLifecycle()
    val ttsState by graph.tts.state.collectAsStateWithLifecycle()
    val sttState by graph.stt.state.collectAsStateWithLifecycle()
    val me = (session as? SessionState.SignedIn)?.user
    val placeMap = places.associateBy { it.id }

    var tab by rememberSaveable { mutableStateOf(Tab.NOW) }
    var diagnostics by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var locationGranted by remember { mutableStateOf(graph.location.hasPermission()) }
    var micGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        locationGranted = ok
        HeartzLog.i("permission", "$PERMISSION_FUSED_LOCATION granted=$ok")
        if (ok) {
            graph.location.stop()
            graph.location.start()
        }
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { micGranted = it }
    // 다른 화면(녹음)이나 시스템 설정에서 바뀐 권한을 탭 전환 때 다시 읽는다
    LaunchedEffect(tab) {
        locationGranted = graph.location.hasPermission()
        micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
    var askedLocation by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(locationGranted) {
        if (!locationGranted && !askedLocation) {
            askedLocation = true
            locationLauncher.launch(PERMISSION_FUSED_LOCATION)
        }
    }

    LaunchedEffect(lib.toast) {
        lib.toast?.let {
            snackbar.showSnackbar(it)
            library.clearToast()
        }
    }
    LaunchedEffect(d.message) {
        d.message?.let {
            snackbar.showSnackbar(it)
            drive.dismissMessage()
        }
    }
    LaunchedEffect(tab, me?.id) {
        when (tab) {
            Tab.REVIEW -> library.loadModeration()
            Tab.SETTINGS -> library.loadTestUsers()
            Tab.COMPOSE -> if (st.step == ai.heartz.app.ui.studio.StudioStep.PICK_PLACE && st.places.isEmpty()) studio.begin()
            else -> Unit
        }
        if (tab == Tab.MINE || tab == Tab.PASSED) library.refresh()
    }
    // 주행 중 → 주차 전환 후 다른 계정으로 바뀌면 검수 탭에서 벗어난다
    if (tab == Tab.REVIEW && me?.role != UserRole.MODERATOR) tab = Tab.NOW

    // 시스템 뒤로 가기: 상세·기술 진단을 닫는다
    androidx.activity.compose.BackHandler(enabled = diagnostics || lib.detail != null) {
        when {
            diagnostics -> diagnostics = false
            else -> library.closeDetail()
        }
    }

    val today = startOfTodayMillis()
    val unplayed = ai.heartz.core.schedule.PassedLetters.unplayed(feed, listen, today)
    val unread = unplayed.size
    val onPlayStory: (Story) -> Unit = { drive.play(it) }
    // 내 프로필(첫 글자 원). 누르면 설정으로
    val openProfile = { diagnostics = false; library.closeDetail(); tab = Tab.SETTINGS }
    val myName = me?.displayName
    val faceButton: @Composable () -> Unit = { if (myName != null) MyProfileButton(myName, openProfile) }

    Row(Modifier.fillMaxSize()) {
        Rail(
            tabs = Tab.entries.filter { it != Tab.REVIEW || me?.role == UserRole.MODERATOR },
            selected = tab,
            highlight = !diagnostics,
            unread = unread,
            onSelect = { t ->
                diagnostics = false
                library.closeDetail()
                if (t == Tab.COMPOSE && tab != Tab.COMPOSE) studio.begin()
                tab = t
            },
        )
        Box(Modifier.width(1.dp).fillMaxHeight().background(Hz.Line))
        Box(Modifier.weight(1f).fillMaxHeight()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.widthIn(max = 1100.dp).fillMaxSize()) {
                    val detail = lib.detail
                    when {
                        diagnostics -> DiagnosticsScreen(
                            ux = d.ux, uxChanges = uxChanges, location = d.location, validity = d.validity,
                            proximity = proximity, navi = navi, playback = d.playback, places = places, logs = logs,
                            onProbeProximity = graph.location::probeProximity,
                            onNaviLocation = graph.navi::requestCurrentLocation,
                            onTestRoute = { p -> graph.navi.requestTestRoute(p.publicLocation.lat, p.publicLocation.lng, p.displayName) },
                            onCancelRoute = graph.navi::cancelTestRoute,
                            onBack = { diagnostics = false },
                            speech = ttsState.toString() to sttState.toString(),
                            onClearListens = {
                                scope.launch {
                                    val ok = graph.repository.clearListenHistoryForTesting()
                                    drive.resetSessionForTesting()
                                    snackbar.showSnackbar(if (ok) "받은 사연 기록을 지웠어요" else "기기 안 데모 데이터에서만 지울 수 있어요")
                                }
                            },
                        )
                        detail != null -> StoryDetailScreen(
                            detail, placeMap, me, d.playback, lib.busy,
                            onBack = library::closeDetail,
                            onPlay = onPlayStory,
                            onPause = drive::pause,
                            hearted = listen.hearted.keys,
                            onHeart = { id, on -> drive.toggleHeart(id, on); library.refreshDetailSoon(id) },
                            onReport = library::report,
                            onHideAuthor = library::hideAuthor,
                            onDelete = library::deleteMine,
                            onSubmit = library::submitPrivate,
                        )
                        else -> when (tab) {
                            Tab.NOW -> NowScreen(
                                d, voiceGuideEnabled, actions, unplayed.map { it.first }, placeMap, faceButton,
                                onOpen = library::openDetail,
                                onOpenPassed = { tab = Tab.PASSED },
                                onCompose = {
                                    studio.begin()
                                    tab = Tab.COMPOSE
                                },
                            )
                            Tab.PASSED -> PassedScreen(feed, placeMap, listen, onOpen = library::openDetail) {
                                ScreenHeader("지나온 사연") { faceButton() }
                            }
                            Tab.COMPOSE -> StudioScreen(st, studio, myName, openProfile, onOpenMine = { tab = Tab.MINE })
                            Tab.MINE -> MineScreen(
                                mine, pending, placeMap, myName ?: "",
                                onOpen = library::openDetail,
                                onRetry = { draft ->
                                    studio.retry(draft) { outcome ->
                                        library.toast(
                                            when (outcome) {
                                                is ai.heartz.app.data.SaveOutcome.Saved ->
                                                    if (outcome.status == StoryStatus.PENDING_REVIEW) "검수에 보냈어요" else "보냈어요"
                                                is ai.heartz.app.data.SaveOutcome.Deferred -> "아직 보내지 못했어요: ${outcome.reason}"
                                            },
                                        )
                                    }
                                },
                                onDiscard = studio::discardPending,
                                onCompose = {
                                    studio.begin()
                                    tab = Tab.COMPOSE
                                },
                            ) { ScreenHeader("내 사연함") { faceButton() } }
                            Tab.REVIEW -> ModerationScreen(
                                lib.moderation, lib.moderationLoading, lib.moderationError, placeMap, d.playback, lib.busy,
                                onRefresh = library::loadModeration, onPlay = { drive.play(it, original = true) }, onPause = drive::pause,
                                onModerate = library::moderate,
                            )
                            Tab.SETTINGS -> SettingsScreen(
                                me = me, users = lib.testUsers, backend = backend,
                                devServerAvailable = graph.settings.devServerAvailable, sync = sync,
                                hiddenAuthors = listen.hiddenAuthorIds, locationGranted = locationGranted, micGranted = micGranted,
                                busy = lib.busy,
                                onSignIn = library::signIn, onBackend = library::switchBackend, onRefresh = library::refresh,
                                onRequestLocation = { locationLauncher.launch(PERMISSION_FUSED_LOCATION) },
                                onRequestMic = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                                onUnhide = library::unhideAuthor,
                                onDiagnostics = { diagnostics = true },
                                roadsideEnabled = roadsideEnabled,
                                onRoadside = { on ->
                                    if (on && !micGranted) micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    graph.setRoadsideEnabled(on)
                                },
                                voiceGuideEnabled = voiceGuideEnabled,
                                onVoiceGuide = graph::setVoiceGuideEnabled,
                                authorNames = feed.associate { it.authorId to it.authorName.shortName() },
                            )
                        }
                    }
                }
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                SnackbarHost(snackbar) { data -> HzToast(data.visuals.message, Icons.Rounded.Check) }
                MiniPlayer(d.playback, feed + mine, placeMap, onPause = drive::pause, onResume = drive::resume, onStop = drive::stopPlayback)
            }
        }
    }
}

/** 왼쪽 메뉴: Hz 표시 + 탭(선택은 초록 알약), 지나온 사연에 안 들은 수 */
@Composable
private fun Rail(tabs: List<Tab>, selected: Tab, highlight: Boolean, unread: Int, onSelect: (Tab) -> Unit) {
    Column(
        Modifier.width(132.dp).fillMaxHeight().background(Hz.Bg).verticalScroll(rememberScrollState()).padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HzBadge(68.dp)
        Spacer(Modifier.height(32.dp))
        tabs.forEach { t ->
            val on = highlight && t == selected
            Column(
                Modifier.fillMaxWidth().clickable { onSelect(t) }.padding(vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box {
                    Box(
                        Modifier.width(80.dp).height(48.dp).clip(RoundedCornerShape(percent = 50)).background(if (on) Hz.Green else Hz.Bg),
                        contentAlignment = Alignment.Center,
                    ) { Icon(t.icon, null, tint = Hz.Ink, modifier = Modifier.size(36.dp)) }
                    if (t == Tab.PASSED && unread > 0) {
                        Box(
                            Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-8).dp).size(34.dp).clip(CircleShape).background(Hz.Ink),
                            contentAlignment = Alignment.Center,
                        ) { Text("$unread", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Hz.Bg) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    t.label,
                    // 메뉴 이름은 시안대로 작게(본문이 아닌 탐색 표시라 26sp 예외)
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Medium),
                    color = if (on) Hz.Ink else Hz.InkMuted, maxLines = 1,
                )
            }
        }
    }
}

/** 주차 중 "지금 이 길": 카드(또는 대기) + 바로 가기 + 아직 안 들은 사연 */
@Composable
private fun NowScreen(
    d: DriveUiState,
    voiceOn: Boolean,
    actions: CardActions,
    unplayed: List<Story>,
    places: Map<String, Place>,
    faceButton: @Composable () -> Unit,
    onOpen: (String) -> Unit,
    onOpenPassed: () -> Unit,
    onCompose: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ParkedPadding)) {
        ScreenHeader("지금 이 길") {
            Chip("주차 중", ChipStyle.Soft, height = 56.dp)
            Spacer(Modifier.width(14.dp))
            faceButton()
        }
        val card = d.card
        if (card != null) {
            StoryCard(card, d.playback, d.voice, driving = false, voiceOn = voiceOn, actions = actions, modifier = Modifier.fillMaxWidth())
        } else {
            WaitingCard(d, Modifier.fillMaxWidth(), plain = true)
        }
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlineButton("지나온 사연", onOpenPassed, Modifier.weight(1f), Icons.Rounded.History, 88.dp)
            PrimaryButton("남기기", onCompose, Modifier.weight(1f), Icons.Rounded.Mic, 88.dp)
        }
        val rows = unplayed.filter { it.id != card?.storyId }.take(3)
        if (rows.isNotEmpty()) {
            SectionHeader("아직 안 들은 사연") {
                Text(
                    "모두 보기", style = MaterialTheme.typography.bodyMedium, color = Hz.InkMuted,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpenPassed).padding(10.dp),
                )
            }
            rows.forEach { s -> StoryRow(s, places, "보낸 이 ${s.authorName.shortName()}", unread = true, onClick = { onOpen(s.id) }) }
        }
        Spacer(Modifier.height(140.dp))
    }
}

/** 주차 중 아래 미니 플레이어: 진행 줄, 얼굴, 제목, 보낸 이, 일시정지, 닫기 */
@Composable
private fun MiniPlayer(
    p: PlaybackUi,
    stories: List<Story>,
    places: Map<String, Place>,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    if (p.storyId == null || p.ended || p.idle) return
    val s = stories.firstOrNull { it.id == p.storyId }
    Surface(color = Hz.Card, shape = RoundedCornerShape(36.dp), modifier = Modifier.widthIn(max = 1000.dp).fillMaxWidth().padding(top = 12.dp)) {
        Column {
            if (p.durationMs > 0) {
                val frac = (p.positionMs.toFloat() / p.durationMs).coerceIn(0f, 1f)
                Box(Modifier.fillMaxWidth().height(6.dp).background(Hz.CardChip)) {
                    Box(Modifier.fillMaxWidth(frac).height(6.dp).background(Hz.Green))
                }
            }
            Row(Modifier.padding(horizontal = 24.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                if (s != null) {
                    ProfileCircle(s.authorName, 84.dp)
                    Spacer(Modifier.width(22.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        s?.let { "${places[it.placeId]?.shortName ?: "어딘가"}의 사연" } ?: (p.title ?: "사연"),
                        style = MaterialTheme.typography.titleMedium, color = Hz.OnCard, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(s?.let { "보낸 이 ${it.authorName.shortName()}" }, if (p.isPlaying) "재생 중" else "잠시 멈춤").joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = Hz.OnCardMuted, maxLines = 1,
                    )
                }
                Surface(onClick = { if (p.isPlaying) onPause() else onResume() }, shape = CircleShape, color = Hz.Green, modifier = Modifier.size(84.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(if (p.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (p.isPlaying) "일시정지" else "재생", tint = Hz.Ink, modifier = Modifier.size(48.dp))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Box(Modifier.size(72.dp).clip(CircleShape).clickable(onClick = onStop), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Close, "그만 듣기", tint = Hz.OnCardMuted, modifier = Modifier.size(40.dp))
                }
            }
        }
    }
}
