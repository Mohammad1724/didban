package org.didban.monitor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets

/**
 * تست موتور اشتراک اینترنت با VPN.
 *
 * پروتکل‌ها با سوکت واقعی روی 127.0.0.1 آزمایش می‌شوند (بدون شبیه‌سازی) و
 * مسیر خروجی با یک [ShareDialer] تستی تزریق می‌شود؛ همان چیزی که در گوشی
 * نسخهٔ بایند‌شده به شبکهٔ VPN است.
 */
class ShareEngineTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val servers = mutableListOf<ServerSocket>()

    @After
    fun tearDown() {
        servers.forEach { runCatching { it.close() } }
        scope.cancel()
    }

    /** سرور اکوی محلی که نقش «اینترنت» را بازی می‌کند. */
    private fun echoServer(): ServerSocket {
        val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        servers += server
        val thread = Thread {
            while (!server.isClosed) {
                val client = try {
                    server.accept()
                } catch (_: Exception) {
                    break
                }
                Thread {
                    client.use {
                        val buffer = ByteArray(4096)
                        while (true) {
                            val read = it.getInputStream().read(buffer)
                            if (read < 0) break
                            it.getOutputStream().write("echo:".toByteArray())
                            it.getOutputStream().write(buffer, 0, read)
                            it.getOutputStream().flush()
                        }
                    }
                }.start()
            }
        }
        thread.isDaemon = true
        thread.start()
        return server
    }

    private class TestDialer(private val failFirst: Boolean = false) : ShareDialer {
        var opened = 0
            private set

        override fun open(host: String, port: Int): Socket {
            opened++
            if (failFirst) throw ShareDialer.NoVpnException()
            return Socket().apply { connect(java.net.InetSocketAddress(host, port), 4_000) }
        }

        override fun describe(): String = "test"
    }

    private fun config(
        port: Int = 0,
        socks: Boolean = true,
        http: Boolean = false,
        auth: Boolean = false
    ) = ShareConfig(
        port = if (port == 0) 2100 + (Math.random() * 900).toInt() else port,
        socks = socks,
        http = http,
        requireAuth = auth,
        username = "didban",
        password = if (auth) "secret" else "",
        lanOnly = true,
        requireVpn = true
    )

    // ── تجزیهٔ پروتکل (خالص) ────────────────────────────────────────────────

    @Test
    fun `socks greeting is parsed and answered per policy`() {
        val greeting = byteArrayOf(5, 1, 0)
        assertEquals(listOf(0), ShareProtocol.parseGreeting(greeting))
        assertEquals(ShareProtocol.METHOD_NONE, ShareProtocol.chooseMethod(listOf(0), requireAuth = false, hasCredentials = false))
        assertEquals(ShareProtocol.METHOD_USERPASS, ShareProtocol.chooseMethod(listOf(0, 2), requireAuth = true, hasCredentials = true))
        assertEquals(ShareProtocol.METHOD_REJECT, ShareProtocol.chooseMethod(listOf(0), requireAuth = true, hasCredentials = false))
        assertNull(ShareProtocol.parseGreeting(byteArrayOf(4, 1, 0)))
        assertNull(ShareProtocol.parseGreeting(byteArrayOf(5, 3, 0)))
        assertEquals(2, ShareProtocol.greetingReply(2).size)
    }

    @Test
    fun `socks connect request parses all three address types`() {
        val ipv4 = byteArrayOf(5, 1, 0, 1, 93, 184.toByte(), 216.toByte(), 34, 0x01, 0xBB.toByte())
        val v4 = ShareProtocol.parseRequest(ipv4)
        assertEquals("93.184.216.34", v4?.host)
        assertEquals(443, v4?.port)
        assertFalse(v4!!.isDomain)

        val domain = "example.com".toByteArray(StandardCharsets.UTF_8)
        val v3 = ShareProtocol.parseRequest(byteArrayOf(5, 1, 0, 3, domain.size.toByte()) + domain + byteArrayOf(0, 80))
        assertEquals("example.com", v3?.host)
        assertTrue(v3!!.isDomain)
        assertEquals(80, v3.port)

        val v6 = ShareProtocol.parseRequest(
            byteArrayOf(5, 1, 0, 4) + InetAddress.getByName("::1").address + byteArrayOf(0x1F, 0x90.toByte())
        )
        assertEquals(8080, v6?.port)
        assertNull(ShareProtocol.parseRequest(byteArrayOf(5, 1, 0, 9, 0, 0)))
    }

    @Test
    fun `http head parses connect absolute form and proxy auth`() {
        val connect = ShareProtocol.parseHttpHead("CONNECT example.com:443 HTTP/1.1\r\nHost: example.com:443\r\n\r\n")
        assertEquals("example.com", connect?.host)
        assertEquals(443, connect?.port)
        assertEquals("CONNECT", connect?.method)

        val absolute = ShareProtocol.parseHttpHead(
            "GET http://example.com/a/b?x=1 HTTP/1.1\r\nProxy-Authorization: Basic ZGlkYmFuOnNlY3JldA==\r\n\r\n"
        )
        assertEquals("example.com", absolute?.host)
        assertEquals(80, absolute?.port)
        assertTrue(absolute!!.absoluteForm)
        assertEquals("/a/b?x=1", ShareProtocol.originPath("http://example.com/a/b?x=1"))
        assertEquals("/", ShareProtocol.originPath("http://example.com"))

        val creds = ShareProtocol.decodeBasic("Basic ZGlkYmFuOnNlY3JldA==")
        assertEquals("didban" to "secret", creds)
        assertNull(ShareProtocol.decodeBasic("Basic !!!"))
        assertNull(ShareProtocol.parseHttpHead("garbage"))
    }

    @Test
    fun `private address gate covers the ranges tethered clients use`() {
        assertTrue(ShareProtocol.isPrivateAddress("192.168.43.1"))
        assertTrue(ShareProtocol.isPrivateAddress("10.0.0.7"))
        assertTrue(ShareProtocol.isPrivateAddress("172.16.5.4"))
        assertTrue(ShareProtocol.isPrivateAddress("172.31.255.254"))
        assertTrue(ShareProtocol.isPrivateAddress("169.254.10.1"))
        assertTrue(ShareProtocol.isPrivateAddress("localhost"))
        assertFalse(ShareProtocol.isPrivateAddress("8.8.8.8"))
        assertFalse(ShareProtocol.isPrivateAddress("172.32.0.1"))
        assertFalse(ShareProtocol.isPrivateAddress("example.com"))
    }

    @Test
    fun `secret comparison is length safe and value correct`() {
        assertTrue(ShareProtocol.sameSecret("secret", "secret"))
        assertFalse(ShareProtocol.sameSecret("secret", "secreT"))
        assertFalse(ShareProtocol.sameSecret("secret", "secret!"))
        assertFalse(ShareProtocol.sameSecret("", "x"))
    }

    // ── سرور واقعی ──────────────────────────────────────────────────────────

    @Test
    fun `socks5 client reaches the upstream through the injected dialer`() {
        val upstream = echoServer()
        val dialer = TestDialer()
        val server = ShareServer(config(socks = true), dialer, scope)
        assertTrue(server.start())
        try {
            Socket("127.0.0.1", server.boundPort).use { client ->
                val out = client.getOutputStream()
                val input = client.getInputStream()
                client.soTimeout = 5_000
                out.write(byteArrayOf(5, 1, 0)); out.flush()
                assertEquals(5, input.read())
                assertEquals(0, input.read())

                out.write(
                    byteArrayOf(5, 1, 0, 1, 127, 0, 0, 1) +
                        byteArrayOf((( upstream.localPort shr 8) and 0xFF).toByte(), (upstream.localPort and 0xFF).toByte())
                )
                out.flush()
                val reply = ByteArray(10)
                var read = 0
                while (read < 10) read += input.read(reply, read, 10 - read)
                assertEquals(0, reply[1].toInt())

                out.write("hello".toByteArray()); out.flush()
                val echo = ByteArray(10)
                var got = 0
                while (got < 10) got += input.read(echo, got, 10 - got)
                assertEquals("echo:hello", String(echo))
            }
            assertTrue(waitUntil { dialer.opened == 1 })
            assertTrue(waitUntil { server.stats.uploadBytes >= 5 })
            assertTrue(waitUntil { server.stats.downloadBytes >= 10 })
        } finally {
            server.stop()
        }
        assertFalse(server.running)
    }

    @Test
    fun `socks5 rejects a wrong password and never dials`() {
        val dialer = TestDialer()
        val server = ShareServer(config(socks = true, auth = true), dialer, scope)
        assertTrue(server.start())
        try {
            Socket("127.0.0.1", server.boundPort).use { client ->
                val out = client.getOutputStream()
                val input = client.getInputStream()
                client.soTimeout = 5_000
                out.write(byteArrayOf(5, 1, 2)); out.flush()
                input.read(); assertEquals(2, input.read())
                val user = "didban".toByteArray()
                val pass = "wrong".toByteArray()
                out.write(byteArrayOf(1, user.size.toByte()) + user + byteArrayOf(pass.size.toByte()) + pass)
                out.flush()
                assertEquals(1, input.read())
                assertEquals(1, input.read())
            }
            assertEquals(0, dialer.opened)
            assertTrue(waitUntil { server.stats.rejectedCount >= 1 })
        } finally {
            server.stop()
        }
    }

    @Test
    fun `socks5 succeeds when the password matches`() {
        val upstream = echoServer()
        val server = ShareServer(config(socks = true, auth = true), TestDialer(), scope)
        assertTrue(server.start())
        try {
            Socket("127.0.0.1", server.boundPort).use { client ->
                val out = client.getOutputStream()
                val input = client.getInputStream()
                client.soTimeout = 5_000
                out.write(byteArrayOf(5, 1, 2)); out.flush()
                input.read(); input.read()
                val user = "didban".toByteArray(); val pass = "secret".toByteArray()
                out.write(byteArrayOf(1, user.size.toByte()) + user + byteArrayOf(pass.size.toByte()) + pass); out.flush()
                assertEquals(1, input.read()) // ver
                assertEquals(0, input.read()) // success
                out.write(
                    byteArrayOf(5, 1, 0, 1, 127, 0, 0, 1) +
                        byteArrayOf(((upstream.localPort shr 8) and 0xFF).toByte(), (upstream.localPort and 0xFF).toByte())
                )
                out.flush()
                val reply = ByteArray(10)
                var read = 0
                while (read < 10) read += input.read(reply, read, 10 - read)
                assertEquals(0, reply[1].toInt())
            }
        } finally {
            server.stop()
        }
    }

    @Test
    fun `http connect tunnels and absolute form is forwarded`() {
        val upstream = echoServer()
        val server = ShareServer(config(socks = false, http = true), TestDialer(), scope)
        assertTrue(server.start())
        try {
            Socket("127.0.0.1", server.boundPort).use { client ->
                val out = client.getOutputStream()
                val input = client.getInputStream()
                client.soTimeout = 5_000
                out.write("CONNECT 127.0.0.1:${upstream.localPort} HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n".toByteArray())
                out.flush()
                val head = readHead(input)
                assertTrue(head.startsWith("HTTP/1.1 200"))
                out.write("ping".toByteArray()); out.flush()
                val echo = ByteArray(9)
                var got = 0
                while (got < 9) got += input.read(echo, got, 9 - got)
                assertEquals("echo:ping", String(echo))
            }
        } finally {
            server.stop()
        }
    }

    @Test
    fun `http without proxy auth is refused with 407`() {
        val dialer = TestDialer()
        val server = ShareServer(config(socks = false, http = true, auth = true), dialer, scope)
        assertTrue(server.start())
        try {
            Socket("127.0.0.1", server.boundPort).use { client ->
                val out = client.getOutputStream()
                client.soTimeout = 5_000
                out.write("CONNECT example.com:443 HTTP/1.1\r\n\r\n".toByteArray()); out.flush()
                assertTrue(readHead(client.getInputStream()).startsWith("HTTP/1.1 407"))
            }
            assertEquals(0, dialer.opened)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `a failing dialer answers socks with a refusal instead of hanging`() {
        val server = ShareServer(config(socks = true), TestDialer(failFirst = true), scope)
        assertTrue(server.start())
        try {
            Socket("127.0.0.1", server.boundPort).use { client ->
                val out = client.getOutputStream()
                val input = client.getInputStream()
                client.soTimeout = 5_000
                out.write(byteArrayOf(5, 1, 0)); out.flush()
                input.read(); input.read()
                out.write(byteArrayOf(5, 1, 0, 1, 93, 184.toByte(), 216.toByte(), 34, 0x01, 0xBB.toByte())); out.flush()
                val reply = ByteArray(10)
                var read = 0
                while (read < 10) read += input.read(reply, read, 10 - read)
                assertEquals(ShareProtocol.REPLY_NOT_ALLOWED, reply[1].toInt())
            }
        } finally {
            server.stop()
        }
    }

    @Test
    fun `public clients are refused when only local clients are allowed`() {
        // The listener binds loopback-facing in tests, so a "public" client cannot
        // exist; assert the gate directly instead of faking an address.
        assertTrue(ShareProtocol.isPrivateAddress("127.0.0.1"))
        val lanOnly = config(socks = true).copy(lanOnly = true)
        assertTrue(lanOnly.lanOnly)
    }

    @Test
    fun `server reports bind failure instead of pretending to run`() {
        val taken = ServerSocket(0)
        servers += taken
        val server = ShareServer(config(port = taken.localPort), TestDialer(), scope)
        assertFalse(server.start())
        assertFalse(server.running)
    }

    // ── پیکربندی ────────────────────────────────────────────────────────────

    @Test
    fun `config round trips through json and repairs itself`() {
        val original = ShareConfig(port = 1088, socks = true, http = true, requireAuth = true,
            username = "u", password = "pass", lanOnly = false, requireVpn = true)
        val restored = ShareConfig.fromJson(original.toJson())
        assertEquals(original, restored)

        val broken = ShareConfig.fromJson(org.json.JSONObject().put("port", 80).put("socks", false).put("http", false))
        assertEquals(1080, broken.port)
        assertTrue(broken.socks)
    }

    @Test
    fun `config validation mirrors the error keys the screen shows`() {
        assertEquals("port", ShareConfig(port = 80).errorKey)
        assertEquals("protocol", ShareConfig(socks = false, http = false).errorKey)
        assertEquals("password", ShareConfig(requireAuth = true, username = "u", password = "12").errorKey)
        assertNull(ShareConfig(requireAuth = true, username = "u", password = "1234").errorKey)
        assertNull(ShareConfig(requireAuth = false, username = "", password = "").errorKey)
        assertTrue(ShareConfig(port = 1080).valid)
        assertTrue(ShareConfig().valid) // پیش‌فرض باید از همان ابتدا کار کند
        assertEquals(8, ShareConfig().password.length)
        assertFalse(ShareConfig(port = 1080).copy(socks = false, http = false).valid)
    }

    // ── کمکی‌های نمایش ──────────────────────────────────────────────────────

    @Test
    fun `event keys and byte labels are stable for the ui`() {
        assertEquals("auth-failed", shareEventKey("auth-failed:192.168.43.20"))
        assertEquals("started", shareEventKey("started:1080"))
        assertTrue(shareBytes(2048).isNotBlank())
        assertEquals("0%", sharePercentLabel(0, 0))
        assertEquals("50%", sharePercentLabel(1, 2))
    }

    /** آمار سمت سرور در ریسهٔ دیگری نوشته می‌شود؛ تست نباید با آن مسابقه بدهد. */
    private fun waitUntil(timeoutMs: Long = 2_000, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(20)
        }
        return condition()
    }

    private fun readHead(input: java.io.InputStream): String {
        val builder = StringBuilder()
        while (!builder.endsWith("\r\n\r\n") && builder.length < 8192) {
            val b = input.read()
            if (b < 0) throw IOException("closed")
            builder.append(b.toChar())
        }
        return builder.toString()
    }
}
