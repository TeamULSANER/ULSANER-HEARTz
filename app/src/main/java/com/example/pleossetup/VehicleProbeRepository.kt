package com.example.pleossetup

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** SDK 호출의 실패 분류. ViewModel은 SDK 타입을 직접 보지 않는다. */
enum class FailureKind { UNSUPPORTED, PERMISSION, SERVICE, OTHER }

class ProbeFailure(val kind: FailureKind, val code: String, override val message: String) :
    Exception(message)

sealed interface DoorEvent {
    /** registerDoorPosition 호출이 예외 없이 반환됨 (Service 측 완료 응답이 아님). */
    data object Registered : DoorEvent
    data class Updated(val value: Int?) : DoorEvent
    data class Error(val failure: ProbeFailure) : DoorEvent
}

/**
 * ViewModel이 사용하는 Vehicle 접근 경계.
 * 실제 구현은 [PleosVehicleProbeRepository] 하나이며, 테스트의 fake는 test 소스셋에만 둔다.
 */
interface VehicleProbeSource {
    fun sdkVersion(): String?
    fun serviceVersion(): String?

    /** Vehicle 인스턴스를 새로 만들고 initialize()를 호출한다. 반환은 연결 완료가 아니다. */
    fun initialize()

    /** 등록된 listener를 정리하고 Vehicle.release()를 호출한다. */
    fun release()

    suspend fun checkDoorPositionCapability(): Result<DoorCapability>
    suspend fun getDoorPosition(): Result<Int?>

    /** collect 동안만 SDK listener를 등록하고, 취소되면 같은 listener·area로 해제한다. */
    fun doorPositionUpdates(): Flow<DoorEvent>

    companion object {
        const val TARGET_AREA = "ROW_1_LEFT"
        const val LOG_TAG = "PleosSetupProbe"
    }
}

class PleosVehicleProbeRepository(context: Context) : VehicleProbeSource {
    private val appContext = context.applicationContext
    private val area = DoorArea(DoorValue.ROW_1_LEFT)

    @Volatile
    private var vehicle: Vehicle? = null

    override fun sdkVersion(): String? = runCatching {
        val info = appContext.packageManager.getApplicationInfo(
            appContext.packageName,
            PackageManager.GET_META_DATA,
        )
        info.metaData?.getString("ai.pleos.playground.sdk.vehicle.current_version_name")
    }.getOrNull()

    override fun serviceVersion(): String? = runCatching {
        val pm = appContext.packageManager
        val info = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(SERVICE_PACKAGE, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(SERVICE_PACKAGE, 0)
        }
        info.versionName
    }.getOrNull()

    override fun initialize() {
        check(vehicle == null) { "already initialized" }
        val created = Vehicle(appContext)
        created.initialize()
        vehicle = created
        Log.i(LOG_TAG, "api=initialize result=returned")
    }

    override fun release() {
        val current = vehicle ?: return
        vehicle = null
        current.release()
        Log.i(LOG_TAG, "api=release result=returned")
    }

    override suspend fun checkDoorPositionCapability(): Result<DoorCapability> =
        suspendCancellableCoroutine { cont ->
            val door = requireVehicle().getDoor()
            try {
                door.checkDoorPositionCapabilities(
                    onComplete = { config, areaMap ->
                        val bounds = areaMap.entries.associate { (key, value) ->
                            key.values.joinToString("|") { it.name } to
                                value.minValue..value.maxValue
                        }
                        val result = DoorCapability(config.accessType.name, bounds)
                        Log.i(
                            LOG_TAG,
                            "api=checkDoorPositionCapabilities result=complete " +
                                "access=${result.accessType} areas=${result.areaBounds}",
                        )
                        if (cont.isActive) cont.resume(Result.success(result))
                    },
                    onFailed = { e ->
                        val failure = e.toProbeFailure()
                        Log.i(LOG_TAG, "api=checkDoorPositionCapabilities result=failed ${failure.code}")
                        if (cont.isActive) cont.resume(Result.failure(failure))
                    },
                )
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(Result.failure(e.toProbeFailure()))
            }
        }

