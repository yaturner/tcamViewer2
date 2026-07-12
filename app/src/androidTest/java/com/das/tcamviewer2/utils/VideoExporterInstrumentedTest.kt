package com.das.tcamviewer2.utils

import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * End-to-end test of the real MediaCodec/MediaMuxer pipeline: encodes synthetic frames, then
 * reads the resulting MP4 back with [MediaExtractor] to confirm the muxed sample timestamps
 * actually match the intended per-frame durations — not just that [VideoExporter]'s internal
 * PTS math is right (see [VideoExporterTest]), but that MediaCodec on this device honors it.
 *
 * This mirrors a real bug caught during development: mixing getInputImage()/getInputBuffer()
 * on the same buffer index caused the hardware encoder to silently drop every frame (0-byte
 * output) without throwing. A frame-count/timing assertion here would have caught it.
 */
@RunWith(AndroidJUnit4::class)
class VideoExporterInstrumentedTest {

    // Real per-frame gaps (ms) from an actual .mtjsn recording used to debug this feature,
    // including a genuine large gap (772ms) and a clamped-fallback gap (125ms).
    private val frameDurationsMs = listOf(110L, 340L, 120L, 110L, 110L, 350L, 110L, 340L, 120L, 772L, 125L)

    @Test
    fun exportedMp4HasExpectedFrameCountAndTiming() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val width = VideoExporter.align16(160)
        val height = VideoExporter.align16(120)
        val frames = List(frameDurationsMs.size) { i -> solidColorBitmap(width, height, i) }
        val outFile = File(context.cacheDir, "video_exporter_test_${System.currentTimeMillis()}.mp4")

        val noSpotmeterRects = List<android.graphics.Rect?>(frameDurationsMs.size) { null }

        try {
            VideoExporter.exportMp4(frames, frameDurationsMs, noSpotmeterRects, outFile, width, height)

            assertTrue("Output file should exist", outFile.exists())
            assertTrue("Output file should be non-empty", outFile.length() > 0)

            val extractor = MediaExtractor()
            extractor.setDataSource(outFile.absolutePath)
            val trackIndex = (0 until extractor.trackCount).first { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            }
            val trackFormat = extractor.getTrackFormat(trackIndex)
            extractor.selectTrack(trackIndex)

            val presentationTimesUs = mutableListOf<Long>()
            while (true) {
                val sampleTime = extractor.sampleTime
                if (sampleTime < 0) break
                presentationTimesUs.add(sampleTime)
                if (!extractor.advance()) break
            }
            extractor.release()

            assertEquals(
                "Sample count must match input frame count",
                frameDurationsMs.size, presentationTimesUs.size
            )

            // Sort defensively: sample iteration order matches presentation order for this
            // simple (no B-frame) encode, but the assertion only cares that the *set* of
            // timestamps is right, not the specific traversal order.
            val actualSorted = presentationTimesUs.sorted()
            val expectedSorted = VideoExporter.computeCumulativePtsUs(frameDurationsMs).toList().sorted()
            assertEquals(expectedSorted, actualSorted)

            // The container's reported track duration is last-sample-pts + an *estimated*
            // duration for that final sample (there's no "next" timestamp to derive it from),
            // so it legitimately overshoots the sum of intended per-frame durations by up to
            // about one frame's worth. Per-sample timing is already verified exactly above;
            // this is just a sanity check that the overall duration is in the right ballpark.
            val expectedTotalUs = frameDurationsMs.sum() * 1000L
            val trackDurationUs = trackFormat.getLong(MediaFormat.KEY_DURATION)
            val toleranceUs = (frameDurationsMs.max() + 200) * 1000L
            assertTrue(
                "Track duration ($trackDurationUs us) should be close to expected total ($expectedTotalUs us)",
                kotlin.math.abs(trackDurationUs - expectedTotalUs) < toleranceUs
            )
        } finally {
            outFile.delete()
            frames.forEach { it.recycle() }
        }
    }

    private fun solidColorBitmap(width: Int, height: Int, seed: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val colors = intArrayOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.CYAN)
        bitmap.eraseColor(colors[seed % colors.size])
        return bitmap
    }
}
