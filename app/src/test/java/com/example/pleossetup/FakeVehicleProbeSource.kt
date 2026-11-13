package com.example.pleossetup

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * TEST-ONLY FAKE. 앱의 상태 처리만 검증한다. 실제 Pleos SDK 연결 증거가 아니다.
 * 각 호출은 테스트가 [CompletableDeferred]를 완료할 때까지 응답하지 않는다.
 */
class FakeVehicleProbeSource : VehicleProbeSource {
    var initializeCount = 0
    var releaseCount = 0
    var registerCount = 0
    var unregisterCount = 0
    var registerThrows: Exception? = null

    var capabilityResponse = CompletableDeferred<Result<DoorCapability>>()
    var readResponse = CompletableDeferred<Result<Int?>>()

    private var listener: SendChannel<DoorEvent>? = null
    val isListening: Boolean get() = listener != null

    override fun sdkVersion(): String = "fake-sdk"
    override fun serviceVersion(): String? = null

    override fun initialize() {
        initializeCount++
    }

    override fun release() {
        releaseCount++
    }

    override suspend fun checkDoorPositionCapability(): Result<DoorCapability> =
        capabilityResponse.await()

    override suspend fun getDoorPosition(): Result<Int?> = readResponse.await()

    override fun doorPositionUpdates(): Flow<DoorEvent> = callbackFlow {
        registerThrows?.let { throw it }
        registerCount++
        listener = channel
        trySend(DoorEvent.Registered)
        awaitClose {
            unregisterCount++
            listener = null
        }
    }

    /** SDK listener callback 흉내. 등록되어 있지 않으면 false (늦은 callback은 버려진다). */
    fun emit(value: Int?): Boolean = listener?.trySend(DoorEvent.Updated(value))?.isSuccess ?: false
}
