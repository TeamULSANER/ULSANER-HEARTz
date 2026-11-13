package ai.heartz.app.platform.record

import ai.heartz.app.HeartzLog
import ai.heartz.app.ui.drive.RecordingFocus
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

/**
 * 지나면서 녹음하는 동안 잡는 오디오 포커스(AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE, USAGE_ASSISTANT·SPEECH).
 * - 음악 등은 일시 정지되어 녹음에 섞이지 않는다.
 * - 통화·음성 비서 등으로 LOSS / LOSS_TRANSIENT를 받으면 녹음을 멈추게 알린다.
 * - LOSS_TRANSIENT_CAN_DUCK(짧은 안내음)은 기록만 하고 계속한다.
 * 포커스 정책 결과는 플랫폼(CarAudioService)이 정한다. 요청 결과를 그대로 로그에 남긴다.
 */
class RecordingFocusManager(context: Context) : RecordingFocus {
    private val am = context.applicationContext.getSystemService(AudioManager::class.java)
    private var request: AudioFocusRequest? = null

    override fun acquire(onLost: (String) -> Unit): Boolean {
        release()
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener { change ->
                HeartzLog.i("focus", "recording focus change=$change")
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ->
                        onLost("다른 소리(통화·음성 비서 등)")
                    else -> Unit
                }
            }
            .build()
        val result = am.requestAudioFocus(req)
        HeartzLog.i("focus", "recording focus request result=$result (1=granted, 0=failed, 2=delayed)")
        return if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            request = req
            true
        } else {
            false
        }
    }

    override fun release() {
        val r = request ?: return
        request = null
        val res = am.abandonAudioFocusRequest(r)
        HeartzLog.i("focus", "recording focus abandoned result=$res")
    }
}
