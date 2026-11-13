package ai.heartz.core.model

object ProfileRules {
    const val MAX_NICKNAME_LENGTH = 12

    fun nicknameError(nickname: String): String? = when {
        nickname.trim().isEmpty() -> "닉네임을 입력해 주세요"
        nickname.trim().length > MAX_NICKNAME_LENGTH -> "닉네임은 12자 이내로 입력해 주세요"
        nickname.any { it.isISOControl() } -> "닉네임에는 줄바꿈을 넣을 수 없어요"
        else -> null
    }
}
