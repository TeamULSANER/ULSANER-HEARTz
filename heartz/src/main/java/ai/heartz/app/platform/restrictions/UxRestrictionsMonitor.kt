package ai.heartz.app.platform.restrictions

import ai.heartz.app.HeartzLog
import ai.heartz.core.drive.UxState
import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AAOS CarUxRestrictionsManager 구독. 앱은 플랫폼이 내려준 값만 반영하고 기어 값으로 대체하지 않는다.
 * 연결 전·실패·해제 상태는 UxState.Unknown이며 주차로 간주하지 않는다.
 */
class UxRestrictionsMonitor(context: Context, private val clock: () -> Long = System::currentTimeMillis) {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow<UxState>(UxState.Unknown("not started"))
    val state: StateFlow<UxState> = _state.asStateFlow()

    private val _changes = MutableStateFlow(0)
    /** 콜백 수신 횟수 (진단용) */
    val changes: StateFlow<Int> = _changes.asStateFlow()

    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null

    private val listener = CarUxRestrictionsManager.OnUxRestrictionsChangedListener { r ->
        _changes.value = _changes.value + 1
        publish(r, "callback")
    }

    @Synchronized
    fun start() {
        if (car != null) return
        try {
            car = Car.createCar(appContext, null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { c, ready ->
                if (ready) onCarReady(c) else {
                    manager = null
                    _state.value = UxState.Unknown("car service not ready")
                    HeartzLog.i("ux", "car lifecycle ready=false")
                }
            }
        } catch (t: Throwable) {
            _state.value = UxState.Unknown("createCar failed: ${t::class.java.simpleName}")
            HeartzLog.i("ux", "createCar threw ${t::class.java.simpleName} ${t.message}")
        }
    }

    private fun onCarReady(c: Car) {
        try {
            val m = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
            if (m == null) {
                _state.value = UxState.Unknown("CarUxRestrictionsManager unavailable")
                HeartzLog.i("ux", "getCarManager(CAR_UX_RESTRICTION_SERVICE)=null")
                return
            }
            manager = m
            m.registerListener(listener)
            HeartzLog.i("ux", "api=registerListener result=returned")
            m.currentCarUxRestrictions?.let { publish(it, "getCurrentCarUxRestrictions") }
                ?: run { _state.value = UxState.Unknown("current restrictions null") }
        } catch (t: Throwable) {
            _state.value = UxState.Unknown("register failed: ${t::class.java.simpleName}")
            HeartzLog.i("ux", "register threw ${t::class.java.simpleName} ${t.message}")
        }
    }

    private fun publish(r: CarUxRestrictions, via: String) {
        val next = UxState.Known(r.isRequiresDistractionOptimization, r.activeRestrictions)
        _state.value = next
        HeartzLog.i(
            "ux",
            "$via requiresDO=${next.requiresDistractionOptimization} active=0x${Integer.toHexString(next.activeRestrictions)} " +
                "at=${clock()}",
        )
    }

    @Synchronized
    fun stop() {
        runCatching { manager?.unregisterListener() }
        runCatching { car?.disconnect() }
        manager = null
        car = null
        _state.value = UxState.Unknown("stopped")
    }

    companion object {
        fun describe(active: Int): List<String> = buildList {
            if (active and CarUxRestrictions.UX_RESTRICTIONS_NO_DIALPAD != 0) add("NO_DIALPAD")
            if (active and CarUxRestrictions.UX_RESTRICTIONS_NO_FILTERING != 0) add("NO_FILTERING")
            if (active and CarUxRestrictions.UX_RESTRICTIONS_LIMIT_STRING_LENGTH != 0) add("LIMIT_STRING_LENGTH")
            if (active and CarUxRestrictions.UX_RESTRICTIONS_NO_KEYBOARD != 0) add("NO_KEYBOARD")
            if (active and CarUxRestrictions.UX_RESTRICTIONS_NO_VIDEO != 0) add("NO_VIDEO")
            if (active and CarUxRestrictions.UX_RESTRICTIONS_LIMIT_CONTENT != 0) add("LIMIT_CONTENT")
            if (active and CarUxRestrictions.UX_RESTRICTIONS_NO_SETUP != 0) add("NO_SETUP")
            if (active and CarUxRestrictions.UX_RESTRICTIONS_NO_TEXT_MESSAGE != 0) add("NO_TEXT_MESSAGE")
            if (active and CarUxRestrictions.UX_RESTRICTIONS_NO_VOICE_TRANSCRIPTION != 0) add("NO_VOICE_TRANSCRIPTION")
        }
    }
}
