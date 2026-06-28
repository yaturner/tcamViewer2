package com.das.tcamviewer2

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
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
}
