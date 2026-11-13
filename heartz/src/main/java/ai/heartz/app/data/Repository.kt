package ai.heartz.app.data

import ai.heartz.app.BuildConfig
import ai.heartz.app.HeartzLog
import ai.heartz.core.model.ListenKind
import ai.heartz.core.model.ListenState
import ai.heartz.core.model.Place
import ai.heartz.core.model.Story
import ai.heartz.core.model.User
import ai.heartz.core.service.ApiException
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val user: User) : SessionState
}

data class SyncStatus(val loading: Boolean = false, val error: String? = null, val lastOkMillis: Long? = null)

/** 주행 화면(편지 카드)이 쓰는 데이터. 테스트에서는 fake로 대체한다. */
interface LetterData {
    val session: StateFlow<SessionState>
    val places: StateFlow<List<Place>>
    val feed: StateFlow<List<Story>>
    val listenState: StateFlow<ListenState>
    suspend fun recordListen(storyId: String, kind: ListenKind)
    suspend fun audioFile(story: Story): File
    /** 공감 하트. 실패하면 예외 */
    suspend fun heart(storyId: String, on: Boolean)
}

fun Throwable.userMessage(): String = when (this) {
    is ApiException -> message
    else -> message ?: this::class.java.simpleName
}

class Settings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("heartz", Context.MODE_PRIVATE)

    var backendMode: BackendMode
        get() = runCatching { BackendMode.valueOf(prefs.getString("backend", null) ?: "") }.getOrDefault(BackendMode.LOCAL_DEMO)
        set(v) = prefs.edit().putString("backend", v.name).apply()

    var lastUserId: String?
        get() = prefs.getString("lastUser", null)
        set(v) = prefs.edit().putString("lastUser", v).apply()

    /** 지나면서 남기기. 주차 중 설정에서 켜고 끈다. 기본 켜짐(2026-11-10 사용자 요청: 주행 중 음성 흐름의 기본 기능) */
    var roadsideEnabled: Boolean
        get() = prefs.getBoolean("roadsideEnabled2", true)
        set(v) = prefs.edit().putBoolean("roadsideEnabled2", v).apply()

    /** 주행 중 편지 도착을 음성으로 알리고 답을 듣기. 기본 켜짐 */
    var voiceGuideEnabled: Boolean
        get() = prefs.getBoolean("voiceGuideEnabled", true)
        set(v) = prefs.edit().putBoolean("voiceGuideEnabled", v).apply()

    val devServerUrl: String get() = BuildConfig.DEV_SERVER_URL
    val devServerAvailable: Boolean get() = devServerUrl.isNotBlank()

}

/**
 * 계정·편성 데이터·청취 상태의 단일 소유자. 백엔드 호출은 IO에서, 상태는 StateFlow로 노출한다.
 * 오류는 빈 목록이나 성공으로 바꾸지 않고 [sync]에 남긴다.
 */
