package com.example.pleossetup

/** 단일 SDK 호출의 결과. null 값과 실패·미지원·타임아웃을 서로 섞지 않는다. */
sealed interface Probe<out T> {
    data object NotRun : Probe<Nothing>
    data object Pending : Probe<Nothing>

    /** 실제 SDK onComplete 결과. value가 null이면 "사용할 수 있는 값 없음"이며 0이 아니다. */
    data class Ok<T>(val value: T, val atMillis: Long) : Probe<T>

    data class Unsupported(val detail: String, val atMillis: Long) : Probe<Nothing>
    data class PermissionDenied(val detail: String, val atMillis: Long) : Probe<Nothing>
    data class Failed(val code: String, val detail: String, val atMillis: Long) : Probe<Nothing>

    /** 제한 시간 안에 onComplete/onFailed 어느 쪽도 오지 않았다. */
    data class TimedOut(val timeoutMillis: Long, val atMillis: Long) : Probe<Nothing>
}

/** initialize() 반환은 서비스 연결 완료를 뜻하지 않는다. */
sealed interface InitState {
    data object NotInitialized : InitState
    data class Returned(val atMillis: Long, val attempt: Int) : InitState
    data class Failed(val detail: String, val atMillis: Long) : InitState
}

data class DoorCapability(
    /** CapabilityConfig.accessType 이름 (NONE / READ / WRITE / READ_WRITE). */
    val accessType: String,
    /** 서비스가 알려준 영역별 범위. key 예: "ROW_1_LEFT" */
    val areaBounds: Map<String, IntRange>,
) {
    val canRead: Boolean get() = accessType == "READ" || accessType == "READ_WRITE"
}

sealed interface Subscription {
    data object Off : Subscription

    /** 사용자가 구독을 요청했지만 화면이 보이지 않아 SDK 등록은 해제된 상태. */
    data object PausedWhileHidden : Subscription

    /** register 호출이 예외 없이 반환됨. 실제 callback 수신 여부는 callbackCount로 따로 본다. */
    data class Registered(val area: String, val atMillis: Long) : Subscription

    data class Failed(val code: String, val detail: String, val atMillis: Long) : Subscription
}

data class LiveUpdates(
    val subscription: Subscription = Subscription.Off,
    /** 현재 구독 세션 동안 실제 SDK listener로 받은 onPositionUpdated 횟수. */
    val callbackCount: Int = 0,
    /** 앱 실행 이후 받은 전체 callback 횟수(모든 구독 세션 합계). */
    val totalCallbackCount: Int = 0,
    val lastValue: Probe<Int?> = Probe.NotRun,
    val lastError: String? = null,
    val registerCount: Int = 0,
    val unregisterCount: Int = 0,
)

data class ProbeUiState(
    val targetArea: String = VehicleProbeSource.TARGET_AREA,
    val sdkVersion: String? = null,
    val serviceVersion: String? = null,
    val initialization: InitState = InitState.NotInitialized,
    val capability: Probe<DoorCapability> = Probe.NotRun,
    val currentValue: Probe<Int?> = Probe.NotRun,
    val live: LiveUpdates = LiveUpdates(),
)
