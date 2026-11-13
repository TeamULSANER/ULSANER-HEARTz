package ai.heartz.app.platform.record

import ai.heartz.app.HeartzLog
import ai.heartz.app.platform.speech.PcmDecoder
import ai.heartz.core.audio.VoiceCleaner
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 녹음 파일의 잡음을 줄여 같은 자리에 다시 저장한다(AAC/MP4).
 * 디코딩 → [VoiceCleaner](저역 차단·스펙트럼 차감·음량 맞춤) → AAC 인코딩 → 임시 파일 → 바꿔치기.
 * 실패하면 원본을 그대로 두고 false를 돌려준다(원본이 사라지지 않는다).
 */
object VoiceCleanup {
    private const val MIME = MediaFormat.MIMETYPE_AUDIO_AAC
    private const val BIT_RATE = 96_000
    private const val CHUNK_SAMPLES = 2_048

    suspend fun cleanInPlace(file: File): Boolean = withContext(Dispatchers.Default) {
        val started = System.currentTimeMillis()
        val tmp = File(file.parentFile, file.name + ".clean.tmp")
        val ok = runCatching {
            val (pcm, rate) = PcmDecoder.decodeMono(file, maxMs = 10 * 60_000L)
            if (pcm.isEmpty()) error("empty audio")
            val cleaned = VoiceCleaner.clean(pcm, rate)
            encodeAac(cleaned, rate, tmp)
            if (tmp.length() == 0L) error("empty output")
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
            HeartzLog.i("clean", "cleaned rate=$rate samples=${pcm.size} ms=${System.currentTimeMillis() - started}")
        }.onFailure { e ->
            tmp.delete()
            HeartzLog.i("clean", "failed keep original ${e::class.java.simpleName}: ${e.message}")
        }.isSuccess
        ok
    }

    internal fun encodeAac(pcm: ShortArray, rate: Int, out: File) {
        val fmt = MediaFormat.createAudioFormat(MIME, rate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, CHUNK_SAMPLES * 2)
        }
        val codec = MediaCodec.createEncoderByType(MIME)
        val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        var muxing = false
        try {
            codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            val info = MediaCodec.BufferInfo()
            var pos = 0
            var inputDone = false
            var outputDone = false
            while (!outputDone) {
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val buf = codec.getInputBuffer(i)!!
                        buf.clear()
                        val n = minOf(CHUNK_SAMPLES, pcm.size - pos, buf.capacity() / 2)
                        val pts = pos * 1_000_000L / rate
                        if (n <= 0) {
                            codec.queueInputBuffer(i, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val bb = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN)
                            bb.asShortBuffer().put(pcm, pos, n)
                            buf.put(bb)
                            codec.queueInputBuffer(i, 0, n * 2, pts, 0)
                            pos += n
                        }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxing = true
                    }
                    o >= 0 -> {
                        val buf = codec.getOutputBuffer(o)!!
                        val config = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        if (info.size > 0 && !config && muxing) {
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            muxer.writeSampleData(track, buf, info)
                        }
                        codec.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
            if (muxing) runCatching { muxer.stop() }
            muxer.release()
        }
        if (!muxing) error("encoder produced no format")
    }
}
