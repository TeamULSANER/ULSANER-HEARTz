package ai.heartz.app.platform.audio

import ai.heartz.app.HeartzLog
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

/**
 * 앱 전체가 하나로 쓰는 오디오 포커스(같은 리스너 = 같은 포커스 클라이언트).
 * 질문(덕킹), 편지 재생, "편지를 열고 공감을 정할 때까지"(멈춤)를 따로 요청하면 그 사이사이에
 * 포커스가 잠깐 비어 음악이 튀어나온다. 그래서 필요한 것들을 임대(lease)로 모아 가장 센 요청 하나만 유지한다.
 * - PAUSE가 하나라도 있으면 AUDIOFOCUS_GAIN_TRANSIENT(음악이 멈췄다가 반납하면 다시 나옴)
 * - 아니면 DUCK이 있으면 AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK(차량 오디오가 음악을 줄임)
 * - 아무것도 없으면 반납
 * 메인 스레드에서만 부른다.
 */
class AudioFocusLeases(context: Context) {
    enum class Kind { DUCK, PAUSE }

    inner class Lease internal constructor(val tag: String, val kind: Kind, internal val onChange: (Int) -> Unit) {
        fun release() = this@AudioFocusLeases.release(this)
    }

    private val am = context.applicationContext.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val leases = mutableListOf<Lease>()
    private var request: AudioFocusRequest? = null
    private var gain: Int? = null

    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        HeartzLog.i("focus", "app focus change=$change leases=${leases.map { it.tag }}")
        if (change == AudioManager.AUDIOFOCUS_LOSS) gain = null
        leases.toList().forEach { it.onChange(change) }
    }

    /** 허용되면 임대, 거절되면 null */
    fun acquire(tag: String, kind: Kind, onChange: (Int) -> Unit = {}): Lease? {
        val lease = Lease(tag, kind, onChange)
        leases += lease
        if (update()) {
            HeartzLog.i("focus", "lease +$tag($kind) now=${leases.map { it.tag }} gain=${gainName(gain)}")
            return lease
        }
        leases -= lease
        update()
        return null
    }

    private fun release(lease: Lease) {
        if (!leases.remove(lease)) return
        update()
        HeartzLog.i("focus", "lease -${lease.tag} now=${leases.map { it.tag }} gain=${gainName(gain)}")
    }

    val holding: Boolean get() = gain != null

    private fun update(): Boolean {
        val want = when {
            leases.any { it.kind == Kind.PAUSE } -> AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            leases.any { it.kind == Kind.DUCK } -> AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            else -> null
        }
        if (want == gain) return true
        if (want == null) {
            request?.let { am.abandonAudioFocusRequest(it) }
            request = null
            gain = null
            HeartzLog.i("focus", "app focus abandoned")
            return true
        }
        val usage = if (want == AudioManager.AUDIOFOCUS_GAIN_TRANSIENT) AudioAttributes.USAGE_MEDIA else AudioAttributes.USAGE_ASSISTANT
        val req = AudioFocusRequest.Builder(want)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(usage).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener(listener, handler)
            .build()
        val r = am.requestAudioFocus(req)
        HeartzLog.i("focus", "app focus request ${gainName(want)} result=$r (1=granted)")
        return if (r == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            request = req
            gain = want
            true
        } else {
            false
        }
    }

    private fun gainName(g: Int?) = when (g) {
        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT -> "GAIN_TRANSIENT"
        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK -> "MAY_DUCK"
        null -> "none"
        else -> "gain=$g"
    }
}
