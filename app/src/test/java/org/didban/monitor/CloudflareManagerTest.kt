package org.didban.monitor

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.BeforeClass
import org.junit.Test

/**
 * JVM integration tests for the H11 pagination walker: a local stub of the
 * Cloudflare v4 API (real HTTP + real OkHttp + real JSON) proves that
 * [CloudflareService.listRecords] / [listZones] walk EVERY page, stop at
 * the right moment (with or without `result_info`), and surface API errors.
 *
 * Stub routing:
 *  - token "bad-token"          -> 403 success:false envelope
 *  - GET .../zones              -> 120 zones (3 pages at per_page=50)
 *  - GET .../zoneX/dns_records  -> 250 records (3 pages at per_page=100;
 *                                  page 3 OMITS result_info on purpose)
 *  - GET .../small/dns_records  -> 30 records (single page)
 *  - GET .../nototal/dns_records-> 150 records, NO result_info at all
 */
class CloudflareManagerTest {

    companion object {
        private var serverSocket: ServerSocket? = null
        private var port = 0

        /** (path tail, zone or endpoint, page) pairs the walker requested. */
        private val requests = mutableListOf<Pair<String, Int>>()

        private const val ZONE_COUNT = 120
        private const val RECORD_COUNT_X = 250
        private const val RECORD_COUNT_SMALL = 30
        private const val RECORD_COUNT_NOTOTAL = 150

        @BeforeClass
        @JvmStatic
        fun startStub() {
            val sock = ServerSocket(0, 64, InetAddress.getByName("127.0.0.1"))
            val acceptor = Thread {
                while (!sock.isClosed) {
                    val conn = try { sock.accept() } catch (e: Exception) { break }
                    Thread { handle(conn) }.start()
                }
            }
            acceptor.isDaemon = true
            acceptor.start()
            serverSocket = sock
            port = sock.localPort
        }

        @AfterClass
        @JvmStatic
        fun stopStub() {
            try { serverSocket?.close() } catch (_: Exception) {}
        }

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
                val pathAndQuery = parts.getOrNull(1) ?: "/"
                val path = pathAndQuery.substringBefore("?")
                val query = pathAndQuery.substringAfter("?", "")
                val page = query.split("&").firstOrNull { it.startsWith("page=") }
                    ?.substringAfter("page=")?.toIntOrNull() ?: 1
                val perPage = query.split("&").firstOrNull { it.startsWith("per_page=") }
                    ?.substringAfter("per_page=")?.toIntOrNull() ?: 100
                val auth = header.toString().lineSequence()
                    .firstOrNull { it.startsWith("Authorization:") }
                    ?.substringAfter("Authorization:")?.trim() ?: ""

                val response: Pair<String, String> = when {
                    auth == "Bearer bad-token" ->
                        "403 Forbidden" to """{"success":false,"errors":[{"message":"Invalid API token"}]}"""
                    path.endsWith("/zones") -> serveZones(page, perPage)
                    path.contains("/zoneX/dns_records") -> serveRecords(page, perPage, RECORD_COUNT_X, omitResultInfoOnPage = 3)
                    path.contains("/small/dns_records") -> serveRecords(page, perPage, RECORD_COUNT_SMALL, omitResultInfoOnPage = -1)
                    path.contains("/nototal/dns_records") -> serveRecords(page, perPage, RECORD_COUNT_NOTOTAL, omitResultInfoOnPage = 0)
                    else -> "404 Not Found" to """{"success":false,"errors":[{"message":"not found"}]}"""
                }
                synchronized(requests) { requests.add(path.substringAfterLast('/') to page) }

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

        private fun zoneJson(i: Int) =
            """{"id":"z$i","name":"zone$i.example.com","status":"active","paused":false}"""

        private fun recordJson(i: Int) =
            """{"id":"r$i","type":"A","name":"rec$i.example.com","content":"10.0.$i.1","proxiable":true,"proxied":false,"ttl":300}"""

        /**
         * Serves [count] records of [perPage] each. When
         * [omitResultInfoOnPage] > 0 that page's response drops
         * `result_info`; == 0 drops it on every page.
         */
        private fun serveRecords(page: Int, perPage: Int, count: Int, omitResultInfoOnPage: Int): Pair<String, String> {
            val start = (page - 1) * perPage
            val end = if (start >= count) start else minOf(start + perPage, count)
            val items = if (start >= count) "" else (start until end).joinToString(",") { recordJson(it) }
            val hasInfo = when (omitResultInfoOnPage) {
                0 -> false
                -1 -> true
                else -> page != omitResultInfoOnPage
            }
            val info = if (hasInfo) {
                ""","result_info":{"page":$page,"per_page":$perPage,"count":${end - start},"total_count":$count}"""
            } else {
                ""
            }
            return "200 OK" to """{"success":true,"errors":[],"result":[$items]$info}"""
        }

        private fun serveZones(page: Int, perPage: Int): Pair<String, String> {
            val start = (page - 1) * perPage
            val end = if (start >= ZONE_COUNT) start else minOf(start + perPage, ZONE_COUNT)
            val items = if (start >= ZONE_COUNT) "" else (start until end).joinToString(",") { zoneJson(it) }
            return "200 OK" to
                """{"success":true,"errors":[],"result":[$items],""" +
                """"result_info":{"page":$page,"per_page":$perPage,"count":${end - start},"total_count":$ZONE_COUNT}}"""
        }

        private fun pagesFor(endpoint: String): List<Int> =
            synchronized(requests) { requests.filter { it.first == endpoint }.map { it.second } }

        private fun clearRequests() {
            synchronized(requests) { requests.clear() }
        }
    }

