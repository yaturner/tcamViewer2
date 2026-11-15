package com.danjuliodesigns.tcamviewer2.ui

import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.danjuliodesigns.tcamviewer2.constants.Constants
import com.danjuliodesigns.tcamviewer2.utils.rotatePoint
import com.danjuliodesigns.tcamviewer2.utils.rotatedSize

/** Fixed marker size, in camera pixels (160x120 space), regardless of the reported rect's own size. */
private const val MARKER_SIZE_PX = 4f

/**
 * Draws a fixed-size 4x4 camera-pixel hotspot/spotmeter square, centered on [spotmeterRect]
 * (always in raw, unrotated sensor space -- see utils/ImageRotation.kt), over a thermal image
 * shown with ContentScale.Fit, mapping into the letterboxed image bounds. [rotationDegrees] is
 * the current Image Rotation setting; the marker's position is rotated to match whatever
 * CameraUtils.rotateForDisplay() already did to the bitmap itself.
 */
@Composable
fun SpotmeterOverlay(spotmeterRect: Rect?, rotationDegrees: Int = 0, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        spotmeterRect?.let { rect ->
            val (dispWidth, dispHeight) = rotatedSize(Constants.IMAGE_WIDTH, Constants.IMAGE_HEIGHT, rotationDegrees)
            val imgAspect = dispWidth.toFloat() / dispHeight
            val boxAspect = size.width / size.height
            val fittedW: Float
            val fittedH: Float
            if (boxAspect > imgAspect) {
                fittedH = size.height
                fittedW = fittedH * imgAspect
            } else {
                fittedW = size.width
                fittedH = fittedW / imgAspect
            }
            val offsetX = (size.width - fittedW) / 2f
            val offsetY = (size.height - fittedH) / 2f
            val sx = fittedW / dispWidth
            val sy = fittedH / dispHeight
            val (centerCol, centerRow) = rotatePoint(
                (rect.left + rect.right) / 2f,
                (rect.top + rect.bottom) / 2f,
                Constants.IMAGE_WIDTH,
                Constants.IMAGE_HEIGHT,
                rotationDegrees,
            )
            val left = offsetX + (centerCol - MARKER_SIZE_PX / 2f) * sx
            val top = offsetY + (centerRow - MARKER_SIZE_PX / 2f) * sy
            val w = MARKER_SIZE_PX * sx
            val h = MARKER_SIZE_PX * sy
            // Hollow square: a thicker black outline with a thinner white outline drawn on top of
            // the same path, so black shows on both sides of the white line for visibility on any palette
            val topLeft = Offset(left, top)
            val squareSize = Size(w, h)
            drawRect(
                color = Color.Black,
                topLeft = topLeft,
                size = squareSize,
                style = Stroke(width = 3.dp.toPx()),
            )
            drawRect(
                color = Color.White,
                topLeft = topLeft,
                size = squareSize,
                style = Stroke(width = 1.dp.toPx()),
            )
        }
    }
}
