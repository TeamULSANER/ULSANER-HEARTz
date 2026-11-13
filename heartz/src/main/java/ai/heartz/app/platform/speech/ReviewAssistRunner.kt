package ai.heartz.app.platform.speech

import ai.heartz.app.HeartzLog
import ai.heartz.app.data.AssistScheduler
import ai.heartz.app.data.HeartzRepository
import ai.heartz.app.data.userMessage
import ai.heartz.core.moderation.Pcm16
import ai.heartz.core.moderation.ReviewAssist
import ai.heartz.core.moderation.ReviewScreening
import ai.heartz.core.moderation.Transcript
import ai.heartz.core.moderation.TranscriptStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 저장된 녹음 → 16 kHz PCM → Pleos STT(ON_DEVICE, sendAudio) → 규칙 기반 의심 구간 → 백엔드에 첨부.
 * 받아쓰기는 검수 우선순위를 돕는 정보일 뿐 승인·반려를 하지 않는다. 실패도 "직접 들어 확인"으로 첨부한다.
 * 음원은 작업용 복사본을 쓰고 끝나면 지운다.
 */
class ReviewAssistRunner(
    private val repo: HeartzRepository,
    private val recognizer: Recognizer,
    private val scope: CoroutineScope,
    private val workDir: File,
) : AssistScheduler {
    override fun schedule(storyId: String, audio: File) {
        if (!audio.isFile) return
        val copy = File(workDir.apply { mkdirs() }, "assist-$storyId.m4a")
        runCatching { audio.copyTo(copy, overwrite = true) }.onFailure { return }
        audio.delete()
        val backend = repo.backend
        scope.launch {
            try {
                val assist = build(copy)
                backend.attachAssist(storyId, assist)
                HeartzLog.i("assist", "attached story=$storyId transcript=${assist.transcript.status} flags=${assist.flags.map { it.kind }}")
                repo.refresh()
            } catch (e: Exception) {
                HeartzLog.i("assist", "not attached story=$storyId ${e.userMessage()}")
            } finally {
                copy.delete()
            }
        }
    }

    suspend fun build(file: File): ReviewAssist {
        val pcm = runCatching { withContext(Dispatchers.IO) { PcmDecoder.decodeTo16kMono(file) } }.getOrElse { e ->
            return ReviewAssist(Transcript(TranscriptStatus.FAILED, null, ENGINE, "decode ${e::class.java.simpleName}"), emptyList(), now())
                .let { it.copy(flags = ReviewScreening.flags(it.transcript, emptyList())) }
        }
        val loud = Pcm16.suddenLoudMoments(pcm, 16_000)
        if (Pcm16.isNearSilent(pcm)) {
            val t = Transcript(TranscriptStatus.NO_SPEECH, null, ENGINE, "녹음이 거의 무음")
            return ReviewAssist(t, ReviewScreening.flags(t, loud), now())
        }
        val transcript = when (val r = recognizer.transcribePcm(Pcm16.toLittleEndianBytes(pcm))) {
            is RecognizeResult.Text -> Transcript(TranscriptStatus.DONE, r.text, ENGINE)
            RecognizeResult.NoSpeech -> Transcript(TranscriptStatus.NO_SPEECH, null, ENGINE)
            is RecognizeResult.Error -> Transcript(TranscriptStatus.FAILED, null, ENGINE, r.reason)
        }
        return ReviewAssist(transcript, ReviewScreening.flags(transcript, loud), now())
    }

    private fun now() = System.currentTimeMillis()

    companion object {
        const val ENGINE = "Pleos SpeechToText 3.3.1 ON_DEVICE"
    }
}
