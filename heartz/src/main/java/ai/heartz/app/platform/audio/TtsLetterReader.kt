package ai.heartz.app.platform.audio

import ai.heartz.app.HeartzLog
import ai.heartz.app.platform.speech.SpeakResult
import android.media.AudioManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 변조 목소리 사연을 읽는 동안 다른 소리를 멈춰 두는 포커스. 거절되면 읽지 않는다. */
interface ReadFocus {
    fun acquire(onChange: (Int) -> Unit): Boolean
    fun release()
}

/**
 * 변조 목소리 사연: 원본 음성 대신 받아쓴 글([PlayRequest.spokenText])을 Pleos TTS로 읽는다.
 * 문장 단위로 읽어 멈춤·이어 듣기를 지원한다. 끝까지 읽어야만 ended, 오류·정지는 끝으로 치지 않는다.
 * 미디어 세션(시스템 미디어 화면)에는 나오지 않는다.
 */
class TtsLetterReader(
    private val scope: CoroutineScope,
    private val speak: suspend (String) -> SpeakResult,
    private val stopSpeech: () -> Unit,
    private val focus: ReadFocus,
) : LetterPlayer {
    private val _state = MutableStateFlow(PlaybackUi(connected = true))
    override val state: StateFlow<PlaybackUi> = _state.asStateFlow()

    private var req: PlayRequest? = null
    private var parts: List<String> = emptyList()
    private var index = 0
    private var job: Job? = null
    private var focused = false

    /** 지금 이 리더가 사연을 들고 있는지(재생·멈춤·끝 포함) */
    val active: Boolean get() = _state.value.storyId != null

    override fun play(req: PlayRequest) {
        halt()
        releaseFocus()
        val text = req.spokenText?.trim().orEmpty()
        if (text.isEmpty()) {
            HeartzLog.i("reader", "no spoken text story=${req.storyId}")
            _state.value = PlaybackUi(connected = true, error = "읽을 글이 없어요")
            return
        }
        this.req = req
        parts = sentences(text)
        index = 0
        _state.value = PlaybackUi(connected = true, storyId = req.storyId, title = req.title, idle = false, durationMs = req.durationMs)
        if (!acquireFocus()) {
            _state.value = PlaybackUi(connected = true, error = "다른 소리 때문에 읽지 못했어요")
            this.req = null
            return
        }
        HeartzLog.i("reader", "user action read story=${req.storyId} parts=${parts.size}")
        start()
    }

    override fun pause() {
        if (job == null) return
        halt()
        _state.update { it.copy(isPlaying = false) }
        HeartzLog.i("reader", "pause at=$index")
    }

    override fun resume() {
        if (req == null || job != null || index >= parts.size) return
        if (!focused && !acquireFocus()) return
        _state.update { it.copy(suppressedByFocus = false, lastFocusEvent = null) }
        start()
    }

    override fun stop() {
        halt()
        releaseFocus()
        req = null
        parts = emptyList()
        index = 0
        _state.value = PlaybackUi(connected = true)
        HeartzLog.i("reader", "stop")
    }

    private fun start() {
        _state.update { it.copy(isPlaying = true, idle = false, ended = false, error = null, positionMs = position()) }
        job = scope.launch {
            while (index < parts.size) {
                when (val r = speak(parts[index])) {
                    SpeakResult.Done -> {
                        index++
                        _state.update { it.copy(positionMs = position()) }
                    }
                    SpeakResult.Stopped -> {
                        // 다른 곳에서 TTS를 멈췄다(안내 음성 등). 끝으로 치지 않고 멈춘 상태로 둔다.
                        job = null
                        _state.update { it.copy(isPlaying = false) }
                        return@launch
                    }
                    is SpeakResult.Error -> {
                        HeartzLog.i("reader", "tts error ${r.reason}")
                        job = null
                        releaseFocus()
                        _state.update { it.copy(isPlaying = false, idle = true, error = "읽기 오류: ${r.reason}") }
                        return@launch
                    }
                }
            }
            job = null
            releaseFocus()
            _state.update { it.copy(isPlaying = false, ended = true, idle = true, positionMs = it.durationMs) }
            HeartzLog.i("reader", "read to end story=${req?.storyId}")
        }
    }

    private fun position(): Long {
        val d = _state.value.durationMs
        return if (parts.isEmpty()) 0 else d * index / parts.size
    }

    private fun halt() {
        val j = job ?: return
        job = null
        j.cancel()
        stopSpeech()
    }

    private fun acquireFocus(): Boolean {
        if (focused) return true
        focused = focus.acquire(::onFocusChange)
        return focused
    }

    private fun releaseFocus() {
        if (!focused) return
        focused = false
        focus.release()
    }

    private fun onFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> if (job != null) {
                halt()
                _state.update { it.copy(isPlaying = false, suppressedByFocus = true, lastFocusEvent = "다른 소리 때문에 잠시 멈춤") }
            }
            AudioManager.AUDIOFOCUS_GAIN -> if (_state.value.suppressedByFocus) resume()
            AudioManager.AUDIOFOCUS_LOSS -> {
                halt()
                focused = false
                _state.update { it.copy(isPlaying = false, suppressedByFocus = false, lastFocusEvent = "다른 소리 때문에 멈춤") }
            }
        }
    }

    companion object {
        const val MAX_CHUNK = 120

        /** 문장 끝(. ! ? …)과 줄바꿈으로 나누고, 너무 긴 문장은 공백에서 다시 나눈다 */
        fun sentences(text: String): List<String> =
            text.split(Regex("""(?<=[.!?。…])\s+|\n+"""))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .flatMap(::chunk)

        private fun chunk(s: String): List<String> {
            if (s.length <= MAX_CHUNK) return listOf(s)
            val out = mutableListOf<String>()
            var rest = s
            while (rest.length > MAX_CHUNK) {
                val cut = rest.lastIndexOf(' ', MAX_CHUNK).takeIf { it > 0 } ?: MAX_CHUNK
                out += rest.substring(0, cut).trim()
                rest = rest.substring(cut).trim()
            }
            if (rest.isNotEmpty()) out += rest
            return out
        }
    }
}
