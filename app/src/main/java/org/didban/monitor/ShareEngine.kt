package org.didban.monitor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

/**
 * اشتراک اینترنت همراه با VPN — موتور داده (فاز ۱، بدون روت).
 *
 * مسئله: اندروید ترافیک دستگاه‌های متصل به هات‌اسپات را به `tun0` نمی‌فرستد؛
 * NAT هات‌اسپات روی اینترفیس فیزیکی بسته می‌شود. بدون روت نمی‌توان قاعدهٔ
 * `iptables`/`ip rule` اضافه کرد، پس راه ممکن این است:
 *
 *   دستگاه دیگر → پروکسی روی گوشی → سوکتِ بایند‌شده به شبکهٔ VPN → اینترنت
 *
 * این فایل فقط منطق پروتکل و رلهٔ داده را دارد و هیچ API اندرویدی لازم ندارد
 * (شبکه از راه [ShareDialer] تزریق می‌شود)، پس روی JVM قابل تست است.
 */
data class ShareConfig(
    val port: Int = 1080,
    val socks: Boolean = true,
    val http: Boolean = false,
    val requireAuth: Boolean = true,
    val username: String = "didban",
    // رمز پیش‌فرض تصادفی ساخته می‌شود تا پروکسی از همان لحظهٔ اول قفل باشد؛
    // الگوی UUID در همین پروژه سابقه دارد (SinglePortRoute).
    val password: String = generatePassword(),
    val lanOnly: Boolean = true,
    val requireVpn: Boolean = true
) {
    /** پورت‌های مجاز: بالای ۱۰۲۴ تا نیازی به روت نباشد. */
    val valid: Boolean get() = port in 1024..65535 && (socks || http) &&
        (!requireAuth || (username.isNotBlank() && password.length >= 4))

    val errorKey: String? get() = when {
        port !in 1024..65535 -> "port"
        !socks && !http -> "protocol"
        requireAuth && username.isBlank() -> "username"
        requireAuth && password.length < 4 -> "password"
        else -> null
    }

    fun toJson(): JSONObject = JSONObject()
        .put("port", port).put("socks", socks).put("http", http)
        .put("requireAuth", requireAuth).put("username", username)
        .put("password", password).put("lanOnly", lanOnly).put("requireVpn", requireVpn)

    companion object {
        /** رمز پیش‌فرض: ۸ نویسه از الفبای بی‌ابهام (بدون 0/O/1/l) و بدون نویسهٔ رزرو JSON. */
        fun generatePassword(random: java.util.Random = java.util.Random()): String {
            val alphabet = "abcdefghijkmnpqrstuvwxyz23456789"
            return (1..8).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
        }

        fun fromJson(o: JSONObject?): ShareConfig {
            if (o == null) return ShareConfig()
            val defaults = ShareConfig()
            return ShareConfig(
                port = o.optInt("port", defaults.port).takeIf { it in 1024..65535 } ?: defaults.port,
                socks = o.optBoolean("socks", defaults.socks),
                http = o.optBoolean("http", defaults.http),
                requireAuth = o.optBoolean("requireAuth", defaults.requireAuth),
                username = o.optString("username", defaults.username),
                password = o.optString("password", defaults.password),
                lanOnly = o.optBoolean("lanOnly", defaults.lanOnly),
                requireVpn = o.optBoolean("requireVpn", defaults.requireVpn)
            ).let { if (it.socks || it.http) it else it.copy(socks = true) }
        }
    }
}

/** یک درخواست SOCKS5 تجزیه‌شده. */
data class SocksRequest(val command: Int, val host: String, val port: Int, val isDomain: Boolean)

/** یک درخواست پروکسی HTTP تجزیه‌شده. */
data class HttpRequest(val method: String, val host: String, val port: Int, val proxyAuth: String?, val absoluteForm: Boolean)

/** آمار زندهٔ اشتراک؛ همهٔ شمارنده‌ها اتمیک‌اند تا از چند ریسه امن بمانند. */
class ShareStats {
    private val clients = AtomicInteger(0)
    private val up = AtomicLong(0)
    private val down = AtomicLong(0)
    private val rejected = AtomicLong(0)
    private val peers = ConcurrentHashMap<String, Long>()

    val clientCount: Int get() = clients.get()
    val uploadBytes: Long get() = up.get()
    val downloadBytes: Long get() = down.get()
    val rejectedCount: Long get() = rejected.get()

