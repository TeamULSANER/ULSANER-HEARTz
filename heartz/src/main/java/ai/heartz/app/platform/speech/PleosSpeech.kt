package ai.heartz.app.platform.speech

import ai.heartz.app.BuildConfig
import ai.heartz.app.HeartzLog
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.listener.EventListener
import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInit
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInit

/** SDK 엔진 상태. initialize() 반환이 아니라 OnInitializeListener 결과로만 Ready가 된다. */
sealed interface EngineState {
    data object NotStarted : EngineState
    data object Initializing : EngineState
    data object Ready : EngineState
    data class Failed(val reason: String) : EngineState
}

sealed interface SpeakResult {
    /** onDone 콜백까지 받음 */
    data object Done : SpeakResult
    data object Stopped : SpeakResult
    data class Error(val reason: String) : SpeakResult
}

sealed interface RecognizeResult {
    /** completed=true 결과. 빈 문자열이면 [NoSpeech] */
    data class Text(val text: String) : RecognizeResult
    data object NoSpeech : RecognizeResult
    data class Error(val reason: String) : RecognizeResult
}

/** 운전 중 안내 음성. 실제 구현은 Pleos TextToSpeech, 테스트에서는 fake. */
interface Speaker {
    val state: StateFlow<EngineState>
    suspend fun speak(text: String, timeoutMs: Long = 15_000): SpeakResult
    fun stop()
}

/** 음성 인식. 마이크(request) 경로와 앱이 넘긴 PCM(sendAudio) 경로를 구분한다. */
interface Recognizer {
    val state: StateFlow<EngineState>
    suspend fun listen(timeoutMs: Long = 8_000): RecognizeResult
    /** 16 kHz · 16-bit · mono PCM */
    suspend fun transcribePcm(pcm: ByteArray, timeoutMs: Long = 60_000): RecognizeResult
    fun stop()
}

/**
 * Pleos TextToSpeech 3.3.1, ON_DEVICE 모드(네트워크·앱 등록·Client Secret 불필요).
 * CAAS 서비스(ai.pleos.playground.caas)에 바인딩한다. 한 번에 한 문장만 말한다.
 */
class PleosTextToSpeech(context: Context) : Speaker {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow<EngineState>(EngineState.NotStarted)
    override val state: StateFlow<EngineState> = _state.asStateFlow()
    private var tts: TextToSpeech? = null
    private val mutex = Mutex()
    @Volatile private var pending: CompletableDeferred<SpeakResult>? = null

    private val listener = object : EventListener {
        override fun onReady() = HeartzLog.i("tts", "callback onReady")
        override fun onStart() = HeartzLog.i("tts", "callback onStart")
        override fun onDone() {
            HeartzLog.i("tts", "callback onDone")
            pending?.complete(SpeakResult.Done)
        }
        override fun onStop() {
            HeartzLog.i("tts", "callback onStop")
            pending?.complete(SpeakResult.Stopped)
        }
        override fun onError(errorType: TtsError) {
            HeartzLog.i("tts", "callback onError $errorType")
            pending?.complete(SpeakResult.Error(errorType.name))
        }
        override fun onUpdatedRms(rms: Double) = Unit
    }

    fun start() {
        if (tts != null) return
        _state.value = EngineState.Initializing
        val t = runCatching { TextToSpeech(appContext, TtsMode.ON_DEVICE) }.getOrElse { e ->
            _state.value = EngineState.Failed("${e::class.java.simpleName}: ${e.message}")
            HeartzLog.i("tts", "construct failed ${e::class.java.simpleName} ${e.message}")
            return
        }
        tts = t
        runCatching {
            t.initialize(object : TtsInit {
                override fun onSuccess() {
                    runCatching {
                        t.setLocale(Locale.KOREA)
                        // 안내 음성은 Gleo처럼 어시스턴트 용도로 낸다(음악과 다른 볼륨 그룹, 음악 덕킹 대상이 아님)
                        t.setAudioAttributes(
                            android.media.AudioAttributes.Builder()
                                .setUsage(android.media.AudioAttributes.USAGE_ASSISTANT)
                                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build(),
                        )
                        t.addEventListener(listener)
                    }.onFailure { e ->
                        _state.value = EngineState.Failed("setup ${e::class.java.simpleName}")
                        return
                    }
                    _state.value = EngineState.Ready
                    HeartzLog.i("tts", "initialize callback onSuccess (ON_DEVICE, ko-KR)")
                }
                override fun onFailure(errorType: TtsError) {
                    _state.value = EngineState.Failed(errorType.name)
                    HeartzLog.i("tts", "initialize callback onFailure $errorType")
                }
            })
            HeartzLog.i("tts", "api=initialize result=returned (not ready until callback)")
        }.onFailure { e ->
            _state.value = EngineState.Failed("${e::class.java.simpleName}: ${e.message}")
            HeartzLog.i("tts", "initialize threw ${e::class.java.simpleName} ${e.message}")
        }
    }