    override suspend fun getDoorPosition(): Result<Int?> = suspendCancellableCoroutine { cont ->
        val door = requireVehicle().getDoor()
        try {
            door.getCurrentDoorPosition(
                area = area,
                onComplete = { value ->
                    Log.i(LOG_TAG, "api=getCurrentDoorPosition area=$TARGET_AREA value=$value")
                    if (cont.isActive) cont.resume(Result.success(value))
                },
                onFailed = { e ->
                    val failure = e.toProbeFailure()
                    Log.i(LOG_TAG, "api=getCurrentDoorPosition area=$TARGET_AREA result=failed ${failure.code}")
                    if (cont.isActive) cont.resume(Result.failure(failure))
                },
            )
        } catch (e: Exception) {
            if (cont.isActive) cont.resume(Result.failure(e.toProbeFailure()))
        }
    }

    override fun doorPositionUpdates(): Flow<DoorEvent> = callbackFlow {
        val door = requireVehicle().getDoor()
        val listener = object : DoorPositionListener {
            override fun onPositionUpdated(pos: Int?) {
                Log.i(LOG_TAG, "api=registerDoorPosition callback area=$TARGET_AREA value=$pos")
                trySend(DoorEvent.Updated(pos))
            }

            override fun onFailed(e: Exception) {
                val failure = e.toProbeFailure()
                Log.i(LOG_TAG, "api=registerDoorPosition callback=failed ${failure.code}")
                trySend(DoorEvent.Error(failure))
            }
        }
        // 권한 누락 시 SDK가 동기 예외를 던질 수 있다 -> Flow 오류로 전달.
        door.registerDoorPosition(area, listener)
        Log.i(LOG_TAG, "api=registerDoorPosition area=$TARGET_AREA result=returned")
        trySend(DoorEvent.Registered)
        awaitClose {
            runCatching { door.unregisterDoorPosition(area, listener) }
                .onSuccess { Log.i(LOG_TAG, "api=unregisterDoorPosition area=$TARGET_AREA result=returned") }
                .onFailure { Log.i(LOG_TAG, "api=unregisterDoorPosition result=failed ${it.message}") }
        }
    }

    private fun requireVehicle(): Vehicle =
        vehicle ?: throw ProbeFailure(FailureKind.SERVICE, "NOT_INITIALIZED", "Vehicle is not initialized")

    companion object {
        const val SERVICE_PACKAGE = "ai.pleos.playground.service.vehicle"
        private const val LOG_TAG = VehicleProbeSource.LOG_TAG
        private const val TARGET_AREA = VehicleProbeSource.TARGET_AREA
    }
}

/**
 * 공개 문서는 `e is VehicleException` / `e.errorCode` 분기를 안내하지만, 배포된 Vehicle 2.3.2 AAR에서
 * VehicleException은 난독화되어 있고(ErrorCode는 runtime 전용 vehicle-data에 있음) 컴파일 시 참조할 수 없다.
 * SDK 소스상 VehicleException.message는 ErrorCode 이름이므로 메시지의 ErrorCode 이름으로 분류한다.
 * 알 수 없는 오류는 OTHER로 남기고 성공으로 바꾸지 않는다.
 */
internal fun Throwable.toProbeFailure(): ProbeFailure {
    if (this is ProbeFailure) return this
    val text = message ?: toString()
    if (this is SecurityException) {
        return ProbeFailure(FailureKind.PERMISSION, "SecurityException", text)
    }
    val code = KNOWN_ERROR_CODES.firstOrNull { text.contains(it) }
        ?: return ProbeFailure(FailureKind.OTHER, this::class.java.name, text)
    val kind = when (code) {
        "ERROR_NOT_SUPPORTED_PROPERTY",
        "ERROR_NOT_SUPPORTED_AREA",
        "ERROR_NOT_SUPPORTED_VALUE_TYPE" -> FailureKind.UNSUPPORTED
        "ERROR_SECURITY_PERMISSION" -> FailureKind.PERMISSION
        "ERROR_SERVICE_CONNECTION_FAILURE",
        "ERROR_INTERNAL_SERVICE" -> FailureKind.SERVICE
        else -> FailureKind.OTHER
    }
    return ProbeFailure(kind, code, text)
}

/** vehicle-data 1.1.11 ErrorCode 상수 이름 (javap로 확인). */
private val KNOWN_ERROR_CODES = listOf(
    "ERROR_NOT_SUPPORTED_PROPERTY",
    "ERROR_NOT_SUPPORTED_AREA",
    "ERROR_NOT_SUPPORTED_VALUE_TYPE",
    "ERROR_SECURITY_PERMISSION",
    "ERROR_SERVICE_CONNECTION_FAILURE",
    "ERROR_INTERNAL_SERVICE",
    "ERROR_DRIVING_RESTRICTED",
    "ERROR_EMPTY_RESULT",
    "ERROR_UNDEFINED",
)