    fun peers(): Map<String, Long> = peers.toMap()

    internal fun clientOpened() = clients.incrementAndGet()

    internal fun clientClosed() {
        clients.updateAndGet { if (it > 0) it - 1 else 0 }
    }

    /** بایت‌ها هم کل و هم به‌ازای هر دستگاه شمرده می‌شوند؛ فهرست UI از همین می‌خواند. */
    internal fun addUp(n: Long, peer: String) {
        up.addAndGet(n)
        peers.merge(peer, n, Long::plus)
    }

    internal fun addDown(n: Long, peer: String) {
        down.addAndGet(n)
        peers.merge(peer, n, Long::plus)
    }
    internal fun reject() = rejected.incrementAndGet()

    internal fun reset() {
        clients.set(0); up.set(0); down.set(0); rejected.set(0); peers.clear()
    }
}

/** منطق خالص پروتکل — بدون سوکت، برای تست قطعی. */
internal object ShareProtocol {

    const val SOCKS_VERSION = 0x05
    const val METHOD_NONE = 0x00
    const val METHOD_USERPASS = 0x02
    const val METHOD_REJECT = 0xFF
    const val CMD_CONNECT = 0x01
    const val ATYP_IPV4 = 0x01
    const val ATYP_DOMAIN = 0x03
    const val ATYP_IPV6 = 0x04

    const val REPLY_OK = 0x00
    const val REPLY_FAILURE = 0x01
    const val REPLY_NOT_ALLOWED = 0x02
    const val REPLY_HOST_UNREACHABLE = 0x04
    const val REPLY_CMD_UNSUPPORTED = 0x07

    /** هدر انتخاب روش (RFC 1928): نسخه، تعداد، فهرست روش‌ها. */
    fun parseGreeting(head: ByteArray): List<Int>? {
        if (head.size < 3 || head[0].toInt() != SOCKS_VERSION) return null
        val count = head[1].toInt() and 0xFF
        if (head.size < 2 + count) return null
        return (0 until count).map { head[2 + it].toInt() and 0xFF }
    }

    /** روش نهایی: اگر احراز هویت خواسته شده و رمز داریم، یوزر/پس؛ وگرنه بی‌احراز هویت. */
    fun chooseMethod(offered: List<Int>, requireAuth: Boolean, hasCredentials: Boolean): Int = when {
        requireAuth && !hasCredentials -> METHOD_REJECT
        requireAuth && offered.contains(METHOD_USERPASS) -> METHOD_USERPASS
        !requireAuth && offered.contains(METHOD_NONE) -> METHOD_NONE
        !requireAuth && offered.contains(METHOD_USERPASS) -> METHOD_USERPASS
        offered.contains(METHOD_NONE) -> METHOD_NONE
        else -> METHOD_REJECT
    }

    fun greetingReply(method: Int): ByteArray = byteArrayOf(SOCKS_VERSION.toByte(), method.toByte())

    /** نام کاربری/رمز (RFC 1929). */
    fun parseUserPass(body: ByteArray): Pair<String, String>? {
        if (body.size < 3) return null
        val userLen = body[1].toInt() and 0xFF
        if (body.size < 2 + userLen + 1) return null
        val user = String(body, 2, userLen, Charsets.UTF_8)
        val passLen = body[2 + userLen].toInt() and 0xFF
        if (body.size < 3 + userLen + passLen) return null
        val pass = String(body, 3 + userLen, passLen, Charsets.UTF_8)
        return user to pass
    }

    fun userPassReply(ok: Boolean): ByteArray = byteArrayOf(0x01, if (ok) 0x00 else 0x01)

    /** درخواست اتصال (RFC 1928): نسخه، فرمان، رزرو، نوع آدرس، آدرس، پورت. */
    fun parseRequest(body: ByteArray): SocksRequest? {
        if (body.size < 4 || body[0].toInt() != SOCKS_VERSION) return null
        val command = body[1].toInt() and 0xFF
        return when (body[3].toInt() and 0xFF) {
            ATYP_IPV4 -> {
                if (body.size < 10) null
                else SocksRequest(command, "${body[4].toInt() and 0xFF}.${body[5].toInt() and 0xFF}." +
                    "${body[6].toInt() and 0xFF}.${body[7].toInt() and 0xFF}", portOf(body, 8), false)
            }
            ATYP_DOMAIN -> {
                val len = body.getOrNull(4)?.toInt()?.and(0xFF) ?: return null
                if (body.size < 5 + len + 2) null
                else SocksRequest(command, String(body, 5, len, Charsets.UTF_8), portOf(body, 5 + len), true)
            }
            ATYP_IPV6 -> {
                if (body.size < 22) null
                else {
                    val bytes = body.copyOfRange(4, 20)
                    SocksRequest(command, InetAddress.getByAddress(bytes).hostAddress ?: "", portOf(body, 20), false)
                }
            }
            else -> null
        }
    }

