package org.didban.monitor

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
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

    fun calculateSubnet(cidrStr: String): SubnetInfo {
        val parts = cidrStr.trim().split("/")
        if (parts.size != 2) throw IllegalArgumentException("Format must be IP/prefix (e.g. 192.168.1.1/24)")

        val ipStr = parts[0].trim()
        val prefix = parts[1].trim().toIntOrNull() ?: throw IllegalArgumentException("Invalid prefix")
        if (prefix !in 0..32) throw IllegalArgumentException("Prefix must be 0-32")

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
        val octets = ip.split(".").map { it.toInt() }
        if (octets.size != 4 || octets.any { it !in 0..255 }) throw IllegalArgumentException("Invalid IPv4: $ip")
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
