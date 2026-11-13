package ai.heartz.core.seed

import ai.heartz.core.model.AudioRef
import ai.heartz.core.model.GeoPoint
import ai.heartz.core.model.Mood
import ai.heartz.core.model.Place
import ai.heartz.core.model.Story
import ai.heartz.core.model.StoryStatus
import ai.heartz.core.model.User
import ai.heartz.core.model.UserRole
import ai.heartz.core.model.VoiceMode
import ai.heartz.core.service.HeartzDb

/**
 * 시연 데이터. 장소는 [SeoulLandmarks](서울 각 구 랜드마크, 근사 좌표), 시드 사연은 용산구 랜드마크에 있다.
 * 계정은 시연 운전자 세 명(장클로드·김피티·상도동메시)과 운영자. 예전 계정 id를 그대로 쓴다(바다→장클로드, 봄날→김피티, 은행나무→상도동메시).
 * 음원(seed/audio): seed-kim·seed-jeon은 팀원이 대본을 읽은 녹음, 나머지는 Pleos TextToSpeech로 읽은 합성 테스트 음원. 모두 실제 사연이 아니다.
 */
object SeedData {
    const val SAMPLE_LABEL = "합성 테스트 음원 · 실제 사연 아님"
    const val TEAM_LABEL = "팀원 녹음 시연 사연 · 실제 사연 아님"

    const val DRIVER_JANG = "u-listener-b"
    const val DRIVER_KIM = "u-seed-spring"
    const val DRIVER_MESSI = "u-seed-ginkgo"
    const val MODERATOR = "u-moderator"

    /** 이촌로를 따라 서→동으로 가는 시연 경로. 사연이 있는 용산구 랜드마크 반경을 차례로 지난다(geo fix 재현용) */
    val demoRoute: List<GeoPoint> = listOf(
        GeoPoint(37.52290, 126.96200),
        GeoPoint(37.52240, 126.96750),
        GeoPoint(37.52200, 126.97300),
        GeoPoint(37.52150, 126.97800),
        GeoPoint(37.52080, 126.98250),
        GeoPoint(37.51980, 126.98700),
        GeoPoint(37.51900, 126.99050),
    )

    /** blobId → seed/audio 파일 이름 */
    val audioFiles: Map<String, String> = mapOf(
        "seed-kim" to "seed-kim.m4a",
        "seed-calm" to "seed-calm.m4a",
        "seed-jeon" to "seed-jeon.m4a",
        "seed-pending" to "seed-pending.m4a",
    )

    private val users = listOf(
        User(DRIVER_JANG, "장클로드", UserRole.LISTENER),
        User(DRIVER_KIM, "김피티", UserRole.LISTENER),
        User(DRIVER_MESSI, "상도동메시", UserRole.LISTENER),
        User(MODERATOR, "운영자 (테스트)", UserRole.MODERATOR),
    )

    /** 서울 25개 구 랜드마크. 시드 사연은 용산구 4곳(p-ichon·p-dongbu·p-seobinggo·p-riverside)에 있다 */
    private val places = SeoulLandmarks.places

    private data class SeedStory(
        val id: String,
        val author: String,
        val place: String,
        val blob: String,
        val seconds: Long,
        val mood: Mood,
        val status: StoryStatus = StoryStatus.APPROVED,
        val ageHours: Long,
        val label: String = SAMPLE_LABEL,
        /** 변조 목소리 사연이면 듣는 사람에게 TTS로 읽어 줄 글 */
        val spokenText: String? = null,
    )

    /**
     * 시연에 쓰는 몇 개만 남긴다. 김피티의 이촌한강공원 사연(시연 장면 1)과 상도동메시의 국립한글박물관 사연은 팀원 녹음.
     * 상도동메시 사연은 변조 목소리: 듣는 사람에게는 대본 글을 TTS로 읽어 주고, 원본 녹음은 운영자만 듣는다.
     */
    private val seedStories = listOf(
        SeedStory("s-seed-riverside", DRIVER_KIM, "p-riverside", "seed-kim", 13, Mood.WARM, ageHours = 6, label = TEAM_LABEL),
        SeedStory("s-seed-seobinggo", DRIVER_KIM, "p-seobinggo", "seed-calm", 7, Mood.CALM, ageHours = 8),
        SeedStory(
            "s-seed-dongbu", DRIVER_MESSI, "p-dongbu", "seed-jeon", 13, Mood.NOSTALGIC, ageHours = 12, label = TEAM_LABEL,
            spokenText = "한글박물관에서 아이가 처음으로 자기 이름을 써 봤어요. 삐뚤빼뚤한 글씨를 보고 둘이 한참 웃었는데, 그 종이가 아직도 냉장고에 붙어 있어요.",
        ),
        SeedStory(
            "s-seed-ichon-pending", DRIVER_MESSI, "p-ichon", "seed-pending", 5, Mood.CHEERFUL,
            status = StoryStatus.PENDING_REVIEW, ageHours = 1,
        ),
    )

    fun db(nowMillis: Long): HeartzDb {
        val byId = users.associateBy { it.id }
        val stories = seedStories.map { s ->
            val created = nowMillis - s.ageHours * 3_600_000
            Story(
                id = s.id,
                authorId = s.author,
                authorName = byId.getValue(s.author).displayName,
                placeId = s.place,
                audio = AudioRef(s.blob, "audio/mp4", 0),
                durationMs = s.seconds * 1000,
                mood = s.mood,
                status = s.status,
                createdAtMillis = created,
                submittedAtMillis = created,
                reviewedAtMillis = if (s.status == StoryStatus.APPROVED) created + 600_000 else null,
                clientDraftId = s.id,
                sampleLabel = s.label,
                voiceMode = if (s.spokenText != null) VoiceMode.MODULATED else VoiceMode.ORIGINAL,
                spokenText = s.spokenText,
            )
        }
        return HeartzDb(users = users, places = places, stories = stories)
    }
}