    private fun portOf(body: ByteArray, at: Int): Int =
        ((body[at].toInt() and 0xFF) shl 8) or (body[at + 1].toInt() and 0xFF)

    fun reply(code: Int, address: ByteArray = byteArrayOf(0, 0, 0, 0), port: Int = 0): ByteArray =
        byteArrayOf(SOCKS_VERSION.toByte(), code.toByte(), 0x00, ATYP_IPV4.toByte()) +
            address.copyOf(4) + byteArrayOf((port shr 8).toByte(), port.toByte())

    /** سرآیند پروکسی HTTP: خط درخواست + هدرها (تا خط خالی). */
    fun parseHttpHead(head: String): HttpRequest? {
        val lines = head.split("\r\n").ifEmpty { head.split("\n") }
        val requestLine = lines.firstOrNull()?.trim().orEmpty()
        val parts = requestLine.split(" ")
        if (parts.size < 3) return null
        val method = parts[0].uppercase()
        val target = parts[1]
        val auth = lines.drop(1).firstOrNull { it.lowercase().startsWith("proxy-authorization:") }
            ?.substringAfter(":")?.trim()
        val absolute = target.startsWith("http://", true) || target.startsWith("https://", true)
        if (method == "CONNECT") {
            val at = target.lastIndexOf(':')
            if (at <= 0) return null
            val host = target.substring(0, at).trim('[', ']')
            val port = target.substring(at + 1).toIntOrNull() ?: return null
            return HttpRequest(method, host, port, auth, false)
        }
        if (!absolute) return null
        val withoutScheme = target.substringAfter("://")
        val hostPort = withoutScheme.substringBefore('/')
        val host = hostPort.substringBefore(':').trim('[', ']')
        val port = hostPort.substringAfter(':', if (target.startsWith("https", true)) "443" else "80").toIntOrNull() ?: 80
        return HttpRequest(method, host, port, auth, true)
    }

    /** مسیر درخواست در شکل مبدأ‌محور که باید به سرور مقصد فرستاده شود. */
    fun originPath(target: String): String {
        val withoutScheme = target.substringAfter("://", target)
        val slash = withoutScheme.indexOf('/')
        return if (slash < 0) "/" else withoutScheme.substring(slash)
    }

    fun decodeBasic(auth: String?): Pair<String, String>? {
        if (auth == null) return null
        val token = auth.substringAfter(' ', "").trim()
        if (token.isEmpty()) return null
        return try {
            // java.util.Base64 (minSdk 26) به‌جای نسخهٔ اندرویدی، تا این منطق در
            // تست‌های JVM واقعاً اجرا شود و نه روی stub بی‌اثر.
            val raw = java.util.Base64.getDecoder().decode(token)
            val text = String(raw, Charsets.UTF_8)
            val at = text.indexOf(':')
            if (at < 0) null else text.substring(0, at) to text.substring(at + 1)
        } catch (_: Exception) {
            null
        }
    }

    /** آیا این آدرس خصوصی/LAN است؟ (فقط برای محدودکردن مشتریان) */
    fun isPrivateAddress(host: String): Boolean {
        val v4 = host.substringBefore('%').split('.').mapNotNull { it.toIntOrNull() }
        if (v4.size == 4 && v4.all { it in 0..255 }) {
            return when {
                v4[0] == 10 -> true
                v4[0] == 172 && v4[1] in 16..31 -> true
                v4[0] == 192 && v4[1] == 168 -> true
                v4[0] == 169 && v4[1] == 254 -> true
                v4[0] == 127 -> true
                else -> false
            }
        }
        val lower = host.lowercase()
        if (lower == "localhost" || lower == "::1") return true
        // fc00::/7 و fe80::/10
        return lower.startsWith("fc") || lower.startsWith("fd") || lower.startsWith("fe8") ||
            lower.startsWith("fe9") || lower.startsWith("fea") || lower.startsWith("feb")
    }