class HeartzRepository(
    context: Context,
    private val settings: Settings,
    private val scope: CoroutineScope,
) : LetterData {
    private val appContext = context.applicationContext
    private val audioDir = File(appContext.cacheDir, "audio")
    val drafts = DraftStore(File(appContext.filesDir, "pending-drafts.json"))
    val recordingsDir = File(appContext.filesDir, "recordings").apply { mkdirs() }

    private val mutex = Mutex()
    var backend: HeartzBackend = createBackend(settings.backendMode)
        private set

    private val _session = MutableStateFlow<SessionState>(SessionState.Loading)
    override val session: StateFlow<SessionState> = _session.asStateFlow()
    private val _places = MutableStateFlow<List<Place>>(emptyList())
    override val places: StateFlow<List<Place>> = _places.asStateFlow()
    private val _feed = MutableStateFlow<List<Story>>(emptyList())
    override val feed: StateFlow<List<Story>> = _feed.asStateFlow()
    private val _listen = MutableStateFlow(ListenState())
    override val listenState: StateFlow<ListenState> = _listen.asStateFlow()
    private val _mine = MutableStateFlow<List<Story>>(emptyList())
    val myStories: StateFlow<List<Story>> = _mine.asStateFlow()
    private val _pending = MutableStateFlow<List<PendingDraft>>(emptyList())
    val pendingDrafts: StateFlow<List<PendingDraft>> = _pending.asStateFlow()
    private val _sync = MutableStateFlow(SyncStatus())
    val sync: StateFlow<SyncStatus> = _sync.asStateFlow()
    private val _backendInfo = MutableStateFlow(backend.label to backend.mode)
    val backendInfo: StateFlow<Pair<String, BackendMode>> = _backendInfo.asStateFlow()

    /** 백엔드 반영에 실패한 청취 기록. 다음 동기화 때 다시 보낸다. */
    private val unsentListens = mutableListOf<Pair<String, ListenKind>>()

    private fun createBackend(mode: BackendMode): HeartzBackend =
        if (mode == BackendMode.DEV_SERVER && settings.devServerAvailable) RemoteBackend(settings.devServerUrl) else LocalDemoBackend(appContext)

    val currentUser: User? get() = (session.value as? SessionState.SignedIn)?.user

    /** 앱 시작 시 마지막 테스트 계정, 없으면 첫 테스트 계정(장클로드)으로 로그인한다. */
    suspend fun bootstrap() {
        val target = settings.lastUserId
        try {
            val users = backend.testUsers()
            val id = users.firstOrNull { it.id == target }?.id ?: users.firstOrNull()?.id
            if (id == null) {
                _session.value = SessionState.SignedOut
                return
            }
            signIn(id)
        } catch (e: Exception) {
            _session.value = SessionState.SignedOut
            _sync.update { it.copy(loading = false, error = e.userMessage()) }
            HeartzLog.i("data", "bootstrap failed ${e.userMessage()}")
        }
    }

    suspend fun testUsers(): List<User> = backend.testUsers()

    suspend fun signIn(userId: String) {
        val user = backend.signIn(userId)
        settings.lastUserId = user.id
        _session.value = SessionState.SignedIn(user)
        HeartzLog.i("data", "signed in test account=${user.id} backend=${backend.mode}")
        refresh()
    }

    suspend fun switchBackend(mode: BackendMode) {
        mutex.withLock {
            backend.signOut()
            backend = createBackend(mode)
            settings.backendMode = backend.mode
            _backendInfo.value = backend.label to backend.mode
            clearData()
        }
        _session.value = SessionState.Loading
        bootstrap()
    }

    private fun clearData() {
        _places.value = emptyList()
        _feed.value = emptyList()
        _listen.value = ListenState()
        _mine.value = emptyList()
        _pending.value = emptyList()
        synchronized(unsentListens) { unsentListens.clear() }
    }

    suspend fun refresh() {
        val user = currentUser ?: return
        _sync.update { it.copy(loading = true) }
        try {
            flushListens()
            val p = backend.places()
            val f = backend.feed()
            val l = backend.listenState()
            val m = backend.myStories()
            _places.value = p
            _feed.value = f
            _listen.value = l
            _mine.value = m
            _pending.value = drafts.list(user.id).filter { it.backendMode == backend.mode }
            _sync.value = SyncStatus(loading = false, error = null, lastOkMillis = System.currentTimeMillis())
            HeartzLog.i("data", "refresh places=${p.size} feed=${f.size} mine=${m.size}")
        } catch (e: Exception) {
            _sync.update { it.copy(loading = false, error = e.userMessage()) }
            _pending.value = drafts.list(user.id).filter { it.backendMode == backend.mode }
            HeartzLog.i("data", "refresh failed ${e.userMessage()}")
        }
    }

    fun reloadPendingDrafts() {
        val user = currentUser ?: return
        _pending.value = drafts.list(user.id).filter { it.backendMode == backend.mode }
    }

    override suspend fun recordListen(storyId: String, kind: ListenKind) {
        val now = System.currentTimeMillis()
        _listen.update {
            when (kind) {
                ListenKind.SUGGESTED -> it.copy(suggested = it.suggested + (storyId to (it.suggested[storyId] ?: now)))
                ListenKind.PLAYED -> it.copy(played = it.played + (storyId to now))
            }
        }
        try {
            _listen.value = mergeListen(_listen.value, backend.recordListen(storyId, kind))
        } catch (e: Exception) {
            synchronized(unsentListens) { unsentListens += storyId to kind }
            HeartzLog.i("data", "recordListen $kind deferred: ${e.userMessage()}")
        }
    }

    private suspend fun flushListens() {
        val batch = synchronized(unsentListens) { unsentListens.toList().also { unsentListens.clear() } }
        for ((id, kind) in batch) {
            try {
                backend.recordListen(id, kind)
            } catch (e: Exception) {
                synchronized(unsentListens) { unsentListens += id to kind }
                throw e
            }
        }
    }

    private fun mergeListen(local: ListenState, remote: ListenState) = remote.copy(
        suggested = local.suggested + remote.suggested,
        played = local.played + remote.played,
    )

    override suspend fun audioFile(story: Story): File = withContext(Dispatchers.IO) {
        val ref = story.audio ?: throw IllegalStateException("이 사연에는 음원이 없습니다")
        // 같은 blob이라도 내용(크기)이 바뀌면 새로 받는다(시드 음원 교체 등)
        val f = File(audioDir, "${backend.mode.name.lowercase()}-${story.id}-${ref.blobId}-${ref.sizeBytes}.m4a")
        if (!f.isFile || f.length() == 0L) backend.downloadAudio(story.id, f)
        f
    }

    override suspend fun heart(storyId: String, on: Boolean) {
        val remote = backend.heart(storyId, on)
        _listen.value = mergeListen(_listen.value, remote)
        // 내 사연함의 하트 수는 다음 동기화 때 서버에서 다시 읽는다
    }

    /** debug 전용. 기기 안 데모 백엔드일 때만 지운다. 지웠으면 true */
    suspend fun clearListenHistoryForTesting(): Boolean {
        val b = backend as? LocalDemoBackend ?: return false
        _listen.value = b.clearListenHistory()
        HeartzLog.i("data", "listen history cleared (debug)")
        return true
    }

    suspend fun hideAuthor(authorId: String) {
        _listen.value = mergeListen(_listen.value, backend.hideAuthor(authorId))
    }

    suspend fun unhideAuthor(authorId: String) {
        _listen.value = backend.unhideAuthor(authorId).let { mergeListen(_listen.value.copy(hiddenAuthorIds = emptySet()), it) }
    }
}
