package com.example.pleossetup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 상태 소유자. SDK 세션(Vehicle 인스턴스 1개) 단위로 Job을 묶어, 재연결·종료 시
 * 진행 중인 요청과 구독을 함께 취소한다. 취소된 세션의 늦은 응답은 상태를 덮지 않는다.
 * 모든 메서드는 메인 스레드에서 호출한다고 가정한다(상태 갱신은 StateFlow.update로 원자적).
 */
class ProbeViewModel(
    private val source: VehicleProbeSource,
    private val timeoutMillis: Long = 10_000,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _state = MutableStateFlow(ProbeUiState())
    val state: StateFlow<ProbeUiState> = _state.asStateFlow()

    private var session: CompletableJob? = null
    private var subscriptionJob: Job? = null
    private var subscriptionRequested = false
    private var screenVisible = false
    private var initAttempt = 0
    private var shutDown = false

    /** 최초 1회. 재구성마다 호출되지 않도록 ViewModel 생성 시점에만 부른다. */
    fun initialize() {
        if (shutDown || session?.isActive == true) return
        startSession()
    }

    fun reconnect() {
        if (shutDown) return
        val old = session
        session = null
        subscriptionJob = null
        viewModelScope.launch {
            old?.cancelAndJoin()
            runCatching { source.release() }
            _state.update {
                it.copy(
                    capability = Probe.NotRun,
                    currentValue = Probe.NotRun,
                    live = it.live.copy(subscription = Subscription.Off, callbackCount = 0),
                )
            }
            startSession()
            if (subscriptionRequested && screenVisible) startCollecting()
        }
    }

    fun checkCapability() = launchInSession { scope ->
        _state.update { it.copy(capability = Probe.Pending) }
        val result = call { source.checkDoorPositionCapability() }
        if (scope.isActiveSession()) _state.update { it.copy(capability = result.toProbe()) }
    }

    fun readCurrent() = launchInSession { scope ->
        _state.update { it.copy(currentValue = Probe.Pending) }
        val result = call { source.getDoorPosition() }
        if (scope.isActiveSession()) _state.update { it.copy(currentValue = result.toProbe()) }
    }

    fun startUpdates() {
        if (shutDown) return
        subscriptionRequested = true
        if (screenVisible) {
            startCollecting()
        } else {
            _state.update { it.copy(live = it.live.copy(subscription = Subscription.PausedWhileHidden)) }
        }
    }

    fun stopUpdates() {
        subscriptionRequested = false
        subscriptionJob?.cancel()
        subscriptionJob = null
        _state.update { it.copy(live = it.live.copy(subscription = Subscription.Off)) }
    }

    /** 화면이 보이지 않는 동안에는 SDK listener를 등록하지 않는다. */
    fun onScreenVisible(visible: Boolean) {
        if (screenVisible == visible) return
        screenVisible = visible
        if (visible) {
            if (subscriptionRequested) startCollecting()
        } else {
            subscriptionJob?.cancel()
            subscriptionJob = null
            if (subscriptionRequested) {
                _state.update {
                    it.copy(live = it.live.copy(subscription = Subscription.PausedWhileHidden))
                }
            }
        }
    }

    override fun onCleared() = shutdown()

    internal fun closeForTest() = shutdown()

    private fun shutdown() {
        if (shutDown) return
        shutDown = true
        subscriptionRequested = false
        subscriptionJob?.cancel()
        subscriptionJob = null
        session?.cancel()
        session = null
        runCatching { source.release() }
    }

    private fun startSession() {
        initAttempt++
        _state.update {
            it.copy(sdkVersion = source.sdkVersion(), serviceVersion = source.serviceVersion())
        }
        try {
            source.initialize()
            session = SupervisorJob(viewModelScope.coroutineContext[Job])
            _state.update { it.copy(initialization = InitState.Returned(clock(), initAttempt)) }
        } catch (e: Exception) {
            session = null
            _state.update {
                it.copy(initialization = InitState.Failed(e.message ?: e.toString(), clock()))
            }
        }
    }

    private fun startCollecting() {
        if (subscriptionJob?.isActive == true) return
        val current = session
        if (current == null) {
            _state.update {
                it.copy(
                    live = it.live.copy(
                        subscription = Subscription.Failed("NOT_INITIALIZED", "Vehicle is not initialized", clock()),
                    ),
                )
            }
            return
        }
        subscriptionJob = viewModelScope.launch(current) {
            var registered = false
            _state.update { it.copy(live = it.live.copy(callbackCount = 0, lastError = null)) }
            source.doorPositionUpdates()
                .onCompletion {
                    if (registered) {
                        _state.update {
                            it.copy(live = it.live.copy(unregisterCount = it.live.unregisterCount + 1))
                        }
                    }
                }
                .catch { e ->
                    val failure = e.toProbeFailure()
                    subscriptionRequested = false
                    _state.update {
                        it.copy(
                            live = it.live.copy(
                                subscription = Subscription.Failed(failure.code, failure.message, clock()),
                            ),
                        )
                    }
                }
                .collect { event ->
                    when (event) {
                        DoorEvent.Registered -> {
                            registered = true
                            _state.update {
                                it.copy(
                                    live = it.live.copy(
                                        subscription = Subscription.Registered(it.targetArea, clock()),
                                        registerCount = it.live.registerCount + 1,
                                    ),
                                )
                            }
                        }
                        is DoorEvent.Updated -> _state.update {
                            it.copy(
                                live = it.live.copy(
                                    callbackCount = it.live.callbackCount + 1,
                                    totalCallbackCount = it.live.totalCallbackCount + 1,
                                    lastValue = Probe.Ok(event.value, clock()),
                                ),
                            )
                        }
                        is DoorEvent.Error -> _state.update {
                            it.copy(
                                live = it.live.copy(
                                    lastError = "${event.failure.code}: ${event.failure.message}",
                                ),
                            )
                        }
                    }
                }
        }
    }

    private fun launchInSession(block: suspend (CoroutineScope) -> Unit) {
        val current = session
        if (current == null) {
            _state.update {
                it.copy(initialization = InitState.Failed("Vehicle is not initialized", clock()))
            }
            return
        }
        viewModelScope.launch(current) { block(this) }
    }

    private fun CoroutineScope.isActiveSession(): Boolean =
        coroutineContext[Job]?.isActive == true

    /** null = 제한 시간 내 응답 없음. 동기 예외도 실패 Result로 바꾼다. */
    private suspend fun <T> call(block: suspend () -> Result<T>): Result<T>? = try {
        withTimeoutOrNull(timeoutMillis) { block() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun <T> Result<T>?.toProbe(): Probe<T> {
        val now = clock()
        if (this == null) return Probe.TimedOut(timeoutMillis, now)
        return fold(
            onSuccess = { Probe.Ok(it, now) },
            onFailure = { e ->
                val f = e.toProbeFailure()
                when (f.kind) {
                    FailureKind.UNSUPPORTED -> Probe.Unsupported("${f.code}: ${f.message}", now)
                    FailureKind.PERMISSION -> Probe.PermissionDenied("${f.code}: ${f.message}", now)
                    FailureKind.SERVICE, FailureKind.OTHER -> Probe.Failed(f.code, f.message, now)
                }
            },
        )
    }
}
