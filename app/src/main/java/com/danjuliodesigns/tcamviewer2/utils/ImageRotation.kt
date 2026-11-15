package com.danjuliodesigns.tcamviewer2.utils

/**
 * Pure geometry helpers for the Image Rotation setting. Rotation is applied only as a final
 * display transform (see CameraUtils' settingImageRotation / rotateForDisplay) -- raw sensor-space
 * data (imageData, spotmeterLocation, measurementRegion, temperature calculations, the real
 * spot-meter position sent to the camera) is never rotated. These helpers let the handful of
 * places that draw a raw-space Rect/point on screen, or convert a screen tap/drag back into raw
 * sensor coordinates, stay consistent with whatever rotateForDisplay actually did to the bitmap.
 */

/**
 * Size of a WxH raw-sensor-space image after rotating it clockwise by [rotationDegrees]
 * (0/90/180/270) for display.
 */
fun rotatedSize(width: Int, height: Int, rotationDegrees: Int): Pair<Int, Int> = if (rotationDegrees == 90 || rotationDegrees == 270) height to width else width to height

/**
 * Maps a point in raw sensor space (0..width, 0..height) to its position after rotating the
 * image clockwise by [rotationDegrees] for display -- same clockwise convention as
 * android.graphics.Matrix.postRotate, which is what actually rotates the bitmap pixels.
 */
fun rotatePoint(x: Float, y: Float, width: Int, height: Int, rotationDegrees: Int): Pair<Float, Float> = when (((rotationDegrees % 360) + 360) % 360) {
    90 -> (height - y) to x
    180 -> (width - x) to (height - y)
    270 -> y to (width - x)
    else -> x to y
}

/** Inverse of [rotatePoint] -- maps a point from the rotated/displayed frame back to raw sensor space. */
fun unrotatePoint(x: Float, y: Float, width: Int, height: Int, rotationDegrees: Int): Pair<Float, Float> = when (((rotationDegrees % 360) + 360) % 360) {
    90 -> y to (height - x)
    180 -> (width - x) to (height - y)
    270 -> (width - y) to x
    else -> x to y
}

/** Inverse of a delta/direction vector (e.g. a drag gesture's frame-to-frame movement) measured
 * in the rotated/displayed frame -- maps it back to the equivalent delta in raw sensor space.
 * Unlike [unrotatePoint], a delta has no absolute position, so this is a pure rotation with no
 * width/height-dependent translation term. */
fun unrotateDelta(dx: Float, dy: Float, rotationDegrees: Int): Pair<Float, Float> = when (((rotationDegrees % 360) + 360) % 360) {
    90 -> dy to -dx
    180 -> -dx to -dy
    270 -> -dy to dx
    else -> dx to dy
}
