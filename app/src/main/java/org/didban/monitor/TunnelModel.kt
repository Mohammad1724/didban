package org.didban.monitor

import org.json.JSONArray
import org.json.JSONObject

enum class TunnelCore(val displayName: String, val description: String, val tag: String) {
    BACKHAUL("Backhaul", "محبوب‌ترین و پایدارترین تانل معکوس با پشتیبانی از WebSocket و مالتی‌پورت", "Reverse"),
    RATHOLE("Rathole", "فوق‌العاده سبک و پرسرعت (نوشته‌شده با Rust) با مصرف زیر ۵ مگابایت رم", "Rust"),
    GOST("GOST", "فوروارد و رله همه‌کاره با پشتیبانی از TCP, UDP, WebSocket, gRPC و TCPMux", "Forward/Relay"),
    CHISEL("Chisel", "تانل امن و سریع TCP/UDP از بستر HTTP و WebSocket با احراز هویت قوی", "WS/HTTP"),
    FRP("FRP", "ریورس پروکسی کلاسیک و قدرتمند با مدیریت همزمان چندین پورت", "Reverse"),
    IPTABLES("IPTables", "فوروارد مستقیم در سطح کرنل لینوکس بدون نیاز به نصب هیچ نرم‌افزاری", "Kernel")
}

enum class TunnelTransport(val displayName: String) {
    TCP("TCP"),
    UDP("UDP"),
    WS("WebSocket (WS)"),
    WSMUX("WebSocket Mux (WSMux)"),
    GRPC("gRPC"),
    TCPMUX("TCPMux")
}

data class TunnelConfig(
    val id: Long,
    var name: String,
    var core: TunnelCore,
    var transport: TunnelTransport = TunnelTransport.TCP,
    var iranHost: String = "",
    var iranPort: Int = 443,
    var foreignHost: String = "",
    var foreignPort: Int = 8443,
    var corePort: Int = 3080,
    var token: String = "",
    var wsPath: String = "/tunnel",
    var wsHost: String = "",
    var multiPorts: String = "", // e.g. "443:8443, 80:8080"
    var isEnabled: Boolean = true,
    var lastStatus: Int = -1, // -1: Unknown, 1: Online, 0: Offline
    var lastLatencyMs: Long = -1,
    var lastChecked: Long = 0
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("core", core.name)
        put("transport", transport.name)
        put("iranHost", iranHost)
        put("iranPort", iranPort)
        put("foreignHost", foreignHost)
        put("foreignPort", foreignPort)
        put("corePort", corePort)
        put("token", token)
        put("wsPath", wsPath)
        put("wsHost", wsHost)
        put("multiPorts", multiPorts)
        put("isEnabled", isEnabled)
        put("lastStatus", lastStatus)
        put("lastLatencyMs", lastLatencyMs)
        put("lastChecked", lastChecked)
    }

    companion object {
        fun fromJson(o: JSONObject): TunnelConfig {
            val coreStr = o.optString("core", TunnelCore.BACKHAUL.name)
            val coreEnum = try { TunnelCore.valueOf(coreStr) } catch (_: Exception) { TunnelCore.BACKHAUL }

            val transportStr = o.optString("transport", TunnelTransport.TCP.name)
            val transportEnum = try { TunnelTransport.valueOf(transportStr) } catch (_: Exception) { TunnelTransport.TCP }

            return TunnelConfig(
                id = o.optLong("id", System.currentTimeMillis()),
                name = o.optString("name", "تونل جدید"),
                core = coreEnum,
                transport = transportEnum,
                iranHost = o.optString("iranHost"),
                iranPort = o.optInt("iranPort", 443),
                foreignHost = o.optString("foreignHost"),
                foreignPort = o.optInt("foreignPort", 8443),
                corePort = o.optInt("corePort", 3080),
                token = o.optString("token"),
                wsPath = o.optString("wsPath", "/tunnel"),
                wsHost = o.optString("wsHost"),
                multiPorts = o.optString("multiPorts"),
                isEnabled = o.optBoolean("isEnabled", true),
                lastStatus = o.optInt("lastStatus", -1),
                lastLatencyMs = o.optLong("lastLatencyMs", -1),
                lastChecked = o.optLong("lastChecked", 0)
            )
        }
    }
}

data class GeneratedTunnelCode(
    val iranConfig: String,
    val iranInstallCommand: String,
    val foreignConfig: String,
    val foreignInstallCommand: String,
    val dockerComposeIran: String,
    val dockerComposeForeign: String,
    val description: String
)
