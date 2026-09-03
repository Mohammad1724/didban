package org.didban.monitor

import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.Locale

// ── Data models (parsed from the agent's JSON API) ──────────────────────────

data class ServerConfig(
    val id: Long,
    var name: String,
    var host: String,
    var port: Int = 8686,
    var token: String = "",
    var useTls: Boolean = true,
    var fingerprint: String = "",
    var cpuAlert: Int = 90,
    var memAlert: Int = 90
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("host", host)
        put("port", port)
        put("token", token)
        put("useTls", useTls)
        put("fingerprint", fingerprint)
        put("cpuAlert", cpuAlert)
        put("memAlert", memAlert)
    }

    companion object {
        fun fromJson(o: JSONObject) = ServerConfig(
            id = o.optLong("id"),
            name = o.optString("name"),
            host = o.optString("host"),
            port = o.optInt("port", 8686),
            token = o.optString("token"),
            useTls = o.optBoolean("useTls", true),
            fingerprint = o.optString("fingerprint"),
            cpuAlert = o.optInt("cpuAlert", 90),
            memAlert = o.optInt("memAlert", 90)
        )
    }
}

data class DiskInfo(val mount: String, val total: Long, val used: Long, val pct: Float)
data class NetInfo(val name: String, val rx: Double, val tx: Double)
data class ProcInfo(val pid: Int, val name: String, val cmd: String, val user: String, val cpu: Float, val memPct: Float, val memMb: Float)
data class ProcBrief(val name: String, val pid: Int, val cpu: Float, val memMb: Float)
data class SpikeEvent(val time: Long, val type: String, val value: Float, val detail: String, val top: List<ProcBrief>)

// ── Shared live state (written by MonitorService, read by UI) ───────────────

object Repo {
    data class State(val metrics: Metrics? = null, val error: String? = null, val updated: Long = 0)

    val states = MutableStateFlow<Map<Long, State>>(emptyMap())

    fun set(id: Long, metrics: Metrics? = null, error: String? = null) {
        val cur = states.value.toMutableMap()
        cur[id] = State(metrics, error, System.currentTimeMillis())
        states.value = cur
    }
}

// ── Formatting helpers ──────────────────────────────────────────────────────

object Fmt {
    fun bytes(v: Long): String {
        if (v < 1024) return "$v B"
        val kb = v / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.0f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
        return String.format(Locale.US, "%.1f GB", mb / 1024.0)
    }

    fun rate(v: Double): String {
        if (v < 1024) return String.format(Locale.US, "%.0f B/s", v)
        val kb = v / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB/s", kb)
        return String.format(Locale.US, "%.1f MB/s", kb / 1024.0)
    }

    fun pct(v: Float): String = String.format(Locale.US, "%.0f%%", v)

    fun uptime(sec: Long): String {
        val d = sec / 86400
        val h = (sec % 86400) / 3600
        val m = (sec % 3600) / 60
        return if (d > 0) "${d}d ${h}h" else if (h > 0) "${h}h ${m}m" else "${m}m"
    }
}

object TimeUtil {
    /** Parses RFC3339 (e.g. 2026-09-03T18:39:12.5Z) to epoch millis; returns 0 on failure. */
    fun parseIso(s: String): Long = try {
        Instant.parse(s).toEpochMilli()
    } catch (e: Exception) {
        0L
    }
}
