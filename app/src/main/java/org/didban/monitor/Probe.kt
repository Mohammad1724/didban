package org.didban.monitor

import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.time.OffsetDateTime

// ── Phase 4 · 4-B — multi-point probing ─────────────────────────────────────
//
// The phone is one vantage point. Each Didban agent the user runs also
// probes the same uptime targets (the agent's host = one probe point),
// and the Uptime screen shows a host × point matrix: the same target
// checked from the phone AND from every server. A target that is "down"
// from the phone but "up" from three servers means the phone's network,
// not the target.

/** One probe target as seen from one agent (GET /api/probe payload). */
data class ProbePoint(
    val name: String,
    val mode: String,       // "tcp" | "http"
    val host: String,
    val port: Int,
    val state: String,      // "" (not observed yet) | "up" | "down"
    val observed: Boolean,
    val lastLatencyMs: Long,
    val lastDetail: String,
    val lastChecked: Long,
    val historyUptimePct: Float // 0..100 over the agent's bounded history
)

/** The full /api/probe response from one agent (one probe point). */
data class ProbeSnapshot(
    val enabled: Boolean,
    val intervalMs: Long,
    val hostname: String,
    val points: List<ProbePoint>
) {
    fun point(name: String): ProbePoint? = points.firstOrNull { it.name == name }

    companion object {
        /**
         * Fault-tolerant parse (old agents never sent this endpoint at all,
         * and newer ones may drop fields — the same contract as
         * [TunnelEngine.parseWatchdog]).
         */
        fun parse(json: JSONObject): ProbeSnapshot {
            val pts = mutableListOf<ProbePoint>()
            val arr = json.optJSONArray("points") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val target = o.optJSONObject("target") ?: continue
                val name = target.optString("name")
                if (name.isEmpty()) continue

                val hist = o.optJSONArray("history") ?: JSONArray()
                var upCount = 0
                val histSize = hist.length()
                for (j in 0 until histSize) {
                    if (hist.optJSONObject(j)?.optBoolean("up", false) == true) upCount++
                }
                val last = o.optJSONObject("last") ?: JSONObject()
                pts.add(
                    ProbePoint(
                        name = name,
                        mode = target.optString("mode", "tcp"),
                        host = target.optString("host"),
                        port = target.optInt("port", 0),
                        state = o.optString("state", ""),
                        observed = o.optBoolean("observed", false),
                        lastLatencyMs = last.optLong("latency_ms", 0),
                        lastDetail = last.optString("detail", ""),
                        lastChecked = parseRfc3339(last.optString("at", "")),
                        historyUptimePct = if (histSize == 0) 0f
                            else upCount.toFloat() / histSize * 100f
                    )
                )
            }
            return ProbeSnapshot(
                enabled = json.optBoolean("enabled", true),
                intervalMs = json.optLong("interval_ms", 0),
                hostname = json.optString("hostname", ""),
                points = pts
            )
        }
    }
}

/** Parses the Go agent's RFC3339Nano timestamps (time.Time JSON encoding). */
internal fun parseRfc3339(s: String): Long {
    if (s.isBlank()) return 0L
    return try {
        OffsetDateTime.parse(s).toInstant().toEpochMilli()
    } catch (_: Exception) {
        0L
    }
}

/**
 * Maps a phone-side uptime target to an agent probe spec (the JSON object
 * the agent validates and stores). Returns null when the target cannot
 * meaningfully be probed from an agent (defensive — every type has a
 * mapping today).
 *
 * Parity with [UptimeEngine.checkTarget]:
 *  - HTTP/KEYWORD/HTTPS: full URL or https://host → mode=http
 *  - TCP / PING (PING is a TCP socket on the phone too): mode=tcp, port ?: 80
 *  - SSL: reachability only (cert expiry stays phone-side), mode=tcp, port ?: 443
 */
object ProbeSpecs {
    /** Agent-side hard limits (see agent/probe.go: name length and batch cap). */
    private const val MAX_NAME_LEN = 64
    private const val MAX_SPECS = 50

    /** The agent-side key for a target: its name, or the raw target when unnamed. */
    fun specName(t: UptimeTarget): String = t.name.ifBlank { t.target.trim() }

    fun forTarget(t: UptimeTarget): JSONObject? {
        val host = t.target.trim()
        if (host.isEmpty()) return null
        val json = JSONObject()
        json.put("name", specName(t))

        when (t.type.uppercase()) {
            "HTTP", "HTTPS", "KEYWORD" -> {
                var u: URL? = null
                try {
                    var s = host
                    if (!s.startsWith("http://") && !s.startsWith("https://")) s = "https://$s"
                    u = URL(s)
                } catch (_: Exception) {
                    return null
                }
                val url = u!!
                json.put("mode", "http")
                json.put("host", url.host)
                json.put("port", url.port.takeIf { it > 0 } ?: (if (url.protocol == "https") 443 else 80))
                json.put("scheme", url.protocol.removeSuffix(":"))
                val path = url.file.ifEmpty { "/" }
                if (path.length <= 256) json.put("path", path)
            }
            "TCP", "PING" -> {
                json.put("mode", "tcp")
                json.put("host", host)
                json.put("port", if (t.port > 0) t.port else 80)
            }
            "SSL" -> {
                json.put("mode", "tcp")
                json.put("host", host)
                json.put("port", if (t.port > 0) t.port else 443)
            }
            else -> return null
        }
        return json
    }

    /** The full target-set payload for PUT/POST /api/probe/targets. */
    fun targetsPayload(targets: List<UptimeTarget>): JSONObject {
        val chosen = targets.filter { !it.isPaused }.take(MAX_SPECS)
        val names = uniqueNames(chosen)
        val arr = org.json.JSONArray()
        chosen.forEach { t ->
            val spec = forTarget(t) ?: return@forEach
            // The agent keys probe points by name, so the name it stores must
            // be the de-duplicated one, not the raw display name.
            spec.put("name", names.getValue(t.id))
            arr.put(spec)
        }
        return JSONObject().put("targets", arr)
    }

    /**
     * Collision-free agent-side keys for a target set, keyed by target id.
     *
     * The agent rejects the *entire* batch when two specs share a name
     * ("duplicate target name") or when a name exceeds 64 characters
     * ("name too long"), and it reports that as a plain 400. Because the
     * matrix UI used to send raw display names, one pair of same-named
     * monitors silently disabled multi-point probing on every server at once.
     * Names are therefore truncated and de-duplicated here, and callers read
     * results back through the same map so the two sides cannot disagree.
     */
    fun uniqueNames(targets: List<UptimeTarget>): Map<Long, String> {
        val out = LinkedHashMap<Long, String>()
        val used = HashSet<String>()
        for (t in targets) {
            if (out.containsKey(t.id)) continue // same id twice: first wins
            val base = specName(t).ifBlank { "target-${t.id}" }
            var name = truncateName(base, "")
            if (!used.add(name)) {
                var n = 2
                while (true) {
                    val suffix = if (n == 2) "-${t.id}" else "-${t.id}-$n"
                    name = truncateName(base, suffix)
                    if (used.add(name)) break
                    n++
                }
            }
            out[t.id] = name
        }
        return out
    }

    /** Shortens [base] so that base + suffix fits the agent's 64-char limit. */
    private fun truncateName(base: String, suffix: String): String {
        val room = (MAX_NAME_LEN - suffix.length).coerceAtLeast(1)
        return base.take(room) + suffix
    }
}
