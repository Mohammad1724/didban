package org.didban.monitor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM smoke tests for the single-port HAProxy text generator. */
class SinglePortEngineTest {

    @Test fun `haproxy config routes each sni to its backend`() {
        val out = SinglePortEngine.generateHaproxyCfg(SinglePortConfig())
        assertTrue(out.contains("bind *:443"))
        assertTrue(out.contains("req_ssl_sni -i panel.example.com"))
        assertTrue(out.contains("server srv_panel 127.0.0.1:10000"))
        assertTrue(out.contains("server srv_sub_tls 127.0.0.1:11000 send-proxy"))
        assertTrue(out.contains("req_ssl_sni -i yahoo.com"))
        assertTrue(out.contains("server srv_fallback 127.0.0.1:13000"))
        assertTrue(out.contains("default_backend backend_fallback"))
    }

    @Test fun `blank domains are skipped but fallback stays`() {
        val out = SinglePortEngine.generateHaproxyCfg(
            SinglePortConfig(panelDomain = "", subTlsDomain = "", realitySni = "")
        )
        assertFalse(out.contains("backend_panel"))
        assertFalse(out.contains("backend_sub_tls"))
        assertFalse(out.contains("backend_reality"))
        assertTrue(out.contains("default_backend backend_fallback"))
    }
}
