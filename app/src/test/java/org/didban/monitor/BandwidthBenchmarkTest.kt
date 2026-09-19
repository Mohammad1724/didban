package org.didban.monitor

import kotlinx.coroutines.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.SocketTimeoutException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLHandshakeException

class BandwidthBenchmarkTest {
    private lateinit var http: MockWebServer
    private val bytes = 131_072L
    private fun config() = ServerConfig(1, "Fixture", "127.0.0.1", http.port,
        token = "read-only-test-token", adminToken = "private-admin-test-token", useTls = false)
    private fun engine() = BandwidthBenchmark(bytes, pingCount = 2, pingDelayMs = 0)
    private fun download() = MockResponse().setBody(Buffer().write(ByteArray(bytes.toInt()) { 37 }))
    private fun upload(received: Long = bytes) = MockResponse().setBody("{\"received_bytes\":$received}")

    @Before fun start() { http = MockWebServer(); http.start() }
    @After fun close() { http.shutdown() }

    @Test fun `real streaming transfers use read token and accurate counts`() = runBlocking {
        http.enqueue(download()); http.enqueue(upload())
        val events = mutableListOf<BenchmarkProgress>()
        val result = withTimeout(5_000) { engine().run(config()) { events += it } }
        assertTrue(result.downloadMbps > 0f)
        assertTrue(result.uploadMbps > 0f)
        assertEquals(0, result.packetLossPct)
        assertEquals(1f, events.last().fraction, 0f)
        val get = http.takeRequest(1, TimeUnit.SECONDS)!!
        val post = http.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("/api/bandwidth/download?bytes=$bytes", get.path)
        assertEquals("POST", post.method)
        assertEquals("/api/bandwidth/upload", post.path)
        assertEquals(bytes, post.bodySize)
        assertEquals("Bearer read-only-test-token", get.getHeader("Authorization"))
        assertEquals("Bearer read-only-test-token", post.getHeader("Authorization"))
    }

    @Test fun `slow download does not block the caller UI dispatcher`() {
        val ui = Executors.newSingleThreadExecutor { Thread(it, "test-ui-thread") }.asCoroutineDispatcher()
        try {
            runBlocking(ui) {
                http.enqueue(download().setBodyDelay(500, TimeUnit.MILLISECONDS)); http.enqueue(upload())
                val ticks = AtomicInteger()
                val ticker = launch { while (true) { delay(10); ticks.incrementAndGet() } }
                withTimeout(5_000) {
                    engine().run(config()) { assertNotEquals("test-ui-thread", Thread.currentThread().name) }
                }
                ticker.cancelAndJoin()
                assertTrue("UI should remain responsive while the body is delayed", ticks.get() >= 10)
            }
        } finally { ui.close() }
    }

    @Test fun `missing download endpoint retains 404 and never starts upload`() = runBlocking {
        http.enqueue(MockResponse().setResponseCode(404).setBody("do not display this body"))
        val failure = try { engine().run(config()) {}; error("expected failure") } catch (e: BenchmarkFailure) { e }
        assertEquals(BenchmarkStage.DOWNLOAD, failure.stage)
        assertEquals(404, (failure.cause as ApiException).statusCode)
        assertEquals(1, http.requestCount)
        val copy = CommandCopy.forLanguage("fa")
        assertEquals(copy.bandwidthUnsupported, describeAgentToolFailure(failure.cause!!, copy, config(), true))
    }

    @Test fun `upload rejection remains distinct from a network error`() = runBlocking {
        http.enqueue(download()); http.enqueue(MockResponse().setResponseCode(413))
        val failure = try { engine().run(config()) {}; error("expected failure") } catch (e: BenchmarkFailure) { e }
        assertEquals(BenchmarkStage.UPLOAD, failure.stage)
        assertEquals(413, (failure.cause as ApiException).statusCode)
        assertTrue(describeAgentToolFailure(failure.cause!!, CommandCopy.forLanguage("en"), config(), true).contains("413"))
    }

    @Test fun `truncated body and incorrect upload acknowledgement never create a success`() = runBlocking {
        http.enqueue(MockResponse().setBody("short"))
        val down = try { engine().run(config()) {}; error("expected failure") } catch (e: BenchmarkFailure) { e }
        assertEquals(BenchmarkStage.DOWNLOAD, down.stage)
        http.enqueue(download()); http.enqueue(upload(bytes - 1))
        val up = try { engine().run(config()) {}; error("expected failure") } catch (e: BenchmarkFailure) { e }
        assertEquals(BenchmarkStage.UPLOAD, up.stage)
    }

    @Test fun `cancellation closes a pending call promptly without uploading`() = runBlocking {
        http.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val work = async(Dispatchers.Default) { engine().run(config()) {} }
        assertNotNull(http.takeRequest(3, TimeUnit.SECONDS))
        withTimeout(2_000) { work.cancelAndJoin() }
        assertTrue(work.isCancelled)
        assertEquals(1, http.requestCount)
    }

    @Test fun `one latency sample does not fabricate jitter`() = runBlocking {
        http.enqueue(download()); http.enqueue(upload())
        val result = BandwidthBenchmark(bytes, pingCount = 1, pingDelayMs = 0).run(config()) {}
        assertNull(result.jitterMs)
    }

    @Test fun `Mbps calculation uses nanoseconds and per-leg elapsed time`() {
        assertEquals(8f, benchmarkMbps(1_000_000, 1_000_000_000), 0.0001f)
        assertEquals(400f, benchmarkMbps(100_000_000, 2_000_000_000), 0.0001f)
    }

    @Test fun `Docker 404 status is preserved by the real API client`() = runBlocking {
        http.enqueue(MockResponse().setResponseCode(404).setBody(config().token))
        val failure = try { ApiClient().dockerContainers(config()); error("expected failure") } catch (e: ApiException) { e }
        assertEquals(404, failure.statusCode)
        val copy = CommandCopy.forLanguage("fa")
        assertEquals(copy.dockerUnsupported, describeAgentToolFailure(failure, copy, config(), false))
    }

    @Test fun `transport authentication and TLS explanations are distinct and redact both tokens`() {
        val copy = CommandCopy.forLanguage("en")
        val server = config()
        assertEquals(copy.agentToolTimeout, describeAgentToolFailure(SocketTimeoutException(), copy, server, true))
        assertEquals(copy.agentToolTlsFailed, describeAgentToolFailure(SSLHandshakeException("bad certificate"), copy, server, true))
        assertTrue(describeAgentToolFailure(ApiException("HTTP 401", 401), copy, server, false).contains(copy.agentToolAuthFailed))
        val redacted = describeAgentToolFailure(Exception("${server.token} ${server.adminToken}"), copy, server, true)
        assertFalse(redacted.contains(server.token)); assertFalse(redacted.contains(server.adminToken))
        assertTrue(describeAgentToolFailure(Exception(), copy, server, true).contains("Exception"))
    }
}
