package ai.heartz.app.data

import ai.heartz.core.model.Mood
import ai.heartz.core.service.HeartzJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

@Serializable
enum class DraftIntent { KEEP_PRIVATE, SUBMIT_PUBLIC }

/**
 * 아직 서버(또는 기기 내 백엔드)에 끝까지 반영되지 않은 녹음.
 * 재시도 시 같은 [clientDraftId]를 써서 중복 생성·공개를 막는다.
 */
@Serializable
data class PendingDraft(
    val clientDraftId: String,
    val userId: String,
    val backendMode: BackendMode,
    val audioPath: String,
    /** null: 지나면서 녹음했지만 공개 장소에 연결하지 못함 → 주차 후 장소를 골라야 보낼 수 있다 */
    val placeId: String?,
    val mood: Mood,
    val durationMs: Long,
    val parentStoryId: String? = null,
    val allowReplies: Boolean = false,
    /** 예전 데이터 호환. 이제 보내면 항상 검수에 제출한다(나만 보기 범위 없음) */
    val intent: DraftIntent = DraftIntent.SUBMIT_PUBLIC,
    val voiceMode: ai.heartz.core.model.VoiceMode = ai.heartz.core.model.VoiceMode.ORIGINAL,
    val createdAtMillis: Long,
    val lastError: String? = null,
    /** 주행 상태 변화 등으로 녹음이 중단되어 확인이 필요한 초안 */
    val interrupted: Boolean = false,
    /** 지나면서 녹음(M3). 장소는 녹음 시작 위치 기준 */
    val recordedWhileDriving: Boolean = false,
    /**
     * 녹음 시작 시점의 임시 위치(소수 4자리, 약 11 m). 기기 안에서 장소 추천에만 쓰고 서버로 보내지 않는다.
     * 초안을 보내거나 버리면 함께 지워진다.
     */
    val anchorLat: Double? = null,
    val anchorLng: Double? = null,
)

class DraftStore(private val file: File) {
    private val serializer = ListSerializer(PendingDraft.serializer())

    @Synchronized
    fun list(userId: String): List<PendingDraft> = readAll().filter { it.userId == userId }

    @Synchronized
    fun upsert(draft: PendingDraft) {
        val all = readAll()
        val next = if (all.any { it.clientDraftId == draft.clientDraftId }) {
            all.map { if (it.clientDraftId == draft.clientDraftId) draft else it }
        } else {
            all + draft
        }
        writeAll(next)
    }

    @Synchronized
    fun remove(clientDraftId: String) = writeAll(readAll().filterNot { it.clientDraftId == clientDraftId })

    private fun readAll(): List<PendingDraft> =
        if (file.isFile) HeartzJson.decodeFromString(serializer, file.readText()) else emptyList()

    private fun writeAll(list: List<PendingDraft>) = writeAtomically(file, HeartzJson.encodeToString(serializer, list).toByteArray())
}
