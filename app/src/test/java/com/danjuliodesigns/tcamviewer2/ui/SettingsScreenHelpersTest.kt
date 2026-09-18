package com.danjuliodesigns.tcamviewer2.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsScreenHelpersTest {
    // --- convertManualBound ---

    @Test
    fun convertManualBoundFahrenheitToCelsius() {
        // 32°F -> 0°C
        assertEquals("0", convertManualBound("32", toCelsius = true))
        // 212°F -> 100°C
        assertEquals("100", convertManualBound("212", toCelsius = true))
    }

    @Test
    fun convertManualBoundCelsiusToFahrenheit() {
        // 0°C -> 32°F
        assertEquals("32", convertManualBound("0", toCelsius = false))
        // 100°C -> 212°F
        assertEquals("212", convertManualBound("100", toCelsius = false))
    }

    @Test
    fun convertManualBoundRoundsToNearestInt() {
        // 98.6°F -> 37.0°C exactly, but pick a value that lands on a rounding boundary:
        // 20°C -> 68°F exactly
        assertEquals("68", convertManualBound("20", toCelsius = false))
        // 21°C -> 69.8°F -> rounds to 70
        assertEquals("70", convertManualBound("21", toCelsius = false))
    }

    @Test
    fun convertManualBoundPassesThroughUnparseableInput() {
        assertEquals("not_a_number", convertManualBound("not_a_number", toCelsius = true))
        assertEquals("", convertManualBound("", toCelsius = false))
    }

    // --- clampManualRangeBound ---

    @Test
    fun clampManualRangeBoundMinFloorsAtAbsoluteZeroCelsius() {
        assertEquals("-273.0", clampManualRangeBound("-500", isCelsiusUnit = true, isMin = true))
        assertEquals("-273.0", clampManualRangeBound("-273.15", isCelsiusUnit = true, isMin = true))
    }

    @Test
    fun clampManualRangeBoundMinLeavesInRangeValuesUnchanged() {
        // Round-trips the original string exactly, not a reformatted float
        assertEquals("30", clampManualRangeBound("30", isCelsiusUnit = true, isMin = true))
        assertEquals("-100", clampManualRangeBound("-100", isCelsiusUnit = true, isMin = true))
    }

    @Test
    fun clampManualRangeBoundMinAtExactFloorIsUnchanged() {
        // Not < floor, so must round-trip verbatim (not get reformatted to "-273.0")
        assertEquals("-273", clampManualRangeBound("-273", isCelsiusUnit = true, isMin = true))
    }

    @Test
    fun clampManualRangeBoundMaxCapsAt999Celsius() {
        assertEquals("999.0", clampManualRangeBound("5000", isCelsiusUnit = true, isMin = false))
        assertEquals("999.0", clampManualRangeBound("1000", isCelsiusUnit = true, isMin = false))
    }

    @Test
    fun clampManualRangeBoundMaxAtExactCeilingIsUnchanged() {
        assertEquals("999", clampManualRangeBound("999", isCelsiusUnit = true, isMin = false))
    }

    @Test
    fun clampManualRangeBoundMaxLeavesInRangeValuesUnchanged() {
        assertEquals("35", clampManualRangeBound("35", isCelsiusUnit = true, isMin = false))
    }

    @Test
    fun clampManualRangeBoundIsUnitAwareInFahrenheit() {
        // -273°C floor == -459.4°F; a value below that should clamp
        assertEquals((-273f * 9f / 5f + 32f).toString(), clampManualRangeBound("-1000", isCelsiusUnit = false, isMin = true))
        // A value between the F floor and a normal Fahrenheit min should NOT be clamped just
        // because it would be out of range in Celsius terms (e.g. -300°F is invalid in C terms
        // but the same numeric value never appears here since bounds are unit-consistent).
        assertEquals("-400", clampManualRangeBound("-400", isCelsiusUnit = false, isMin = true))

        // 999°C ceiling == 1830.2°F; a value above that should clamp
        assertEquals((999f * 9f / 5f + 32f).toString(), clampManualRangeBound("5000", isCelsiusUnit = false, isMin = false))
        assertEquals("1000", clampManualRangeBound("1000", isCelsiusUnit = false, isMin = false))
    }

    @Test
    fun clampManualRangeBoundPassesThroughUnparseableInput() {
        assertEquals("", clampManualRangeBound("", isCelsiusUnit = true, isMin = true))
        assertEquals("-", clampManualRangeBound("-", isCelsiusUnit = true, isMin = false))
        assertEquals("abc", clampManualRangeBound("abc", isCelsiusUnit = true, isMin = true))
    }
}
