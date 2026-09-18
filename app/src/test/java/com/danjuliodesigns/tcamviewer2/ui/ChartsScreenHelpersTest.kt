package com.danjuliodesigns.tcamviewer2.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class ChartsScreenHelpersTest {
    // --- loadTempChart ---

    private fun writeTempFile(content: String): File {
        val file = File.createTempFile("chart_test", ".tchart")
        file.deleteOnExit()
        file.writeText(content)
        return file
    }

    @Test
    fun loadTempChartParsesFullValidFile() {
        val json = """
            {
              "saved_time": "09/18/26 12:00:00",
              "unit": "Fahrenheit",
              "primary_label": "Avg",
              "samples": [
                {"t": 1000, "spot": 70.5, "max": 80.0, "min": 60.0},
                {"t": 2000, "spot": 71.0, "max": 81.0, "min": 61.0}
              ]
            }
        """.trimIndent()
        val chart = loadTempChart(writeTempFile(json))
        assertNotNull(chart)
        assertEquals("09/18/26 12:00:00", chart!!.savedTime)
        assertEquals(false, chart.isCelsius)
        assertEquals("Avg", chart.primaryLabel)
        assertEquals(2, chart.samples.size)
        assertEquals(1000L, chart.samples[0].timestampMs)
        assertEquals(70.5f, chart.samples[0].spot, 0.001f)
        assertEquals(80.0f, chart.samples[0].max, 0.001f)
        assertEquals(60.0f, chart.samples[0].min, 0.001f)
    }

    @Test
    fun loadTempChartDefaultsUnitToCelsiusWhenMissing() {
        val json = """{"samples": [{"t": 1, "spot": 1.0, "max": 1.0, "min": 1.0}]}"""
        val chart = loadTempChart(writeTempFile(json))
        assertNotNull(chart)
        assertEquals(true, chart!!.isCelsius)
    }

    @Test
    fun loadTempChartDefaultsPrimaryLabelToSpotWhenMissing() {
        // Pre-issue-#19 files predate the primary_label field entirely -- must still load
        // as point-mode "Spot" charts rather than failing to parse.
        val json = """{"samples": [{"t": 1, "spot": 1.0, "max": 1.0, "min": 1.0}]}"""
        val chart = loadTempChart(writeTempFile(json))
        assertNotNull(chart)
        assertEquals("Spot", chart!!.primaryLabel)
    }

    @Test
    fun loadTempChartReturnsNullOnMalformedJson() {
        val chart = loadTempChart(writeTempFile("not valid json{{{"))
        assertNull(chart)
    }

    @Test
    fun loadTempChartReturnsNullWhenSamplesKeyMissing() {
        val json = """{"saved_time": "09/18/26 12:00:00", "unit": "Celsius"}"""
        val chart = loadTempChart(writeTempFile(json))
        assertNull(chart)
    }

    @Test
    fun loadTempChartReturnsNullForNonexistentFile() {
        val chart = loadTempChart(File("/nonexistent/path/does_not_exist.tchart"))
        assertNull(chart)
    }

    @Test
    fun loadTempChartHandlesEmptySamplesArray() {
        val json = """{"unit": "Celsius", "samples": []}"""
        val chart = loadTempChart(writeTempFile(json))
        assertNotNull(chart)
        assertEquals(0, chart!!.samples.size)
    }

    // --- formatChartFilename ---

    @Test
    fun formatChartFilenameParsesValidName() {
        assertEquals("08:17:39", formatChartFilename("chart_08_17_39.tchart"))
    }

    @Test
    fun formatChartFilenameReturnsOriginalOnUnexpectedShape() {
        assertEquals("weird_name.tchart", formatChartFilename("weird_name.tchart"))
        assertEquals("chart_08_17.tchart", formatChartFilename("chart_08_17.tchart")) // missing a segment
        assertEquals("not_a_chart_file.txt", formatChartFilename("not_a_chart_file.txt"))
    }

    // --- parseFolderDateMillis (LibraryScreen.kt, untested elsewhere; shared with ChartsScreen) ---

    @Test
    fun parseFolderDateMillisParsesValidFolderName() {
        val millis = parseFolderDateMillis("06_25_2026")
        assertNotNull(millis)
    }

    @Test
    fun parseFolderDateMillisReturnsNullForUnexpectedShape() {
        assertNull(parseFolderDateMillis("not_a_date"))
        assertNull(parseFolderDateMillis(""))
    }

    @Test
    fun parseFolderDateMillisReturnsNullForInvalidCalendarDate() {
        // Lenient parsing is explicitly disabled -- month 13 must not silently roll over to
        // the next year's January.
        assertNull(parseFolderDateMillis("13_01_2026"))
        assertNull(parseFolderDateMillis("02_30_2026")) // no such day
    }

    @Test
    fun parseFolderDateMillisDistinctDatesProduceDistinctMillis() {
        val a = parseFolderDateMillis("01_01_2026")
        val b = parseFolderDateMillis("01_02_2026")
        assertNotNull(a)
        assertNotNull(b)
        assert(b!! > a!!)
    }
}
