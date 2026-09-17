package org.didban.monitor

import java.io.ByteArrayInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.BeforeClass
import org.junit.Test

/**
 * JVM tests for the H7 client pool and pinning logic.
 *
 *  - The pinning trust manager is tested DIRECTLY (it is exactly the code
 *    OkHttp invokes on every TLS handshake): TOFU capture, pin acceptance,
 *    pin-mismatch rejection, empty chain. A real TLS server is not available
 *    in the sandbox JDK, so the handshake itself is not simulated here.
 *  - The OkHttp client layer (per-key caching, LRU eviction with dispatcher
 *    shutdown, shared connection pool, request/error handling, streaming)
 *    is tested against a real local HTTP server.
 *
 * The certificate is a fixed self-signed pair (CN=didban-jvm-test); its
 * SHA-256 fingerprint was computed independently with
 * `openssl x509 -fingerprint -sha256` and is EXPECTED_FP below.
 */
class HttpClientPoolTest {

    companion object {

        private const val EXPECTED_FP =
            "f57f843cc202cf23d8eb98740a9c7476f3e0e53325193bd35520cc12eacbf9b9"

        private val CERT_PEM =
"""-----BEGIN CERTIFICATE-----
MIIDFTCCAf2gAwIBAgIULuBpUQIK9u2VZ2dTxYXV+8ONtkAwDQYJKoZIhvcNAQEL
BQAwGjEYMBYGA1UEAwwPZGlkYmFuLWp2bS10ZXN0MB4XDTI2MDkxMzEwMjIwMVoX
DTM2MDkxMDEwMjIwMVowGjEYMBYGA1UEAwwPZGlkYmFuLWp2bS10ZXN0MIIBIjAN
BgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEArSL9jdmiAnXvSoZ6yzg//FO8GB88
ptlije2XWr9A313CisOq6K7uom6d/ZbOkrrdYbAMAvFBeOis3ETLuDHXYEPG9FIE
XGrNqmqi54vTDyz0fuD+yWN5fBwxbDiGl95wFAEW0QeP1KhOo0Xza+uKNuECW6ps
fiOZPgyERChi3ll1aWJke+KVbvTTb9mVaJTRqjvtVln0ex2hxPloWSp46/oWWbaF
A0W59B1NYortGYuXD8Hw7XtKrjl/2ME9BAE+94vCL6qVuWsPJznN2j2fxy25P0yl
mMAe4r4sKEjlmXFWhOnZz84y3CRNbG3/mfFdoHMz6r6j7pTDsqMy2OnPaQIDAQAB
o1MwUTAdBgNVHQ4EFgQUwAVcGPLvrRKUfM4Oac5kLrK45M8wHwYDVR0jBBgwFoAU
wAVcGPLvrRKUfM4Oac5kLrK45M8wDwYDVR0TAQH/BAUwAwEB/zANBgkqhkiG9w0B
AQsFAAOCAQEAHo5NqeoKjc9ZvIHk0c7IYcvYrhaLBbbv9qAxS9mG4FGmzTu12Vh6
MD1ZqBSgDaEHidEfBpxD4q+P0gqLsaTiqAcoNhPx3d9QtA9Uf28t42FiqEmz84ih
lfY9DSut41wpJHEdBIfO5HcUQy8TMVr5GP/qLqJ+at2xfTiT08rczs5d8p5QDoXl
k5uVLwF6JM8MSCdzp3qMF9IXAjN3E8qCBQWQXhdi32vgHDS3Atn+COIc/2YwORrN
Tn/eDTUE64VCrcSnVXYolnZhPWJDDrNrE7UYLxtrEWNQRGkqfzDnIbTDtKy/8Zkw
8O0c4EddqwS+o697/LWNDoS2gKBQPDd6yA==
-----END CERTIFICATE-----
"""

        private const val METRICS_JSON =
            """{"hostname":"didban-test-host","time":"2026-09-13T10:00:00Z","uptime_sec":42,
               "load_avg":[0.1,0.2,0.3],
               "cpu":{"cores":4,"usage":12.5,"user":8.0,"system":3.0,"iowait":0.0,"steal":0.0},
               "memory":{"total":1000,"used":400,"available":600,"usage_pct":40.0,
                         "swap_total":0,"swap_used":0,"swap_usage_pct":0.0},
               "disks":[],"network":[]}"""

        private var httpServerSocket: ServerSocket? = null
        private var httpPort = 0

        @BeforeClass
        @JvmStatic
        fun startServers() {
            httpPort = startHttpServer()
        }

        @AfterClass
        @JvmStatic
        fun stopServers() {
            try { httpServerSocket?.close() } catch (_: Exception) {}
        }

        private fun fixtureCert(): X509Certificate =
            CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(CERT_PEM.toByteArray()))
                as X509Certificate