    @Test
    fun listZonesWalksAllPages() = runBlocking {
        clearRequests()
        val zones = CloudflareService.listZones("tok", "http://127.0.0.1:$port")
        assertEquals(ZONE_COUNT, zones.size)
        assertEquals("z0", zones.first().id)
        assertEquals("zone119.example.com", zones.last().name)
        assertEquals("must request exactly pages 1,2,3", listOf(1, 2, 3), pagesFor("zones"))
    }

    @Test
    fun listRecordsWalksAllPagesIncludingResultInfoFallback() = runBlocking {
        clearRequests()
        val records = CloudflareService.listRecords("tok", "zoneX", "http://127.0.0.1:$port")
        assertEquals(RECORD_COUNT_X, records.size)
        assertEquals("r0", records.first().id)
        assertEquals("rec249.example.com", records.last().name)
        assertEquals("zoneX", records.first().zoneId)
        // page 3 has no result_info: the walker must still stop there
        assertEquals(listOf(1, 2, 3), pagesFor("dns_records"))
    }

    @Test
    fun smallListingStopsAfterOnePage() = runBlocking {
        clearRequests()
        val records = CloudflareService.listRecords("tok", "small", "http://127.0.0.1:$port")
        assertEquals(RECORD_COUNT_SMALL, records.size)
        assertEquals("no page 2 for a single-page listing", listOf(1), pagesFor("dns_records"))
    }

    @Test
    fun unknownTotalWalksUntilPartialPage() = runBlocking {
        clearRequests()
        // no result_info anywhere: the walk must end on the partial page 2
        val records = CloudflareService.listRecords("tok", "nototal", "http://127.0.0.1:$port")
        assertEquals(RECORD_COUNT_NOTOTAL, records.size)
        assertEquals(listOf(1, 2), pagesFor("dns_records"))
    }

    @Test
    fun apiErrorSurfacesTheCloudflareMessage() = runBlocking {
        try {
            CloudflareService.listRecords("bad-token", "zoneX", "http://127.0.0.1:$port")
            fail("expected Exception")
        } catch (e: Exception) {
            assertEquals("Invalid API token", e.message)
        }
    }
}
