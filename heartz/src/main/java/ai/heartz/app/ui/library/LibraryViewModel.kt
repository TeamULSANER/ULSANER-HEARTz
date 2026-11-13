package ai.heartz.app.ui.library

import ai.heartz.app.HeartzLog
import ai.heartz.app.data.BackendMode
import ai.heartz.app.data.HeartzRepository
import ai.heartz.app.data.userMessage
import ai.heartz.core.model.Story
import ai.heartz.core.model.User
import ai.heartz.core.service.ModerationAction
import ai.heartz.core.service.ModerationOverview
import ai.heartz.core.service.ModerationRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailState(
    val storyId: String,
    val story: Story? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

data class LibraryState(
    val testUsers: List<User> = emptyList(),
    val moderation: ModerationOverview? = null,
    val moderationLoading: Boolean = false,
    val moderationError: String? = null,
    val detail: DetailState? = null,
    val busy: Boolean = false,
    val toast: String? = null,
)

/** 주차 중 화면(목록·상세·검수·설정)의 동작. 모든 변경 결과는 서버 응답으로 다시 읽는다. */
class LibraryViewModel(private val repo: HeartzRepository) : ViewModel() {
    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    private fun act(label: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                block()
            } catch (e: Exception) {
                HeartzLog.i("library", "$label failed ${e.userMessage()}")
                _state.update { it.copy(toast = "$label 실패: ${e.userMessage()}") }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    fun toast(msg: String) = _state.update { it.copy(toast = msg) }
    fun clearToast() = _state.update { it.copy(toast = null) }

    fun refresh() = act("동기화") { repo.refresh() }

    fun loadTestUsers() = act("테스트 계정 목록") {
        val users = repo.testUsers()
        _state.update { it.copy(testUsers = users) }
    }

    fun signIn(userId: String) = act("계정 전환") {
        repo.signIn(userId)
        _state.update { it.copy(moderation = null, detail = null, toast = "계정을 바꿨어요") }
    }

    fun switchBackend(mode: BackendMode) = act("백엔드 전환") {
        repo.switchBackend(mode)
        val users = runCatching { repo.testUsers() }.getOrDefault(emptyList())
        _state.update { it.copy(testUsers = users, moderation = null, detail = null) }
    }

    fun openDetail(storyId: String) {
        _state.update { it.copy(detail = DetailState(storyId)) }
        loadDetail(storyId)
    }

    fun closeDetail() = _state.update { it.copy(detail = null) }

    private fun loadDetail(storyId: String) {
        viewModelScope.launch {
            try {
                val story = repo.backend.story(storyId)
                _state.update { s ->
                    if (s.detail?.storyId != storyId) s else s.copy(detail = DetailState(storyId, story, loading = false))
                }
            } catch (e: Exception) {
                _state.update { s ->
                    if (s.detail?.storyId != storyId) s else s.copy(detail = DetailState(storyId, loading = false, error = e.userMessage()))
                }
            }
        }
    }

    /** 하트를 바꾼 뒤 상세의 하트 수를 다시 읽는다 */
    fun refreshDetailSoon(storyId: String) {
        viewModelScope.launch {
            kotlinx.coroutines.delay(400)
            if (_state.value.detail?.storyId == storyId) loadDetail(storyId)
        }
    }

    fun report(storyId: String, reason: String) = act("신고") {
        repo.backend.report(storyId, reason)
        _state.update { it.copy(toast = "신고했어요. 운영자가 확인할게요.") }
    }

    fun hideAuthor(authorId: String) = act("작성자 숨기기") {
        repo.hideAuthor(authorId)
        _state.update { it.copy(toast = "이 사람의 사연을 숨겼어요", detail = null) }
    }

    fun unhideAuthor(authorId: String) = act("숨기기 해제") {
        repo.unhideAuthor(authorId)
    }

    fun deleteMine(storyId: String) = act("삭제") {
        repo.backend.delete(storyId)
        repo.refresh()
        _state.update { it.copy(toast = "삭제했어요", detail = null) }
    }

    fun submitPrivate(storyId: String) = act("공개 제출") {
        val r = repo.backend.submit(storyId)
        repo.refresh()
        _state.update { it.copy(toast = if (r.alreadySubmitted) "이미 제출했어요" else "검수에 보냈어요") }
        // 열린 상세 화면이 바뀐 상태(검수 대기)를 보여 주도록 다시 읽는다
        if (_state.value.detail?.storyId == storyId) loadDetail(storyId)
    }

    fun loadModeration() {
        viewModelScope.launch {
            _state.update { it.copy(moderationLoading = true, moderationError = null) }
            try {
                val o = repo.backend.moderation()
                _state.update { it.copy(moderation = o, moderationLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(moderationLoading = false, moderationError = e.userMessage()) }
            }
        }
    }

    fun moderate(storyId: String, action: ModerationAction, note: String?) = act("검수") {
        val r = repo.backend.moderate(storyId, ModerationRequest(action, note))
        HeartzLog.i("moderation", "story=$storyId action=$action -> ${r.status} playable=${r.publiclyPlayable}")
        _state.update {
            it.copy(
                toast = when (action) {
                    ModerationAction.APPROVE -> "승인했어요"
                    ModerationAction.REJECT -> "반려했어요"
                    ModerationAction.HIDE -> "숨겼어요"
                },
            )
        }
        repo.refresh()
        loadModeration()
    }
}
