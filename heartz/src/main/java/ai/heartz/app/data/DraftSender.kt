package ai.heartz.app.data

import ai.heartz.app.HeartzLog
import ai.heartz.core.model.StoryStatus
import ai.heartz.core.service.CreateStoryRequest
import java.io.File

sealed interface SaveOutcome {
    data class Saved(val status: StoryStatus, val placeName: String, val storyId: String) : SaveOutcome
    data class Deferred(val reason: String) : SaveOutcome
}

/** 저장된 녹음으로 검수 보조(받아쓰기·의심 구간)를 만들어 붙이는 작업. 실패해도 저장 결과를 바꾸지 않는다. */
fun interface AssistScheduler {
    fun schedule(storyId: String, audio: File)
}

/**
 * 초안을 기기에 먼저 남기고 백엔드에 생성 → 업로드 → 검수 제출한다(공개 범위는 하나: 검수 후 공개).
 * 성공하면 기기 초안을 지우고, 실패하면 사유와 함께 남겨 같은 clientDraftId로 재시도한다(중복 공개 없음).
 * 주차 후 녹음과 지나면서 녹음이 같은 경로를 쓴다.
 */
class DraftSender(private val repo: HeartzRepository, private val assist: AssistScheduler?) {
    suspend fun send(draft: PendingDraft): SaveOutcome {
        repo.drafts.upsert(draft)
        val placeId = draft.placeId ?: run {
            repo.reloadPendingDrafts()
            return SaveOutcome.Deferred("이야기를 남길 장소를 골라 주세요")
        }
        val placeName = repo.places.value.firstOrNull { it.id == placeId }?.displayName ?: placeId
        return try {
            val b = repo.backend
            val story = b.createDraft(
                CreateStoryRequest(
                    draft.clientDraftId, placeId, draft.mood, draft.durationMs, draft.parentStoryId, draft.allowReplies,
                    recordedWhileDriving = draft.recordedWhileDriving,
                    voiceMode = draft.voiceMode,
                ),
            )
            val audio = File(draft.audioPath)
            if (story.status == StoryStatus.DRAFT || story.status == StoryStatus.PRIVATE) {
                if (story.audio == null) b.uploadAudio(story.id, audio, "audio/mp4")
            }
            val status = b.submit(story.id).status
            // 검수 보조는 음원 복사본으로 따로 돌린다. 원본 녹음은 기기에서 지운다.
            if (story.assist == null && assist != null) assist.schedule(story.id, audio) else audio.delete()
            repo.drafts.remove(draft.clientDraftId)
            HeartzLog.i("studio", "saved story=${story.id} status=$status voice=${draft.voiceMode} roadside=${draft.recordedWhileDriving}")
            repo.refresh()
            SaveOutcome.Saved(status, placeName, story.id)
        } catch (e: Exception) {
            repo.drafts.upsert(draft.copy(lastError = e.userMessage()))
            repo.reloadPendingDrafts()
            HeartzLog.i("studio", "save deferred ${e.userMessage()}")
            SaveOutcome.Deferred(e.userMessage())
        }
    }

    fun discard(draft: PendingDraft) {
        repo.drafts.remove(draft.clientDraftId)
        File(draft.audioPath).delete()
        repo.reloadPendingDrafts()
    }
}
