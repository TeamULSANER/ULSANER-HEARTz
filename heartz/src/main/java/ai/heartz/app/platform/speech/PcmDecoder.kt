package ai.heartz.app.platform.speech

import ai.heartz.core.moderation.Pcm16
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteOrder

/**
 * 녹음 파일(AAC/MP4 등)을 STT sendAudio 형식(16 kHz · 16-bit · mono PCM)으로 디코딩한다.
 * 다운믹스·리샘플은 core [Pcm16] 순수 함수로 한다.
 */
object PcmDecoder {
    fun decodeTo16kMono(file: File, maxMs: Long = 70_000): ShortArray {
        val (mono, rate) = decodeMono(file, maxMs)
        return Pcm16.resample(mono, rate, 16_000)
    }

    /** 원래 샘플레이트 그대로 mono 16-bit PCM으로 디코딩한다. (pcm, sampleRate) */
    fun decodeMono(file: File, maxMs: Long = 70_000): Pair<ShortArray, Int> {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(file.absolutePath)
            val track = (0 until ex.trackCount).firstOrNull {
                ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("no audio track")
            ex.selectTrack(track)
            val fmt = ex.getTrackFormat(track)
            val mime = fmt.getString(MediaFormat.KEY_MIME)!!
            var rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val codec = MediaCodec.createDecoderByType(mime)
            val out = ByteArrayOutputStream()
            try {
                codec.configure(fmt, null, null, 0)
                codec.start()
                val info = MediaCodec.BufferInfo()
                var inputDone = false
                var outputDone = false
                while (!outputDone) {
                    if (!inputDone) {
                        val i = codec.dequeueInputBuffer(10_000)
                        if (i >= 0) {
                            val buf = codec.getInputBuffer(i)!!
                            val n = ex.readSampleData(buf, 0)
                            if (n < 0 || ex.sampleTime > maxMs * 1000) {
                                codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0)
                                ex.advance()
                            }
                        }
                    }
                    val o = codec.dequeueOutputBuffer(info, 10_000)
                    when {
                        o >= 0 -> {
                            val buf = codec.getOutputBuffer(o)!!
                            if (info.size > 0) {
                                val bytes = ByteArray(info.size)
                                buf.position(info.offset)
                                buf.get(bytes)
                                out.write(bytes)
                            }
                            codec.releaseOutputBuffer(o, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                        }
                        o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val f = codec.outputFormat
                            rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                    }
                }
            } finally {
                runCatching { codec.stop() }
                codec.release()
            }
            val raw = out.toByteArray()
            val shorts = ShortArray(raw.size / 2)
            java.nio.ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
            return Pcm16.downmix(shorts, channels) to rate
        } finally {
            ex.release()
        }
    }
}
