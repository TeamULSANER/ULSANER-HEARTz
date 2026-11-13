package ai.heartz.core

import ai.heartz.core.model.AudioRef
import ai.heartz.core.model.ListenState
import ai.heartz.core.model.Mood
import ai.heartz.core.model.Story
import ai.heartz.core.model.StoryStatus
import ai.heartz.core.schedule.PassedLetters
import org.junit.Assert.assertEquals
import org.junit.Test

class PassedLettersTest {
    private fun story(id: String, author: String) = Story(
        id = id, authorId = author, authorName = author, placeId = "p", audio = AudioRef("b", "audio/mp4", 1),
        durationMs = 10_000, mood = Mood.WARM, status = StoryStatus.APPROVED, createdAtMillis = 0, clientDraftId = id,
    )
    private val feed = listOf(story("a1", "a"), story("b1", "b"), story("b2", "b"))
    private val today = 1_000L

    @Test
    fun unplayedSuggestionsOfTodayNewestFirst() {
        val listen = ListenState(suggested = mapOf("a1" to 1_100, "b1" to 1_200, "b2" to 900), played = emptyMap())
        assertEquals(listOf("b1", "a1"), PassedLetters.unplayed(feed, listen, today).map { it.first.id })
    }

    @Test
    fun hiddenAuthorsAreExcludedFromPassedAndHeard() {
        val listen = ListenState(
            suggested = mapOf("a1" to 1_100, "b1" to 1_200),
            played = mapOf("b2" to 1_300),
            hiddenAuthorIds = setOf("b"),
        )
        assertEquals(listOf("a1"), PassedLetters.unplayed(feed, listen, today).map { it.first.id })
        assertEquals(emptyList<String>(), PassedLetters.heard(feed, listen, today).map { it.first.id })
    }

    @Test
    fun storiesNoLongerInFeedAreDropped() {
        val listen = ListenState(suggested = mapOf("gone" to 1_100, "a1" to 1_100))
        assertEquals(listOf("a1"), PassedLetters.unplayed(feed, listen, today).map { it.first.id })
    }
}
