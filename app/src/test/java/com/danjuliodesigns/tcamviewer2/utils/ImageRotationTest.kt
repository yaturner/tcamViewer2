package com.danjuliodesigns.tcamviewer2.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageRotationTest {

    private val width = 160
    private val height = 120

    @Test
    fun rotatedSize_0and180_keepOriginalDimensions() {
        assertEquals(width to height, rotatedSize(width, height, 0))
        assertEquals(width to height, rotatedSize(width, height, 180))
    }

    @Test
    fun rotatedSize_90and270_swapDimensions() {
        assertEquals(height to width, rotatedSize(width, height, 90))
        assertEquals(height to width, rotatedSize(width, height, 270))
    }

    @Test
    fun rotatePoint_0_isIdentity() {
        assertEquals(10f to 20f, rotatePoint(10f, 20f, width, height, 0))
    }

    @Test
    fun rotatePoint_90_matchesExpectedCorner() {
        // Top-left corner (0,0) of a WxH image rotated 90 clockwise lands at the top-right
        // of the HxW result, i.e. (height, 0).
        assertEquals(height.toFloat() to 0f, rotatePoint(0f, 0f, width, height, 90))
    }

    @Test
    fun rotatePoint_180_matchesExpectedCorner() {
        assertEquals(width.toFloat() to height.toFloat(), rotatePoint(0f, 0f, width, height, 180))
    }

    @Test
    fun rotatePoint_270_matchesExpectedCorner() {
        assertEquals(0f to width.toFloat(), rotatePoint(0f, 0f, width, height, 270))
    }

    @Test
    fun rotatePoint_negativeAndOver360_normalizeTheSameAsPositiveEquivalent() {
        assertEquals(rotatePoint(10f, 20f, width, height, 90), rotatePoint(10f, 20f, width, height, -270))
        assertEquals(rotatePoint(10f, 20f, width, height, 180), rotatePoint(10f, 20f, width, height, 540))
    }

    @Test
    fun unrotateDelta_matchesDifferenceOfTwoUnrotatedPoints_forAllAngles() {
        // A delta between two rotated-space points must equal the difference of their
        // unrotated (raw-space) counterparts -- this is what makes unrotateDelta correct for
        // a drag gesture's per-frame movement, which has no absolute position of its own.
        val p1 = 40f to 15f
        val p2 = 55f to 38f
        for (degrees in listOf(0, 90, 180, 270)) {
            val (ux1, uy1) = unrotatePoint(p1.first, p1.second, width, height, degrees)
            val (ux2, uy2) = unrotatePoint(p2.first, p2.second, width, height, degrees)
            val expectedDx = ux2 - ux1
            val expectedDy = uy2 - uy1
            val (dx, dy) = unrotateDelta(p2.first - p1.first, p2.second - p1.second, degrees)
            assertEquals("dx mismatch at $degrees deg", expectedDx, dx, 0.001f)
            assertEquals("dy mismatch at $degrees deg", expectedDy, dy, 0.001f)
        }
    }

    @Test
    fun unrotatePoint_isTrueInverseOfRotatePoint_forAllAngles() {
        val samples = listOf(0f to 0f, 10f to 20f, width.toFloat() to height.toFloat(), 77.5f to 33.25f)
        for (degrees in listOf(0, 90, 180, 270)) {
            for ((x, y) in samples) {
                val (rx, ry) = rotatePoint(x, y, width, height, degrees)
                val (rw, rh) = rotatedSize(width, height, degrees)
                val (ux, uy) = unrotatePoint(rx, ry, width, height, degrees)
                assertEquals("x round-trip failed at $degrees deg for ($x,$y)", x, ux, 0.001f)
                assertEquals("y round-trip failed at $degrees deg for ($x,$y)", y, uy, 0.001f)
                // Sanity: the rotated point must actually fall within the rotated frame's bounds.
                assertEquals(true, rx in 0f..rw.toFloat() && ry in 0f..rh.toFloat())
            }
        }
    }
}
