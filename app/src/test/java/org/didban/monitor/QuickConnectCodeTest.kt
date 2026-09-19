package org.didban.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class QuickConnectCodeTest {
    private val read = "a".repeat(48)
    private val admin = "b".repeat(64)
    private val fp = "01".repeat(32)

    @Test fun parsesInstallerCode() {
        val result = QuickConnectCodeParser.parse("didban://203.0.113.8:8686?token=$read&admin_token=$admin&fp=$fp&name=Production%20One")
        assertEquals("203.0.113.8", result.host)
        assertEquals(8686, result.port)
        assertEquals("Production One", result.name)
        assertEquals(read, result.readToken)
        assertEquals(admin, result.adminToken)
        assertEquals(fp, result.fingerprint)
    }

    @Test fun acceptsTerminalLabelsAndWrappedLines() {
        val pasted = """
            One-Click Mobile Import Link:
              didban://203.0.113.8:8686?token=$read&admin_token=
              $admin&fp=$fp&name=Production%20One
            Save these values.
        """.trimIndent()
        val result = QuickConnectCodeParser.parse(pasted)
        assertEquals("Production One", result.name)
        assertEquals(admin, result.adminToken)
    }

    @Test fun rejectsMissingWeakEqualOrUnexpectedCredentials() {
        rejects("https://203.0.113.8:8686?token=$read&admin_token=$admin&fp=$fp")
        rejects("didban://203.0.113.8:8686?token=weak&admin_token=$admin&fp=$fp")
        rejects("didban://203.0.113.8:8686?token=$read&admin_token=$read&fp=$fp")
        rejects("didban://203.0.113.8:8686?token=$read&admin_token=$admin&fp=$fp&evil=x")
        rejects("didban://user@203.0.113.8:8686?token=$read&admin_token=$admin&fp=$fp")
    }

    private fun rejects(value: String) {
        try {
            QuickConnectCodeParser.parse(value)
            fail("accepted invalid code: $value")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
