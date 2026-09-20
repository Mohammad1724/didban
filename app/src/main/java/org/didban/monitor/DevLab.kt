package org.didban.monitor

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.Inet4Address
import java.net.InetAddress
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object DevLabTools {

    // ── Base64 & URL ─────────────────────────────────────────────────────────

    fun base64Encode(text: String): String =
        Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    fun base64Decode(b64: String): String = try {
        String(Base64.decode(b64.trim(), Base64.DEFAULT), Charsets.UTF_8)
    } catch (e: Exception) {
        "Error: Invalid Base64 string"
    }

    fun urlEncode(text: String): String =
        URLEncoder.encode(text, "UTF-8")

    fun urlDecode(text: String): String = try {
        URLDecoder.decode(text, "UTF-8")
    } catch (e: Exception) {
        "Error: Invalid URL encoded text"
    }

    // ── Hashing & Identifier ─────────────────────────────────────────────────

    fun hash(text: String, algo: String): String {
        val md = MessageDigest.getInstance(algo)
        val digest = md.digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { String.format("%02x", it) }
    }

    fun identifyHash(hash: String): String {
        val h = hash.trim()
        val len = h.length
        return when {
            h.startsWith("\$2a\$") || h.startsWith("\$2b\$") || h.startsWith("\$2y\$") -> "Bcrypt Hash"
            h.startsWith("\$argon2id\$") || h.startsWith("\$argon2i\$") -> "Argon2 Hash"
            h.startsWith("\$6\$") -> "SHA-512 Unix Crypt"
            h.startsWith("\$1\$") -> "MD5 Unix Crypt"
            len == 32 && h.all { it in "0123456789abcdefABCDEF" } -> "MD5 / NTLM Hash"
            len == 40 && h.all { it in "0123456789abcdefABCDEF" } -> "SHA-1 / RIPEMD-160 Hash"
            len == 64 && h.all { it in "0123456789abcdefABCDEF" } -> "SHA-256 / HMAC-SHA256"
            len == 128 && h.all { it in "0123456789abcdefABCDEF" } -> "SHA-512 Hash"
            h.count { it == '.' } == 2 -> "JSON Web Token (JWT)"
            else -> "Unknown / Custom Hash"
        }
    }

    // ── JSON Formatter & Minifier ────────────────────────────────────────────

    fun formatJson(input: String): String = try {
        val trimmed = input.trim()
        if (trimmed.startsWith("{")) {
            JSONObject(trimmed).toString(2)
        } else if (trimmed.startsWith("[")) {
            JSONArray(trimmed).toString(2)
        } else {
            "Invalid JSON structure"
        }
    } catch (e: Exception) {
        "JSON Error: ${e.message}"
    }

    fun minifyJson(input: String): String = try {
        val trimmed = input.trim()
        if (trimmed.startsWith("{")) {
            JSONObject(trimmed).toString()
        } else if (trimmed.startsWith("[")) {
            JSONArray(trimmed).toString()
        } else {
            trimmed.replace("\\s+".toRegex(), "")
        }
    } catch (e: Exception) {
        "JSON Error: ${e.message}"
    }

    // ── Subnet / CIDR Calculator ─────────────────────────────────────────────

    data class SubnetInfo(
        val network: String,
        val broadcast: String,
        val firstHost: String,
        val lastHost: String,
        val netmask: String,
        val wildcard: String,
        val usableHosts: Long,
        val totalHosts: Long
    )

    /** Stable error codes for subnet input problems; the UI maps them to localized copy. */
    class SubnetInputException(val code: String) : IllegalArgumentException(code)

    data class SubnetRequest(val host: String, val prefix: Int, val prefixAssumed: Boolean)

    /** True for digit-and-dot input (valid or not); anything else is treated as a domain name. */
    fun looksLikeIpv4(host: String): Boolean = host.isNotEmpty() && host.all { it.isDigit() || it == '.' }

    private val hostPattern = Regex("^[A-Za-z0-9]([A-Za-z0-9.-]{0,251}[A-Za-z0-9])?\$")

    /**
     * Parses the flexible subnet input: "ip/prefix", bare "ip" (assumes /24),
     * "domain" or "domain/prefix". Tolerates a pasted URL scheme, a ":port"
     * suffix and a trailing slash. Throws [SubnetInputException] with code
     * "format", "prefix" or "ipv6".
     */
    fun parseSubnetRequest(rawInput: String): SubnetRequest {
        var text = rawInput.trim()
        if (text.startsWith("http://", ignoreCase = true) || text.startsWith("https://", ignoreCase = true)) {
            // A pasted link contributes only its host; the path is not a prefix.
            text = text.substringAfter("://").substringBefore("/")
        }
        if (text.isEmpty()) throw SubnetInputException("format")
        text = text.substringBefore("?").substringBefore("#").trimEnd('/')
        if (text.isEmpty()) throw SubnetInputException("format")

        val host: String
        val prefix: Int
        val assumed: Boolean
        if ("/" in text) {
            val slash = text.indexOf("/")
            host = text.substring(0, slash).trim()
            val prefixText = text.substring(slash + 1).trim()
            if (host.isEmpty() || prefixText.isEmpty() || "/" in prefixText) throw SubnetInputException("format")
            prefix = prefixText.toIntOrNull() ?: throw SubnetInputException("prefix")
            if (prefix !in 0..32) throw SubnetInputException("prefix")
            assumed = false
        } else {
            host = text.trim()
            prefix = 24
            assumed = true
        }
        if (host.isEmpty()) throw SubnetInputException("format")
        return SubnetRequest(stripPort(host), prefix, assumed)
    }

    private fun stripPort(host: String): String {
        val colons = host.count { it == ':' }
        if (colons == 0) {
            if (!looksLikeIpv4(host) && !hostPattern.matches(host)) throw SubnetInputException("format")
            return host
        }
        if (colons > 1) throw SubnetInputException("ipv6")
        // A single colon with a numeric suffix is a tolerated ":port"; anything else is rejected.
        val name = host.substringBefore(":")
        val port = host.substringAfter(":")
        if (name.isEmpty() || port.isEmpty() || !port.all(Char::isDigit)) throw SubnetInputException("format")
        if (!looksLikeIpv4(name) && !hostPattern.matches(name)) throw SubnetInputException("format")
        return name
    }

    /**
     * Returns the IPv4 address for [host]: digit-and-dot literals pass through
     * untouched (octet validity is checked later by [calculateSubnet]) while
     * domain names are resolved via DNS. Blocking: call off the main thread.
     * Throws [SubnetInputException] with code "dns" when resolution fails.
     */
    fun resolveIpv4(host: String): String {
        if (looksLikeIpv4(host)) return host
        return try {
            InetAddress.getAllByName(host).firstOrNull { it is Inet4Address }?.hostAddress
                ?: throw SubnetInputException("dns")
        } catch (e: SubnetInputException) {
            throw e
        } catch (_: Exception) {
            throw SubnetInputException("dns")
        }
    }

    fun calculateSubnet(cidrStr: String): SubnetInfo {
        val parts = cidrStr.trim().split("/")
        if (parts.size != 2) throw SubnetInputException("format")

        val ipStr = parts[0].trim()
        val prefix = parts[1].trim().toIntOrNull() ?: throw SubnetInputException("prefix")
        if (prefix !in 0..32) throw SubnetInputException("prefix")

        val ipNum = ipToLong(ipStr)
        val maskNum = if (prefix == 0) 0L else (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
        val wildcardNum = maskNum xor 0xFFFFFFFFL

        val netNum = ipNum and maskNum
        val bcastNum = netNum or wildcardNum

        val total = 1L shl (32 - prefix)
        val usable = if (prefix >= 31) (if (prefix == 31) 2L else 1L) else (total - 2).coerceAtLeast(0)

        val firstHost = if (prefix >= 31) netNum else netNum + 1
        val lastHost = if (prefix >= 31) bcastNum else bcastNum - 1

        return SubnetInfo(
            network = longToIp(netNum),
            broadcast = longToIp(bcastNum),
            firstHost = longToIp(firstHost),
            lastHost = longToIp(lastHost),
            netmask = longToIp(maskNum),
            wildcard = longToIp(wildcardNum),
            usableHosts = usable,
            totalHosts = total
        )
    }

    private fun ipToLong(ip: String): Long {
        val octets = ip.split(".").map { it.toIntOrNull() ?: throw SubnetInputException("ip") }
        if (octets.size != 4 || octets.any { it !in 0..255 }) throw SubnetInputException("ip")
        return ((octets[0].toLong() shl 24) or (octets[1].toLong() shl 16) or (octets[2].toLong() shl 8) or octets[3].toLong()) and 0xFFFFFFFFL
    }

    private fun longToIp(v: Long): String =
        "${(v shr 24) and 0xFF}.${(v shr 16) and 0xFF}.${(v shr 8) and 0xFF}.${v and 0xFF}"

    // ── JWT Decoder ──────────────────────────────────────────────────────────

    data class JwtInfo(
        val header: String,
        val payload: String,
        val isExpired: Boolean,
        val expiryDate: String?
    )

    fun decodeJwt(token: String): JwtInfo {
        val parts = token.trim().split(".")
        if (parts.size < 2) throw IllegalArgumentException("Invalid JWT token format (expected at least 2 dots)")

        val headerJson = base64Decode(parts[0])
        val payloadJson = base64Decode(parts[1])

        var isExpired = false
        var expDateStr: String? = null

        try {
            val pObj = JSONObject(payloadJson)
            if (pObj.has("exp")) {
                val expSec = pObj.getLong("exp")
                val expDate = Date(expSec * 1000)
                isExpired = expDate.before(Date())
                val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                expDateStr = fmt.format(expDate)
            }
        } catch (_: Exception) {}

        return JwtInfo(
            header = formatJson(headerJson),
            payload = formatJson(payloadJson),
            isExpired = isExpired,
            expiryDate = expDateStr
        )
    }

    // ── Generator ────────────────────────────────────────────────────────────

    fun generatePassword(length: Int = 16, useSymbols: Boolean = true): String {
        val letters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val numbers = "0123456789"
        val symbols = "!@#$%^&*()-_=+[]{}<>?"
        var chars = letters + numbers
        if (useSymbols) chars += symbols

        val random = SecureRandom()
        val sb = java.lang.StringBuilder()
        for (i in 0 until length) {
            sb.append(chars[random.nextInt(chars.length)])
        }
        return sb.toString()
    }

    fun generateUuid(): String = UUID.randomUUID().toString()
}
