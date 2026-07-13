package com.das.tcamviewer2

import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.das.tcamviewer2.constants.Constants
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsDataManagerTest {

    private lateinit var manager: SettingsDataManager

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // JUnit4 doesn't run @Test methods in declaration order, and this DataStore is a
        // real on-device store shared across the whole suite — without clearing it first,
        // a "default value" assertion can flake depending on what an earlier test left behind.
        context.dataStore.edit { it.clear() }
        manager = SettingsDataManager(context)
    }

    @Test
    fun defaultTemperatureUnitIsCelsius() = runBlocking {
        assertTrue(manager.isUnitsCelsius())
        assertFalse(manager.isUnitsFahrenheit())
    }

    @Test
    fun defaultManualRangeIsOff() = runBlocking {
        assertFalse(manager.isManualRange())
    }

    @Test
    fun defaultCameraIpIsSet() = runBlocking {
        val ip = manager.getCameraIp()
        assertTrue("Default IP should be non-empty", ip.isNotEmpty())
        assertTrue("Default IP should match valid IP format", ip.contains("."))
    }

    @Test
    fun saveThenReadCameraIp() = runBlocking {
        val testIp = "10.0.0.99"
        manager.saveCameraIp(testIp)
        assertEquals(testIp, manager.getCameraIp())
    }

    @Test
    fun saveThenReadPalette() = runBlocking {
        manager.saveSelectedPalette("Arctic")
        assertEquals("Arctic", manager.getSelectedPalette())

        manager.saveSelectedPalette("Rainbow")
        assertEquals("Rainbow", manager.getSelectedPalette())
    }

    @Test
    fun saveThenReadTemperatureUnit() = runBlocking {
        manager.saveTemperatureUnit("Fahrenheit")
        assertFalse(manager.isUnitsCelsius())
        assertTrue(manager.isUnitsFahrenheit())

        manager.saveTemperatureUnit("Celsius")
        assertTrue(manager.isUnitsCelsius())
        assertFalse(manager.isUnitsFahrenheit())
    }

    @Test
    fun saveThenReadManualRange() = runBlocking {
        manager.saveManualRange(true)
        assertTrue(manager.isManualRange())

        manager.saveManualRange(false)
        assertFalse(manager.isManualRange())
    }

    @Test
    fun saveThenReadMinMaxValues() = runBlocking {
        manager.saveMinValue("20.5")
        manager.saveMaxValue("45.0")
        assertEquals(20.5f, manager.getManualMinTemperature(), 0.01f)
        assertEquals(45.0f, manager.getManualMaxTemperature(), 0.01f)
    }

    @Test
    fun saveThenReadGainMode() = runBlocking {
        manager.saveCameraGainMode(1)
        assertEquals(1, manager.cameraGainModeFlow.first())

        manager.saveCameraGainMode(0)
        assertEquals(0, manager.cameraGainModeFlow.first())
    }

    @Test
    fun saveThenReadAgcEnabled() = runBlocking {
        manager.saveCameraAgc(true)
        assertEquals(true, manager.cameraAgcFlow.first())

        manager.saveCameraAgc(false)
        assertEquals(false, manager.cameraAgcFlow.first())
    }

    // --- Defaults for keys untouched by the tests above (safe regardless of JUnit run order) ---

    @Test
    fun defaultExportPictureIsFalse() = runBlocking {
        assertFalse(manager.getExportPicture())
    }

    @Test
    fun defaultExportMetadataIsFalse() = runBlocking {
        assertFalse(manager.getExportMetadata())
    }

    @Test
    fun defaultExportResolutionIs320x240() = runBlocking {
        assertEquals("320x240", manager.getExportResolution())
    }

    @Test
    fun defaultShutterSoundIsTrue() = runBlocking {
        assertTrue(manager.getShutterSound())
    }

    @Test
    fun defaultSpotmeterIsTrue() = runBlocking {
        assertTrue(manager.getSpotmeter())
    }

    @Test
    fun defaultCameraEmissivityIs90() = runBlocking {
        assertEquals("90", manager.cameraEmissivityFlow.first())
    }

    @Test
    fun defaultMinMaxValueStringsAreZeroAndHundred() = runBlocking {
        assertEquals("0", manager.getMinValue())
        assertEquals("100", manager.getMaxValue())
    }

    // --- Round trips for every remaining persisted setting ---

    @Test
    fun saveThenReadExportPicture() = runBlocking {
        manager.saveExportPicture(true)
        assertTrue(manager.getExportPicture())

        manager.saveExportPicture(false)
        assertFalse(manager.getExportPicture())
    }

    @Test
    fun saveThenReadExportMetadata() = runBlocking {
        manager.saveExportMetadata(true)
        assertTrue(manager.getExportMetadata())

        manager.saveExportMetadata(false)
        assertFalse(manager.getExportMetadata())
    }

    @Test
    fun saveThenReadExportResolution() = runBlocking {
        // The Settings screen only offers these four via its dropdown.
        val resolutions = listOf("160x120", "320x240", "480x360", "640x480")
        for (resolution in resolutions) {
            manager.saveExportResolution(resolution)
            assertEquals(resolution, manager.getExportResolution())
        }
    }

    @Test
    fun saveThenReadShutterSound() = runBlocking {
        manager.saveShutterSound(false)
        assertFalse(manager.getShutterSound())

        manager.saveShutterSound(true)
        assertTrue(manager.getShutterSound())
    }

    @Test
    fun saveThenReadSpotmeter() = runBlocking {
        manager.saveSpotmeter(false)
        assertFalse(manager.getSpotmeter())

        manager.saveSpotmeter(true)
        assertTrue(manager.getSpotmeter())
    }

    @Test
    fun saveThenReadCameraEmissivity() = runBlocking {
        manager.saveCameraEmissivity("42")
        assertEquals("42", manager.cameraEmissivityFlow.first())

        manager.saveCameraEmissivity("90")
        assertEquals("90", manager.cameraEmissivityFlow.first())
    }

    @Test
    fun cameraEmissivityStoresRawStringWithoutClamping() = runBlocking {
        // SettingsDataManager itself does no validation/clamping — that only happens in
        // SettingsScreen's emissivityPct() helper. Document the manager's actual behavior
        // so a future "fix" doesn't silently start rejecting out-of-range values here.
        manager.saveCameraEmissivity("150")
        assertEquals("150", manager.cameraEmissivityFlow.first())

        manager.saveCameraEmissivity("90")
    }

    @Test
    fun saveThenReadAllThreeGainModes() = runBlocking {
        for (mode in listOf(Constants.GAIN_MODE_HIGH, Constants.GAIN_MODE_LOW, Constants.GAIN_MODE_AUTO)) {
            manager.saveCameraGainMode(mode)
            assertEquals(mode, manager.cameraGainModeFlow.first())
        }
    }
}
