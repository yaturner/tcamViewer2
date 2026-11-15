package com.danjuliodesigns.tcamviewer2.utils

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.danjuliodesigns.tcamviewer2.constants.Constants

/**
 * Draws the fixed 4x4-camera-pixel hotspot/spotmeter square — matching the on-screen
 * [com.danjuliodesigns.tcamviewer2.ui.SpotmeterOverlay] marker — onto a plain [android.graphics.Canvas],
 * for non-Compose contexts (image export, video export).
 *
 * [spotmeterRect] is in 160x120 camera pixel space (raw, unrotated sensor space -- see
 * ImageRotation.kt). [imgWidthPx]/[imgHeightPx] is the destination image size (already rotated,
 * if [rotationDegrees] is non-zero), assumed to fill that space with no letterboxing (a plain
 * scale, unlike the Compose overlay which has to handle aspect-fit letterboxing). [offsetX]/
 * [offsetY] shift the marker if the image doesn't start at the canvas origin (e.g. below a header).
 */
fun drawHotspotMarker(
    canvas: Canvas,
    spotmeterRect: Rect,
    imgWidthPx: Int,
    imgHeightPx: Int,
    rotationDegrees: Int = 0,
    offsetX: Float = 0f,
    offsetY: Float = 0f,
) {
    val markerSizePx = 4f
    val (dispWidth, dispHeight) = rotatedSize(Constants.IMAGE_WIDTH, Constants.IMAGE_HEIGHT, rotationDegrees)
    val sx = imgWidthPx / dispWidth.toFloat()
    val sy = imgHeightPx / dispHeight.toFloat()
    val (centerCol, centerRow) = rotatePoint(
        (spotmeterRect.left + spotmeterRect.right) / 2f,
        (spotmeterRect.top + spotmeterRect.bottom) / 2f,
        Constants.IMAGE_WIDTH,
        Constants.IMAGE_HEIGHT,
        rotationDegrees,
    )
    val left = offsetX + (centerCol - markerSizePx / 2f) * sx
    val top = offsetY + (centerRow - markerSizePx / 2f) * sy
    val w = markerSizePx * sx
    val h = markerSizePx * sy

    // Hollow square: a thicker black outline with a thinner white outline drawn on top of the
    // same rect, so black shows on both sides of the white line for visibility on any palette
    val blackStroke =
        Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
    val whiteStroke =
        Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
    canvas.drawRect(left, top, left + w, top + h, blackStroke)
    canvas.drawRect(left, top, left + w, top + h, whiteStroke)
}
