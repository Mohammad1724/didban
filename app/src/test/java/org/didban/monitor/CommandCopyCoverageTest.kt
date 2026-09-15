package org.didban.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Guards the i18n migration itself.
 *
 * The Command shell grew 191 hardcoded Persian strings inside screen files
 * while shipping a language switch, so English mode showed a half-translated
 * UI and nothing in the build noticed. Every user-facing string now lives in
 * [CommandCopy]; this test keeps it that way by scanning the sources.
 */
class CommandCopyCoverageTest {

    private val persian = Regex("[\u0600-\u06FF]")

    /** app/src/main/java/org/didban/monitor, resolved from the module dir. */
    private val sourceDir = File("src/main/java/org/didban/monitor")
        .takeIf { it.isDirectory }
        ?: File("app/src/main/java/org/didban/monitor").takeIf { it.isDirectory }

    private fun literalsIn(file: File): List<Pair<Int, String>> {
        val out = mutableListOf<Pair<Int, String>>()
        var inBlockComment = false
        file.readLines().forEachIndexed { index, raw ->
            val line = raw.trim()
            if (line.startsWith("/*")) inBlockComment = true
            if (inBlockComment) {
                if ("*/" in line) inBlockComment = false
                return@forEachIndexed
            }
            if (line.startsWith("//") || line.startsWith("*")) return@forEachIndexed
            Regex(""""((?:[^"\\]|\\.)*)"""").findAll(raw).forEach { m ->
                if (persian.containsMatchIn(m.groupValues[1])) out.add(index + 1 to m.groupValues[1])
            }
        }
        return out
    }

    @Test
    fun `no screen hardcodes Persian text`() {
        assumeTrue("source tree not reachable from the test working directory", sourceDir != null)
        val offenders = sourceDir!!.listFiles { f -> f.name.startsWith("Command") && f.name.endsWith(".kt") }
            ?.filter { it.name != "CommandTokens.kt" }   // the fa table itself
            ?.flatMap { f -> literalsIn(f).map { (line, s) -> "${f.name}:$line \"$s\"" } }
            .orEmpty()
        assertEquals(
            "Hardcoded Persian strings found outside CommandCopy — add a key instead:\n" +
                offenders.joinToString("\n"),
            emptyList<String>(), offenders
        )
    }

    @Test
    fun `both language tables cover every key`() {
        // A missing override is a compile error now that CommandCopy is an
        // interface, but the tables are also asserted equal in size so that a
        // future refactor back to a copy()-derived form is caught here.
        val faKeys = CommandCopyFa::class.java.declaredFields.map { it.name }
        val enKeys = CommandCopyEn::class.java.declaredFields.map { it.name }
        assertTrue("Persian table looks empty", faKeys.size >= 300)
        assertEquals("fa and en tables diverged", faKeys.sorted(), enKeys.sorted())
    }
}
