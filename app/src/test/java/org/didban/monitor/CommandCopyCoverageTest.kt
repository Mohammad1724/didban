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
 * [CommandCopy]; this test keeps it that way by scanning screens and
 * notification/alert engines.
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
        val offenders = sourceDir!!.listFiles { f -> (f.name.startsWith("Command") || f.name == "MonitorService.kt" || f.name == "MainActivity.kt" || f.name == "AlertEngine.kt" || f.name == "UptimeEngine.kt") && f.name.endsWith(".kt") }
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
    fun `every key is actually rendered by a screen or notification`() {
        // The migration is mechanical, so a key can be added and never wired
        // up — or a screen can stop using one. Either way the table should not
        // carry strings nothing renders.
        assumeTrue("source tree not reachable from the test working directory", sourceDir != null)
        val used = sourceDir!!.listFiles { f -> (f.name.startsWith("Command") || f.name == "MonitorService.kt" || f.name == "MainActivity.kt" || f.name == "AlertEngine.kt" || f.name == "UptimeEngine.kt") && f.name.endsWith(".kt") }
            ?.filter { it.name != "CommandTokens.kt" }
            ?.joinToString("") { it.readText() }
            .orEmpty()
        // Read the key list off the interface's getters: the implementing
        // objects also carry synthetic fields (INSTANCE, Compose's $stable).
        val declared = CommandCopy::class.java.methods
            .filter { it.parameterCount == 0 && it.returnType == String::class.java && it.name.startsWith("get") }
            .map { it.name.removePrefix("get").replaceFirstChar(Char::lowercaseChar) }
        val dead = declared.filter { ".$it" !in used }
        assertEquals("CommandCopy keys nothing references: $dead", emptyList<String>(), dead)
    }

    @Test
    fun `both language tables cover every key`() {
        // A missing override is a compile error now that CommandCopy is an
        // interface, but the tables are also asserted equal in size so that a
        // future refactor back to a copy()-derived form is caught here.
        val faKeys = CommandCopyFa::class.java.methods.filter { it.name.startsWith("get") }.map { it.name }
        val enKeys = CommandCopyEn::class.java.methods.filter { it.name.startsWith("get") }.map { it.name }
        assertTrue("Persian table looks empty", faKeys.size >= 400)
        assertEquals("fa and en tables diverged", faKeys.sorted(), enKeys.sorted())
    }
}
