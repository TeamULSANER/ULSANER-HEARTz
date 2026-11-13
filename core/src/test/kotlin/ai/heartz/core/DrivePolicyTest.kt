package ai.heartz.core

import ai.heartz.core.drive.DrivePolicy
import ai.heartz.core.drive.DrivingRecordingCondition
import ai.heartz.core.drive.DrivingRecordingGate
import ai.heartz.core.drive.UxState
import ai.heartz.core.drive.VerificationStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrivePolicyTest {
    @Test
    fun parkedWithoutRestrictionAllowsParkedFeatures() {
        val parked = UxState.Known(requiresDistractionOptimization = false, activeRestrictions = 0)
        assertFalse(DrivePolicy.showDrivingView(parked))
        assertTrue(DrivePolicy.allowParkedFeatures(parked))
    }

    @Test
    fun restrictedStateShowsDrivingViewOnly() {
        val driving = UxState.Known(requiresDistractionOptimization = true, activeRestrictions = 0x50)
        assertTrue(DrivePolicy.showDrivingView(driving))
        assertFalse(DrivePolicy.allowParkedFeatures(driving))
    }

    @Test
    fun unknownStateIsTreatedAsRestricted() {
        val unknown = UxState.Unknown("car service unavailable")
        assertTrue(DrivePolicy.showDrivingView(unknown))
        assertFalse(DrivePolicy.allowParkedFeatures(unknown))
    }

    @Test
    fun drivingRecordingIsDisabledByDefault() {
        assertFalse(DrivingRecordingGate.isEnabled())
    }

    @Test
    fun drivingRecordingNeedsEveryConditionVerified() {
        val all = DrivingRecordingCondition.entries.associateWith { VerificationStatus.VERIFIED }
        assertTrue(DrivingRecordingGate.isEnabled(all))
        assertFalse(DrivingRecordingGate.isEnabled(all + (DrivingRecordingCondition.MICROPHONE to VerificationStatus.FAILED)))
        assertFalse(DrivingRecordingGate.isEnabled(all - DrivingRecordingCondition.INTEGRATION_SCOPE))
    }
}
