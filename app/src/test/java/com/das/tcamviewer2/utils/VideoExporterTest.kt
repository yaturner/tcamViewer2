package com.das.tcamviewer2.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoExporterTest {

    // --- align16 ---

    @Test
    fun align16RoundsUpToNextMultipleOf16() {
        assertEquals(16, VideoExporter.align16(1))
        assertEquals(16, VideoExporter.align16(15))
        assertEquals(16, VideoExporter.align16(16))
        assertEquals(32, VideoExporter.align16(17))
        assertEquals(240, VideoExporter.align16(240))
        assertEquals(128, VideoExporter.align16(120))
        assertEquals(368, VideoExporter.align16(360))
    }

    @Test
    fun align16OfZeroIsZero() {
        assertEquals(0, VideoExporter.align16(0))
    }

    // --- computeCumulativePtsUs ---
    //
    // This is the exact math whose consumer (VideoPlayerWindow's frameIntervals) had a real
    // parsing bug found while debugging this feature: a 2-digit fractional-seconds timestamp
    // like "8:17:41.48" is 480ms, not 48ms. That bug lived in the timestamp *parser*, not here,
    // but these tests lock down the PTS accumulation this function is responsible for so a
    // similar mistake in this half of the pipeline would be caught immediately.

    @Test
    fun cumulativePtsStartsAtZero() {
        val pts = VideoExporter.computeCumulativePtsUs(listOf(110L, 340L, 120L))
        assertEquals(0L, pts[0])
    }

    @Test
    fun cumulativePtsIsRunningSumOfPriorDurationsInMicroseconds() {
        val pts = VideoExporter.computeCumulativePtsUs(listOf(110L, 340L, 120L, 110L))
        assertEquals(0L, pts[0])
        assertEquals(110_000L, pts[1])
        assertEquals(450_000L, pts[2])   // 110 + 340
        assertEquals(570_000L, pts[3])   // 110 + 340 + 120
    }

    @Test
    fun cumulativePtsMatchesKnownRealCaptureSequence() {
        // Real per-frame gaps (ms) pulled from an actual .mtjsn recording used to debug this
        // feature, including a genuine large gap (772ms) and a clamped-fallback gap (125ms)
        // from a non-monotonic source timestamp.
        val durations = listOf(110L, 340L, 120L, 110L, 110L, 350L, 110L, 340L, 120L, 772L, 125L)
        val pts = VideoExporter.computeCumulativePtsUs(durations)
        assertEquals(11, pts.size)
        assertEquals(0L, pts[0])
        assertEquals(1_710_000L, pts[9])   // sum of first 9 durations = 1710ms
        assertEquals(2_482_000L, pts[10])  // + 772ms
    }

    @Test
    fun cumulativePtsOfSingleFrameIsZero() {
        val pts = VideoExporter.computeCumulativePtsUs(listOf(500L))
        assertEquals(1, pts.size)
        assertEquals(0L, pts[0])
    }

    @Test
    fun cumulativePtsOfEmptyListIsEmpty() {
        val pts = VideoExporter.computeCumulativePtsUs(emptyList())
        assertEquals(0, pts.size)
    }

    // --- computeFps ---

    @Test
    fun computeFpsAveragesFrameCountOverDuration() {
        // 327 frames over 92080ms ≈ 3.55fps → truncated to 3
        assertEquals(3, VideoExporter.computeFps(327, 92_080L))
    }

    @Test
    fun computeFpsClampsToAtLeastOne() {
        // 1 frame over a huge duration would compute to 0fps otherwise
        assertEquals(1, VideoExporter.computeFps(1, 60_000L))
    }

    @Test
    fun computeFpsClampsToAtMostThirty() {
        // 100 frames in 100ms would compute to 1000fps otherwise
        assertEquals(30, VideoExporter.computeFps(100, 100L))
    }

    @Test
    fun computeFpsHandlesZeroDurationWithoutDividingByZero() {
        // coerceAtLeast(1L) on the duration guards against a single-frame, zero-duration input
        assertEquals(30, VideoExporter.computeFps(5, 0L))
    }

    // --- computeBitRate ---

    @Test
    fun computeBitRateScalesWithResolutionAndFps() {
        assertEquals(320 * 240 * 8 * 4, VideoExporter.computeBitRate(320, 240, 8))
    }

    @Test
    fun computeBitRateIsZeroAtZeroFps() {
        assertEquals(0, VideoExporter.computeBitRate(320, 240, 0))
    }
}