    override suspend fun speak(text: String, timeoutMs: Long): SpeakResult = mutex.withLock {
        val t = tts
        if (t == null || state.value != EngineState.Ready) return SpeakResult.Error("engine ${state.value}")
        val d = CompletableDeferred<SpeakResult>()
        pending = d
        val started = runCatching { withContext(Dispatchers.Main) { t.speak(text, true) } }
        // 호출한 작업이 취소됐으면 오류로 바꾸지 않고 취소를 그대로 전한다
        (started.exceptionOrNull() as? kotlinx.coroutines.CancellationException)?.let { pending = null; throw it }
        if (started.isFailure) {
            pending = null
            return SpeakResult.Error("speak threw ${started.exceptionOrNull()?.javaClass?.simpleName}")
        }
        HeartzLog.i("tts", "api=speak result=returned chars=${text.length}")
        val r = withTimeoutOrNull(timeoutMs) { d.await() } ?: SpeakResult.Error("timeout ${timeoutMs}ms (no onDone)")
        pending = null
        r
    }

    override fun stop() {
        runCatching { tts?.stop() }
    }

    fun release() {
        val t = tts ?: return
        tts = null
        runCatching { t.removeEventListener(listener) }
        runCatching { t.release() }
        _state.value = EngineState.NotStarted
        HeartzLog.i("tts", "released")
    }
}

/**
 * Pleos SpeechToText 3.3.1, ON_DEVICE 모드.
 * - [listen]: request()로 SDK가 차량 마이크를 연다.
 * - [transcribePcm]: sendAudio()로 앱이 가진 PCM을 인식한다(가이드상 ON_DEVICE 전용).
 * 결과 텍스트는 release 로그에 남기지 않는다.
 */
class PleosSpeechToText(context: Context) : Recognizer {
    private val appContext = context.applicationContext
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val _state = MutableStateFlow<EngineState>(EngineState.NotStarted)
    override val state: StateFlow<EngineState> = _state.asStateFlow()
    private var stt: SpeechToText? = null
    private val mutex = Mutex()

    /** 콜백은 메인 스레드에서 온다. [segments]도 메인 스레드에서만 다룬다 */
    private class Session(val keepAll: Boolean) {
        val done = CompletableDeferred<RecognizeResult>()
        val segments = TranscriptSegments(keepAll)
    }
    @Volatile private var session: Session? = null

    private val listener = object : ResultListener {
        override fun onReady() = HeartzLog.i("stt", "callback onReady")
        override fun onStartedRecognition() = HeartzLog.i("stt", "callback onStartedRecognition")
        override fun onUpdated(stt: String, completed: Boolean) {
            val s = session ?: return
            HeartzLog.i("stt", "callback onUpdated completed=$completed chars=${stt.length}" + if (BuildConfig.DEBUG) " text=\"${stt.take(80)}\"" else "")
            s.segments.onUpdated(stt, completed)?.let { s.done.complete(it) }
        }
        override fun onUpdatedEpdData(on: Long, off: Long) = HeartzLog.i("stt", "callback onUpdatedEpdData on=$on off=$off")
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onEndedRecognition() {
            HeartzLog.i("stt", "callback onEndedRecognition")
            val s = session ?: return
            // 받아쓰기(녹음 파일)는 말이 멈출 때마다 조각이 끝나므로 여기서 끝내지 않고 오디오를 다 보낸 뒤 모은다.
            if (s.keepAll) return
            // AVD 관찰: completed=true 최종 결과가 onEndedRecognition 뒤(~20 ms)에 온다.
            // 유예 안에 최종 결과가 없으면 NoSpeech. 확정되지 않은 중간 결과는 쓰지 않는다.
            mainHandler.postDelayed({ s.done.complete(RecognizeResult.NoSpeech) }, FINAL_GRACE_MS)
        }
        override fun onError(errorType: SttError) {
            HeartzLog.i("stt", "callback onError $errorType")
            session?.done?.complete(RecognizeResult.Error(errorType.name))
        }
    }

