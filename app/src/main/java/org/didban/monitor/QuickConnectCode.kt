package org.didban.monitor

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class QuickConnectCode(
    val host: String,
    val port: Int,
    val readToken: String,
    val adminToken: String,
    val fingerprint: String,
    val name: String
)

/** Strict parser for the one-time didban:// code printed by the Agent installer. */
object QuickConnectCodeParser {
    private val allowedKeys = setOf("token", "admin_token", "fp", "name")

    fun parse(raw: String): QuickConnectCode {
        val text = extractCode(raw)
        require(text.length in 1..2048) { "invalid quick-connect code" }
        val uri = runCatching { URI(text) }.getOrElse { throw IllegalArgumentException("invalid quick-connect code") }
        require(uri.scheme.equals("didban", ignoreCase = true)) { "invalid quick-connect scheme" }
        require(uri.userInfo == null && uri.fragment == null && (uri.path.isNullOrEmpty() || uri.path == "/")) { "invalid quick-connect code" }
        val host = uri.host?.trim()?.removePrefix("[")?.removeSuffix("]").orEmpty()
        require(TunnelFieldValidation.isHost(host)) { "invalid server host" }
        val port = uri.port.takeIf { it in 1..65535 } ?: 8686

        val values = linkedMapOf<String, String>()
        val query = uri.rawQuery ?: throw IllegalArgumentException("quick-connect data is missing")
        for (part in query.split('&')) {
            require(part.isNotEmpty() && part.contains('=')) { "invalid quick-connect data" }
            val key = decode(part.substringBefore('='))
            val value = decode(part.substringAfter('='))
            require(key in allowedKeys && key !in values) { "invalid quick-connect data" }
            values[key] = value
        }
        val readToken = values["token"].orEmpty()
        val adminToken = values["admin_token"].orEmpty()
        val fingerprint = CertFingerprint.normalizeFingerprint(values["fp"].orEmpty())
        require(AgentTokenPolicy.isValid(readToken)) { "invalid read token" }
        require(AgentTokenPolicy.isValid(adminToken) && adminToken != readToken) { "invalid admin token" }
        require(CertFingerprint.isValidSha256(fingerprint)) { "invalid certificate fingerprint" }
        val name = values["name"]?.trim()?.take(100).orEmpty().ifBlank { host }
        return QuickConnectCode(host, port, readToken, adminToken, fingerprint, name)
    }

    /**
     * Terminal apps may copy the explanatory label, indentation, or hard line
     * wraps together with the URI. Extract only the installer-shaped code and
     * remove whitespace introduced by wrapping; credentials themselves cannot
     * legally contain whitespace.
     */
    private fun extractCode(raw: String): String {
        require(raw.length in 1..8192) { "invalid quick-connect code" }
        val compact = raw.replace(Regex("\\s+"), "")
        val start = compact.indexOf("didban://", ignoreCase = true)
        require(start >= 0) { "quick-connect scheme is missing" }
        val candidate = compact.substring(start)
        val match = Regex(
            "(?i)^didban://[^?&#]+\\?token=[^&#]+&admin_token=[^&#]+&fp=[0-9a-f: ]{64,95}(?:&name=[A-Za-z0-9._%:-]{1,300})?"
        ).find(candidate) ?: throw IllegalArgumentException("incomplete quick-connect code")
        require(!candidate.substring(match.value.length).startsWith("&")) { "unexpected quick-connect data" }
        return match.value
    }

    private fun decode(value: String): String = runCatching {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }.getOrElse { throw IllegalArgumentException("invalid quick-connect encoding") }
}
