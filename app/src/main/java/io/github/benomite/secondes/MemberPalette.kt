package io.github.benomite.secondes

import androidx.compose.ui.graphics.Color
import java.time.LocalDate

/** The members' colors, in join order; the sixth member starts again from the first. The orange is never one. */
val MemberColors = listOf(
    Color(0xFF2D4E8E), // ink blue
    Color(0xFF3B6E45), // forest green
    Color(0xFF7A3E6E), // plum
    Color(0xFF8A6410), // ochre
    Color(0xFF1F6B6B), // teal
)

/** The color of the member who joined [joinOrder]th (1 for the first): the same on every phone of the journal. */
fun memberColor(joinOrder: Int): Color = MemberColors[(joinOrder - 1).mod(MemberColors.size)]

/**
 * The join order of the journal's members, from 1, given each filled day as (author, date). The journal keeps no
 * list of members yet: a member's place is that of their earliest filled day, ties broken by name, so every phone
 * with the same journal finds the same order.
 */
fun memberJoinOrder(filled: Iterable<Pair<String, LocalDate>>): Map<String, Int> =
    filled.groupBy({ it.first }, { it.second })
        .map { (author, dates) -> author to dates.min() }
        .sortedWith(compareBy<Pair<String, LocalDate>>({ it.second }, { it.first }))
        .mapIndexed { i, (author, _) -> author to i + 1 }
        .toMap()

/** The first name of a member, as shown wherever an author is: the first word of the name they typed. */
fun firstName(name: String): String = name.trim().split(Regex("\\s+")).first()
