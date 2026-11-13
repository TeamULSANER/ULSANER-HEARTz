package ai.heartz.app.platform.audio

import ai.heartz.app.HeartzLog
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.io.File

data class PlayRequest(
    val storyId: String,
    /** 원본 목소리 음원. 변조 목소리 사연이면 null */
    val file: File?,
    val title: String,
    val subtitle: String,
    /** 변조 목소리 사연: TTS로 읽을 받아쓴 글 */
    val spokenText: String? = null,
    val durationMs: Long = 0,
)

data class PlaybackUi(
    val connected: Boolean = false,
    val storyId: String? = null,
    val title: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val ended: Boolean = false,
    /** 정지(IDLE) 상태. 포커스를 들고 있지 않으며 화면에는 재생 항목이 없는 것으로 보인다. */
    val idle: Boolean = true,
    /** 일시적 포커스 상실(내비 안내·Gleo 등)로 재생이 멈춘 상태 */
    val suppressedByFocus: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val lastFocusEvent: String? = null,
    val error: String? = null,
)

/**
 * 플레이어 상태 → 완료 여부. 서비스가 끝까지 재생한 뒤 곧바로 stop()(IDLE)하므로
 * 컨트롤러가 ENDED를 건너뛰고 IDLE만 받을 수 있다. IDLE이어도 위치가 끝에 닿아 있으면 완료로 본다.
 */
fun isCompleted(playbackState: Int, positionMs: Long, durationMs: Long): Boolean = when (playbackState) {
    Player.STATE_ENDED -> true
    Player.STATE_IDLE -> durationMs > 0 && positionMs >= durationMs - COMPLETION_TOLERANCE_MS
    else -> false
}

private const val COMPLETION_TOLERANCE_MS = 500L

/** 사연 재생 명령. 화면 로직 테스트에서는 fake로 대체한다. 재생은 사용자 동작이나 사용자가 켠 주행 중 자동 읽기로만 시작한다. */
interface LetterPlayer {
    val state: StateFlow<PlaybackUi>
    fun play(req: PlayRequest)
    fun pause()
    fun resume()
    fun stop()
}

const val EXTRA_STORY_ID = "heartz.storyId"

/**
 * HeartzPlaybackService(MediaLibraryService)에 붙는 MediaController.
 * 화면이 보이는 동안 연결하고, 화면이 사라져도 재생은 서비스에서 계속된다.
 */
class PlaybackController(context: Context) : LetterPlayer {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(PlaybackUi())
    override val state: StateFlow<PlaybackUi> = _state.asStateFlow()

    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val pending = mutableListOf<(MediaController) -> Unit>()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish(player)

