package io.github.benomite.secondes

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** Every text of the app comes from the string resources, in English (values/) and in French (values-fr/). */
class StringResourcesTest {
    // Unit tests run from the module's directory.
    private val main = File("src/main")

    /** The names of the strings and plurals defined in the resource folder [folder]. */
    private fun keys(folder: String): Set<String> {
        val files = File(main, "res/$folder").listFiles { f -> f.extension == "xml" }.orEmpty()
        assertTrue("no resource file in $folder", files.isNotEmpty())
        return files.flatMap { file ->
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            listOf("string", "plurals").flatMap { tag ->
                val nodes = doc.getElementsByTagName(tag)
                (0 until nodes.length).map { (nodes.item(it) as Element).getAttribute("name") }
            }
        }.toSet()
    }

    @Test
    fun englishAndFrenchDefineTheSameKeys() {
        val english = keys("values")
        val french = keys("values-fr")
        assertTrue(english.isNotEmpty())
        assertEquals("missing in values-fr", emptySet<String>(), english - french)
        assertEquals("missing in values", emptySet<String>(), french - english)
    }

    // The calls that put a text in front of the user: Compose texts, labels, descriptions, notifications, toasts, dialogs.
    private val userFacing = Regex(
        """(?:\bText\(|\btext\s*=|\blabel\s*=|\bcontentDescription\s*=|setContentTitle\(|setContentText\(|setTicker\(|""" +
            """makeText\([^,]*,|\bsetTitle\(|\bsetMessage\(|\btitle\s*=)\s*"((?:[^"\\]|\\.)*)"""",
    )

    @Test
    fun noUserFacingTextIsWrittenInTheKotlinSources() {
        val sources = File(main, "java").walk().filter { it.extension == "kt" }.toList()
        assertTrue(sources.isNotEmpty())
        val hardcoded = sources.flatMap { file ->
            userFacing.findAll(file.readText()).mapNotNull { match ->
                // What the literal writes itself, its ${...} templates aside: a word means a text to translate.
                val literal = match.groupValues[1].replace(Regex("""\$\{[^}]*}"""), "").replace(Regex("""\$\w+"""), "")
                if (literal.any { it.isLetter() }) "${file.name}: ${match.value}" else null
            }.toList()
        }
        assertEquals(emptyList<String>(), hardcoded)
    }
}
