package ai.heartz.app.platform.location

import ai.heartz.app.HeartzLog
import ai.heartz.core.model.Place
import ai.heartz.core.schedule.LocationFix
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.listener.ProximityAlertListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.location.fused.model.ProximityAlertEvent
import ai.pleos.playground.location.fused.model.ProximityAlertRequest
import ai.pleos.playground.location.fused.model.ProximityTransition
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 앱이 실제로 받은 위치 상태. 초기화 반환·등록 반환은 위치 수신 성공이 아니다.
 * null 콜백은 Receiving으로 바꾸지 않고 횟수만 센다.
 */
sealed interface LocationStatus {
    data object NotStarted : LocationStatus
    data object PermissionMissing : LocationStatus
    data object Unsupported : LocationStatus
    data class Waiting(val sinceMillis: Long, val nullCallbacks: Int = 0) : LocationStatus
    data class Receiving(
        val fix: LocationFix,
        val receivedAtMillis: Long,
        val callbacks: Int,
        val nullCallbacks: Int,
        val confidence: Float,
    ) : LocationStatus
    data class Failed(val message: String, val atMillis: Long) : LocationStatus
}

/** 근접 알림(addProximityAlert) 시험 결과. SDK 1.1.3 API가 AVD Service 1.0.0에서 동작하는지 확인용 */
sealed interface ProximityProbe {
    data object Idle : ProximityProbe
    data class Requested(val placeId: String) : ProximityProbe
    data class Registered(val placeId: String, val alertId: String) : ProximityProbe
    data class Transition(val placeId: String, val transition: String, val count: Int) : ProximityProbe
    data class Error(val placeId: String, val message: String) : ProximityProbe
}

const val PERMISSION_FUSED_LOCATION = "pleos.car.permission.FUSED_LOCATION"
const val SOURCE_SDK_CALLBACK = "FusedLocation SDK callback"

/**
 * Pleos FusedLocation SDK 연결. 화면이 보이는 동안만 start/stop 한다.
 * SDK 콜백은 SDK 내부 스레드에서 오므로 StateFlow.update로만 갱신한다.
 */
