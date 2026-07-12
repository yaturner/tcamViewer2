package com.das.tcamviewer2.ui

import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.das.tcamviewer2.constants.Constants

/** Fixed marker size, in camera pixels (160x120 space), regardless of the reported rect's own size. */
private const val MARKER_SIZE_PX = 4f

/**
 * Draws a fixed-size 4x4 camera-pixel hotspot/spotmeter square, centered on [spotmeterRect],
 * over a thermal image shown with ContentScale.Fit, mapping into the letterboxed image bounds.
 */
@Composable
fun SpotmeterOverlay(spotmeterRect: Rect?, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        spotmeterRect?.let { rect ->
            val imgAspect = Constants.IMAGE_WIDTH.toFloat() / Constants.IMAGE_HEIGHT
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
            val sx = fittedW / Constants.IMAGE_WIDTH
            val sy = fittedH / Constants.IMAGE_HEIGHT
            val centerCol = (rect.left + rect.right) / 2f
            val centerRow = (rect.top + rect.bottom) / 2f
            val left = offsetX + (centerCol - MARKER_SIZE_PX / 2f) * sx
            val top = offsetY + (centerRow - MARKER_SIZE_PX / 2f) * sy
            val w = MARKER_SIZE_PX * sx
            val h = MARKER_SIZE_PX * sy
            // Solid black border behind a solid white square for visibility on any palette
            val border = 1.dp.toPx()
            drawRect(
                color = Color.Black,
                topLeft = Offset(left - border, top - border),
                size = Size(w + border * 2, h + border * 2)
            )
            drawRect(
                color = Color.White,
                topLeft = Offset(left, top),
                size = Size(w, h)
            )
        }
    }
}
