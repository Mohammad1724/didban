package org.didban.monitor

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verdict logic for the repeated DPI diagnosis: this is where
 * "IP blocked" must be distinguished from "SNI/pattern blocked".
 */
class DpiVerdictTest {
    private fun result(
        tcpOk: Int = 3, tcpTries: Int = 3,
        plainOk: Int = 2, plainTries: Int = 2,
        sniOk: Int = 2, sniTries: Int = 2,
        tcpReset: Boolean = false
    ) = DpiDeepResult(
        host = "198.51.100.7", port = 443, sni = "yahoo.com",
        tcpOk = tcpOk, tcpTries = tcpTries,
        tlsPlainOk = plainOk, tlsPlainTries = plainTries,
        tlsSniOk = sniOk, tlsSniTries = sniTries,
        tcpReset = tcpReset, sniReset = false,
        avgLatencyMs = 120
    )

    @Test
    fun `all green is healthy`() {
        assertEquals(DpiVerdict.HEALTHY, result().verdict)
    }

    @Test
    fun `tcp timeouts with resets read as blocked`() {
        assertEquals(DpiVerdict.TCP_BLOCKED, result(tcpOk = 0, tcpReset = true).verdict)
    }

    @Test
    fun `tcp timeouts without resets read as down`() {
        assertEquals(DpiVerdict.TCP_DOWN, result(tcpOk = 0).verdict)
    }

    @Test
    fun `partial tcp failures are unstable`() {
        assertEquals(DpiVerdict.UNSTABLE, result(tcpOk = 2).verdict)
    }

    @Test
    fun `tls failing without sni is tls blocked`() {
        assertEquals(DpiVerdict.TLS_BLOCKED, result(plainOk = 0).verdict)
    }

    @Test
    fun `tls failing only with the sni is sni blocked`() {
        assertEquals(DpiVerdict.SNI_BLOCKED, result(sniOk = 0).verdict)
    }

    @Test
    fun `partial sni failures are unstable`() {
        assertEquals(DpiVerdict.UNSTABLE, result(sniOk = 1).verdict)
    }
}
