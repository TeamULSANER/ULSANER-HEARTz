package ai.heartz.core.service

import ai.heartz.core.model.ListenKind
import ai.heartz.core.model.ListenState
import ai.heartz.core.model.Place
import ai.heartz.core.model.Story
import ai.heartz.core.model.User
import ai.heartz.core.model.UserRole
import ai.heartz.core.model.AudioRef
import ai.heartz.core.model.StoryStatus
import ai.heartz.core.model.VoiceMode
import ai.heartz.core.moderation.ReviewAssist
import ai.heartz.core.moderation.TranscriptStatus
import kotlinx.serialization.Serializable

/** 저장 단위 스냅샷. 작은 시연 규모라 JSON 한 파일로 충분하다. */
@Serializable
data class HeartzDb(
    val users: List<User> = emptyList(),
    val places: List<Place> = emptyList(),
    val stories: List<Story> = emptyList(),
    val listenStates: Map<String, ListenState> = emptyMap(),
    val reports: List<Report> = emptyList(),
)

interface HeartzStore {
    fun load(): HeartzDb?
    fun save(db: HeartzDb)
}

interface BlobStore {
    fun put(blobId: String, bytes: ByteArray)
    fun get(blobId: String): ByteArray?
    fun delete(blobId: String)
}

/**
 * 공개 범위·검수 권한을 강제하는 서비스. 로컬 HTTP 서버와 앱의 기기 내 백엔드가 같은 규칙을 쓴다.
 * 모든 공개 메서드는 [actor]를 받아 권한을 서버 측에서 검사한다.
 */