    /** کوتاه‌کردن پیام برای مقایسهٔ رمز با زمان ثابت. */
    fun sameSecret(a: String, b: String): Boolean {
        val x = a.toByteArray(Charsets.UTF_8)
        val y = b.toByteArray(Charsets.UTF_8)
        if (x.size != y.size) return false
        var diff = 0
        for (i in x.indices) diff = diff or (x[i].toInt() xor y[i].toInt())
        return diff == 0
    }
}

/**
 * تزریق شبکه: پیاده‌سازی اندرویدی سوکت را به شبکهٔ VPN بایند می‌کند و
 * پیاده‌سازی تستی سوکت معمولی می‌سازد.
 */
interface ShareDialer {
    /** اتصال خروجی به host:port؛ باید یا از VPN برود یا استثنا بدهد. */
    fun open(host: String, port: Int): Socket

    /** توضیح کوتاه مسیر برای نمایش در رابط کاربری. */
    fun describe(): String

    /** خطای «VPN روشن نیست» که مدیریت آن با [ShareRuntime] است. */
    class NoVpnException : IOException("vpn-not-active")
}

/**
 * سرور پروکسی اشتراک. یک سوکت شنونده روی همهٔ اینترفیس‌ها (شامل هات‌اسپات)
 * باز می‌کند و هر اتصال را با پروتکل SOCKS5 یا HTTP CONNECT می‌پذیرد و به
 * مسیر VPN رله می‌کند.
 */
