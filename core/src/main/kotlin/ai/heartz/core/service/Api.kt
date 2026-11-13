package ai.heartz.core.service

import ai.heartz.core.model.ListenKind
import ai.heartz.core.model.ListenState
import ai.heartz.core.model.Mood
import ai.heartz.core.model.Place
import ai.heartz.core.model.Story
import ai.heartz.core.model.StoryStatus
import ai.heartz.core.model.User
import kotlinx.serialization.Serializable

/** 서버·로컬 백엔드 공통 오류. HTTP 계층은 [httpStatus]로 매핑한다. */
@Serializable
enum class ApiErrorCode(val httpStatus: Int) {
    UNAUTHORIZED(401),
    FORBIDDEN(403),
    NOT_FOUND(404),
    CONFLICT(409),
    INVALID(400),
    TOO_LARGE(413),
    UNAVAILABLE(503),
}

class ApiException(val code: ApiErrorCode, override val message: String) : Exception(message)

@Serializable
data class ApiErrorBody(val code: ApiErrorCode, val message: String)

@Serializable
data class LoginRequest(val userId: String)

@Serializable
data class LoginResponse(val token: String, val user: User)

@Serializable
data class CreateStoryRequest(
    val clientDraftId: String,
    val placeId: String,
    val mood: Mood,
    val durationMs: Long,
    /** 음성 답장은 지원하지 않는다. 값이 있으면 서버가 거절한다 */
    val parentStoryId: String? = null,
    val allowReplies: Boolean = false,
    val recordedWhileDriving: Boolean = false,
    val voiceMode: ai.heartz.core.model.VoiceMode = ai.heartz.core.model.VoiceMode.ORIGINAL,
)

/** 업로드 성공은 공개 제출·승인·공개 재생 가능과 다른 결과다. */
@Serializable
data class UploadResult(val storyId: String, val sizeBytes: Long, val status: StoryStatus)

@Serializable
data class SubmitResult(val storyId: String, val status: StoryStatus, val alreadySubmitted: Boolean)

@Serializable
enum class ModerationAction { APPROVE, REJECT, HIDE }

@Serializable
data class ModerationRequest(val action: ModerationAction, val note: String? = null)

@Serializable
data class ModerationResult(val storyId: String, val status: StoryStatus, val publiclyPlayable: Boolean)

@Serializable
data class ReportRequest(val reason: String)

@Serializable
data class Report(
    val id: String,
    val storyId: String,
    val reporterId: String,
    val reason: String,
    val createdAtMillis: Long,
)

@Serializable
data class ListenEventRequest(val storyId: String, val kind: ListenKind)

@Serializable
data class HideAuthorRequest(val authorId: String)

@Serializable
data class ProfileRequest(val nickname: String)

@Serializable
data class ModerationOverview(
    val pending: List<Story>,
    val approved: List<Story>,
    val reports: List<Report>,
)

@Serializable
data class StoryList(val stories: List<Story>)

@Serializable
data class PlaceList(val places: List<Place>)

@Serializable
data class UserList(val users: List<User>)

@Serializable
data class ListenStateResponse(val state: ListenState)