class HeartzService(
    private val store: HeartzStore,
    private val blobs: BlobStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { java.util.UUID.randomUUID().toString() },
) {
    private val lock = Any()
    private var db: HeartzDb = store.load() ?: HeartzDb()

    fun seedIfEmpty(seed: HeartzDb, seedBlobs: Map<String, ByteArray>) = synchronized(lock) {
        if (db.users.isNotEmpty() || db.places.isNotEmpty() || db.stories.isNotEmpty()) return@synchronized
        seedBlobs.forEach { (id, bytes) -> blobs.put(id, bytes) }
        commit(seed)
    }

    /**
     * 기존 기기·서버 데이터의 시드 사연을 지금의 시드 정의에 맞춘다(음원 파일·길이·표시·목소리·읽어 줄 글). 시작할 때마다 부른다.
     * sampleLabel이 있는 시드 사연만, 같은 id의 정의가 있고 그 음원이 있을 때만 바꾼다. 상태·하트·사람이 남긴 사연은 그대로다.
     */
    fun refreshSeedStories(seedStories: List<Story>, seedBlobs: Map<String, ByteArray>) = synchronized(lock) {
        val defs = seedStories.associateBy { it.id }
        var changed = false
        val stories = db.stories.map { s ->
            val def = defs[s.id]
            val ref = def?.audio
            val bytes = ref?.blobId?.let(seedBlobs::get)
            if (s.sampleLabel == null || def == null || ref == null || bytes == null) return@map s
            blobs.put(ref.blobId, bytes)
            val next = s.copy(audio = ref.copy(sizeBytes = bytes.size.toLong()), durationMs = def.durationMs, sampleLabel = def.sampleLabel,
                voiceMode = def.voiceMode, spokenText = def.spokenText,
            )
            if (next != s) changed = true
            next
        }
        if (changed) commit(db.copy(stories = stories))
    }

    fun users(): List<User> = synchronized(lock) { db.users }
    fun user(userId: String): User? = synchronized(lock) { db.users.firstOrNull { it.id == userId } }
    fun places(): List<Place> = synchronized(lock) { db.places }

    /**
     * 닉네임 저장. 앱은 Pleos Connect 프로필 이름(Android 사용자 이름)을 그대로 보낸다.
     * 사연 응답의 "보낸 이"는 최신 닉네임을 쓴다.
     */
    fun setNickname(actor: User, nickname: String): User = synchronized(lock) {
        requireKnown(actor)
        ai.heartz.core.model.ProfileRules.nicknameError(nickname)?.let { invalid(it) }
        val users = db.users.map { if (it.id == actor.id) it.copy(displayName = nickname.trim()) else it }
        commit(db.copy(users = users))
        users.first { it.id == actor.id }
    }

    /**
     * 장소 목록을 새 시드(서울 랜드마크)로 맞춘다. 같은 ID는 새 값으로 바꾸고 새 장소는 더한다. 사연은 건드리지 않는다.
     * 이전 기기·서버 데이터에도 반영되게 시작할 때마다 부른다. 바뀐 게 없으면 저장하지 않는다.
     */
    fun refreshSeedPlaces(seedPlaces: List<Place>) = synchronized(lock) {
        val seedIds = seedPlaces.map { it.id }.toSet()
        val next = seedPlaces + db.places.filter { it.id !in seedIds }
        if (next != db.places) commit(db.copy(places = next))
    }

    /** 승인·오디오 있음·답장 아님. 다른 계정에 공개되는 유일한 목록 */
    fun publicFeed(actor: User): List<Story> = synchronized(lock) {
        requireKnown(actor)
        db.stories.filter { it.isPubliclyPlayable() && it.parentStoryId == null }
            .sortedByDescending { it.reviewedAtMillis ?: it.createdAtMillis }
            .map { it.redactedFor(actor).withHearts() }
    }

    fun myStories(actor: User): List<Story> = synchronized(lock) {
        requireKnown(actor)
        db.stories.filter { it.authorId == actor.id }.sortedByDescending { it.createdAtMillis }.map { it.withHearts() }
    }

    fun story(actor: User, storyId: String): Story = synchronized(lock) { visibleStory(actor, storyId).redactedFor(actor).withHearts() }

    /** 작성자만, 검수 전(DRAFT·PRIVATE·PENDING_REVIEW)에만 붙인다. 검수 상태는 바꾸지 않는다. */
    fun attachAssist(actor: User, storyId: String, assist: ReviewAssist): Story = synchronized(lock) {
        val story = ownStory(actor, storyId)
        if (story.status !in setOf(StoryStatus.DRAFT, StoryStatus.PRIVATE, StoryStatus.PENDING_REVIEW)) {
            conflict("assist can only be attached before review (${story.status})")
        }
        // 변조 목소리로 보낼 사연은 받아쓴 글을 읽어 줄 글로 쓴다
        val spoken = if (story.voiceMode == VoiceMode.MODULATED && assist.transcript.status == TranscriptStatus.DONE) {
            assist.transcript.text?.trim()?.takeIf { it.isNotEmpty() }
        } else {
            story.spokenText
        }
        story.copy(assist = assist, spokenText = spoken).also(::replace)
    }

    fun createDraft(actor: User, req: CreateStoryRequest): Story = synchronized(lock) {
        requireKnown(actor)
        db.stories.firstOrNull { it.authorId == actor.id && it.clientDraftId == req.clientDraftId }?.let { return it }
        if (req.clientDraftId.isBlank() || req.clientDraftId.length > 80) invalid("clientDraftId is required")
        // 남의 사연에 음성으로 답하는 기능은 제공하지 않는다(공감 하트만)
        if (req.parentStoryId != null) invalid("voice replies are not supported")
        val placeId = req.placeId
        val maxDuration = MAX_STORY_DURATION_MS
        if (db.places.none { it.id == placeId }) invalid("unknown place")
        if (req.durationMs !in MIN_DURATION_MS..maxDuration) invalid("duration must be ${MIN_DURATION_MS}..$maxDuration ms")
        val story = Story(
            id = idGenerator(),
            authorId = actor.id,
            authorName = actor.displayName,
            placeId = placeId,
            durationMs = req.durationMs,
            mood = req.mood,
            status = StoryStatus.DRAFT,
            createdAtMillis = clock(),
            clientDraftId = req.clientDraftId,
            recordedWhileDriving = req.recordedWhileDriving,
            voiceMode = req.voiceMode,
        )
        commit(db.copy(stories = db.stories + story))
        story
    }

    fun uploadAudio(actor: User, storyId: String, bytes: ByteArray, mimeType: String): UploadResult = synchronized(lock) {
        val story = ownStory(actor, storyId)
        if (story.status != StoryStatus.DRAFT && story.status != StoryStatus.PRIVATE) {
            throw ApiException(ApiErrorCode.CONFLICT, "audio cannot change after submission (${story.status})")
        }
        if (bytes.isEmpty()) invalid("empty audio")
        if (bytes.size > MAX_AUDIO_BYTES) throw ApiException(ApiErrorCode.TOO_LARGE, "audio exceeds $MAX_AUDIO_BYTES bytes")
        if (!mimeType.startsWith("audio/")) invalid("unsupported media type $mimeType")
        val blobId = "audio-${story.id}"
        blobs.put(blobId, bytes)
        val updated = story.copy(audio = AudioRef(blobId, mimeType, bytes.size.toLong()))
        replace(updated)
        UploadResult(story.id, bytes.size.toLong(), updated.status)
    }

    fun submit(actor: User, storyId: String): SubmitResult = synchronized(lock) {
        val story = ownStory(actor, storyId)
        when (story.status) {
            StoryStatus.PENDING_REVIEW, StoryStatus.APPROVED -> SubmitResult(story.id, story.status, alreadySubmitted = true)
            StoryStatus.DRAFT, StoryStatus.PRIVATE -> {
                if (story.audio == null) invalid("upload audio before submitting")
                val updated = story.copy(status = StoryStatus.PENDING_REVIEW, submittedAtMillis = clock())
                replace(updated)
                SubmitResult(story.id, updated.status, alreadySubmitted = false)
            }
            StoryStatus.REJECTED, StoryStatus.HIDDEN ->
                throw ApiException(ApiErrorCode.CONFLICT, "cannot resubmit a ${story.status} story")
        }
    }

    fun deleteOwn(actor: User, storyId: String) = synchronized(lock) {
        val story = ownStory(actor, storyId)
        story.audio?.let { blobs.delete(it.blobId) }
        commit(db.copy(stories = db.stories.filterNot { it.id == story.id }))
    }

    fun audio(actor: User, storyId: String): Pair<Story, ByteArray> = synchronized(lock) {
        val story = visibleStory(actor, storyId).redactedFor(actor)
        // 변조 목소리로 보낸 사연의 원래 녹음은 작성자·운영자만 받는다
        if (story.voiceMode == VoiceMode.MODULATED && story.authorId != actor.id && actor.role != UserRole.MODERATOR) notFound()
        val ref = story.audio ?: notFound()
        val bytes = blobs.get(ref.blobId) ?: throw ApiException(ApiErrorCode.UNAVAILABLE, "audio blob missing")
        story to bytes
    }

    fun moderationOverview(actor: User): ModerationOverview = synchronized(lock) {
        requireModerator(actor)
        ModerationOverview(
            pending = db.stories.filter { it.status == StoryStatus.PENDING_REVIEW }.sortedBy { it.submittedAtMillis }.map { it.withHearts() },
            approved = db.stories.filter { it.status == StoryStatus.APPROVED }.sortedByDescending { it.reviewedAtMillis }.map { it.withHearts() },
            reports = db.reports.sortedByDescending { it.createdAtMillis },
        )
    }

    fun moderate(actor: User, storyId: String, req: ModerationRequest): ModerationResult = synchronized(lock) {
        requireModerator(actor)
        val story = db.stories.firstOrNull { it.id == storyId } ?: notFound()
        val next = when (req.action) {
            ModerationAction.APPROVE, ModerationAction.REJECT -> {
                if (story.status != StoryStatus.PENDING_REVIEW) conflict("only pending stories can be reviewed")
                if (req.action == ModerationAction.APPROVE && story.audio == null) conflict("no audio")
                if (req.action == ModerationAction.APPROVE && story.voiceMode == VoiceMode.MODULATED && story.spokenText.isNullOrBlank()) {
                    conflict("modulated story has no text to read")
                }
                if (req.action == ModerationAction.APPROVE) StoryStatus.APPROVED else StoryStatus.REJECTED
            }
            ModerationAction.HIDE -> {
                if (story.status != StoryStatus.APPROVED) conflict("only approved stories can be hidden")
                StoryStatus.HIDDEN
            }
        }
        val updated = story.copy(status = next, reviewedAtMillis = clock(), reviewNote = req.note?.take(200))
        replace(updated)
        ModerationResult(story.id, next, updated.isPubliclyPlayable())
    }

    fun report(actor: User, storyId: String, reason: String): Report = synchronized(lock) {
        val story = visibleStory(actor, storyId)
        if (story.status != StoryStatus.APPROVED) notFound()
        val report = Report(idGenerator(), story.id, actor.id, reason.take(200), clock())
        commit(db.copy(reports = db.reports + report))
        report
    }

    fun listenState(actor: User): ListenState = synchronized(lock) {
        requireKnown(actor)
        db.listenStates[actor.id] ?: ListenState()
    }

    fun recordListen(actor: User, storyId: String, kind: ListenKind): ListenState = updateListen(actor) { st ->
        val at = clock()
        when (kind) {
            ListenKind.SUGGESTED -> st.copy(suggested = st.suggested + (storyId to (st.suggested[storyId] ?: at)))
            ListenKind.PLAYED -> st.copy(played = st.played + (storyId to at))
        }
    }

    /** 공감 하트. 다른 사람의 공개 사연에만, 한 사람당 한 번. [on]=false면 거둔다. */
    fun heart(actor: User, storyId: String, on: Boolean): ListenState = synchronized(lock) {
        val story = visibleStory(actor, storyId)
        if (!story.isPubliclyPlayable()) notFound()
        if (story.authorId == actor.id) invalid("cannot heart your own story")
        updateListen(actor) { st ->
            if (on) st.copy(hearted = st.hearted + (storyId to (st.hearted[storyId] ?: clock())))
            else st.copy(hearted = st.hearted - storyId)
        }
    }

    /** 시험용: 받은·들은 편지 기록만 지운다(하트·숨긴 사람은 둔다). 같은 편지를 다시 받아 볼 때 쓴다. */
    fun clearListenHistory(actor: User): ListenState =
        updateListen(actor) { it.copy(suggested = emptyMap(), played = emptyMap()) }

    fun hideAuthor(actor: User, authorId: String): ListenState {
        if (authorId == actor.id) invalid("cannot hide yourself")
        return updateListen(actor) { it.copy(hiddenAuthorIds = it.hiddenAuthorIds + authorId) }
    }

    fun unhideAuthor(actor: User, authorId: String): ListenState =
        updateListen(actor) { it.copy(hiddenAuthorIds = it.hiddenAuthorIds - authorId) }

    private fun updateListen(actor: User, f: (ListenState) -> ListenState): ListenState = synchronized(lock) {
        requireKnown(actor)
        val next = f(db.listenStates[actor.id] ?: ListenState())
        commit(db.copy(listenStates = db.listenStates + (actor.id to next)))
        next
    }

    private fun Story.isPubliclyPlayable() = status == StoryStatus.APPROVED && audio != null

    /** 응답 시 채우는 값: 하트 수, 작성자의 최신 닉네임 */
    private fun Story.withHearts(): Story = copy(
        heartCount = db.listenStates.values.count { id in it.hearted },
        authorName = db.users.firstOrNull { it.id == authorId }?.displayName ?: authorName,
    )

    /** 받아쓰기는 개인정보가 담길 수 있어 작성자·운영자에게만 보낸다 */
    private fun Story.redactedFor(actor: User): Story =
        if (authorId == actor.id || actor.role == UserRole.MODERATOR) this else copy(assist = null)

    /** 작성자·운영자 또는 공개 재생 가능한 사연만 보인다. 그 외는 존재 자체를 숨긴다(NOT_FOUND). */
    private fun visibleStory(actor: User, storyId: String): Story {
        requireKnown(actor)
        val s = db.stories.firstOrNull { it.id == storyId } ?: notFound()
        val visible = s.authorId == actor.id || actor.role == UserRole.MODERATOR || s.isPubliclyPlayable()
        return if (visible) s else notFound()
    }

    private fun ownStory(actor: User, storyId: String): Story {
        requireKnown(actor)
        return db.stories.firstOrNull { it.id == storyId && it.authorId == actor.id } ?: notFound()
    }

    private fun requireKnown(actor: User) {
        val known = db.users.firstOrNull { it.id == actor.id }
            ?: throw ApiException(ApiErrorCode.UNAUTHORIZED, "unknown user")
        if (known.role != actor.role) throw ApiException(ApiErrorCode.UNAUTHORIZED, "role mismatch")
    }

    private fun requireModerator(actor: User) {
        requireKnown(actor)
        if (actor.role != UserRole.MODERATOR) throw ApiException(ApiErrorCode.FORBIDDEN, "moderator only")
    }

    private fun replace(story: Story) = commit(db.copy(stories = db.stories.map { if (it.id == story.id) story else it }))

    private fun commit(next: HeartzDb) {
        store.save(next)
        db = next
    }

    private fun invalid(msg: String): Nothing = throw ApiException(ApiErrorCode.INVALID, msg)
    private fun conflict(msg: String): Nothing = throw ApiException(ApiErrorCode.CONFLICT, msg)
    private fun notFound(): Nothing = throw ApiException(ApiErrorCode.NOT_FOUND, "story not found")

    companion object {
        const val MAX_AUDIO_BYTES = 5L * 1024 * 1024
        const val MIN_DURATION_MS = 2_000L
        const val MAX_STORY_DURATION_MS = 90_000L
    }
}
