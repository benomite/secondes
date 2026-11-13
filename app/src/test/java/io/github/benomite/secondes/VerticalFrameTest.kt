package io.github.benomite.secondes

import org.junit.Assert.assertEquals
import org.junit.Test

class VerticalFrameTest {
    @Test
    fun aVerticalVideoFillsTheFrame() {
        assertEquals(FrameFit(1f, 1f), fitInVerticalFrame(1080, 1920))
    }

    @Test
    fun aHorizontalVideoKeepsTheFullWidthWithBarsAboveAndBelow() {
        val fit = fitInVerticalFrame(1920, 1080)
        assertEquals(1f, fit.widthFraction, 0f)
        assertEquals((9f / 16f) / (16f / 9f), fit.heightFraction, 0.0001f)
    }

    @Test
    fun aSquareVideoTakesTheWidthAndPartOfTheHeight() {
        val fit = fitInVerticalFrame(1000, 1000)
        assertEquals(1f, fit.widthFraction, 0f)
        assertEquals(0.5625f, fit.heightFraction, 0.0001f)
    }

    @Test
    fun aVideoNarrowerThanTheFrameHasBarsOnTheSides() {
        val fit = fitInVerticalFrame(900, 2000)
        assertEquals(0.45f / 0.5625f, fit.widthFraction, 0.0001f)
        assertEquals(1f, fit.heightFraction, 0f)
    }
}
