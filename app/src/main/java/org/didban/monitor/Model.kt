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
data class HistPoint(val t: Long, val cpu: Float, val mem: Float, val rx: Double, val tx: Double)

data class Metrics(
    val hostname: String,
    val time: Long,
    val uptime: Long,
    val load1: Float,
    val cores: Int,
    val cpuUsage: Float,
    val cpuUser: Float,
    val cpuSystem: Float,
    val cpuIowait: Float,
    val cpuSteal: Float,
    val memTotal: Long,
    val memUsed: Long,
    val memAvailable: Long,
    val memPct: Float,
    val swapTotal: Long,
    val swapUsed: Long,
    val swapPct: Float,
    val disks: List<DiskInfo>,
    val nets: List<NetInfo>
) {
    companion object {
        fun fromJson(o: JSONObject): Metrics {
            val cpu = o.optJSONObject("cpu") ?: JSONObject()
            val mem = o.optJSONObject("memory") ?: JSONObject()
            val disks = mutableListOf<DiskInfo>()
            val da = o.optJSONArray("disks")
            if (da != null) for (i in 0 until da.length()) {
                val d = da.optJSONObject(i) ?: continue
                disks.add(DiskInfo(d.optString("mount"), d.optLong("total"), d.optLong("used"), d.optDouble("usage_pct").toFloat()))
            }
            val nets = mutableListOf<NetInfo>()
            val na = o.optJSONArray("network")
            if (na != null) for (i in 0 until na.length()) {
                val n = na.optJSONObject(i) ?: continue
                nets.add(NetInfo(n.optString("name"), n.optDouble("rx"), n.optDouble("tx")))
            }
            val load = o.optJSONArray("load_avg")
            return Metrics(
                hostname = o.optString("hostname"),
                time = TimeUtil.parseIso(o.optString("time")),
                uptime = o.optLong("uptime_sec"),
                load1 = if (load != null && load.length() > 0) load.optDouble(0).toFloat() else 0f,
                cores = cpu.optInt("cores", 1),
                cpuUsage = cpu.optDouble("usage").toFloat(),
                cpuUser = cpu.optDouble("user").toFloat(),
                cpuSystem = cpu.optDouble("system").toFloat(),
                cpuIowait = cpu.optDouble("iowait").toFloat(),
                cpuSteal = cpu.optDouble("steal").toFloat(),
                memTotal = mem.optLong("total"),
                memUsed = mem.optLong("used"),
                memAvailable = mem.optLong("available"),
                memPct = mem.optDouble("usage_pct").toFloat(),
                swapTotal = mem.optLong("swap_total"),
                swapUsed = mem.optLong("swap_used"),
                swapPct = mem.optDouble("swap_usage_pct").toFloat(),
                disks = disks,
                nets = nets
            )
        }
    }
}

object JsonParse {
    fun processes(o: JSONObject): List<ProcInfo> {
        val arr = o.optJSONArray("processes") ?: JSONArray()
        val out = mutableListOf<ProcInfo>()
        for (i in 0 until arr.length()) {
            val p = arr.optJSONObject(i) ?: continue
            out.add(ProcInfo(
                pid = p.optInt("pid"),
                name = p.optString("name"),
                cmd = p.optString("cmd"),
                user = p.optString("user"),
                cpu = p.optDouble("cpu").toFloat(),
                memPct = p.optDouble("mem_pct").toFloat(),
                memMb = p.optDouble("mem_mb").toFloat()
            ))
        }
        return out
    }

    fun events(o: JSONObject): List<SpikeEvent> {
        val arr = o.optJSONArray("events") ?: JSONArray()
        val out = mutableListOf<SpikeEvent>()
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i) ?: continue
            val top = mutableListOf<ProcBrief>()
            val ta = e.optJSONArray("top")
            if (ta != null) for (j in 0 until ta.length()) {
                val p = ta.optJSONObject(j) ?: continue
                top.add(ProcBrief(p.optString("name"), p.optInt("pid"), p.optDouble("cpu").toFloat(), p.optDouble("mem_mb").toFloat()))
            }
            out.add(SpikeEvent(
                time = TimeUtil.parseIso(e.optString("time")),
                type = e.optString("type"),
                value = e.optDouble("value").toFloat(),
                detail = e.optString("detail"),
                top = top
            ))
        }
        return out
    }

    fun history(o: JSONObject): List<HistPoint> {
        val arr = o.optJSONArray("points") ?: JSONArray()
        val out = mutableListOf<HistPoint>()
        for (i in 0 until arr.length()) {
            val p = arr.optJSONObject(i) ?: continue
            out.add(HistPoint(
                t = p.optLong("t"),
                cpu = p.optDouble("cpu").toFloat(),
                mem = p.optDouble("mem").toFloat(),
                rx = p.optDouble("rx"),
                tx = p.optDouble("tx")
            ))
        }
        return out
    }
}

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
