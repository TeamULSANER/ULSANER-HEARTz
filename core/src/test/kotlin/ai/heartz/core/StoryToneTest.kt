package ai.heartz.core

import ai.heartz.core.model.StoryTone
import org.junit.Assert.assertEquals
import org.junit.Test

/** 사연마다 파랑·주황·노랑·초록 중 하나를 고르게(무작위처럼) 붙이되, 같은 사연은 언제나 같은 색 */
class StoryToneTest {
    @Test
    fun sameStoryAlwaysGetsSameTone() {
        assertEquals(StoryTone.of("s-seed-ichon-warm"), StoryTone.of("s-seed-ichon-warm"))
    }

    @Test
    fun allFourTonesAreUsedFairly() {
        val counts = (1..400).groupingBy { StoryTone.of("story-$it") }.eachCount()
        assertEquals(StoryTone.entries.toSet(), counts.keys)
        counts.values.forEach { assert(it in 60..140) { "tone counts $counts" } }
    }
}
