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
        val text = raw.trim()
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

    private fun decode(value: String): String = runCatching {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }.getOrElse { throw IllegalArgumentException("invalid quick-connect encoding") }
}
