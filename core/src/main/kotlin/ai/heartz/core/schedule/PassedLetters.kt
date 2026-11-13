package ai.heartz.core.schedule

import ai.heartz.core.model.ListenState
import ai.heartz.core.model.Story

/**
 * "오늘 지나친 편지"(표시됐지만 재생하지 않음)와 "오늘 들은 편지" 목록.
 * 공개 목록(feed)에 남아 있고, 사용자가 숨긴 작성자가 아닌 사연만 보여 준다. 최신순.
 */
object PassedLetters {
    fun unplayed(feed: List<Story>, listen: ListenState, startOfToday: Long): List<Pair<Story, Long>> =
        pick(feed, listen, listen.suggested.filter { (id, at) -> at >= startOfToday && id !in listen.played })

    fun heard(feed: List<Story>, listen: ListenState, startOfToday: Long): List<Pair<Story, Long>> =
        pick(feed, listen, listen.played.filter { (_, at) -> at >= startOfToday })

    private fun pick(feed: List<Story>, listen: ListenState, entries: Map<String, Long>): List<Pair<Story, Long>> {
        val byId = feed.associateBy { it.id }
        return entries.entries.sortedByDescending { it.value }
            .mapNotNull { (id, at) -> byId[id]?.takeIf { it.authorId !in listen.hiddenAuthorIds }?.let { it to at } }
    }
}
