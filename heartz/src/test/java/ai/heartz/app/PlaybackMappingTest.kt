package ai.heartz.app

import ai.heartz.app.platform.audio.isCompleted
import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackMappingTest {
    @Test
    fun endedStateIsCompleted() {
        assertTrue(isCompleted(Player.STATE_ENDED, 24_120, 24_120))
    }

    @Test
    fun idleAtEndAfterServiceStopIsCompleted() {
        assertTrue(isCompleted(Player.STATE_IDLE, 24_100, 24_120))
    }

    @Test
    fun idleBeforeEndIsNotCompleted() {
        assertFalse(isCompleted(Player.STATE_IDLE, 5_000, 24_120))
        assertFalse(isCompleted(Player.STATE_IDLE, 0, 0))
    }

    @Test
    fun readyNearEndIsNotYetCompleted() {
        assertFalse(isCompleted(Player.STATE_READY, 24_100, 24_120))
    }
}
