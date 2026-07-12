package com.das.tcamviewer2.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Encodes a sequence of thermal-frame bitmaps into an H.264 MP4 file.
 *
 * [frameDurationsMs] holds one entry per frame (its on-screen duration in
 * milliseconds, e.g. from playback frame intervals). Frames are muxed with
 * cumulative presentation timestamps derived from these durations, so the
 * resulting file's timing matches how the recording actually played back
 * instead of baking in a fixed frame rate.
 *
 * [spotmeterRects] holds one entry per frame (nullable, in 160x120 camera pixel
 * space) — when present, the hotspot marker is burned into that frame before
 * encoding, matching what the live/library views draw on top of the image.
 */
object VideoExporter {
    private const val TIMEOUT_US = 10_000L
    private const val BITS_PER_PIXEL_PER_SECOND = 4

    suspend fun exportMp4(
        frames: List<Bitmap>,
        frameDurationsMs: List<Long>,
        spotmeterRects: List<Rect?>,
        outputFile: File,
        width: Int,
        height: Int
    ) = withContext(Dispatchers.Default) {
        require(frames.isNotEmpty()) { "No frames to export" }
        require(frames.size == frameDurationsMs.size) { "Frame/duration count mismatch" }
        require(frames.size == spotmeterRects.size) { "Frame/spotmeter count mismatch" }

        val fps = computeFps(frames.size, frameDurationsMs.sum())
        val bitRate = computeBitRate(width, height, fps)

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        // Prefer the software AVC encoder: vendor hardware encoders on some devices don't
        // reliably honor arbitrary (non-real-time-paced) presentationTimeUs values on input,
        // silently drifting the muxed timing — exactly what "correct timing" needs to avoid.
        val codec = createSoftwareAvcEncoder()
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerTrack = -1
        var muxerStarted = false

        val ptsUs = computeCumulativePtsUs(frameDurationsMs)
        val accMs = frameDurationsMs.sum()

        val bufferInfo = MediaCodec.BufferInfo()
        var frameIndex = 0
        var inputDone = false
        var outputDone = false

        try {
            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        if (frameIndex < frames.size) {
                            val scaled = Bitmap.createScaledBitmap(frames[frameIndex], width, height, true)
                            spotmeterRects[frameIndex]?.let { rect ->
                                drawHotspotMarker(Canvas(scaled), rect, width, height)
                            }
                            val image = codec.getInputImage(inIndex)
                            if (image != null) {
                                writeBitmapToYuvImage(scaled, image, width, height)
                                codec.queueInputBuffer(inIndex, 0, width * height * 3 / 2, ptsUs[frameIndex], 0)
                            } else {
                                codec.queueInputBuffer(inIndex, 0, 0, ptsUs[frameIndex], 0)
                            }
                            if (scaled !== frames[frameIndex]) scaled.recycle()
                            frameIndex++
                        } else {
                            codec.queueInputBuffer(inIndex, 0, 0, accMs * 1000L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                when {
                    outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> { /* no output ready yet */ }
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        check(!muxerStarted) { "Encoder output format changed more than once" }
                        muxerTrack = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    outIndex >= 0 -> {
                        val encodedData = codec.getOutputBuffer(outIndex)
                        if (encodedData != null && bufferInfo.size != 0 && muxerStarted) {
                            encodedData.position(bufferInfo.offset)
                            encodedData.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(muxerTrack, encodedData, bufferInfo)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }
                }
            }
        } finally {
            codec.stop()
            codec.release()
            if (muxerStarted) muxer.stop()
            muxer.release()
        }
    }

    /** Writes an ARGB bitmap into a flexible YUV420 [Image] (BT.601 full-range), sized to match. */
    private fun writeBitmapToYuvImage(bitmap: Bitmap, image: Image, width: Int, height: Int) {
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val yBuf = yPlane.buffer
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer
        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        val uRowStride = uPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vRowStride = vPlane.rowStride
        val vPixelStride = vPlane.pixelStride

        for (row in 0 until height) {
            val rowBase = row * width
            for (col in 0 until width) {
                val argb = pixels[rowBase + col]
                val r = (argb shr 16) and 0xFF
                val g = (argb shr 8) and 0xFF
                val b = argb and 0xFF
                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yBuf.put(row * yRowStride + col * yPixelStride, y.coerceIn(0, 255).toByte())

                if (row % 2 == 0 && col % 2 == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    val uvRow = row / 2
                    val uvCol = col / 2
                    uBuf.put(uvRow * uRowStride + uvCol * uPixelStride, u.coerceIn(0, 255).toByte())
                    vBuf.put(uvRow * vRowStride + uvCol * vPixelStride, v.coerceIn(0, 255).toByte())
                }
            }
        }
    }

    /** Rounds up to the nearest multiple of 16 — many hardware encoders require macroblock-aligned dimensions. */
    fun align16(n: Int): Int = ((n + 15) / 16) * 16

    /**
     * Cumulative presentation timestamp (in microseconds) for each frame: frame i begins
     * showing at the sum of the durations of all frames before it. This is what makes the
     * muxed file's timing match real per-frame capture gaps instead of a fixed frame rate.
     */
    internal fun computeCumulativePtsUs(frameDurationsMs: List<Long>): LongArray {
        val ptsUs = LongArray(frameDurationsMs.size)
        var accMs = 0L
        for (i in frameDurationsMs.indices) {
            ptsUs[i] = accMs * 1000L
            accMs += frameDurationsMs[i]
        }
        return ptsUs
    }

    /** Average fps implied by [frameCount] frames spanning [totalDurationMs], clamped to a sane encoder range. */
    internal fun computeFps(frameCount: Int, totalDurationMs: Long): Int =
        (frameCount * 1000L / totalDurationMs.coerceAtLeast(1L)).toInt().coerceIn(1, 30)

    /** A simple resolution/frame-rate-scaled bitrate target; not critical to correctness. */
    internal fun computeBitRate(width: Int, height: Int, fps: Int): Int =
        width * height * fps * BITS_PER_PIXEL_PER_SECOND

    /**
     * Picks the AOSP software AVC encoder by name (present on all modern Android devices as the
     * guaranteed software fallback), falling back to whatever the platform prefers by default if
     * neither known name is found.
     */
    private fun createSoftwareAvcEncoder(): MediaCodec {
        for (name in arrayOf("c2.android.avc.encoder", "OMX.google.h264.encoder")) {
            runCatching { return MediaCodec.createByCodecName(name) }
        }
        return MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
    }
}