    fun start() {
        if (stt != null) return
        _state.value = EngineState.Initializing
        val s = runCatching { SpeechToText(appContext, SttMode.ON_DEVICE) }.getOrElse { e ->
            _state.value = EngineState.Failed("${e::class.java.simpleName}: ${e.message}")
            HeartzLog.i("stt", "construct failed ${e::class.java.simpleName} ${e.message}")
            return
        }
        stt = s
        runCatching {
            s.initialize(object : SttInit {
                override fun onSuccess() {
                    runCatching {
                        s.setLocale(Locale.KOREA)
                        s.addListener(listener)
                    }.onFailure { e ->
                        _state.value = EngineState.Failed("setup ${e::class.java.simpleName}")
                        return
                    }
                    _state.value = EngineState.Ready
                    HeartzLog.i("stt", "initialize callback onSuccess (ON_DEVICE, ko-KR)")
                }
                override fun onFailure(errorType: SttError) {
                    _state.value = EngineState.Failed(errorType.name)
                    HeartzLog.i("stt", "initialize callback onFailure $errorType")
                }
            })
            HeartzLog.i("stt", "api=initialize result=returned (not ready until callback)")
        }.onFailure { e ->
            _state.value = EngineState.Failed("${e::class.java.simpleName}: ${e.message}")
            HeartzLog.i("stt", "initialize threw ${e::class.java.simpleName} ${e.message}")
        }
    }

    override suspend fun listen(timeoutMs: Long): RecognizeResult = run("request", timeoutMs, keepAll = false) { it.request() }

    /** 녹음 전체 받아쓰기. 말이 멈출 때마다 나뉘는 조각을 모두 모아 줄바꿈으로 잇는다 */
    override suspend fun transcribePcm(pcm: ByteArray, timeoutMs: Long): RecognizeResult =
        run("sendAudio bytes=${pcm.size}", timeoutMs, keepAll = true) { s ->
            // 가이드 예시: 100 ms(3,200 bytes) 단위 실시간 전송
            // 끝점 검출(EPD)이 마지막 말을 닫을 수 있게 1초 무음을 덧붙인다.
            withContext(Dispatchers.IO) {
                val padded = pcm + ByteArray(BYTES_PER_100_MS * 10)
                var i = 0
                while (i < padded.size) {
                    val end = minOf(i + BYTES_PER_100_MS, padded.size)
                    s.sendAudio(padded.copyOfRange(i, end))
                    i = end
                    Thread.sleep(100)
                }
                s.completeAudioSend()
            }
        }

    private suspend fun run(api: String, timeoutMs: Long, keepAll: Boolean, call: suspend (SpeechToText) -> Unit): RecognizeResult = mutex.withLock {
        val s = stt
        if (s == null || state.value != EngineState.Ready) return RecognizeResult.Error("engine ${state.value}")
        val sess = Session(keepAll)
        session = sess
        val started = runCatching { call(s) }
        (started.exceptionOrNull() as? kotlinx.coroutines.CancellationException)?.let {
            session = null
            runCatching { s.stop() }
            throw it
        }
        if (started.isFailure) {
            session = null
            val e = started.exceptionOrNull()
            HeartzLog.i("stt", "api=$api threw ${e?.javaClass?.simpleName} ${e?.message}")
            return RecognizeResult.Error("${e?.javaClass?.simpleName}")
        }
        HeartzLog.i("stt", "api=$api result=returned")
        val r = withTimeoutOrNull(timeoutMs) {
            if (keepAll) withContext(Dispatchers.Main) {
                // 덧붙인 1초 무음으로 마지막 조각이 닫힐 시간을 주고, 아직 인식 중이면 조금 더 기다린다
                kotlinx.coroutines.delay(FINAL_GRACE_MS)
                var waited = 0L
                while (sess.segments.pending && waited < PENDING_WAIT_MS) {
                    kotlinx.coroutines.delay(100)
                    waited += 100
                }
                val result = sess.segments.finish()
                HeartzLog.i("stt", "transcript segments done pending=${sess.segments.pending} result=${result::class.simpleName}" +
                    ((result as? RecognizeResult.Text)?.let { " lines=${it.text.lines().size} chars=${it.text.length}" } ?: ""))
                sess.done.complete(result)
            }
            sess.done.await()
        }
        if (r == null) {
            runCatching { s.stop() }
            HeartzLog.i("stt", "timeout ${timeoutMs}ms -> stop()")
        }
        session = null
        r ?: RecognizeResult.Error("timeout")
    }

    override fun stop() {
        runCatching { stt?.stop() }
    }

    fun release() {
        val s = stt ?: return
        stt = null
        runCatching { s.removeListener(listener) }
        runCatching { s.release() }
        _state.value = EngineState.NotStarted
        HeartzLog.i("stt", "released")
    }

    companion object {
        const val BYTES_PER_100_MS = 3_200
        const val FINAL_GRACE_MS = 1_500L
        /** 받아쓰기: 마지막 조각이 아직 인식 중일 때 더 기다리는 최대 시간 */
        const val PENDING_WAIT_MS = 3_000L
    }
}
