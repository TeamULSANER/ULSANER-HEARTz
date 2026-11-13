package ai.heartz.app.platform.record

import ai.heartz.app.ui.drive.PromptFocus
import android.content.Context
import ai.heartz.app.platform.audio.AudioFocusLeases
import android.media.AudioManager

/**
 * 편지 흐름의 포커스를 앱 공용 [AudioFocusLeases]로 요청한다.
 * - 질문: DUCK(AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, USAGE_ASSISTANT). AAOS에서는 음악 앱에 덕킹 신호를 보내지 않고 차량 오디오(HAL)가 줄인다.
 *   AVD 확인(2026-11-10): 이 AVD는 `Use hal ducking signals false`라 음악 크기가 줄지 않는다.
 * - 편지를 열고 공감을 정할 때까지: PAUSE(AUDIOFOCUS_GAIN_TRANSIENT). 음악이 멈췄다가 놓으면 다시 나온다.
 */
class PromptFocusManager(private val leases: AudioFocusLeases) : PromptFocus {
    private var duckLease: AudioFocusLeases.Lease? = null
    private var pauseLease: AudioFocusLeases.Lease? = null

    override fun duck(): Boolean {
        release()
        duckLease = leases.acquire("prompt", AudioFocusLeases.Kind.DUCK)
        return duckLease != null
    }

    override fun release() {
        duckLease?.release()
        duckLease = null
    }

    override fun holdPause() {
        if (pauseLease == null) pauseLease = leases.acquire("letter-session", AudioFocusLeases.Kind.PAUSE)
    }

    override fun releasePause() {
        pauseLease?.release()
        pauseLease = null
    }
}

/** 전화 수신·통화 중인지. 녹음이 끊긴 이유가 통화인지 Gleo인지 나눌 때 쓴다. */
fun isInCall(context: Context): Boolean {
    val mode = context.getSystemService(AudioManager::class.java).mode
    return mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION ||
        mode == AudioManager.MODE_RINGTONE ||
        (android.os.Build.VERSION.SDK_INT >= 30 && mode == AudioManager.MODE_CALL_SCREENING)
}
