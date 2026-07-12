package com.das.tcamviewer2.ui

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryScreenHelpersTest {

    // --- formatTemp ---

    @Test
    fun formatTempCelsiusLowResolution() {
        // rawValue=27998, scale=100 → (27998/100 - 273.15) = 6.83 -> "6.8°C"
        // (deliberately not an X.X5 boundary value, to avoid float-rounding ambiguity)
        assertEquals("6.8°C", formatTemp(27998, 100f, isCelsius = true))
    }

    @Test
    fun formatTempCelsiusHighResolution() {
        // rawValue=27315, scale=100 → (27315/100 - 273.15) = 0.0
        assertEquals("0.0°C", formatTemp(27315, 100f, isCelsius = true))
    }

    @Test
    fun formatTempFahrenheitConvertsFromCelsius() {
        // 100°C -> 212°F
        assertEquals("212.0°F", formatTemp(37315, 100f, isCelsius = false))
    }

    // --- formatDateFolder ---

    @Test
    fun formatDateFolderConvertsNumericMonthToName() {
        assertEquals("June 25, 2026", formatDateFolder("06_25_2026"))
    }

    @Test
    fun formatDateFolderHandlesDecemberAndJanuary() {
        assertEquals("December 31, 2025", formatDateFolder("12_31_2025"))
        // The day part is passed through verbatim (not re-parsed as an int), so its
        // original zero-padding is preserved.
        assertEquals("January 01, 2026", formatDateFolder("01_01_2026"))
    }

    @Test
    fun formatDateFolderReturnsOriginalOnUnexpectedShape() {
        assertEquals("not_a_date", formatDateFolder("not_a_date"))
        assertEquals("13_01_2026", formatDateFolder("13_01_2026")) // month 13 out of range
    }

    // --- formatFilename ---

    @Test
    fun formatFilenameImageFile() {
        assertEquals("08:17:39", formatFilename("img_08_17_39.tjsn"))
    }

    @Test
    fun formatFilenameVideoFile() {
        assertEquals("08:17:39", formatFilename("vid_08_17_39.mtjsn"))
    }

    @Test
    fun formatFilenameTimelapseFile() {
        assertEquals("08:17:39", formatFilename("tl_08_17_39.tltjsn"))
    }

    @Test
    fun formatFilenameReturnsOriginalOnUnexpectedShape() {
        assertEquals("weird_name.tjsn", formatFilename("weird_name.tjsn"))
    }

    // --- parseResolution ---

    @Test
    fun parseResolutionParsesValidStrings() {
        assertEquals(320 to 240, parseResolution("320x240"))
        assertEquals(160 to 120, parseResolution("160x120"))
        assertEquals(640 to 480, parseResolution("640X480")) // case-insensitive
    }

    @Test
    fun parseResolutionRejectsMalformedStrings() {
        assertNull(parseResolution("320"))
        assertNull(parseResolution("320x240x10"))
        assertNull(parseResolution("axb"))
        assertNull(parseResolution(""))
    }

    // --- parseVideoTimeMs ---
    //
    // The 2-digit-fractional-seconds case ("41.48" = 480ms, not 48ms) is the exact edge case
    // found while debugging VideoExporter timing this session — the source camera doesn't
    // always zero-pad milliseconds to 3 digits, so this must scale rather than read verbatim.

    @Test
    fun parseVideoTimeMsWithThreeDigitFraction() {
        // 8h 17m 41.158s
        val expected = 8L * 3_600_000 + 17L * 60_000 + 41L * 1_000 + 158
        assertEquals(expected, parseVideoTimeMs("8:17:41.158"))
    }

    @Test
    fun parseVideoTimeMsWithTwoDigitFractionScalesToMilliseconds() {
        // "41.48" means 41.48 seconds = 41s + 480ms, NOT 41s + 48ms
        val expected = 8L * 3_600_000 + 17L * 60_000 + 41L * 1_000 + 480
        assertEquals(expected, parseVideoTimeMs("8:17:41.48"))
    }

    @Test
    fun parseVideoTimeMsWithOneDigitFractionScalesToMilliseconds() {
        // "41.4" means 400ms
        val expected = 8L * 3_600_000 + 17L * 60_000 + 41L * 1_000 + 400
        assertEquals(expected, parseVideoTimeMs("8:17:41.4"))
    }

    @Test
    fun parseVideoTimeMsWithNoFraction() {
        val expected = 8L * 3_600_000 + 17L * 60_000 + 41L * 1_000
        assertEquals(expected, parseVideoTimeMs("8:17:41"))
    }

    @Test
    fun parseVideoTimeMsReturnsNullOnMalformedInput() {
        assertNull(parseVideoTimeMs("not a time"))
        assertNull(parseVideoTimeMs("8:17"))
        assertNull(parseVideoTimeMs(""))
    }

    // --- parseFrameTimestampMs ---

    @Test
    fun parseFrameTimestampMsReadsMetadataTimeField() {
        val json = JSONObject().apply {
            put("metadata", JSONObject().apply { put("Time", "8:17:41.158") })
        }
        val expected = 8L * 3_600_000 + 17L * 60_000 + 41L * 1_000 + 158
        assertEquals(expected, parseFrameTimestampMs(json))
    }

    @Test
    fun parseFrameTimestampMsHandlesTwoDigitFractionField() {
        val json = JSONObject().apply {
            put("metadata", JSONObject().apply { put("Time", "8:17:41.48") })
        }
        val expected = 8L * 3_600_000 + 17L * 60_000 + 41L * 1_000 + 480
        assertEquals(expected, parseFrameTimestampMs(json))
    }

    @Test
    fun parseFrameTimestampMsReturnsZeroWhenMetadataMissing() {
        val json = JSONObject()
        assertEquals(0L, parseFrameTimestampMs(json))
    }

    @Test
    fun parseFrameTimestampMsReturnsZeroWhenTimeFieldMissing() {
        val json = JSONObject().apply { put("metadata", JSONObject()) }
        assertEquals(0L, parseFrameTimestampMs(json))
    }

    // --- calculateFrameInterval ---

    @Test
    fun calculateFrameIntervalDefaultsWhenVideoInfoMissing() {
        assertEquals(125L, calculateFrameInterval(null, 100))
    }

    @Test
    fun calculateFrameIntervalDefaultsForSingleFrame() {
        val videoInfo = JSONObject().apply {
            put("start_time", "0:00:00.000")
            put("end_time", "0:00:10.000")
        }
        assertEquals(125L, calculateFrameInterval(videoInfo, 1))
        assertEquals(125L, calculateFrameInterval(videoInfo, 0))
    }

    @Test
    fun calculateFrameIntervalDividesDurationAcrossFrames() {
        val videoInfo = JSONObject().apply {
            put("start_time", "0:00:00.000")
            put("end_time", "0:00:10.000") // 10,000ms
        }
        assertEquals(100L, calculateFrameInterval(videoInfo, 100)) // 10000 / 100
    }

    @Test
    fun calculateFrameIntervalDefaultsWhenEndBeforeStart() {
        val videoInfo = JSONObject().apply {
            put("start_time", "0:00:10.000")
            put("end_time", "0:00:00.000")
        }
        assertEquals(125L, calculateFrameInterval(videoInfo, 50))
    }

    @Test
    fun calculateFrameIntervalDefaultsOnMissingFields() {
        val videoInfo = JSONObject()
        assertEquals(125L, calculateFrameInterval(videoInfo, 50))
    }
}
