package org.didban.monitor

/**
 * Agent/app compatibility helpers.
 *
 * The app and the agent release independently: a user can install a new APK
 * while their server still runs an older agent that does not serve the
 * newer Docker and bandwidth endpoints. Those calls then fail with
 * HTTP 404/405 — not a network problem, just an outdated agent.
 *
 * Everything here is a pure function so it can be unit-tested without a
 * network or an Android device.
 */

/** First agent release that serves the Docker and bandwidth endpoints. */
const val MIN_AGENT_VERSION = "v0.7.0"

/**
 * Command the user runs on the server (as root) to install or update the
 * agent to the latest published release. Installed agents keep their
 * existing tokens, so updating never breaks the saved connection.
 */
const val AGENT_UPDATE_COMMAND =
    "curl -fsSL https://raw.githubusercontent.com/Mohammad1724/didban/main/agent/install.sh -o didban-install.sh && sudo bash didban-install.sh"

/**
 * True when a tool failure means "this agent does not have that endpoint".
 * Callers show the update-agent action instead of a bare retry.
 */
fun isMissingEndpointFailure(failure: Throwable): Boolean {
    val status = (failure as? ApiException)?.statusCode
    return status == 404 || status == 405
}

/**
 * True when [agentVersion] is known to predate [MIN_AGENT_VERSION] and the
 * server therefore cannot serve Docker/bandwidth calls.
 *
 * Release builds report their tag (`v0.6.1`); CI main builds report `main`
 * and local builds report `dev`. Anything that is not a parseable release
 * tag is treated as *unknown* (returns false) rather than outdated, so
 * development builds never get a bogus warning.
 */
fun isAgentOutdated(agentVersion: String): Boolean {
    val release = parseReleaseTag(agentVersion) ?: return false
    return compareVersion(release, MIN_AGENT_TRIPLE) < 0
}

private val MIN_AGENT_TRIPLE = Triple(0, 7, 0)

private fun parseReleaseTag(raw: String): Triple<Int, Int, Int>? {
    var s = raw.trim()
    if (s.isEmpty()) return null
    if (s.startsWith("v", ignoreCase = true)) s = s.substring(1)
    // Drop pre-release/build suffixes: "0.7.0-rc1", "0.7.0+ci.5".
    s = s.split('-', '+').firstOrNull().orEmpty()
    if (s.isEmpty()) return null
    val parts = s.split('.')
    if (parts.size > 3) return null
    val numbers = parts.map { it.toIntOrNull() ?: return null }
    if (numbers.any { it < 0 }) return null
    return Triple(
        numbers.getOrElse(0) { 0 },
        numbers.getOrElse(1) { 0 },
        numbers.getOrElse(2) { 0 }
    )
}

private fun compareVersion(a: Triple<Int, Int, Int>, b: Triple<Int, Int, Int>): Int {
    if (a.first != b.first) return a.first.compareTo(b.first)
    if (a.second != b.second) return a.second.compareTo(b.second)
    return a.third.compareTo(b.third)
}
