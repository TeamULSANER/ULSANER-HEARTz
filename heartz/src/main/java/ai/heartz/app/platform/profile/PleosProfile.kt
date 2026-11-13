package ai.heartz.app.platform.profile

/**
 * Pleos Connect 프로필 모양(상태 표시줄의 "이름 첫 글자 + 원")을 앱에서도 쓴다.
 *
 * Pleos 프로필 이름 자체를 읽어 닉네임으로 쓰는 것은 추후 개발로 남겼다(.docs/HEARTZ-PLAN.md "향후 개발").
 * 2026-11-12 확인: 프로필 이름은 차량 Android 사용자 이름인데, 일반 앱은 QUERY_USERS 등 시스템 권한이 없어
 * UserManager.getUserName()이 SecurityException이고, Pleos SDK·공식 문서에도 앱용 프로필 API가 없다.
 */
object PleosProfile {
    /** 프로필 원에 쓰는 첫 글자. 테스트 계정 꼬리("하늘 (테스트 청취자)")는 빼고 본다 */
    fun initial(name: String): String {
        val base = name.substringBefore(" (").trim()
        val first = base.firstOrNull() ?: return "?"
        return first.uppercaseChar().toString()
    }
}
