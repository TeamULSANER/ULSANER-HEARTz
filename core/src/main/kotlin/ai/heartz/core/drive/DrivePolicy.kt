package ai.heartz.core.drive

/**
 * 플랫폼 CarUxRestrictions를 앱이 받은 그대로 옮긴 값.
 * Unknown은 서비스 미연결·미지원·오류이며, 주차로 간주하지 않는다.
 */
sealed interface UxState {
    data class Unknown(val reason: String) : UxState
    data class Known(val requiresDistractionOptimization: Boolean, val activeRestrictions: Int) : UxState
}

/** 화면·기능 노출 규칙. 기어 값 하나가 아니라 플랫폼 UX 제한 상태를 기준으로 한다. */
object DrivePolicy {
    /** 주행용 최소 화면(정적 카드 + 듣기/정지)만 보여야 하는가 */
    fun showDrivingView(ux: UxState): Boolean = !allowParkedFeatures(ux)

    /** 주차 후 녹음·작성·목록·설정을 허용하는가 */
    fun allowParkedFeatures(ux: UxState): Boolean =
        ux is UxState.Known && !ux.requiresDistractionOptimization
}

/** 기획서 6절의 주행 중 녹음·전송 검증 조건 */
enum class DrivingRecordingCondition(val label: String, val short: String) {
    EXPLICIT_INPUT("플랫폼이 허용한 명시적 입력으로 시작·종료", "시작·종료"),
    MICROPHONE("AVD에서 Heartz 자체 마이크 접근과 음성 파일 생성", "마이크"),
    LOCATION_AT_START("녹음 시작 시점의 유효 위치를 임시 장소로 고정", "위치"),
    SUBMIT_CONFIRMATION("허용된 입력으로 공개 제출 의사 확인 후 검수 대기까지", "전송"),
    INTEGRATION_SCOPE("Gleo·음성 명령 연동 범위 확인", "Gleo"),
}

enum class VerificationStatus(val label: String) {
    NOT_VERIFIED("미검증"),
    PARTIAL("일부 확인"),
    BLOCKED("환경 차단"),
    VERIFIED("확인"),
    FAILED("실패"),
}

object DrivingRecordingGate {
    /**
     * 현재 검증 상태(2026-11-09, Pleos Connect v2.1.0 AVD). 근거: .docs/SETUP-RESULTS.md "Heartz M3".
     * - 시작·종료: 주행 화면 버튼 한 번(DO 화면)은 AVD 확인, Gleo는 모의 Intent만 → 일부
     * - 마이크: 2026-11-10 AVD에서 가상 입력 장치(합성 음성)로 녹음 진폭·파일·STT 인식 확인 → 확인
 *   (사람 목소리·차량 마이크는 미확인. 이전 "무음" 판정은 이날 증거로 대체)
     * - 위치: 시작 위치 고정(녹음 중 이동해도 유지) AVD 확인
     * - 전송: 음성 답("전달해 줘" → "모두에게 공개해 줘") → 검수 대기, 통화 중단 → 기기 초안을 AVD에서 확인(합성 음성).
 *   주행 중 음성 답이 Pleos 심사에서 허용된 입력인지는 문서 미확인 → 일부
     * - Gleo: schema·Intent 계약은 모의 호출로 확인, 실제 음성 호출은 마이크 차단 → 일부
     */
    val current: Map<DrivingRecordingCondition, VerificationStatus> = mapOf(
        DrivingRecordingCondition.EXPLICIT_INPUT to VerificationStatus.PARTIAL,
        DrivingRecordingCondition.MICROPHONE to VerificationStatus.VERIFIED,
        DrivingRecordingCondition.LOCATION_AT_START to VerificationStatus.VERIFIED,
        DrivingRecordingCondition.SUBMIT_CONFIRMATION to VerificationStatus.PARTIAL,
        DrivingRecordingCondition.INTEGRATION_SCOPE to VerificationStatus.PARTIAL,
    )

    fun isEnabled(status: Map<DrivingRecordingCondition, VerificationStatus> = current): Boolean =
        DrivingRecordingCondition.entries.all { status[it] == VerificationStatus.VERIFIED }
}
