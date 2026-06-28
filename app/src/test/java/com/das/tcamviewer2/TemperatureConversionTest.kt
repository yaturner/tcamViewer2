package com.das.tcamviewer2

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for the temperature conversion math mirrored from CameraUtils.convertToRadiometric.
 * Radiometric values are stored as Kelvin × scale, where scale = 10 (tLinearResolution=0)
 * or 100 (tLinearResolution=1).
 */
class TemperatureConversionTest {

    // Mirror of CameraUtils.convertToRadiometric
    private fun toRadiometric(valueDegrees: Float, isCelsius: Boolean, resolution: Int): Int {
        val scale = if (resolution == 0) 10f else 100f
        return if (isCelsius) {
            Math.round((valueDegrees + 273.15f) * scale)
        } else {
            val celsius = (valueDegrees - 32f) * 0.5556f
            Math.round((celsius + 273.15f) * scale)
        }
    }

    // --- Low resolution (scale = 10, tLinearResolution = 0) ---

    @Test
    fun zeroCelsiusLowRes() {
        // 0°C = 273.15 K → 273.15 × 10 = 2731.5 → rounds to 2732
        assertEquals(2732, toRadiometric(0f, isCelsius = true, resolution = 0))
    }

    @Test
    fun oneHundredCelsiusLowRes() {
        // 100°C = 373.15 K → 3731.5 → 3732
        assertEquals(3732, toRadiometric(100f, isCelsius = true, resolution = 0))
    }

    @Test
    fun absoluteZeroLowRes() {
        // -273.15°C = 0 K → 0
        assertEquals(0, toRadiometric(-273.15f, isCelsius = true, resolution = 0))
    }

    @Test
    fun bodyTemperatureLowRes() {
        // 37°C = 310.15 K → 3101.5 → 3102
        assertEquals(3102, toRadiometric(37f, isCelsius = true, resolution = 0))
    }

    // --- High resolution (scale = 100, tLinearResolution = 1) ---

    @Test
    fun zeroCelsiusHighRes() {
        // 0°C = 273.15 K → 273.15 × 100 = 27315
        assertEquals(27315, toRadiometric(0f, isCelsius = true, resolution = 1))
    }

    @Test
    fun oneHundredCelsiusHighRes() {
        // 100°C = 373.15 K → 37315
        assertEquals(37315, toRadiometric(100f, isCelsius = true, resolution = 1))
    }

    // --- Fahrenheit conversion ---

    @Test
    fun freezingPointFahrenheit() {
        // 32°F = 0°C → 2732 (low res)
        assertEquals(2732, toRadiometric(32f, isCelsius = false, resolution = 0))
    }

    @Test
    fun boilingPointFahrenheit() {
        // 212°F = 100°C → 3732 (low res)
        // c = (212 - 32) × 0.5556 = 180 × 0.5556 = 100.008°C
        // (100.008 + 273.15) × 10 = 3731.58 → rounds to 3732
        assertEquals(3732, toRadiometric(212f, isCelsius = false, resolution = 0))
    }

    @Test
    fun bodyTemperatureFahrenheit() {
        // 98.6°F = 37°C → same as zeroCelsiusLowRes for 37°C
        // c = (98.6 - 32) × 0.5556 = 66.6 × 0.5556 = 37.003°C
        // (37.003 + 273.15) × 10 = 3101.53 → 3102
        assertEquals(3102, toRadiometric(98.6f, isCelsius = false, resolution = 0))
    }

    // --- Round-trip consistency: C and F equivalent temperatures should give same radiometric value ---

    @Test
    fun celsiusAndFahrenheitEquivalentValuesProduceSameRadiometric() {
        // 0°C == 32°F
        assertEquals(
            toRadiometric(0f, isCelsius = true, resolution = 0),
            toRadiometric(32f, isCelsius = false, resolution = 0)
        )
        // 100°C == 212°F
        assertEquals(
            toRadiometric(100f, isCelsius = true, resolution = 0),
            toRadiometric(212f, isCelsius = false, resolution = 0)
        )
    }
}
