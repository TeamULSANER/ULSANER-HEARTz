package ai.heartz.core.schedule

import ai.heartz.core.model.ListenState
import ai.heartz.core.model.Place
import ai.heartz.core.model.Story

/** 지금 화면에 놓인 편지 카드. 카드 표시는 재생·오디오 포커스와 무관하다. */
data class LetterCard(
    val story: Story,
    val place: Place,
    val shownAtMillis: Long,
    val lastInSegmentMillis: Long,
    val playing: Boolean = false,
    val playedToEnd: Boolean = false,
)

sealed interface SessionEvent {
    data class Shown(val card: LetterCard) : SessionEvent
    /** 지금 사연을 읽는 중이라 다음 차례로 줄 세움 */
    data class Queued(val card: LetterCard) : SessionEvent
    data class Cleared(val storyId: String) : SessionEvent
}

/**
 * 위치 갱신마다 카드 표시·유지·만료를 결정하는 순수 상태 기계.
 * - 유효한 위치(LocationValidity.Valid)에서만 새 카드를 띄운다.
 * - 구간을 벗어난 뒤 [lingerMillis] 동안 유지하고, 재생 중에는 만료하지 않는다.
 * - 한 세션에서 이미 띄운 사연은 다시 띄우지 않는다(서버 listen state 반영 지연과 무관).
 * - 작성자를 숨기거나 사연이 공개 목록에서 빠지면 지금 카드도 즉시 내린다.
 */
class LetterSession(
    private val picker: LetterPicker,
    private val lingerMillis: Long = DEFAULT_LINGER_MILLIS,
) {
    var card: LetterCard? = null
        private set

    private val shownIds = mutableSetOf<String>()

    /** 지금 사연을 읽거나 공감을 묻는 중. 이때는 지금 카드를 바꾸거나 내리지 않고 새 사연은 줄 세운다 */
    var busy: Boolean = false

    private val queue = ArrayDeque<LetterCard>()
    val queuedCount: Int get() = queue.size

    /** 다 듣고(또는 넘기고) 닫은 카드의 장소. 그 구간을 벗어날 때까지 새 편지를 띄우지 않는다. */
    private var quietPlace: Place? = null

    fun onLocation(
        validity: LocationValidity,
        nowMillis: Long,
        stories: List<Story>,
        places: List<Place>,
        listenState: ListenState,
        viewerId: String,
    ): List<SessionEvent> {
        val events = mutableListOf<SessionEvent>()
        val fix = (validity as? LocationValidity.Valid)?.fix
        val current = card
        quietPlace?.let { q -> if (fix != null && !Eligibility.inArea(q, fix)) quietPlace = null }
        fun candidate(): LetterCard? {
            if (fix == null) return null
            val quiet = quietPlace?.id
            val evaluations = Eligibility.evaluate(stories, places, fix, listenState, viewerId)
                .filter { it.story.placeId != quiet }
                .map { if (it.story.id in shownIds) it.copy(exclusions = it.exclusions + Exclusion.ALREADY_SUGGESTED) else it }
            val story = picker.pick(evaluations) ?: return null
            val place = places.first { it.id == story.placeId }
            return LetterCard(story, place, shownAtMillis = nowMillis, lastInSegmentMillis = nowMillis)
        }
        fun show(next: LetterCard) {
            shownIds += next.story.id
            card = next
            events += SessionEvent.Shown(next)
        }

        // 줄 선 사연 중 숨기거나 공개 목록에서 빠진 것은 버린다
        queue.removeAll { q -> q.story.authorId in listenState.hiddenAuthorIds || stories.none { it.id == q.story.id } }

        if (current == null && queue.isNotEmpty()) {
            val next = queue.removeFirst().copy(shownAtMillis = nowMillis, lastInSegmentMillis = nowMillis)
            card = next
            events += SessionEvent.Shown(next)
            return events
        }

        if (current != null) {
            // 사용자가 작성자를 숨겼거나 사연이 공개 목록에서 빠졌으면(운영자 숨김 등) 재생 여부와 관계없이 바로 내린다
            val withdrawn = current.story.authorId in listenState.hiddenAuthorIds || stories.none { it.id == current.story.id }
            if (withdrawn) {
                events += SessionEvent.Cleared(current.story.id)
                card = null
                return events
            }
            if (fix != null && Eligibility.inArea(current.place, fix)) {
                card = current.copy(lastInSegmentMillis = nowMillis)
                return events
            }
            if (current.playing || busy) {
                // 다른 장소에 들어오면 그곳 사연을 한 장소에 하나씩 줄 세운다
                val next = candidate()
                if (next != null && next.place.id != current.place.id && queue.none { it.place.id == next.place.id }) {
                    shownIds += next.story.id
                    queue += next
                    events += SessionEvent.Queued(next)
                }
                return events
            }
            val expired = nowMillis - current.lastInSegmentMillis > lingerMillis
            if (!expired) {
                val next = candidate() ?: return events
                events += SessionEvent.Cleared(current.story.id)
                show(next)
                return events
            }
            events += SessionEvent.Cleared(current.story.id)
            card = null
        }
        candidate()?.let(::show)
        return events
    }

    fun onPlaybackStarted(storyId: String) {
        val c = card ?: return
        if (c.story.id == storyId) card = c.copy(playing = true)
    }

    fun onPlaybackStopped(storyId: String, completed: Boolean, nowMillis: Long) {
        val c = card ?: return
        if (c.story.id != storyId) return
        card = c.copy(
            playing = false,
            playedToEnd = c.playedToEnd || completed,
            lastInSegmentMillis = maxOf(c.lastInSegmentMillis, nowMillis),
        )
    }

    /** 다 듣거나 넘긴 카드를 닫고 대기 화면으로 돌아간다. 지금 카드가 [storyId]일 때만. */
    fun dismiss(storyId: String): Boolean {
        val c = card ?: return false
        if (c.story.id != storyId) return false
        card = null
        quietPlace = c.place
        return true
    }

    /** 계정 전환 등으로 세션을 비운다. */
    fun reset() {
        card = null
        queue.clear()
        busy = false
        quietPlace = null
        shownIds.clear()
    }

    companion object {
        const val DEFAULT_LINGER_MILLIS = 90_000L
    }
}
