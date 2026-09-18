package com.danjuliodesigns.tcamviewer2.model

import android.graphics.Rect
import com.danjuliodesigns.tcamviewer2.constants.Constants
import org.junit.Assert.assertEquals
import org.junit.Test

class CameraViewModelHelpersTest {
    // android.graphics.Rect's 4-arg constructor is a stub under plain JVM unit tests (it
    // silently no-ops and leaves left/top/right/bottom at 0 instead of throwing or setting
    // them) — only the no-arg constructor + direct field assignment reliably works here, so
    // every Rect used in these tests must go through this helper rather than `Rect(l, t, r, b)`.
    private fun rectOf(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ): Rect = Rect().apply {
        this.left = left
        this.top = top
        this.right = right
        this.bottom = bottom
    }

    // --- formatTemp ---

    @Test
    fun formatTempCelsiusLowResolution() {
        // rawValue=27998, scale=10 -> 2799.8 - 273.15 = 2526.65... use scale=100 to match app usage
        // (rawValue/scale - 273.15), scale=100: 27998/100 - 273.15 = 6.83 -> "6.8°C"
        val (value, text) = formatTemp(27998, 100f, isCelsius = true)
        assertEquals(6.83f, value, 0.01f)
        assertEquals("6.8°C", text)
    }

    @Test
    fun formatTempCelsiusHighResolutionAtZero() {
        // 27315/100 - 273.15 = 0.0
        val (value, text) = formatTemp(27315, 100f, isCelsius = true)
        assertEquals(0.0f, value, 0.01f)
        assertEquals("0.0°C", text)
    }

    @Test
    fun formatTempFahrenheitConvertsFromCelsius() {
        // 37315/100 - 273.15 = 100.0C -> 212.0F
        val (value, text) = formatTemp(37315, 100f, isCelsius = false)
        assertEquals(212.0f, value, 0.01f)
        assertEquals("212.0°F", text)
    }

    @Test
    fun formatTempHandlesNegativeValues() {
        // -10C: (263.15 * 100) = 26315 raw
        val (value, text) = formatTemp(26315, 100f, isCelsius = true)
        assertEquals(-10.0f, value, 0.01f)
        assertEquals("-10.0°C", text)
    }

    // --- formatManualBound ---

    @Test
    fun formatManualBoundCelsius() {
        assertEquals("30.0°C", formatManualBound(30f, isCelsius = true))
    }

    @Test
    fun formatManualBoundFahrenheit() {
        assertEquals("86.0°F", formatManualBound(86f, isCelsius = false))
    }

    @Test
    fun formatManualBoundNegativeValue() {
        assertEquals("-273.0°C", formatManualBound(-273f, isCelsius = true))
    }

    @Test
    fun formatManualBoundRoundsToOneDecimal() {
        // 30.26f is unambiguously closer to 30.3 than 30.2, avoiding the float-precision
        // ambiguity of an exact X.X5 boundary value (e.g. 30.05f isn't exactly representable).
        assertEquals("30.3°C", formatManualBound(30.26f, isCelsius = true))
    }

    // --- calcSpotTemp ---
    // imageData is a 160x120 flat array (Constants.IMAGE_WIDTH x IMAGE_HEIGHT); calcSpotTemp
    // averages a small neighborhood around (cx, cy).

    private fun flatImageOf(value: Int): IntArray = IntArray(Constants.IMAGE_WIDTH * Constants.IMAGE_HEIGHT) { value }

    @Test
    fun calcSpotTempAveragesUniformNeighborhood() {
        val data = flatImageOf(27315) // 0.0C everywhere
        val (value, text) = calcSpotTemp(data, cx = 80, cy = 60, scale = 100f, isCelsius = true)
        assertEquals(0.0f, value, 0.01f)
        assertEquals("0.0°C", text)
    }

    @Test
    fun calcSpotTempAveragesNonUniformNeighborhood() {
        val data = flatImageOf(27315)
        // Set the 2x2 neighborhood around (80,60) -- calcSpotTemp reads (cx..cx+1, cy..cy+1) --
        // to a different, known value so the average is verifiably different from the background.
        val idx = 60 * Constants.IMAGE_WIDTH + 80
        data[idx] = 37315 // 100.0C
        val (value, _) = calcSpotTemp(data, cx = 80, cy = 60, scale = 100f, isCelsius = true)
        // Average of {37315, 27315, 27315, 27315} = 29815 -> 298.15 - 273.15 = 25.0C
        assertEquals(25.0f, value, 0.01f)
    }

    @Test
    fun calcSpotTempClampsCoordinatesAtImageEdges() {
        val data = flatImageOf(27315)
        // Bottom-right corner and beyond -- must not throw an index-out-of-bounds
        val (value, _) = calcSpotTemp(data, cx = Constants.IMAGE_WIDTH + 50, cy = Constants.IMAGE_HEIGHT + 50, scale = 100f, isCelsius = true)
        assertEquals(0.0f, value, 0.01f)
    }

