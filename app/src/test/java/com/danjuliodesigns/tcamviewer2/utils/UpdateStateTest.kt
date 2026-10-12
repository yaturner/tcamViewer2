package com.danjuliodesigns.tcamviewer2.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateStateTest {

    @Test
    fun mapUpdateState_availableAndFlexibleAllowed_returnsAvailable() {
        val state = mapUpdateState(updateAvailable = true, flexibleUpdateAllowed = true, availableVersionCode = 42)
        assertEquals(UpdateState.Available(42), state)
    }

    @Test
    fun mapUpdateState_availableButFlexibleNotAllowed_returnsUpToDate() {
        val state = mapUpdateState(updateAvailable = true, flexibleUpdateAllowed = false, availableVersionCode = 42)
        assertEquals(UpdateState.UpToDate, state)
    }

    @Test
    fun mapUpdateState_noUpdateAvailable_returnsUpToDate() {
        val state = mapUpdateState(updateAvailable = false, flexibleUpdateAllowed = true, availableVersionCode = 42)
        assertEquals(UpdateState.UpToDate, state)
    }

    @Test
    fun isUpdateReadyToInstall_trueOnlyWhenStatusMatchesDownloadedConstant() {
        assertTrue(isUpdateReadyToInstall(installStatus = 11, downloadedStatus = 11))
        assertFalse(isUpdateReadyToInstall(installStatus = 2, downloadedStatus = 11))
    }

    @Test
    fun canCompleteUpdateNow_falseWhileRecordingOrTimeLapsing() {
        assertTrue(canCompleteUpdateNow(isRecording = false, isTimeLapsing = false))
        assertFalse(canCompleteUpdateNow(isRecording = true, isTimeLapsing = false))
        assertFalse(canCompleteUpdateNow(isRecording = false, isTimeLapsing = true))
        assertFalse(canCompleteUpdateNow(isRecording = true, isTimeLapsing = true))
    }
}