        override fun onPlayerError(error: PlaybackException) {
            _state.update { it.copy(error = error.errorCodeName) }
        }
    }

    /** 서비스의 LetterFocus가 세션 extras로 알리는 포커스 상태 */
    private val sessionListener = object : MediaController.Listener {
        override fun onExtrasChanged(controller: MediaController, extras: Bundle) = applyFocusExtras(extras)
    }

    private fun applyFocusExtras(extras: Bundle) {
        val paused = extras.getBoolean(LetterFocus.EXTRA_FOCUS_PAUSED, false)
        val lost = extras.getBoolean(LetterFocus.EXTRA_FOCUS_LOST, false)
        _state.update {
            it.copy(
                suppressedByFocus = paused,
                lastFocusEvent = when {
                    paused -> "다른 소리 때문에 잠시 멈춤"
                    lost -> "다른 소리 때문에 멈춤"
                    else -> null
                },
            )
        }
    }

    fun connect() {
        if (future != null) return
        val token = SessionToken(appContext, ComponentName(appContext, HeartzPlaybackService::class.java))
        val f = MediaController.Builder(appContext, token).setListener(sessionListener).buildAsync()
        future = f
        f.addListener({
            val c = runCatching { f.get() }.getOrNull()
            if (c == null) {
                _state.update { it.copy(connected = false, error = "playback service unavailable") }
                future = null
                return@addListener
            }
            controller = c
            c.addListener(listener)
            applyFocusExtras(c.sessionExtras)
            publish(c)
            pending.toList().forEach { it(c) }
            pending.clear()
        }, MoreExecutors.directExecutor())
    }

    fun disconnect() {
        controller?.removeListener(listener)
        future?.let { MediaController.releaseFuture(it) }
        future = null
        controller = null
        _state.update { it.copy(connected = false) }
    }

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) block(c) else {
            pending += block
            connect()
        }
    }

    /** 진행 위치 갱신용. 주차 화면에서만 주기적으로 호출한다. */
    fun refreshPosition() {
        controller?.let { publish(it) }
    }

    private fun publish(p: Player) {
        val item = p.currentMediaItem
        val position = p.currentPosition.coerceAtLeast(0)
        val duration = p.duration.takeIf { d -> d > 0 } ?: 0
        _state.update {
            it.copy(
                connected = true,
                storyId = item?.mediaMetadata?.extras?.getString(EXTRA_STORY_ID) ?: item?.mediaId,
                title = item?.mediaMetadata?.title?.toString(),
                isPlaying = p.isPlaying,
                isBuffering = p.playbackState == Player.STATE_BUFFERING,
                ended = item != null && isCompleted(p.playbackState, position, duration),
                idle = p.playbackState == Player.STATE_IDLE,
                positionMs = position,
                durationMs = duration,
                error = if (p.playerError != null) p.playerError?.errorCodeName else it.error.takeIf { _ -> p.playbackState != Player.STATE_READY },
            )
        }
    }

    override fun play(req: PlayRequest) = withController { c ->
        val file = req.file
        if (file == null) {
            _state.update { it.copy(error = "음원이 없어요") }
            return@withController
        }
        val extras = Bundle().apply { putString(EXTRA_STORY_ID, req.storyId) }
        val item = MediaItem.Builder()
            .setMediaId(req.storyId)
            .setUri(Uri.fromFile(file))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(req.title)
                    .setArtist(req.subtitle)
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                    .setExtras(extras)
                    .build(),
            )
            .build()
        _state.update { it.copy(error = null, lastFocusEvent = null) }
        c.setMediaItem(item)
        c.prepare()
        c.play()
        HeartzLog.i("playback", "user action play story=${req.storyId}")
    }

    override fun pause() = withController { it.pause(); HeartzLog.i("playback", "user action pause") }
    override fun resume() = withController { it.play(); HeartzLog.i("playback", "user action resume") }
    override fun stop() = withController {
        it.stop()
        it.clearMediaItems()
        HeartzLog.i("playback", "user action stop")
    }
}

/**
 * 원본 목소리는 미디어 서비스([PlaybackController])로, 변조 목소리는 [TtsLetterReader]로 보낸다.
 * 화면에는 지금 사연을 들고 있는 쪽의 상태를 보여 준다.
 */
class HeartzPlayer(
    val media: PlaybackController,
    private val reader: TtsLetterReader,
    scope: kotlinx.coroutines.CoroutineScope,
) : LetterPlayer {
    override val state: StateFlow<PlaybackUi> = kotlinx.coroutines.flow.combine(media.state, reader.state) { m, r ->
        if (r.storyId != null || r.error != null) r.copy(connected = m.connected) else m
    }.stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, PlaybackUi())

    override fun play(req: PlayRequest) {
        if (req.spokenText != null) {
            media.stop()
            reader.play(req)
        } else {
            reader.stop()
            media.play(req)
        }
    }

    override fun pause() = if (reader.active) reader.pause() else media.pause()
    override fun resume() = if (reader.active) reader.resume() else media.resume()
    override fun stop() {
        if (reader.active) reader.stop()
        media.stop()
    }

    fun connect() = media.connect()
    fun disconnect() = media.disconnect()
    fun refreshPosition() = media.refreshPosition()
}