    @Test
    fun calcSpotTempClampsNegativeCoordinates() {
        val data = flatImageOf(27315)
        val (value, _) = calcSpotTemp(data, cx = -1, cy = -1, scale = 100f, isCelsius = true)
        assertEquals(0.0f, value, 0.01f)
    }

    @Test
    fun calcSpotTempOnLargeNegativeCoordinatesReturnsZeroCountFallback() {
        // Documents an existing edge case rather than asserting "correct" behavior: cx/cy only
        // has its *lower* bound clamped via coerceIn(0, ...); (cx+1) is coerced only at the
        // *upper* end (coerceAtMost), so a large negative cx/cy produces c1=0 but c2 still
        // negative — an empty "0..negative" range, count=0, and calcSpotTemp's 0-raw-value
        // fallback formats as -273.15°C rather than clamping to a real pixel. Not reachable from
        // the UI (touch coordinates are always within the displayed image), but worth pinning so
        // a future refactor doesn't silently change this fallback's behavior.
        val data = flatImageOf(27315)
        val (value, text) = calcSpotTemp(data, cx = -10, cy = -10, scale = 100f, isCelsius = true)
        assertEquals(-273.15f, value, 0.01f)
        assertEquals("-273.1°C", text)
    }

    // --- calcRegionStats ---

    @Test
    fun calcRegionStatsComputesAvgMinMaxOverRegion() {
        val data = flatImageOf(27315) // baseline 0.0C
        // A 4x4 region with one hot pixel and one cold pixel
        val rect = rectOf(10, 10, 13, 13) // left,top,right,bottom (inclusive per calcRegionStats' coerceIn usage)
        val hotIdx = 11 * Constants.IMAGE_WIDTH + 11
        val coldIdx = 12 * Constants.IMAGE_WIDTH + 12
        data[hotIdx] = 37315 // 100.0C
        data[coldIdx] = 17315 // -100.0C
        val (avg, min, max) = calcRegionStats(data, rect, scale = 100f, isCelsius = true)
        assertEquals(-100.0f, min.first, 0.01f)
        assertEquals(100.0f, max.first, 0.01f)
        assertEquals("-100.0°C", min.second)
        assertEquals("100.0°C", max.second)
        // avg should be strictly between min and max
        assert(avg.first in min.first..max.first)
    }

    @Test
    fun calcRegionStatsUniformRegionHasEqualAvgMinMax() {
        val data = flatImageOf(27315)
        val rect = rectOf(5, 5, 8, 8)
        val (avg, min, max) = calcRegionStats(data, rect, scale = 100f, isCelsius = true)
        assertEquals(0.0f, avg.first, 0.01f)
        assertEquals(0.0f, min.first, 0.01f)
        assertEquals(0.0f, max.first, 0.01f)
    }

    @Test
    fun calcRegionStatsClampsRegionToImageBounds() {
        val data = flatImageOf(27315)
        // Region far outside the image on every edge -- must not throw
        val rect = rectOf(-100, -100, Constants.IMAGE_WIDTH + 100, Constants.IMAGE_HEIGHT + 100)
        val (avg, _, _) = calcRegionStats(data, rect, scale = 100f, isCelsius = true)
        assertEquals(0.0f, avg.first, 0.01f)
    }

    @Test
    fun calcRegionStatsFahrenheitConversion() {
        val data = flatImageOf(37315) // 100.0C everywhere -> 212.0F
        val rect = rectOf(0, 0, 2, 2)
        val (avg, min, max) = calcRegionStats(data, rect, scale = 100f, isCelsius = false)
        assertEquals(212.0f, avg.first, 0.01f)
        assertEquals("212.0°F", min.second)
        assertEquals("212.0°F", max.second)
    }

    // --- buildFooterJson ---

    @Test
    fun buildFooterJsonContainsExpectedFields() {
        val json = buildFooterJson(startMs = 0L, endMs = 1000L, numFrames = 42)
        assert(json.contains("\"num_frames\":42")) { "Expected num_frames field in: $json" }
        assert(json.contains("\"version\":1")) { "Expected version field in: $json" }
        assert(json.startsWith("{\"video_info\":{")) { "Expected video_info wrapper in: $json" }
    }

    @Test
    fun buildFooterJsonIsValidJsonShape() {
        // Sanity: parseable by org.json without throwing
        val json = buildFooterJson(startMs = 1_700_000_000_000L, endMs = 1_700_000_010_000L, numFrames = 5)
        val parsed = org.json.JSONObject(json)
        val videoInfo = parsed.getJSONObject("video_info")
        assertEquals(5, videoInfo.getInt("num_frames"))
        assertEquals(1, videoInfo.getInt("version"))
    }

    @Test
    fun buildFooterJsonHandlesZeroFrames() {
        val json = buildFooterJson(startMs = 0L, endMs = 0L, numFrames = 0)
        val parsed = org.json.JSONObject(json)
        assertEquals(0, parsed.getJSONObject("video_info").getInt("num_frames"))
    }
}
