package ai.heartz.app.platform.audio

import ai.heartz.app.HeartzLog
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player

/**
 * 편지 재생용 오디오 포커스. 앱 공용 [AudioFocusLeases]에 PAUSE로 요청한다(AUDIOFOCUS_GAIN_TRANSIENT).
 * AVD 확인(2026-11-10, 시험 음악 앱): GAIN_TRANSIENT를 요청하면 음악이 LOSS_TRANSIENT로 멈추고, 반납하면 GAIN으로 다시 재생된다.
 * - 재생을 시작할 때 요청하고, 사용자가 멈추거나 끝나면 놓는다. 주행 중 편지 흐름이 따로 잡고 있으면(공감 질문까지)
 *   같은 앱 포커스라 음악은 계속 멈춰 있다.
 * - 일시적으로 잃으면(통화·Gleo) 멈췄다가 다시 받으면 이어 재생, 영구로 잃으면 정지.
 * - 내비 안내가 말하는 동안은 편지 소리를 줄인다(차량 덕킹이 없는 환경에서도 안내가 들리게).
 * 상태는 세션 extras([EXTRA_FOCUS_PAUSED], [EXTRA_FOCUS_LOST])로 화면에 알린다.
 */
class LetterFocus(
    context: Context,
    private val leases: AudioFocusLeases,
    private val player: Player,
    private val publish: (Bundle) -> Unit,
) : Player.Listener {
    private val am = context.getSystemService(AudioManager::class.java)
    private var lease: AudioFocusLeases.Lease? = null
    private var pausedByFocus = false
    private var navSpeaking = false

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) = updateNav(configs)
    }

    init {
        am.registerAudioPlaybackCallback(playbackCallback, Handler(Looper.getMainLooper()))
        updateNav(am.activePlaybackConfigurations)
    }

    fun release() {
        am.unregisterAudioPlaybackCallback(playbackCallback)
        lease?.release()
        lease = null
    }

    private fun updateNav(configs: List<AudioPlaybackConfiguration>) {
        val now = configs.any { it.audioAttributes.usage == AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE }
        if (now == navSpeaking) return
        navSpeaking = now
        player.volume = if (now) NAV_DUCK_VOLUME else 1f
        HeartzLog.i("focus", "navigation guidance ${if (now) "speaking -> letter volume $NAV_DUCK_VOLUME" else "quiet -> letter volume 1.0"}")
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (playWhenReady) {
            pausedByFocus = false
            if (lease == null) {
                lease = leases.acquire("letter", AudioFocusLeases.Kind.PAUSE, ::onFocusChange)
                if (lease == null) {
                    HeartzLog.i("focus", "letter focus denied -> pause")
                    player.pause()
                    publish(state(paused = false, lost = true))
                } else {
                    publish(state(paused = false, lost = false))
                }
            }
        } else if (!pausedByFocus) {
            letGo("paused by user")
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) letGo("state=$playbackState")
    }

    private fun onFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                pausedByFocus = false
                HeartzLog.i("focus", "permanent focus loss -> stop (session not kept active)")
                player.stop()
                letGo("permanent loss")
                publish(state(paused = false, lost = true))
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> if (player.playWhenReady) {
                pausedByFocus = true
                player.pause()
                publish(state(paused = true, lost = false))
            }
            AudioManager.AUDIOFOCUS_GAIN -> if (pausedByFocus) {
                pausedByFocus = false
                player.play()
                publish(state(paused = false, lost = false))
            }
        }
    }

    private fun letGo(why: String) {
        val l = lease ?: return
        lease = null
        pausedByFocus = false
        l.release()
        HeartzLog.i("focus", "letter lease released ($why)")
        publish(state(paused = false, lost = false))
    }

    private fun state(paused: Boolean, lost: Boolean) = Bundle().apply {
        putBoolean(EXTRA_FOCUS_PAUSED, paused)
        putBoolean(EXTRA_FOCUS_LOST, lost)
    }

    companion object {
        const val EXTRA_FOCUS_PAUSED = "heartz.focusPaused"
        const val EXTRA_FOCUS_LOST = "heartz.focusLost"
        const val NAV_DUCK_VOLUME = 0.3f
    }
}
