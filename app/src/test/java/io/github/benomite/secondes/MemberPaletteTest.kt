package io.github.benomite.secondes

import androidx.compose.ui.graphics.Color
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MemberPaletteTest {
    @Test
    fun membersOneToFiveGetTheFiveColorsThenTheSixthStartsAgain() {
        assertEquals(Color(0xFF2D4E8E), memberColor(1))
        assertEquals(Color(0xFF3B6E45), memberColor(2))
        assertEquals(Color(0xFF7A3E6E), memberColor(3))
        assertEquals(Color(0xFF8A6410), memberColor(4))
        assertEquals(Color(0xFF1F6B6B), memberColor(5))
        assertEquals(Color(0xFF2D4E8E), memberColor(6))
    }

    @Test
    fun theOrangeIsNeverAMembersColor() {
        for (member in 1..100) {
            assertNotEquals(Color(0xFFC9572A), memberColor(member))
            assertNotEquals(Palette.ToFill, memberColor(member))
            assertNotEquals(Palette.ToFillText, memberColor(member))
        }
    }

    @Test
    fun theJoinOrderIsTheSameWhateverTheOrderTheDaysArrive() {
        val days = listOf(
            "Sam" to LocalDate.of(2026, 3, 2),
            "Ana" to LocalDate.of(2026, 3, 5),
            "Sam" to LocalDate.of(2026, 3, 9),
            "Zoé" to LocalDate.of(2026, 3, 1),
            "Bob" to LocalDate.of(2026, 3, 5),
        )
        val expected = mapOf("Zoé" to 1, "Sam" to 2, "Ana" to 3, "Bob" to 4)
        assertEquals(expected, memberJoinOrder(days))
        assertEquals(expected, memberJoinOrder(days.reversed()))
    }

    @Test
    fun anAuthorIsShownByTheirFirstName() {
        assertEquals("Léa", firstName("Léa Martin"))
        assertEquals("Léa", firstName("  Léa   Martin "))
        assertEquals("Léa", firstName("Léa"))
    }
}
