package com.das.tcamviewer2.utils

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.das.tcamviewer2.constants.Constants

/**
 * Draws the fixed 4x4-camera-pixel hotspot/spotmeter square — matching the on-screen
 * [com.das.tcamviewer2.ui.SpotmeterOverlay] marker — onto a plain [android.graphics.Canvas],
 * for non-Compose contexts (image export, video export).
 *
 * [spotmeterRect] is in 160x120 camera pixel space. [imgWidthPx]/[imgHeightPx] is the
 * destination image size, assumed to fill that space with no letterboxing (a plain scale,
 * unlike the Compose overlay which has to handle aspect-fit letterboxing). [offsetX]/[offsetY]
 * shift the marker if the image doesn't start at the canvas origin (e.g. below a header).
 */
fun drawHotspotMarker(
    canvas: Canvas,
    spotmeterRect: Rect,
    imgWidthPx: Int,
    imgHeightPx: Int,
    offsetX: Float = 0f,
    offsetY: Float = 0f
) {
    val markerSizePx = 4f
    val sx = imgWidthPx / Constants.IMAGE_WIDTH.toFloat()
    val sy = imgHeightPx / Constants.IMAGE_HEIGHT.toFloat()
    val centerCol = (spotmeterRect.left + spotmeterRect.right) / 2f
    val centerRow = (spotmeterRect.top + spotmeterRect.bottom) / 2f
    val left = offsetX + (centerCol - markerSizePx / 2f) * sx
    val top = offsetY + (centerRow - markerSizePx / 2f) * sy
    val w = markerSizePx * sx
    val h = markerSizePx * sy
    val border = 3f

    val blackFill = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
    val whiteFill = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
    canvas.drawRect(left - border, top - border, left + w + border, top + h + border, blackFill)
    canvas.drawRect(left, top, left + w, top + h, whiteFill)
}
