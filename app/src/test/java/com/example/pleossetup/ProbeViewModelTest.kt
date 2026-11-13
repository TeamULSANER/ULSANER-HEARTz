package com.example.pleossetup

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProbeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var fake: FakeVehicleProbeSource

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        fake = FakeVehicleProbeSource()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.newViewModel(): ProbeViewModel =
        ProbeViewModel(fake, timeoutMillis = 5_000, clock = { testScheduler.currentTime })

    @Test
    fun initializeReturnAloneIsNotDataSuccess() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.initialize()
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state.initialization is InitState.Returned)
        assertEquals(Probe.NotRun, state.capability)
        assertEquals(Probe.NotRun, state.currentValue)
        assertEquals(Subscription.Off, state.live.subscription)
        assertEquals(1, fake.initializeCount)
    }

    @Test
    fun nullReadStaysNullAndIsNotZero() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.initialize()
        vm.readCurrent()
        fake.readResponse.complete(Result.success(null))
        advanceUntilIdle()

        val current = vm.state.value.currentValue
        assertTrue(current is Probe.Ok)
        assertEquals(null, (current as Probe.Ok).value)
        assertNotEquals(0, current.value)
    }

    @Test
    fun failedAndUnsupportedReadsAreNotSuccess() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.initialize()

        vm.readCurrent()
        fake.readResponse.complete(
            Result.failure(ProbeFailure(FailureKind.UNSUPPORTED, "ERROR_NOT_SUPPORTED_AREA", "area")),
        )
        advanceUntilIdle()
        assertTrue(vm.state.value.currentValue is Probe.Unsupported)

        fake.readResponse = CompletableDeferred()
        vm.readCurrent()
        fake.readResponse.complete(
            Result.failure(ProbeFailure(FailureKind.PERMISSION, "ERROR_SECURITY_PERMISSION", "perm")),
        )
        advanceUntilIdle()
        assertTrue(vm.state.value.currentValue is Probe.PermissionDenied)

        fake.readResponse = CompletableDeferred()
        vm.readCurrent()
        fake.readResponse.complete(
            Result.failure(ProbeFailure(FailureKind.SERVICE, "ERROR_INTERNAL_SERVICE", "svc")),
        )
        advanceUntilIdle()
        assertTrue(vm.state.value.currentValue is Probe.Failed)
    }

    @Test
    fun noResponseBecomesTimeoutAndLateAnswerIsIgnored() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.initialize()
        vm.checkCapability()
        runCurrent()
        assertEquals(Probe.Pending, vm.state.value.capability)

        advanceTimeBy(5_001)
        runCurrent()
        assertTrue(vm.state.value.capability is Probe.TimedOut)

        fake.capabilityResponse.complete(Result.success(DoorCapability("READ", emptyMap())))
        advanceUntilIdle()
        assertTrue(vm.state.value.capability is Probe.TimedOut)
    }

    @Test
    fun reconnectDropsInFlightResultFromOldSession() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.initialize()
        vm.readCurrent()
        runCurrent()
        val stale = fake.readResponse

        fake.readResponse = CompletableDeferred()
        vm.reconnect()
        advanceUntilIdle()
        stale.complete(Result.success(1))
        advanceUntilIdle()

        assertEquals(1, fake.releaseCount)
        assertEquals(2, fake.initializeCount)
        assertFalse(vm.state.value.currentValue is Probe.Ok)
    }

    @Test
    fun duplicateStartRegistersOnceAndStopUnregisters() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.initialize()
        vm.onScreenVisible(true)
        vm.startUpdates()
        vm.startUpdates()
        advanceUntilIdle()
        assertEquals(1, fake.registerCount)
        assertTrue(vm.state.value.live.subscription is Subscription.Registered)

        assertTrue(fake.emit(0))
        assertTrue(fake.emit(1))
        assertTrue(fake.emit(0))
        advanceUntilIdle()
        assertEquals(3, vm.state.value.live.callbackCount)
        assertEquals(Probe.Ok(0, testScheduler.currentTime), vm.state.value.live.lastValue)

        vm.stopUpdates()
        advanceUntilIdle()
        assertEquals(1, fake.unregisterCount)
        assertEquals(Subscription.Off, vm.state.value.live.subscription)
        assertFalse(fake.emit(1))
        advanceUntilIdle()
        assertEquals(3, vm.state.value.live.totalCallbackCount)
    }

    @Test
    fun hiddenScreenUnregistersAndVisibleReRegistersOnce() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.initialize()
        vm.onScreenVisible(true)
        vm.startUpdates()
        advanceUntilIdle()

        vm.onScreenVisible(false)
        advanceUntilIdle()
        assertEquals(1, fake.unregisterCount)
        assertEquals(Subscription.PausedWhileHidden, vm.state.value.live.subscription)

        vm.onScreenVisible(true)
        vm.onScreenVisible(true)
        advanceUntilIdle()
        assertEquals(2, fake.registerCount)
        assertTrue(vm.state.value.live.subscription is Subscription.Registered)
    }

    @Test
    fun registerFailureIsReportedNotSubscribed() = runTest(dispatcher) {
        fake.registerThrows = SecurityException("registerDoorPosition need Permission")
        val vm = newViewModel()
        vm.initialize()
        vm.onScreenVisible(true)
        vm.startUpdates()
        advanceUntilIdle()

        val sub = vm.state.value.live.subscription
        assertTrue(sub is Subscription.Failed)
        assertEquals("SecurityException", (sub as Subscription.Failed).code)
    }

    @Test
    fun clearingViewModelReleasesSdk() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.initialize()
        vm.onScreenVisible(true)
        vm.startUpdates()
        advanceUntilIdle()

        vm.closeForTest()
        advanceUntilIdle()
        assertEquals(1, fake.releaseCount)
        assertFalse(fake.isListening)
    }

    private fun TestScope.runCurrent() = testScheduler.runCurrent()
}
