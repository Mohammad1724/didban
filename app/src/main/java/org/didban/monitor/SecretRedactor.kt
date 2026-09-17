package org.didban.monitor

/** Removes credential-shaped values before text reaches logs, crash reports, or UI errors. */
object SecretRedactor {
    private const val REDACTED = "[REDACTED]"

    private val patterns = listOf(
        Regex("(?i)(authorization\\s*[:=]\\s*bearer\\s+)[^\\s,;\\\"]+"),
        Regex("(?i)(bearer\\s+)[A-Za-z0-9._~+/-]{8,}"),
        Regex("github_pat_[A-Za-z0-9_]+"),
        Regex("gh[pousr]_[A-Za-z0-9]{20,}"),
        Regex("(?i)(api\\.telegram\\.org/bot)[^/\\s]+"),
        Regex("(?i)(discord(?:app)?\\.com/api/webhooks/\\d+/)[^/?\\s]+"),
        Regex("(?i)([\"']?(?:token|password|secret|api[_-]?key)[\"']?\\s*[:=]\\s*[\"'])[^\"']+([\"'])")
    )

    fun redact(text: String, knownSecrets: Iterable<String> = emptyList()): String {
        var safe = text
        knownSecrets.filter { it.length >= 4 }.distinct().forEach { safe = safe.replace(it, REDACTED) }
        patterns.forEachIndexed { index, regex ->
            safe = if (index == patterns.lastIndex) {
                regex.replace(safe) { "${it.groupValues[1]}$REDACTED${it.groupValues[2]}" }
            } else {
                regex.replace(safe) { "${it.groupValues.getOrElse(1) { "" }}$REDACTED" }
            }
        }
        return safe
    }
}
