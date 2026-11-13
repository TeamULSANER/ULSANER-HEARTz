package ai.heartz.app.platform.gleo

import ai.heartz.app.HeartzLog
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

enum class QuietOutcome { AFTER_SPEECH, NO_SPEECH, TIMED_OUT }

/**
 * Gleo는 앱 액션 응답을 받은 **뒤에** 대답을 말한다(AVD 관찰: 응답 후 약 1 s 뒤 시작).
 * 그래서 Gleo로 시작한 재생은 응답을 보낸 뒤 "어시스턴트 말소리가 시작됐다가 [quietMs] 동안 조용해질 때"까지 기다린다.
 * - [startWaitMs] 안에 말소리가 없으면 더 기다리지 않는다(NO_SPEECH).
 * - 전체 [maxWaitMs]를 넘기면 기다리기를 그만둔다(TIMED_OUT).
 */
class AssistantQuiet(
    private val startWaitMs: Long = 4_000,
    private val quietMs: Long = 600,
    private val maxWaitMs: Long = 15_000,
) {
    suspend fun await(speaking: Flow<Boolean>): QuietOutcome =
        withTimeoutOrNull(maxWaitMs) { awaitSpeechThenQuiet(speaking) } ?: QuietOutcome.TIMED_OUT

    private suspend fun awaitSpeechThenQuiet(speaking: Flow<Boolean>): QuietOutcome {
        withTimeoutOrNull(startWaitMs) { speaking.first { it } } ?: return QuietOutcome.NO_SPEECH
        while (true) {
            speaking.first { !it }
            withTimeoutOrNull(quietMs) { speaking.first { it } } ?: return QuietOutcome.AFTER_SPEECH
        }
    }
}

/**
 * 다른 앱(Gleo 등)의 USAGE_ASSISTANT 재생이 지금 있는지. 공개 API인 재생 구성 콜백만 쓴다
 * (다른 앱의 구성은 uid가 지워진 채 오지만 AudioAttributes는 남는다).
 */
class AssistantSpeechMonitor(
    context: Context,
    private val usages: Set<Int> = setOf(AudioAttributes.USAGE_ASSISTANT),
) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    private val callback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) = update(configs)
    }

    private fun update(configs: List<AudioPlaybackConfiguration>) {
        val now = configs.any { it.audioAttributes.usage in usages }
        if (now != _speaking.value) HeartzLog.i("gleo", "assistant speaking=$now")
        _speaking.value = now
    }

    fun start() {
        update(audio.activePlaybackConfigurations)
        audio.registerAudioPlaybackCallback(callback, Handler(Looper.getMainLooper()))
    }

    fun stop() {
        audio.unregisterAudioPlaybackCallback(callback)
        _speaking.value = false
    }
}
