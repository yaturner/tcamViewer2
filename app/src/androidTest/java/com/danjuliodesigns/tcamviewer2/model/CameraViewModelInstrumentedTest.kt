package com.danjuliodesigns.tcamviewer2.model

import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.danjuliodesigns.tcamviewer2.SettingsDataManager
import com.danjuliodesigns.tcamviewer2.appContext
import com.danjuliodesigns.tcamviewer2.cameraService
import com.danjuliodesigns.tcamviewer2.cameraUtils
import com.danjuliodesigns.tcamviewer2.dataStore
import com.danjuliodesigns.tcamviewer2.factory.PaletteFactory
import com.danjuliodesigns.tcamviewer2.paletteFactory
import com.danjuliodesigns.tcamviewer2.services.CameraService
import com.danjuliodesigns.tcamviewer2.settingsDataManager
import com.danjuliodesigns.tcamviewer2.utils
import com.danjuliodesigns.tcamviewer2.utils.CameraUtils
import com.danjuliodesigns.tcamviewer2.utils.Utils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Instrumented regression tests for CameraViewModel behavior that was previously only reachable
 *  manually, since the ViewModel references the app's global `lateinit var` singletons directly
 *  rather than taking them as constructor params (MainActivity.kt, where they're normally set up)
 *  -- a real Context is needed for SettingsDataManager's DataStore and CameraUtils, so this can't
 *  run as a plain JVM unit test. A fresh ViewModel is constructed per test with those globals
 *  pointed at real instances, and never connected to an actual camera unless a test specifically
 *  needs one (CameraService silently no-ops writes when not connected, so calling its methods
 *  without a connection is safe). */
@RunWith(AndroidJUnit4::class)
class CameraViewModelInstrumentedTest {
    private lateinit var viewModel: CameraViewModel

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // A real on-device DataStore, shared across the whole suite -- clear it first so a
        // leftover value from an earlier test (or a manual run of the app itself) can't flake an
        // assertion here. Also keeps auto-connect off so the ViewModel's init{} doesn't go off
        // trying to reach a camera that isn't there.
        context.dataStore.edit { it.clear() }

        cameraService = CameraService()
        settingsDataManager = SettingsDataManager(context)
        cameraUtils = CameraUtils(context)
        paletteFactory = PaletteFactory()
        utils = Utils(context)
        appContext = context

        viewModel = CameraViewModel()
    }

    // --- issue #3: changing a camera setting (emissivity/AGC/gain) updates the app's own
    // displayed state immediately, rather than waiting on a round trip to the camera ---

    @Test
    fun sendCameraConfig_updatesCameraConfigStateImmediately() {
        assertEquals(null, viewModel.cameraConfig.value)

        viewModel.sendCameraConfig(agcEnabled = true, emissivity = 75, gainMode = 1)

        val config = viewModel.cameraConfig.value
        assertEquals(true, config?.agcEnabled)
        assertEquals(75, config?.emissivity)
        assertEquals(1, config?.gainMode)
    }

    // --- issue #6: temperature unit changes apply immediately (observeSettings' collector runs
    // from the moment the ViewModel is constructed, regardless of connection state) ---

    @Test
    fun temperatureUnitSettingChange_immediatelyUpdatesIsCelsius() = runBlocking {
        assertTrue("Default should be Celsius", viewModel.isCelsius.value)

        settingsDataManager.saveTemperatureUnit("Fahrenheit")
        // observeSettings()'s collector runs on a separate coroutine; give it a moment to see
        // the DataStore write propagate through the Flow.
        awaitValue { !viewModel.isCelsius.value }

        assertFalse(viewModel.isCelsius.value)

        settingsDataManager.saveTemperatureUnit("Celsius")
        awaitValue { viewModel.isCelsius.value }
        assertTrue(viewModel.isCelsius.value)
    }

    // --- issues #7/#19/#55: the Spotmeter setting fully drives spotmeterEnabled, and turning on
    // Region Measurement is mutually exclusive with it at the settings-flow level too ---

    @Test
    fun spotmeterSettingChange_immediatelyUpdatesSpotmeterEnabled() = runBlocking {
        settingsDataManager.saveSpotmeter(true)
        awaitValue { viewModel.spotmeterEnabled.value }
        assertTrue(viewModel.spotmeterEnabled.value)

        settingsDataManager.saveSpotmeter(false)
        awaitValue { !viewModel.spotmeterEnabled.value }
        assertFalse("Disabling Spotmeter must fully disable it, not just hide it", viewModel.spotmeterEnabled.value)
    }

    @Test
    fun regionMeasurementSettingChange_drivesMeasurementMode() = runBlocking {
        assertEquals(MeasurementMode.POINT, viewModel.measurementMode.value)

        settingsDataManager.saveRegionMeasurement(true)
        awaitValue { viewModel.measurementMode.value == MeasurementMode.REGION }
        assertEquals(MeasurementMode.REGION, viewModel.measurementMode.value)

        settingsDataManager.saveRegionMeasurement(false)
        awaitValue { viewModel.measurementMode.value == MeasurementMode.POINT }
        assertEquals(MeasurementMode.POINT, viewModel.measurementMode.value)
    }

    // --- issues #20/#57: Clear doesn't silently resume a stopped chart ---

    @Test
    fun clearChartHistory_doesNotResumeAPausedChart() {
        viewModel.toggleChartHistoryPaused() // Stop
        assertTrue(viewModel.isChartHistoryPaused.value)

        viewModel.clearChartHistory()

        assertTrue("Clear must not silently resume collection", viewModel.isChartHistoryPaused.value)
        assertTrue(viewModel.tempHistory.value.isEmpty())
    }

    @Test
    fun clearChartHistory_leavesARunningChartRunning() {
        // Never paused -- default state is running.
        assertFalse(viewModel.isChartHistoryPaused.value)

        viewModel.clearChartHistory()

        assertFalse(viewModel.isChartHistoryPaused.value)
    }

    @Test
    fun toggleChartHistoryPaused_flipsBetweenStopAndResume() {
        assertFalse(viewModel.isChartHistoryPaused.value)
        viewModel.toggleChartHistoryPaused()
        assertTrue(viewModel.isChartHistoryPaused.value)
        viewModel.toggleChartHistoryPaused()
        assertFalse(viewModel.isChartHistoryPaused.value)
    }

    // --- issue #51: a config change that stops the camera's stream must update the app's own
    // streaming flag immediately, not leave it desynced until the user taps twice ---

    @Test
    fun sendCameraConfig_stopsStreamingStateWhenStreamingWasActive() {
        viewModel.toggleStreaming() // starts streaming (cameraService silently no-ops the actual
        // write since it's not connected to anything in this test -- only the local state matters)
        assertTrue(viewModel.isStreaming.value)

        viewModel.sendCameraConfig(agcEnabled = true, emissivity = 90, gainMode = 0)

        assertFalse(
            "Changing a sensor setting while streaming must flip isStreaming back to false " +
                "immediately, matching what the camera does on its own",
            viewModel.isStreaming.value,
        )
    }

    @Test
    fun sendCameraConfig_doesNothingToStreamingStateWhenNotStreaming() {
        assertFalse(viewModel.isStreaming.value)
        viewModel.sendCameraConfig(agcEnabled = false, emissivity = 90, gainMode = 0)
        assertFalse(viewModel.isStreaming.value)
    }

    /** Polls [condition] for up to 2s -- observeSettings()'s Flow collectors run asynchronously
     *  relative to the suspend fun that wrote the DataStore value, so a StateFlow this test reads
     *  can lag the write by a few collector dispatches. */
    private suspend fun awaitValue(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 2_000L
        while (!condition() && System.currentTimeMillis() < deadline) {
            kotlinx.coroutines.delay(20L)
        }
    }
}
