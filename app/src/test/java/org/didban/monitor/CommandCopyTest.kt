package org.didban.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract tests for the Command shell's copy table.
 *
 * `CommandCopy.en` is built with `fa.copy(...)`, so a key that is declared and
 * filled in for Persian but forgotten in English compiles, runs, and silently
 * shows Persian to an English user. Nothing in the build catches that — these
 * tests do.
 */
class CommandCopyTest {

    private val fa = CommandCopy.fa
    private val en = CommandCopy.en

    /**
     * Every key of the table, by name.
     *
     * Read through the interface's getters rather than the implementing
     * object's fields, so the test does not depend on how the table is stored.
     */
    private val keys: List<String> =
        CommandCopy::class.java.methods
            .filter { it.parameterCount == 0 && it.returnType == String::class.java }
            .map { it.name }
            // Kotlin exposes `val foo` to Java reflection as `getFoo()`
            .filter { it.startsWith("get") && it != "getClass" }
            .map { it.removePrefix("get").replaceFirstChar(Char::lowercaseChar) }
            .sorted()

    private fun value(copy: CommandCopy, key: String): String =
        CommandCopy::class.java.getMethod("get" + key.replaceFirstChar(Char::uppercaseChar))
            .invoke(copy) as String

    private val persian = Regex("[\u0600-\u06FF]")

    /** `%s`, `%d`, `%1`, `%2` … in the order they appear. */
    private fun placeholders(s: String): List<String> =
        Regex("%\\d*[sd]?").findAll(s).map { it.value }.toList()

    /**
     * Regression guard for the JVM's 255-parameter method limit.
     *
     * CommandCopy used to be a data class; at 256 properties its generated
     * constructor exceeded the limit, which Kotlin compiled without complaint
     * and the JVM then rejected at class load with ClassFormatError. Merely
     * loading the interface and both objects proves the table is still
     * representable, and the assertion below keeps the size honest.
     */
    @Test
    fun `the copy table is not empty and both languages resolve`() {
        assertTrue("expected a substantial copy table, got ${keys.size}", keys.size >= 150)
        assertEquals(fa, CommandCopy.forLanguage("fa"))
        assertEquals(en, CommandCopy.forLanguage("en"))
        // an unknown language must fall back to English, not to null or Persian
        assertEquals(en, CommandCopy.forLanguage("de"))
    }

    @Test
    fun `every key has a non-blank value in both languages`() {
        keys.forEach { key ->
            assertTrue("$key is blank in fa", value(fa, key).isNotBlank())
            assertTrue("$key is blank in en", value(en, key).isNotBlank())
        }
    }

    /**
     * Keys whose value is deliberately the same in both languages because it
     * names the language itself: a language picker shows each option in its
     * own script ("فارسی", not "Persian"), which is the standard endonym
     * convention. Exempt from the leak check for that reason only.
     */
    private val endonyms = setOf("persian")

    @Test
    fun `no English string contains Persian characters`() {
        // This is the silent-leak check: a key present in fa but missing from
        // the en copy() keeps its Persian text.
        val leaked = keys.filter { it !in endonyms && persian.containsMatchIn(value(en, it)) }
        assertEquals("English copy still contains Persian for: $leaked", emptyList<String>(), leaked)
    }

    @Test
    fun `endonyms are identical in both languages on purpose`() {
        endonyms.forEach { key ->
            assertEquals("$key should be an endonym", value(fa, key), value(en, key))
        }
    }

    @Test
    fun `placeholders match between the two languages`() {
        // A translated string that drops or reorders a placeholder renders as
        // a literal "%1" or puts the wrong value in the wrong slot.
        keys.forEach { key ->
            val a = placeholders(value(fa, key))
            val b = placeholders(value(en, key))
            assertEquals(
                "$key placeholder mismatch: fa=$a en=$b (fa=\"${value(fa, key)}\" en=\"${value(en, key)}\")",
                a.sorted(), b.sorted()
            )
        }
    }

    @Test
    fun `no value contains a stray Kotlin template marker`() {
        // "$" in a Kotlin literal starts a template; a literal `$s` would not
        // compile, but a `${'$'}` that slips through renders as "$" in the UI
        // and confuses placeholder substitution.
        keys.forEach { key ->
            assertFalse("$key fa contains '$'", value(fa, key).contains('$'))
            assertFalse("$key en contains '$'", value(en, key).contains('$'))
        }
    }

    @Test
    fun `translated strings are not just the key name repeated`() {
        // Guards against a placeholder-filled migration where a key was wired
        // to its own name instead of real copy.
        // Only camelCase keys are checked: a one-word value can legitimately
        // equal its own lowercase name (key `and` -> "and").
        keys.filter { it.any(Char::isUpperCase) }.forEach { key ->
            assertFalse("$key fa equals its own name", value(fa, key).equals(key, ignoreCase = true))
            assertFalse("$key en equals its own name", value(en, key).equals(key, ignoreCase = true))
        }
    }
}