class ShareServer(
    private val config: ShareConfig,
    private val dialer: ShareDialer,
    private val scope: CoroutineScope,
    private val onEvent: (String) -> Unit = {}
) {
    private val listener = AtomicReference<ServerSocket?>(null)
    private val jobs = AtomicReference<Job?>(null)
    val stats = ShareStats()

    val running: Boolean get() = listener.get()?.isClosed == false

    val boundPort: Int get() = listener.get()?.localPort ?: config.port

    fun start(): Boolean {
        if (running) return true
        val socket = try {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress("0.0.0.0", config.port))
            }
        } catch (e: Exception) {
            onEvent("bind-failed:${e.javaClass.simpleName}")
            return false
        }
        listener.set(socket)
        stats.reset()
        jobs.set(scope.launch(Dispatchers.IO) {
            while (isActive && !socket.isClosed) {
                val client = try {
                    socket.accept()
                } catch (_: Exception) {
                    break
                }
                scope.launch(Dispatchers.IO) { handle(client) }
            }
        })
        onEvent("started:${socket.localPort}")
        return true
    }

    fun stop() {
        jobs.getAndSet(null)?.cancel()
        try {
            listener.getAndSet(null)?.close()
        } catch (_: Exception) {
        }
        onEvent("stopped")
    }

    private suspend fun handle(client: Socket) {
        val peer = client.inetAddress?.hostAddress.orEmpty()
        try {
            client.tcpNoDelay = true
            client.soTimeout = HANDSHAKE_TIMEOUT_MS
            if (config.lanOnly && !ShareProtocol.isPrivateAddress(peer)) {
                stats.reject()
                onEvent("rejected:$peer")
                return
            }
            val input = client.getInputStream()
            val first = input.read()
            if (first < 0) return
            when {
                first == ShareProtocol.SOCKS_VERSION ->
                    serveSocks(client, input, client.getOutputStream(), peer, first.toByte())
                first == 'C'.code || first == 'G'.code || first == 'P'.code || first == 'H'.code ->
                    serveHttp(client, input, client.getOutputStream(), peer, first.toByte())
                else -> onEvent("unsupported:$peer")
            }
        } catch (e: Exception) {
            if (e !is java.net.SocketException && e !is IOException) onEvent("error:${e.javaClass.simpleName}")
        } finally {
            try {
                client.close()
            } catch (_: Exception) {
            }
        }
    }

    // ── SOCKS5 ────────────────────────────────────────────────────────────────

    private suspend fun serveSocks(
        client: Socket, input: InputStream, output: OutputStream, peer: String, firstByte: Byte
    ) {
        // نسخه در دیسپچر خوانده شده است؛ بقیهٔ سرآیند از همین‌جا ادامه می‌یابد.
        val methodCount = readExact(input, 1) ?: return
        val methods = readExact(input, methodCount[0].toInt() and 0xFF) ?: return
        val offered = ShareProtocol.parseGreeting(byteArrayOf(firstByte) + methodCount + methods) ?: return
        val hasCreds = config.password.isNotEmpty()
        val method = ShareProtocol.chooseMethod(offered, config.requireAuth, hasCreds)
        output.write(ShareProtocol.greetingReply(method)); output.flush()
        if (method == ShareProtocol.METHOD_REJECT) {
            stats.reject(); onEvent("auth-rejected:$peer"); return
        }
        if (method == ShareProtocol.METHOD_USERPASS) {
            val head = readExact(input, 2) ?: return
            val user = readExact(input, head[1].toInt() and 0xFF) ?: return
            val passLen = readExact(input, 1) ?: return
            val pass = readExact(input, passLen[0].toInt() and 0xFF) ?: return
            val creds = ShareProtocol.parseUserPass(head + user + passLen + pass)
            val ok = creds != null && ShareProtocol.sameSecret(creds.first, config.username) &&
                ShareProtocol.sameSecret(creds.second, config.password)
            output.write(ShareProtocol.userPassReply(ok)); output.flush()
            if (!ok) {
                stats.reject(); onEvent("auth-failed:$peer"); return
            }
        }
        val head = readExact(input, 4) ?: return
        val rest = when (head[3].toInt() and 0xFF) {
            ShareProtocol.ATYP_IPV4 -> readExact(input, 6)
            ShareProtocol.ATYP_DOMAIN -> {
                val len = readExact(input, 1) ?: return
                readExact(input, (len[0].toInt() and 0xFF) + 2)
            }
            ShareProtocol.ATYP_IPV6 -> readExact(input, 18)
            else -> null
        } ?: return
        val request = ShareProtocol.parseRequest(head + rest)
        if (request == null) {
            output.write(ShareProtocol.reply(ShareProtocol.REPLY_FAILURE)); output.flush(); return
        }
        if (request.command != ShareProtocol.CMD_CONNECT) {
            output.write(ShareProtocol.reply(ShareProtocol.REPLY_CMD_UNSUPPORTED)); output.flush()
            onEvent("unsupported-command:$peer")
            return
        }
        relay(client, input, output, peer, request.host, request.port, socks = true)
    }

    // ── HTTP ─────────────────────────────────────────────────────────────────

    private suspend fun serveHttp(
        client: Socket, input: InputStream, output: OutputStream, peer: String, firstByte: Byte
    ) {
        val head = readHttpHead(input, firstByte) ?: return
        val request = ShareProtocol.parseHttpHead(head)
        if (request == null) {
            output.write("HTTP/1.1 400 Bad Request\r\nConnection: close\r\n\r\n".toByteArray()); output.flush()
            return
        }
        if (config.requireAuth) {
            val creds = ShareProtocol.decodeBasic(request.proxyAuth)
            val ok = creds != null && ShareProtocol.sameSecret(creds.first, config.username) &&
                ShareProtocol.sameSecret(creds.second, config.password)
            if (!ok) {
                output.write(
                    ("HTTP/1.1 407 Proxy Authentication Required\r\n" +
                        "Proxy-Authenticate: Basic realm=\"didban\"\r\n" +
                        "Connection: close\r\n\r\n").toByteArray()
                )
                output.flush(); stats.reject(); onEvent("auth-failed:$peer")
                return
            }
        }
        val upstream = try {
            dialer.open(request.host, request.port)
        } catch (e: ShareDialer.NoVpnException) {
            output.write("HTTP/1.1 503 VPN Unavailable\r\nConnection: close\r\n\r\n".toByteArray())
            output.flush(); onEvent("no-vpn:$peer"); return
        } catch (e: Exception) {
            output.write("HTTP/1.1 502 Bad Gateway\r\nConnection: close\r\n\r\n".toByteArray())
            output.flush(); onEvent("dial-failed:$peer"); return
        }
        upstream.use { target ->
            client.soTimeout = 0
            if (request.method == "CONNECT") {
                output.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray()); output.flush()
            } else {
                val rebuilt = buildString {
                    append(request.method).append(' ').append(ShareProtocol.originPath(headLineTarget(head))).append(" ")
                        .append(headLineVersion(head)).append("\r\n")
                    head.split("\r\n").drop(1).filter { it.isNotBlank() }
                        .filterNot { it.lowercase().startsWith("proxy-authorization:") }
                        .filterNot { it.lowercase().startsWith("proxy-connection:") }
                        .forEach { append(it).append("\r\n") }
                    append("\r\n")
                }
                target.getOutputStream().write(rebuilt.toByteArray()); target.getOutputStream().flush()
            }
            stats.clientOpened()
            try {
                val up = scope.launch(Dispatchers.IO) { splice(peer, input, target.getOutputStream(), up = true) }
                splice(peer, target.getInputStream(), output, up = false)
                up.join()
            } finally {
                stats.clientClosed()
            }
        }
    }

    private fun headLineTarget(head: String): String = head.substringBefore("\r\n").split(" ").getOrNull(1).orEmpty()
    private fun headLineVersion(head: String): String = head.substringBefore("\r\n").split(" ").getOrNull(2) ?: "HTTP/1.1"

    // ── رله ──────────────────────────────────────────────────────────────────

    private suspend fun relay(
        client: Socket, input: InputStream, output: OutputStream, peer: String,
        host: String, port: Int, socks: Boolean
    ) {
        val upstream = try {
            dialer.open(host, port)
        } catch (e: ShareDialer.NoVpnException) {
            output.write(ShareProtocol.reply(ShareProtocol.REPLY_NOT_ALLOWED)); output.flush()
            onEvent("no-vpn:$peer"); return
        } catch (_: Exception) {
            output.write(ShareProtocol.reply(ShareProtocol.REPLY_HOST_UNREACHABLE)); output.flush()
            onEvent("dial-failed:$peer"); return
        }
        upstream.use { target ->
            if (socks) {
                output.write(ShareProtocol.reply(ShareProtocol.REPLY_OK)); output.flush()
            }
            client.soTimeout = 0
            stats.clientOpened()
            try {
                val up = scope.launch(Dispatchers.IO) { splice(peer, input, target.getOutputStream(), up = true) }
                splice(peer, target.getInputStream(), output, up = false)
                up.join()
            } finally {
                stats.clientClosed()
            }
        }
    }

    private suspend fun splice(peer: String, from: InputStream, to: OutputStream, up: Boolean) {
        val buffer = ByteArray(BUFFER_BYTES)
        try {
            while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                val read = from.read(buffer)
                if (read < 0) break
                to.write(buffer, 0, read)
                to.flush()
                if (up) stats.addUp(read.toLong(), peer) else stats.addDown(read.toLong(), peer)
            }
        } catch (_: Exception) {
            // دستگاه قطع شد؛ بستن سوکت‌ها در بلوک finally بالادست انجام می‌شود.
        }
    }

    private fun readExact(input: InputStream, count: Int): ByteArray? {
        val buffer = ByteArray(count)
        var read = 0
        while (read < count) {
            val n = input.read(buffer, read, count - read)
            if (n < 0) return null
            read += n
        }
        return buffer
    }

    private fun readHttpHead(input: InputStream, firstByte: Byte): String? {
        val builder = StringBuilder().append(firstByte.toInt().toChar())
        var matched = 0
        val terminator = "\r\n\r\n"
        var last = firstByte.toInt().toChar()
        try {
            while (builder.length < MAX_HEAD_BYTES) {
                val b = input.read()
                if (b < 0) return null
                val c = b.toChar()
                builder.append(c)
                last = c
                if (c == terminator[matched]) {
                    matched++
                    if (matched == 4) return builder.toString()
                } else {
                    matched = if (c == '\r') 1 else 0
                }
            }
        } catch (_: Exception) {
            return null
        }
        return if (last == '\n') builder.toString() else null
    }

    companion object {
        const val HANDSHAKE_TIMEOUT_MS = 15_000
        const val BUFFER_BYTES = 16 * 1024
        const val MAX_HEAD_BYTES = 16 * 1024
    }
}

/** آخرین رویداد موتور (برای خطای نمایشی) با ترجمه در لایهٔ رابط کاربری. */
internal fun shareEventKey(event: String): String = event.substringBefore(':')

/** کمکی برای نمایش نرخ: بایت → مقدار خوانا با همان سنجهٔ پروژه. */
internal fun shareBytes(value: Long): String = Fmt.bytes(min(value, Long.MAX_VALUE))