        private fun startHttpServer(): Int {
            val sock = ServerSocket(0, 64, InetAddress.getByName("127.0.0.1"))
            val acceptor = Thread {
                while (!sock.isClosed) {
                    val conn = try { sock.accept() } catch (e: Exception) { break }
                    Thread { handle(conn) }.start()
                }
            }
            acceptor.isDaemon = true
            acceptor.start()
            httpServerSocket = sock
            return sock.localPort
        }

        /**
         * Minimal HTTP/1.1 stub agent:
         *   GET  /api/metrics     -> 200 METRICS_JSON
         *   POST /api/alerts/test -> 500 {"error":"boom"}
         *   anything else         -> 404 {"error":"not found"}
         */
        private fun handle(conn: Socket) {
            try {
                conn.soTimeout = 10_000
                val input = conn.getInputStream()
                val header = StringBuilder()
                while (!header.endsWith("\r\n\r\n") && header.length < 8192) {
                    val c = input.read()
                    if (c == -1) break
                    header.append(c.toChar())
                }
                val requestLine = header.toString().substringBefore("\r\n")
                val parts = requestLine.split(" ")
                val method = parts.getOrNull(0) ?: "GET"
                val path = parts.getOrNull(1) ?: "/"

                val response: Pair<String, String> = when {
                    method == "GET" && path.startsWith("/api/metrics") ->
                        "200 OK" to METRICS_JSON
                    method == "POST" && path.startsWith("/api/alerts/test") ->
                        "500 Internal Server Error" to "{\"error\":\"boom\"}"
                    else ->
                        "404 Not Found" to "{\"error\":\"not found\"}"
                }
                val payload = response.second.toByteArray()
                val head = "HTTP/1.1 ${response.first}\r\n" +
                        "Content-Type: application/json\r\n" +
                        "Content-Length: ${payload.size}\r\n" +
                        "Connection: close\r\n\r\n"
                val out = conn.getOutputStream()
                out.write(head.toByteArray())
                out.write(payload)
                out.flush()
            } catch (_: Exception) {
            } finally {
                try { conn.close() } catch (_: Exception) {}
            }
        }
    }

    private fun server(port: Int = 39999, useTls: Boolean = true, fingerprint: String = if (useTls) EXPECTED_FP else "") =
        ServerConfig(
            id = 1, name = "test", host = "127.0.0.1", port = port,
            token = "test-token", useTls = useTls, fingerprint = fingerprint
        )

    // ── Pinning trust manager (the exact code OkHttp runs per handshake) ──

    @Test
    fun tofuModeAcceptsAndRecordsFingerprint() {
        val holder = AtomicReference<String>("")
        val tm = HttpClientPool.trustManager("", holder) // no pin yet
        tm.checkServerTrusted(arrayOf(fixtureCert()), "RSA") // must not throw
        assertEquals(EXPECTED_FP, holder.get())
    }

    @Test
    fun matchingPinIsAccepted() {
        val tm = HttpClientPool.trustManager(EXPECTED_FP, null)
        tm.checkServerTrusted(arrayOf(fixtureCert()), "RSA") // must not throw
    }

    @Test
    fun mismatchedPinIsRejected() {
        val tm = HttpClientPool.trustManager("ab".repeat(32), null)
        try {
            tm.checkServerTrusted(arrayOf(fixtureCert()), "RSA")
            fail("expected CertificateException for pin mismatch")
        } catch (e: java.security.cert.CertificateException) {
            assertTrue(
                "message should explain the mismatch, got: ${e.message}",
                e.message!!.contains("mismatch")
            )
        }
    }

    @Test
    fun emptyChainIsRejected() {
        val tm = HttpClientPool.trustManager("", AtomicReference<String>(""))
        try {
            tm.checkServerTrusted(emptyArray(), "RSA")
            fail("expected CertificateException for empty chain")
        } catch (e: java.security.cert.CertificateException) {
            assertTrue(e.message!!.contains("empty"))
        }
    }

    @Test
    fun tofuThenStoredPinRoundTripWorks() {
        // 1) first connection: no pin, capture
        val holder = AtomicReference<String>("")
        HttpClientPool.trustManager("", holder).checkServerTrusted(arrayOf(fixtureCert()), "RSA")
        val stored = holder.get()
        assertEquals(EXPECTED_FP, stored)
        // 2) UI stores it in the server config; every later connection pins
        val repinned = HttpClientPool.trustManager(
            CertFingerprint.normalizeFingerprint(stored), null
        )
        repinned.checkServerTrusted(arrayOf(fixtureCert()), "RSA") // must not throw
    }

    // ── Key normalization & client caching ─────────────────────────────────

    @Test
    fun keyForNormalizesFingerprint() {
        val upperColon =
            " F5:7F:84:3C:C2:02:CF:23:D8:EB:98:74:0A:9C:74:76:F3:E0:E5:33:25:19:3B:D3:55:20:CC:12:EA:CB:F9:B9 "
        val s = server(fingerprint = upperColon)
        assertEquals(ClientKey("127.0.0.1", s.port, true, EXPECTED_FP), HttpClientPool.keyFor(s))
    }

    @Test
    fun tlsClientWithoutVerifiedPinIsRejectedBeforeSendingCredentials() {
        try {
            HttpClientPool.standardClient(server(fingerprint = ""))
            fail("expected unpinned TLS client to be rejected")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("fingerprint"))
        }
    }

    @Test
    fun standardClientIsCachedPerServerKey() {
        val s = server(port = 21001) // no connection ever made — pure cache behavior
        val a = HttpClientPool.standardClient(s)
        assertSame(a, HttpClientPool.standardClient(s))
        val otherPort = server(port = 21002)
        assertNotSame(a.client, HttpClientPool.standardClient(otherPort).client)
        HttpClientPool.evictForServer(s)
        assertNotSame(a, HttpClientPool.standardClient(s))
    }

    @Test
    fun fingerprintHolderExistsOnlyForTls() {
        assertNotNull(HttpClientPool.standardClient(server()).fingerprint)
        assertNull(HttpClientPool.standardClient(server(port = 21003, useTls = false)).fingerprint)
    }

    @Test
    fun sharedConnectionPoolAcrossStandardAndStreaming() {
        val s = server(port = 21004)
        val std = HttpClientPool.standardClient(s)
        val stream = HttpClientPool.streamingClient(s)
        assertNotSame(std.client, stream)
        // both must ride the single shared pool (the whole point of H7)
        assertSame(std.client.connectionPool, stream.connectionPool)
        HttpClientPool.releaseStreaming(stream)
        assertTrue(stream.dispatcher.executorService.isShutdown)
    }

    @Test
    fun lruEvictionShutsDownEvictedDispatcher() {
        // Fill the cache with a fresh batch of `capacity` distinct servers;
        // anything older is evicted first, so afterwards the cache contains
        // exactly the batch. One more insertion then evicts the batch's LRU.
        val batch = (0 until HttpClientPool.MAX_CACHED_CLIENTS)
            .map { server(port = 22000 + it) }
        val first = HttpClientPool.standardClient(batch[0])
        for (s in batch.drop(1)) HttpClientPool.standardClient(s)
        HttpClientPool.standardClient(server(port = 22000 + HttpClientPool.MAX_CACHED_CLIENTS))
        assertTrue(
            "evicted client dispatcher must be shut down",
            first.client.dispatcher.executorService.isShutdown
        )
        val rebuilt = HttpClientPool.standardClient(batch[0])
        assertNotSame(first, rebuilt)
        assertFalse(rebuilt.client.dispatcher.executorService.isShutdown)
    }

    // ── OkHttp layer against the local HTTP stub agent ─────────────────────

    @Test
    fun httpClientParsesMetrics() = runBlocking {
        val m = ApiClient().metrics(server(port = httpPort, useTls = false))
        assertEquals("didban-test-host", m.hostname)
        assertEquals(12.5f, m.cpuUsage, 0.001f)
    }

    @Test
    fun plainHttpHasNoFingerprint() = runBlocking {
        val api = ApiClient()
        api.metrics(server(port = httpPort, useTls = false))
        assertNull("no fingerprint for plain HTTP", api.lastSeenFingerprint)
    }

    @Test
    fun httpErrorBecomesApiExceptionWithCode() = runBlocking {
        try {
            ApiClient().tunnelStatus(server(port = httpPort, useTls = false), "t1")
            fail("expected ApiException for HTTP 404")
        } catch (e: ApiException) {
            assertTrue("expected 404 in message, got: ${e.message}", e.message!!.contains("404"))
        }
    }

    @Test
    fun postSurfacesAgentErrorField() = runBlocking {
        try {
            ApiClient().testTelegram(server(port = httpPort, useTls = false))
            fail("expected ApiException for HTTP 500")
        } catch (e: ApiException) {
            assertEquals("boom", e.message)
        }
    }

    @Test
    fun streamingCallReachesServerAndReleases() = runBlocking {
        val api = ApiClient()
        val call = api.openStreamingCall(
            server(port = httpPort, useTls = false),
            "/api/bandwidth/download?bytes=1000"
        )
        call.execute().use { resp ->
            assertTrue("expected 404 from stub for bandwidth path", resp.code == 404)
        }
        api.releaseStreaming() // must not throw
    }
}
