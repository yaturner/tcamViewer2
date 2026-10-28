package com.danjuliodesigns.tcamviewer2.ui

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class CameraScreenHelpersTest {
    // android.graphics.Rect's 4-arg constructor is a stub under plain JVM unit tests (silently
    // leaves left/top/right/bottom at 0 instead of setting them) — only the no-arg constructor +
    // direct field assignment reliably works here, so every Rect in these tests goes through
    // this helper rather than `Rect(l, t, r, b)`.
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

    // --- colorBarMarkerFraction (issues #12, #54) ---

    @Test
    fun colorBarMarkerFraction_spotAtMaxPegsToTop() {
        // 0 = top/max per the function's own contract
        assertEquals(0f, colorBarMarkerFraction(spot = 30f, max = 30f, min = 10f)!!, 0.001f)
    }

    @Test
    fun colorBarMarkerFraction_spotAtMinPegsToBottom() {
        assertEquals(1f, colorBarMarkerFraction(spot = 10f, max = 30f, min = 10f)!!, 0.001f)
    }

    @Test
    fun colorBarMarkerFraction_spotAtMidpoint() {
        assertEquals(0.5f, colorBarMarkerFraction(spot = 20f, max = 30f, min = 10f)!!, 0.001f)
    }

    @Test
    fun colorBarMarkerFraction_spotAboveMaxClampsToTopInsteadOfExtrapolating() {
        // issue #54: in Manual mode, a spot reading above the manual max previously computed a
        // negative fraction and floated somewhere in the middle instead of pegging to the end.
        assertEquals(0f, colorBarMarkerFraction(spot = 45f, max = 30f, min = 10f)!!, 0.001f)
    }

    @Test
    fun colorBarMarkerFraction_spotBelowMinClampsToBottom() {
        assertEquals(1f, colorBarMarkerFraction(spot = 2f, max = 30f, min = 10f)!!, 0.001f)
    }

    @Test
    fun colorBarMarkerFraction_nullWhenAnyInputMissing() {
        assertEquals(null, colorBarMarkerFraction(spot = null, max = 30f, min = 10f))
        assertEquals(null, colorBarMarkerFraction(spot = 20f, max = null, min = 10f))
        assertEquals(null, colorBarMarkerFraction(spot = 20f, max = 30f, min = null))
    }

    @Test
    fun colorBarMarkerFraction_nullWhenMaxEqualsMin() {
        // would otherwise divide by zero
        assertEquals(null, colorBarMarkerFraction(spot = 20f, max = 20f, min = 20f))
    }

    // --- niceAxisStep ---

    @Test
    fun niceAxisStepRoundsToOneTwoOrFiveTimesPowerOfTen() {
        // range=50, targetTicks=5 -> rawStep=10 -> already a "nice" value
        assertEquals(10f, niceAxisStep(50f, targetTicks = 5), 0.001f)
    }

    @Test
    fun niceAxisStepRoundsUpNonNiceRawStep() {
        // range=37, targetTicks=5 -> rawStep=7.4 -> magnitude=1 -> residual=7.4 -> rounds to 10
        assertEquals(10f, niceAxisStep(37f, targetTicks = 5), 0.001f)
    }

    @Test
    fun niceAxisStepHandlesSmallResiduals() {
        // range=8, targetTicks=5 -> rawStep=1.6 -> magnitude=1 -> residual=1.6 -> rounds to 2
        assertEquals(2f, niceAxisStep(8f, targetTicks = 5), 0.001f)
    }

    @Test
    fun niceAxisStepFallsBackToOneForZeroOrNegativeRange() {
        assertEquals(niceAxisStep(1f, targetTicks = 5), niceAxisStep(0f, targetTicks = 5), 0.001f)
        assertEquals(niceAxisStep(1f, targetTicks = 5), niceAxisStep(-5f, targetTicks = 5), 0.001f)
    }

    @Test
    fun niceAxisStepHandlesLargeRanges() {
        // range=50000, targetTicks=5 -> rawStep=10000 -> already nice
        assertEquals(10000f, niceAxisStep(50000f, targetTicks = 5), 0.001f)
    }

    @Test
    fun niceAxisStepRespectsCustomTargetTicks() {
        // range=100, targetTicks=1 -> rawStep=100 -> already nice
        assertEquals(100f, niceAxisStep(100f, targetTicks = 1), 0.001f)
    }

    // --- resolveRegionDragTarget ---

    @Test
    fun resolveRegionDragTargetDetectsExactCorners() {
        val region = rectOf(10, 10, 50, 50)
        assertEquals(RegionDragTarget.TOP_LEFT, resolveRegionDragTarget(region, 10f, 10f))
        assertEquals(RegionDragTarget.TOP_RIGHT, resolveRegionDragTarget(region, 50f, 10f))
        assertEquals(RegionDragTarget.BOTTOM_LEFT, resolveRegionDragTarget(region, 10f, 50f))
        assertEquals(RegionDragTarget.BOTTOM_RIGHT, resolveRegionDragTarget(region, 50f, 50f))
    }

    @Test
    fun resolveRegionDragTargetDetectsCornersWithinHitRadius() {
        val region = rectOf(10, 10, 50, 50)
        // 8px away (within the 10px REGION_HANDLE_HIT_PX radius) from top-left
        assertEquals(RegionDragTarget.TOP_LEFT, resolveRegionDragTarget(region, 15f, 15f))
    }

    @Test
    fun resolveRegionDragTargetReturnsMoveInsideBoxAwayFromCorners() {
        val region = rectOf(10, 10, 50, 50)
        assertEquals(RegionDragTarget.MOVE, resolveRegionDragTarget(region, 30f, 30f))
    }

    @Test
    fun resolveRegionDragTargetReturnsMoveOnBoundaryEdgeNotNearACorner() {
        val region = rectOf(10, 10, 50, 50)
        // Middle of the top edge -- inside the inclusive box bounds, but not within hit radius
        // of any corner.
        assertEquals(RegionDragTarget.MOVE, resolveRegionDragTarget(region, 30f, 10f))
    }

    @Test
    fun resolveRegionDragTargetReturnsNoneOutsideBox() {
        val region = rectOf(10, 10, 50, 50)
        assertEquals(RegionDragTarget.NONE, resolveRegionDragTarget(region, 100f, 100f))
        assertEquals(RegionDragTarget.NONE, resolveRegionDragTarget(region, -5f, -5f))
    }

    // --- applyRegionDrag ---

    @Test
    fun applyRegionDragMoveShiftsAllFourEdgesEqually() {
        val region = rectOf(10, 10, 50, 50)
        val result = applyRegionDrag(region, RegionDragTarget.MOVE, dx = 5f, dy = -3f)
        assertEquals(15, result.left)
        assertEquals(55, result.right)
        assertEquals(7, result.top)
        assertEquals(47, result.bottom)
    }

    @Test
    fun applyRegionDragTopLeftOnlyMovesTopAndLeftEdges() {
        val region = rectOf(10, 10, 50, 50)
        val result = applyRegionDrag(region, RegionDragTarget.TOP_LEFT, dx = 4f, dy = 6f)
        assertEquals(14, result.left)
        assertEquals(16, result.top)
        assertEquals(50, result.right) // unchanged
        assertEquals(50, result.bottom) // unchanged
    }

    @Test
    fun applyRegionDragBottomRightOnlyMovesBottomAndRightEdges() {
        val region = rectOf(10, 10, 50, 50)
        val result = applyRegionDrag(region, RegionDragTarget.BOTTOM_RIGHT, dx = -4f, dy = -6f)
        assertEquals(10, result.left) // unchanged
        assertEquals(10, result.top) // unchanged
        assertEquals(46, result.right)
        assertEquals(44, result.bottom)
    }

    @Test
    fun applyRegionDragTopRightOnlyMovesTopAndRightEdges() {
        val region = rectOf(10, 10, 50, 50)
        val result = applyRegionDrag(region, RegionDragTarget.TOP_RIGHT, dx = 4f, dy = 6f)
        assertEquals(10, result.left) // unchanged
        assertEquals(16, result.top)
        assertEquals(54, result.right)
        assertEquals(50, result.bottom) // unchanged
    }

    @Test
    fun applyRegionDragBottomLeftOnlyMovesBottomAndLeftEdges() {
        val region = rectOf(10, 10, 50, 50)
        val result = applyRegionDrag(region, RegionDragTarget.BOTTOM_LEFT, dx = 4f, dy = 6f)
        assertEquals(14, result.left)
        assertEquals(10, result.top) // unchanged
        assertEquals(50, result.right) // unchanged
        assertEquals(56, result.bottom)
    }

    @Test
    fun applyRegionDragNoneReturnsRegionUnchanged() {
        val region = rectOf(10, 10, 50, 50)
        val result = applyRegionDrag(region, RegionDragTarget.NONE, dx = 100f, dy = 100f)
        assertEquals(10, result.left)
        assertEquals(10, result.top)
        assertEquals(50, result.right)
        assertEquals(50, result.bottom)
    }

    @Test
    fun applyRegionDragFractionalDeltaRoundsToNearestPixel() {
        val region = rectOf(10, 10, 50, 50)
        // dx=0.4 rounds to 0 -- no visible change on the left/right edges
        val result = applyRegionDrag(region, RegionDragTarget.MOVE, dx = 0.4f, dy = 0.6f)
        assertEquals(10, result.left)
        assertEquals(50, result.right)
        // dy=0.6 rounds to 1
        assertEquals(11, result.top)
        assertEquals(51, result.bottom)
    }
}
