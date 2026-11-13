package io.github.benomite.secondes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecondWindowTest {
    @Test
    fun aWindowLastsOneSecond() {
        assertEquals(3_500L, SecondWindow(2_500).endMs)
    }

    @Test
    fun keepsAStartThatFitsInTheVideo() {
        assertEquals(SecondWindow(4_200), secondWindowAt(4_200, durationMs = 10_000))
    }

    @Test
    fun neverStartsBeforeTheVideo() {
        assertEquals(SecondWindow(0), secondWindowAt(-300, durationMs = 10_000))
    }

    @Test
    fun neverRunsPastTheEndOfTheVideo() {
        assertEquals(SecondWindow(9_000), secondWindowAt(9_800, durationMs = 10_000))
    }

    @Test
    fun aOneSecondVideoHoldsExactlyOneWindow() {
        assertEquals(SecondWindow(0), secondWindowAt(500, durationMs = 1_000))
    }

    @Test
    fun aVideoShorterThanASecondIsKeptWhole() {
        assertTrue(isShorterThanOneSecond(999))
        assertFalse(isShorterThanOneSecond(1_000))
        assertEquals(SecondWindow(0, 999), secondWindowAt(400, durationMs = 999))
        assertEquals(SecondWindow(0, 600), secondWindowAtFraction(0.5f, durationMs = 600))
        assertEquals(0f, sliderFraction(SecondWindow(0, 600), durationMs = 600), 0f)
    }

    @Test
    fun theSliderEndsMapToTheFirstAndLastSecond() {
        assertEquals(SecondWindow(0), secondWindowAtFraction(0f, durationMs = 10_000))
        assertEquals(SecondWindow(9_000), secondWindowAtFraction(1f, durationMs = 10_000))
        assertEquals(SecondWindow(4_500), secondWindowAtFraction(0.5f, durationMs = 10_000))
    }

    @Test
    fun slidingPastTheEndsIsClamped() {
        assertEquals(SecondWindow(0), secondWindowAtFraction(-0.2f, durationMs = 10_000))
        assertEquals(SecondWindow(9_000), secondWindowAtFraction(1.4f, durationMs = 10_000))
    }

    @Test
    fun aSavedWindowReopensAtItsPlaceOnTheSlider() {
        val saved = SecondWindow(2_250)

        val fraction = sliderFraction(saved, durationMs = 10_000)

        assertEquals(0.25f, fraction, 0.0001f)
        assertEquals(saved, secondWindowAtFraction(fraction, durationMs = 10_000))
    }

    @Test
    fun aOneSecondVideoSitsAtTheStartOfTheSlider() {
        assertEquals(0f, sliderFraction(SecondWindow(0), durationMs = 1_000), 0f)
    }
}
