package ai.heartz.app.platform.record

import ai.heartz.app.HeartzLog
import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import java.io.File

/** 녹음 결과. 파일 생성 여부와 실제 길이를 확인한 뒤에만 Success다. */
sealed interface RecordResult {
    data class Success(val file: File, val durationMs: Long, val sizeBytes: Long) : RecordResult
    data class Failure(val reason: String) : RecordResult
}

/**
 * MediaRecorder(AAC/MP4) 래퍼. 주차 후 녹음과 지나면서 남기기에서 쓰며, 정책 확인은 호출자가 한다.
 * 시작 반환은 녹음 성공이 아니다. stop 후 파일·길이로 확인한다.
 */
class AudioRecorder(context: Context) : ai.heartz.app.ui.drive.ClipRecorder {
    private val appContext = context.applicationContext
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    override val isRecording: Boolean get() = recorder != null

    override fun start(target: File, maxDurationMs: Int, onMaxReached: () -> Unit): Result<Unit> =
        start(target, maxDurationMs, MediaRecorder.AudioSource.MIC, onMaxReached)

    fun start(target: File, maxDurationMs: Int, audioSource: Int, onMaxReached: () -> Unit): Result<Unit> = runCatching {
        check(recorder == null) { "already recording" }
        target.parentFile?.mkdirs()
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(appContext) else @Suppress("DEPRECATION") MediaRecorder()
        try {
            r.setAudioSource(audioSource)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(44_100)
            r.setAudioEncodingBitRate(96_000)
            r.setMaxDuration(maxDurationMs)
            r.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) onMaxReached()
            }
            r.setOutputFile(target.absolutePath)
            r.prepare()
            r.start()
        } catch (e: Exception) {
            r.release()
            target.delete()
            throw e
        }
        recorder = r
        file = target
        startedAt = SystemClock.elapsedRealtime()
        HeartzLog.i("record", "api=MediaRecorder.start result=returned")
    }

    /** 0..32767. 녹음 중이 아니면 0 */
    override fun amplitude(): Int = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)

    fun elapsedMs(): Long = if (recorder == null) 0 else SystemClock.elapsedRealtime() - startedAt

    override fun stop(): RecordResult {
        val r = recorder ?: return RecordResult.Failure("not recording")
        val f = file
        val elapsed = elapsedMs()
        recorder = null
        file = null
        val stopped = runCatching { r.stop() }
        r.release()
        if (stopped.isFailure || f == null || !f.isFile || f.length() == 0L) {
            f?.delete()
            val why = stopped.exceptionOrNull()?.let { "${it::class.java.simpleName}: ${it.message}" } ?: "empty file"
            HeartzLog.i("record", "stop failed $why")
            return RecordResult.Failure(why)
        }
        val measured = runCatching {
            // minSdk 28: MediaMetadataRetriever는 API 29부터 AutoCloseable이라 use 대신 release()
            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(f.absolutePath)
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            } finally {
                mmr.release()
            }
        }.getOrNull()
        val duration = measured ?: elapsed
        HeartzLog.i("record", "file created bytes=${f.length()} durationMs=$duration (measured=${measured != null})")
        return RecordResult.Success(f, duration, f.length())
    }

    override fun cancel() {
        val r = recorder ?: return
        recorder = null
        runCatching { r.stop() }
        r.release()
        file?.delete()
        file = null
        HeartzLog.i("record", "cancelled")
    }
}
