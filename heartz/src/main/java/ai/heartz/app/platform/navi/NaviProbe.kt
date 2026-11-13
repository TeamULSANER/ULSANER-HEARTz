package ai.heartz.app.platform.navi

import ai.heartz.app.HeartzLog
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * NaviHelper 연결 상태와 내비 안내 상태. 위치 편성에는 쓰지 않고(FusedLocation 사용),
 * M0에서 내비 안내 활성 여부와 Navi가 보고하는 현재 위치를 비교하는 데 쓴다.
 */
data class NaviState(
    val connection: Connection = Connection.NotStarted,
    val navigationStatus: String? = null,
    val navigationStatusAtMillis: Long? = null,
    val lastLocation: NaviLocation? = null,
    val lastRouteEvent: String? = null,
    val lastError: String? = null,
) {
    enum class Connection { NotStarted, Connecting, Connected, Failed }
}

data class NaviLocation(val lat: Double, val lng: Double, val heading: Double, val atMillis: Long, val hasAddress: Boolean)

class NaviProbe(context: Context, private val clock: () -> Long = System::currentTimeMillis) {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(NaviState())
    val state: StateFlow<NaviState> = _state.asStateFlow()

    private var helper: NaviHelper? = null

    private val listener = object : NaviHelperEventListener {
        override fun onNavigationStatus(status: NavigationStatus) {
            _state.update { it.copy(navigationStatus = status.name, navigationStatusAtMillis = clock()) }
            HeartzLog.i("navi", "callback onNavigationStatus=$status")
        }

        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
            val loc = NaviLocation(
                currentLocationInfo.latitude,
                currentLocationInfo.longitude,
                currentLocationInfo.heading,
                clock(),
                !currentLocationInfo.address.isNullOrBlank(),
            )
            _state.update { it.copy(lastLocation = loc) }
            HeartzLog.i("navi", "callback onCurrentLocationInfo ${HeartzLog.coord(loc.lat, loc.lng)} heading=${loc.heading}")
        }

        override fun onRouteStarted(routeStartInfo: RouteStartInfo) = route("onRouteStarted")
        override fun onRoutingCompleted() = route("onRoutingCompleted")
        override fun onRouteEnded() = route("onRouteEnded")
        override fun onRouteCancelled() = route("onRouteCancelled")

        override fun onError(errorCode: NaviErrorCode) {
            _state.update { it.copy(lastError = errorCode.name) }
            HeartzLog.i("navi", "callback onError=$errorCode")
        }
    }

    private fun route(name: String) {
        _state.update { it.copy(lastRouteEvent = name) }
        HeartzLog.i("navi", "callback $name")
    }

    @Synchronized
    fun start() {
        if (helper != null) return
        val h = NaviHelper(appContext)
        helper = h
        _state.update { it.copy(connection = NaviState.Connection.Connecting, lastError = null) }
        try {
            h.initialize(
                onConnected = {
                    _state.update { it.copy(connection = NaviState.Connection.Connected) }
                    HeartzLog.i("navi", "api=initialize onConnected")
                },
                onFailed = { t ->
                    _state.update { it.copy(connection = NaviState.Connection.Failed, lastError = t.message ?: t.toString()) }
                    HeartzLog.i("navi", "api=initialize onFailed ${t.message}")
                },
            )
            h.addListener(listener)
        } catch (e: Exception) {
            _state.update { it.copy(connection = NaviState.Connection.Failed, lastError = e.message ?: e.toString()) }
            HeartzLog.i("navi", "api=initialize threw ${e::class.java.simpleName} ${e.message}")
        }
    }

    /** 진단 버튼으로만 호출한다. 결과는 onCurrentLocationInfo 콜백으로 온다. */
    fun requestCurrentLocation() {
        val h = helper ?: return
        try {
            h.getCurrentLocationInfo()
            HeartzLog.i("navi", "api=getCurrentLocationInfo result=returned")
        } catch (e: Exception) {
            _state.update { it.copy(lastError = e.message ?: e.toString()) }
            HeartzLog.i("navi", "api=getCurrentLocationInfo threw ${e::class.java.simpleName} ${e.message}")
        }
    }

    /**
     * debug 진단 전용: 내비 안내(ROUTE_DRIVE) 상태를 재현하기 위한 시험 경로 요청.
     * 릴리스 빌드에는 NAVI_ROUTE_SEARCH 권한이 없어 SecurityException으로 끝난다.
     */
    fun requestTestRoute(lat: Double, lng: Double, name: String) {
        val h = helper ?: return
        try {
            h.requestRoute(RouteInfo(lat, lng, null, null, name, "", "", "", RouteOption.RECOMMENDED, "", false))
            HeartzLog.i("navi", "api=requestRoute(test) result=returned dest=$name")
        } catch (e: Exception) {
            _state.update { it.copy(lastError = e.message ?: e.toString()) }
            HeartzLog.i("navi", "api=requestRoute threw ${e::class.java.simpleName} ${e.message}")
        }
    }

    fun cancelTestRoute() {
        val h = helper ?: return
        try {
            h.cancelRoute()
            HeartzLog.i("navi", "api=cancelRoute(test) result=returned")
        } catch (e: Exception) {
            HeartzLog.i("navi", "api=cancelRoute threw ${e::class.java.simpleName} ${e.message}")
        }
    }

    @Synchronized
    fun stop() {
        val h = helper ?: return
        runCatching { h.removeListener(listener) }
        runCatching { h.release() }
        helper = null
        _state.update { NaviState() }
        HeartzLog.i("navi", "api=removeListener+release result=returned")
    }
}