class FusedLocationSource(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val appContext = context.applicationContext
    private val _status = MutableStateFlow<LocationStatus>(LocationStatus.NotStarted)
    val status: StateFlow<LocationStatus> = _status.asStateFlow()

    private val _proximity = MutableStateFlow<ProximityProbe>(ProximityProbe.Idle)
    val proximity: StateFlow<ProximityProbe> = _proximity.asStateFlow()

    private var fused: FusedLocation? = null
    private var listener: FusedLocationListener? = null
    private var proximityAlertId: String? = null

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, PERMISSION_FUSED_LOCATION) == PackageManager.PERMISSION_GRANTED

    @Synchronized
    fun start() {
        if (listener != null) return
        if (!hasPermission()) {
            _status.value = LocationStatus.PermissionMissing
            HeartzLog.i("location", "permission=$PERMISSION_FUSED_LOCATION granted=false")
            return
        }
        val sdk = FusedLocation(appContext)
        if (!sdk.isSupported()) {
            _status.value = LocationStatus.Unsupported
            HeartzLog.i("location", "api=isSupported result=false")
            return
        }
        sdk.initialize()
        HeartzLog.i("location", "api=initialize result=returned (not a connection result)")
        val l = object : FusedLocationListener {
            override fun onChanged(location: LocationData?) = onLocation(location)
            override fun onError(exception: Exception) {
                HeartzLog.i("location", "api=registerFusedLocationCallback callback=onError ${exception.message}")
                _status.value = LocationStatus.Failed(exception.message ?: exception.toString(), clock())
            }
        }
        try {
            sdk.registerFusedLocationCallback(l)
            HeartzLog.i("location", "api=registerFusedLocationCallback result=returned")
            _status.value = LocationStatus.Waiting(clock())
            fused = sdk
            listener = l
            sdk.getLastKnownLocation(object : FusedLocationListener {
                override fun onChanged(location: LocationData?) {
                    HeartzLog.i(
                        "location",
                        "api=getLastKnownLocation value=" +
                            (location?.let { "${HeartzLog.coord(it.latitude, it.longitude)} ts=${it.timestampMillis}" } ?: "null"),
                    )
                }
                override fun onError(exception: Exception) {
                    HeartzLog.i("location", "api=getLastKnownLocation onError ${exception.message}")
                }
            })
        } catch (e: SecurityException) {
            _status.value = LocationStatus.PermissionMissing
            HeartzLog.i("location", "api=registerFusedLocationCallback SecurityException ${e.message}")
            runCatching { sdk.release() }
        }
    }

    private fun onLocation(location: LocationData?) {
        val now = clock()
        if (location == null) {
            _status.update {
                when (it) {
                    is LocationStatus.Waiting -> it.copy(nullCallbacks = it.nullCallbacks + 1)
                    is LocationStatus.Receiving -> it.copy(nullCallbacks = it.nullCallbacks + 1)
                    else -> LocationStatus.Waiting(now, 1)
                }
            }
            HeartzLog.i("location", "callback value=null")
            return
        }
        val fix = LocationFix(
            lat = location.latitude,
            lng = location.longitude,
            headingDeg = location.heading.toDouble().takeIf { it.isFinite() },
            timestampMillis = location.timestampMillis,
            source = SOURCE_SDK_CALLBACK,
        )
        _status.update {
            val prev = it as? LocationStatus.Receiving
            val nulls = (it as? LocationStatus.Waiting)?.nullCallbacks ?: prev?.nullCallbacks ?: 0
            LocationStatus.Receiving(fix, now, (prev?.callbacks ?: 0) + 1, nulls, location.confidence)
        }
        HeartzLog.i(
            "location",
            "callback ${HeartzLog.coord(fix.lat, fix.lng)} heading=${location.heading} conf=${location.confidence} " +
                "ts=${location.timestampMillis} ageMs=${now - location.timestampMillis}",
        )
    }

    @Synchronized
    fun stop() {
        val sdk = fused
        listener?.let { l -> runCatching { sdk?.unregisterFusedLocationCallback(l) } }
        proximityAlertId?.let { id -> runCatching { sdk?.removeProximityAlert(id) } }
        runCatching { sdk?.release() }
        if (sdk != null) HeartzLog.i("location", "api=unregisterFusedLocationCallback+release result=returned")
        fused = null
        listener = null
        proximityAlertId = null
        _status.value = LocationStatus.NotStarted
        _proximity.value = ProximityProbe.Idle
    }

    /** 진단용: 장소 중심 반경으로 근접 알림을 등록해 본다. 편성 판정에는 쓰지 않는다. */
    @Synchronized
    fun probeProximity(place: Place, radiusMeters: Float = 120f) {
        val sdk = fused ?: run {
            _proximity.value = ProximityProbe.Error(place.id, "location not started")
            return
        }
        proximityAlertId?.let { runCatching { sdk.removeProximityAlert(it) } }
        proximityAlertId = null
        _proximity.value = ProximityProbe.Requested(place.id)
        var transitions = 0
        try {
            sdk.addProximityAlert(
                ProximityAlertRequest(
                    place.publicLocation.lat,
                    place.publicLocation.lng,
                    radiusMeters,
                    setOf(ProximityTransition.ENTER, ProximityTransition.EXIT),
                ),
                object : ProximityAlertListener {
                    override fun onRegistered(alertId: String) {
                        proximityAlertId = alertId
                        _proximity.value = ProximityProbe.Registered(place.id, alertId)
                        HeartzLog.i("proximity", "api=addProximityAlert onRegistered place=${place.id}")
                    }

                    override fun onTransition(event: ProximityAlertEvent) {
                        transitions++
                        _proximity.value = ProximityProbe.Transition(place.id, event.transition.name, transitions)
                        HeartzLog.i("proximity", "callback transition=${event.transition} place=${place.id}")
                    }

                    override fun onError(exception: Exception) {
                        _proximity.value = ProximityProbe.Error(place.id, exception.message ?: exception.toString())
                        HeartzLog.i("proximity", "api=addProximityAlert onError ${exception.message}")
                    }
                },
            )
            HeartzLog.i("proximity", "api=addProximityAlert result=returned place=${place.id} r=$radiusMeters")
        } catch (e: Exception) {
            _proximity.value = ProximityProbe.Error(place.id, e.message ?: e.toString())
            HeartzLog.i("proximity", "api=addProximityAlert threw ${e::class.java.simpleName} ${e.message}")
        }
    }
}
