package ai.heartz.core

import ai.heartz.core.moderation.Pcm16
import ai.heartz.core.moderation.ReviewFlagKind
import ai.heartz.core.moderation.ReviewScreening
import ai.heartz.core.moderation.Transcript
import ai.heartz.core.moderation.TranscriptStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModerationAssistTest {
    @Test
    fun downmixAveragesStereoFrames() {
        val stereo = shortArrayOf(100, 300, -200, 0)
        assertEquals(listOf<Short>(200, -100), Pcm16.downmix(stereo, 2).toList())
    }

    @Test
    fun resampleHalvesSampleCountFrom32kTo16k() {
        val src = ShortArray(3200) { (it % 100).toShort() }
        val out = Pcm16.resample(src, 32_000, 16_000)
        assertEquals(1600, out.size)
    }

    @Test
    fun resampleSameRateIsIdentity() {
        val src = shortArrayOf(1, 2, 3)
        assertEquals(src.toList(), Pcm16.resample(src, 16_000, 16_000).toList())
    }

    @Test
    fun littleEndianBytes() {
        assertEquals(listOf<Byte>(0x34, 0x12), Pcm16.toLittleEndianBytes(shortArrayOf(0x1234)).toList())
    }

    @Test
    fun suddenLoudSpikeDetectedAgainstQuietBackground() {
        val rate = 16_000
        val quiet = ShortArray(rate * 3) { if (it % 2 == 0) 300 else -300 }
        // 2.0 s 지점에 100 ms 큰 소리
        for (i in 2 * rate until 2 * rate + rate / 10) quiet[i] = if (i % 2 == 0) 32_000 else -32_000
        val spikes = Pcm16.suddenLoudMoments(quiet, rate)
        assertEquals(1, spikes.size)
        assertTrue(spikes[0] in 1_900L..2_100L)
    }

    @Test
    fun steadyLoudSpeechIsNotSudden() {
        val rate = 16_000
        val loud = ShortArray(rate * 2) { if (it % 2 == 0) 30_000 else -30_000 }
        assertTrue(Pcm16.suddenLoudMoments(loud, rate).isEmpty())
    }

    @Test
    fun phoneNumberAndAddressAreFlaggedNotBlocked() {
        val t = Transcript(TranscriptStatus.DONE, "제 번호는 010-1234-5678이고 101동 1203호에 살아요", engine = "test")
        val kinds = ReviewScreening.flags(t, emptyList()).map { it.kind }
        assertTrue(ReviewFlagKind.PHONE_NUMBER in kinds)
        assertTrue(ReviewFlagKind.ADDRESS_LIKE in kinds)
    }

    @Test
    fun spokenDigitsPhoneNumberIsFlagged() {
        val t = Transcript(TranscriptStatus.DONE, "공일공 일이삼사 오육칠팔로 연락 주세요", engine = "test")
        assertTrue(ReviewFlagKind.PHONE_NUMBER in ReviewScreening.flags(t, emptyList()).map { it.kind })
    }

    @Test
    fun repeatedPhraseIsFlagged() {
        val t = Transcript(TranscriptStatus.DONE, "야호 야호 야호 야호 야호 야호", engine = "test")
        assertTrue(ReviewFlagKind.REPEATED_PHRASE in ReviewScreening.flags(t, emptyList()).map { it.kind })
    }

    @Test
    fun ordinaryStoryHasNoFlags() {
        val t = Transcript(TranscriptStatus.DONE, "이 길을 지날 때마다 할머니 생각이 나요. 오늘 꽃이라도 사들고 가야겠네요", engine = "test")
        assertEquals(emptyList<Any>(), ReviewScreening.flags(t, emptyList()))
    }

    @Test
    fun failedTranscriptIsFlaggedForManualListeningNeverPassed() {
        val t = Transcript(TranscriptStatus.FAILED, null, engine = "test", reason = "ON_DEVICE_ENGINE_ERROR")
        val flags = ReviewScreening.flags(t, emptyList())
        assertEquals(listOf(ReviewFlagKind.TRANSCRIPT_UNAVAILABLE), flags.map { it.kind })
    }

    @Test
    fun noSpeechIsFlagged() {
        val t = Transcript(TranscriptStatus.NO_SPEECH, null, engine = "test")
        assertEquals(listOf(ReviewFlagKind.NO_SPEECH), ReviewScreening.flags(t, emptyList()).map { it.kind })
    }

    @Test
    fun loudMomentsBecomeFlagsWithTime() {
        val t = Transcript(TranscriptStatus.DONE, "안녕하세요", engine = "test")
        val flags = ReviewScreening.flags(t, listOf(2_000L))
        assertEquals(ReviewFlagKind.SUDDEN_LOUD, flags.single().kind)
        assertEquals(2_000L, flags.single().atMs)
    }

    @Test
    fun nearSilentAudioIsDetected() {
        assertTrue(Pcm16.isNearSilent(ShortArray(16_000) { (it % 3).toShort() }))
        assertTrue(!Pcm16.isNearSilent(ShortArray(16_000) { if (it == 8_000) 5_000 else 0 }))
        assertTrue(Pcm16.isNearSilent(ShortArray(0)))
    }
}
