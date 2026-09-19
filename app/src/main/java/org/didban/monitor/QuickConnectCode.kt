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
        // Installer-generated credentials are hexadecimal. Matching each field
        // separately lets us tolerate terminal wrapping without accidentally
        // swallowing the explanatory line printed after the URI into `name`.
        // Some rich-text terminals/clipboard bridges encode '&' as '&amp;'.
        // Decode only that separator entity (never arbitrary HTML) before
        // parsing. Hard wraps are also allowed inside hexadecimal values.
        val normalizedInput = raw.replace(Regex("&amp;", RegexOption.IGNORE_CASE), "&")
        val match = Regex(
            "didban://\\s*([A-Za-z0-9.-]+)(?::(\\d{1,5}))?\\s*\\?\\s*" +
                "token=\\s*((?:[A-Fa-f0-9]\\s*){32,256})&\\s*" +
                "admin_token=\\s*((?:[A-Fa-f0-9]\\s*){32,256})&\\s*" +
                "fp=\\s*((?:[A-Fa-f0-9]{2}:?\\s*){32})" +
                "(?:&\\s*name=\\s*([^\\s&]+))?",
            RegexOption.IGNORE_CASE
        ).find(normalizedInput) ?: throw IllegalArgumentException("incomplete quick-connect code")
        val trailing = normalizedInput.substring(match.range.last + 1).trimStart()
        require(!trailing.startsWith("&")) { "unexpected quick-connect data" }
        val host = match.groupValues[1]
        val port = match.groupValues[2].ifEmpty { "8686" }
        val readToken = match.groupValues[3].replace(Regex("\\s+"), "")
        val adminToken = match.groupValues[4].replace(Regex("\\s+"), "")
        val fingerprint = match.groupValues[5].replace(Regex("\\s+"), "")
        val name = match.groupValues[6]
        return buildString {
            append("didban://").append(host).append(':').append(port)
            append("?token=").append(readToken)
            append("&admin_token=").append(adminToken)
            append("&fp=").append(fingerprint)
            if (name.isNotEmpty()) append("&name=").append(name)
        }
    }

    private fun decode(value: String): String = runCatching {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }.getOrElse { throw IllegalArgumentException("invalid quick-connect encoding") }
}
