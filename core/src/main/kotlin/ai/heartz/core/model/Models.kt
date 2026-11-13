package ai.heartz.core.model

import ai.heartz.core.moderation.ReviewAssist
import kotlinx.serialization.Serializable

@Serializable
data class GeoPoint(val lat: Double, val lng: Double)

/**
 * 편성에 사용하는 구간. 시연 데이터에서 직접 지정한다.
 * [points]를 잇는 선에서 [corridorMeters] 안이면 구간 안으로 본다.
 * [directional]이면 진행 방향(첫 점→마지막 점)과 차량 heading 차이가 [headingToleranceDeg] 이하여야 한다.
 */
@Serializable
data class Segment(
    val points: List<GeoPoint>,
    val corridorMeters: Double,
    val directional: Boolean = false,
    val headingToleranceDeg: Double = 60.0,
)

@Serializable
data class Place(
    val id: String,
    /** 랜드마크 이름. 카드·상세·주행 화면과 안내 음성에 쓴다. 예: "국립중앙박물관" */
    val shortName: String,
    /** "구 동 랜드마크". 예: "용산구 용산동6가 국립중앙박물관" */
    val displayName: String,
    /** 공개 화면에 쓰는 승인된 공개 위치(랜드마크 중심). 개인 주소·원본 GPS가 아니다. */
    val publicLocation: GeoPoint,
    /** 예전 데이터 호환용(도로 구간 판정은 없앴다). 새 장소는 null */
    val segment: Segment? = null,
    val description: String = "",
    /** 구. 예: "용산구" */
    val district: String = "",
    /** 동. 예: "용산동6가" */
    val dong: String = "",
    /** 이 반경 안에 들어오면 이곳 사연이 도착한다 */
    val radiusMeters: Double = DEFAULT_RADIUS_METERS,
) {
    /** 목록용 "구 랜드마크". 구를 모르면 랜드마크만 */
    val listLabel: String get() = if (district.isBlank()) shortName else "$district $shortName"

    companion object {
        const val DEFAULT_RADIUS_METERS = 1_000.0
    }
}

/**
 * 사연을 다른 사람에게 어떤 목소리로 전할지.
 * - ORIGINAL: 사연자가 녹음한 목소리(잡음 정리 후) 그대로
 * - MODULATED: 받아쓴 글을 듣는 사람 기기의 TTS로 읽는다. 원래 녹음은 작성자·운영자만 받을 수 있다.
 */
@Serializable
enum class VoiceMode { ORIGINAL, MODULATED }

/**
 * 사연 색. 분위기 분석이 들어오기 전까지 파랑·주황·노랑·초록 중 하나를 사연 ID로 고르게 붙인다(같은 사연은 항상 같은 색).
 */
enum class StoryTone {
    BLUE, ORANGE, YELLOW, GREEN;

    companion object {
        fun of(storyId: String): StoryTone {
            // 문자열 해시를 한 번 더 섞어 비슷한 ID가 같은 색으로 몰리지 않게 한다
            var h = storyId.hashCode().toLong() and 0xffffffffL
            h = (h xor (h ushr 16)) * 0x45d9f3bL and 0xffffffffL
            h = h xor (h ushr 16)
            return entries[(h % entries.size).toInt()]
        }
    }
}

/** 예전 데이터 호환용 분위기. 화면에서는 쓰지 않는다(감정 분석 도입 시 다시 검토). */
@Serializable
enum class Mood(val label: String) {
    WARM("따뜻한 이야기"),
    CHEERFUL("유쾌한 기억"),
    NOSTALGIC("그리운 이야기"),
    CALM("담담한 이야기"),
}

/**
 * 사연의 수명주기.
 * DRAFT(초안) → PRIVATE(나만 듣기) 또는 PENDING_REVIEW(공개 제출·검수 대기)
 * PENDING_REVIEW → APPROVED | REJECTED (운영자), APPROVED → HIDDEN (운영자)
 * 공개 재생은 APPROVED + 오디오 업로드 완료일 때만 가능하다.
 */
@Serializable
enum class StoryStatus { DRAFT, PRIVATE, PENDING_REVIEW, APPROVED, REJECTED, HIDDEN }

@Serializable
data class AudioRef(val blobId: String, val mimeType: String, val sizeBytes: Long)

@Serializable
data class Story(
    val id: String,
    val authorId: String,
    val authorName: String,
    val placeId: String,
    val audio: AudioRef? = null,
    val durationMs: Long,
    val mood: Mood,
    val status: StoryStatus,
    val createdAtMillis: Long,
    val submittedAtMillis: Long? = null,
    val reviewedAtMillis: Long? = null,
    val reviewNote: String? = null,
    /** 예전 데이터 호환용(음성 답장 기능은 없앴다). 새 사연은 항상 null */
    val parentStoryId: String? = null,
    val allowReplies: Boolean = false,
    /** 재시도 시 같은 초안이 중복 생성·공개되지 않게 하는 클라이언트 키 */
    val clientDraftId: String,
    /** 시드·합성 음원 등 실제 사용자 음성이 아닌 데이터 표시 */
    val sampleLabel: String? = null,
    /** 받아쓰기·의심 구간(검수 보조). 작성자·운영자에게만 내려간다 */
    val assist: ReviewAssist? = null,
    /** 지나면서 녹음한 사연(녹음 시작 위치로 장소를 연결함). 원본 좌표는 담지 않는다 */
    val recordedWhileDriving: Boolean = false,
    /** 받은 공감 하트 수. 서버가 응답할 때 계산해 채운다 */
    val heartCount: Int = 0,
    val voiceMode: VoiceMode = VoiceMode.ORIGINAL,
    /** MODULATED일 때 듣는 사람에게 TTS로 읽어 줄 글(받아쓰기). 운영자가 듣고 승인한다 */
    val spokenText: String? = null,
)

@Serializable
enum class UserRole { LISTENER, MODERATOR }

/** 개발·시연용 테스트 계정. 실명 확인을 구현하지 않는다. */
@Serializable
data class User(
    val id: String,
    val displayName: String,
    val role: UserRole,
    val isTestAccount: Boolean = true,
    /** 시드 작성자처럼 로그인 목록에 보이지 않는 계정은 false */
    val canSignIn: Boolean = true,
)


@Serializable
enum class ListenKind { SUGGESTED, PLAYED }

/** 사용자별 제안·재생 여부와 숨긴 작성자. 주행 좌표 이력은 저장하지 않는다. */
@Serializable
data class ListenState(
    val suggested: Map<String, Long> = emptyMap(),
    val played: Map<String, Long> = emptyMap(),
    val hiddenAuthorIds: Set<String> = emptySet(),
    /** 이 사용자가 공감 하트를 남긴 사연과 시각 */
    val hearted: Map<String, Long> = emptyMap(),
)
