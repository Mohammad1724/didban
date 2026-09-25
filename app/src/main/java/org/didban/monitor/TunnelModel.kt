package org.didban.monitor

import org.json.JSONObject

enum class TunnelCore(val displayName: String, val description: String, val tag: String) {
    BACKPACK("BackPack 🎒", "تانل نسل جدید Go با رمزنگاری Stealth، دور زدن کرنل PCK و گیمینگ KCP+FEC", "NextGen"),
    PAQET("Paqet (Raw Socket)", "تانل پرسرعت بر بستر Raw Socket و KCP با دور زدن لایه‌های شبکه و فایروال", "RawSocket"),
    NARNIA("Narnia (ICMP Ping)", "تانل اختصاصی پنهان درون پکت‌های ICMP Ping با رمزنگاری پیشرفته ChaCha20", "ICMP"),
    SPOOF_TUNNEL("Spoof Tunnel (IP Spoofing)", "تانل جعل دوطرفه IP مبدا با سوکت خام، لایه Reliability و تصحیح خطای FEC", "IP-Spoof"),
    BACKHAUL("Backhaul", "محبوب‌ترین و پایدارترین تانل معکوس با پشتیبانی از WebSocket و مالتی‌پورت", "Reverse"),
    RATHOLE("Rathole", "فوق‌العاده سبک و پرسرعت (نوشته‌شده با Rust) با مصرف زیر ۵ مگابایت رم", "Rust"),
    GOST("GOST", "فوروارد و رله همه‌کاره با پشتیبانی از TCP, UDP, WebSocket, gRPC و TCPMux", "Forward/Relay"),
    CHISEL("Chisel", "تانل امن و سریع TCP/UDP از بستر HTTP و WebSocket با احراز هویت قوی", "WS/HTTP"),
    FRP("FRP", "ریورس پروکسی کلاسیک و قدرتمند با مدیریت همزمان چندین پورت", "Reverse"),
    IPTABLES("IPTables", "فوروارد مستقیم در سطح کرنل لینوکس بدون نیاز به نصب هیچ نرم‌افزاری", "Kernel")
}

enum class TunnelTransport(val displayName: String, val description: String = "") {
    STEALTH("TCP + Stealth (Noise NNpsk0)", "رمزنگاری Noise بدون فینگرپرینت TLS و غیرقابل شناسایی برای DPI"),
    PCK("TCP + PCK (Kernel Bypass)", "ارسال پکت خارج از کانکشن‌ترکینگ کرنل جهت جلوگیری از تراتل و RST"),
    KCP_FEC("UDP + KCP + FEC (Gaming)", "پروتکل بلادرنگ کم‌تاخیر با تصحیح خطای خودکار و ترمیم پکت‌های گمشده"),
    RAW_KCP("Raw Socket + KCP", "ارسال مستقیم بسته‌های KCP روی سوکت خام سیستم‌عامل"),
    ICMP_CHACHA("ICMP Ping (ChaCha20)", "پنهان‌سازی کل ترافیک در قالب بسته‌های عادی Ping"),
    IP_SPOOF_UDP("Mutual IP Spoof (UDP)", "جعل دوطرفه IP مبدا روی بسته‌های UDP با لایه بازسازی داده"),
    IP_SPOOF_ICMP("Mutual IP Spoof (ICMP)", "جعل دوطرفه IP مبدا روی بسته‌های پینگ ICMP"),
    QUIC("UDP + QUIC", "تونل امن مالتی‌پلکس QUIC بر بستر TLS 1.3"),
    TCP("TCP", "ارتباط مستقیم استاندارد TCP"),
    TCPMUX("TCP Mux (Multiplex)", "مالتی‌پلکس چندین استریم روی یک کانکشن TCP"),
    WS("WebSocket (WS)", "پوشش ترافیک در قالب بسته‌های وب‌سوکت"),
    WSMUX("WebSocket Mux (WSMux)", "مالتی‌پلکس چندگانه روی کانکشن‌های وب‌سوکت"),
    WSSMUX("WSS Mux (Chrome TLS)", "استتار کامل به عنوان ترافیک HTTPS مرورگر کروم"),
    GRPC("gRPC", "انتقال ترافیک روی بسترهای مدرن gRPC"),
    XDI("xDi (ICMP Ping)", "انتقال تانل روی پکت‌های ICMP در زمان فیلتر بودن کامل TCP و UDP"),
    SPOOF("IP Spoofing Carrier", "تغییر و جعل IP مبدا پکت‌ها برای دور زدن فیلترینگ لایه ۳/۴"),
    UDP("UDP", "انتقال مستقیم بسته‌های دیتای UDP")
}

data class TunnelConfig(
    val id: Long,
    var name: String,
    var core: TunnelCore,
    var transport: TunnelTransport = TunnelTransport.STEALTH,
    var iranHost: String = "",
    var iranPort: Int = 443,
    var foreignHost: String = "",
    var foreignPort: Int = 8443,
    var corePort: Int = 3080,
    var token: String = "",
    var preset: String = "turbo", // turbo, balance, aggressive, gaming
    var kcpMode: String = "fast", // normal, fast, fast2, fast3 (for Paqet)
    var encryption: String = "aes-128-gcm", // aes-128-gcm, aes-256-gcm, chacha20, none
    var spoofSrcIp: String = "1.1.1.1", // for Spoof Tunnel
    var spoofPeerIp: String = "8.8.8.8",
    var virtualIpIran: String = "10.200.200.2", // for Narnia
    var virtualIpKharej: String = "10.200.200.1",
    var mtu: Int = 1350,
    var acceptUdp: Boolean = true,
    var proxyProtocol: Boolean = false,
    var wsPath: String = "/tunnel",
    var wsHost: String = "",
    var multiPorts: String = "", // e.g. "443:8443, 80:8080"
    var autoSync: Boolean = true, // Auto-deploy and configure on servers like Smite panel
    // M17: true when this tunnel came from auto-discovery (process/docker
    // scan) instead of manual creation. Discovered tunnels do NOT own a
    // credential — their token stays blank until the user enters the real
    // one, and the deploy gate refuses to mint/fabricate one.
    var discovered: Boolean = false,
    var iranServerId: Long? = null,
    var foreignServerId: Long? = null,
    var syncStatusIran: String = "",
    var syncStatusForeign: String = "",
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
        put("preset", preset)
        put("kcpMode", kcpMode)
        put("encryption", encryption)
        put("spoofSrcIp", spoofSrcIp)
        put("spoofPeerIp", spoofPeerIp)
        put("virtualIpIran", virtualIpIran)
        put("virtualIpKharej", virtualIpKharej)
        put("mtu", mtu)
        put("acceptUdp", acceptUdp)
        put("proxyProtocol", proxyProtocol)
        put("wsPath", wsPath)
        put("wsHost", wsHost)
        put("multiPorts", multiPorts)
        put("autoSync", autoSync)
        put("discovered", discovered)
        iranServerId?.let { put("iranServerId", it) }
        foreignServerId?.let { put("foreignServerId", it) }
        put("syncStatusIran", syncStatusIran)
        put("syncStatusForeign", syncStatusForeign)
        put("isEnabled", isEnabled)
        put("lastStatus", lastStatus)
        put("lastLatencyMs", lastLatencyMs)
        put("lastChecked", lastChecked)
    }

    companion object {
        fun fromJson(o: JSONObject): TunnelConfig {
            val coreStr = o.optString("core", TunnelCore.BACKPACK.name)
            val coreEnum = try { TunnelCore.valueOf(coreStr) } catch (_: Exception) { TunnelCore.BACKPACK }

            // M17 migration: the old discovery path stored the literal
            // "auto-detected" as a fake token. It is healed to a BLANK token
            // (unknown) and the tunnel is marked discovered, so the deploy
            // gate blocks it instead of silently deploying a wrong secret.
            val rawToken = o.optString("token")
            val wasPlaceholder = rawToken == "auto-detected"

            val transportStr = o.optString("transport", TunnelTransport.STEALTH.name)
            val transportEnum = try { TunnelTransport.valueOf(transportStr) } catch (_: Exception) { TunnelTransport.STEALTH }

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
                token = if (wasPlaceholder) "" else rawToken,
                preset = o.optString("preset", "turbo"),
                kcpMode = o.optString("kcpMode", "fast"),
                encryption = o.optString("encryption", "aes-128-gcm"),
                spoofSrcIp = o.optString("spoofSrcIp", "1.1.1.1"),
                spoofPeerIp = o.optString("spoofPeerIp", "8.8.8.8"),
                virtualIpIran = o.optString("virtualIpIran", "10.200.200.2"),
                virtualIpKharej = o.optString("virtualIpKharej", "10.200.200.1"),
                mtu = o.optInt("mtu", 1350),
                acceptUdp = o.optBoolean("acceptUdp", true),
                proxyProtocol = o.optBoolean("proxyProtocol", false),
                wsPath = o.optString("wsPath", "/tunnel"),
                wsHost = o.optString("wsHost"),
                multiPorts = o.optString("multiPorts"),
                autoSync = o.optBoolean("autoSync", true),
                discovered = o.optBoolean("discovered") || wasPlaceholder,
                iranServerId = if (o.has("iranServerId")) o.optLong("iranServerId") else null,
                foreignServerId = if (o.has("foreignServerId")) o.optLong("foreignServerId") else null,
                syncStatusIran = o.optString("syncStatusIran"),
                syncStatusForeign = o.optString("syncStatusForeign"),
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
    // Item 26: the runtime config file the install script writes and the
    // unit's ExecStart reads — per-tunnel, inside the agent's config
    // sandbox, so same-core tunnels on one host never collide and the
    // agent's delete removes the real file ("" = core keeps no file).
    val iranConfigPath: String = "",
    val foreignConfigPath: String = "",
    // Phase 4 · 4-A: the local TCP port each role's service is expected to
    // listen on (0 = this role does not listen, e.g. the dialing client side).
    // The agent's watchdog uses these for the "service alive but port dead"
    // (degraded) check. Each generator sets them where it writes the bind.
    val listenPortIran: Int = 0,
    val listenPortForeign: Int = 0,
    val description: String
)

// Phase 4 · 4-A — agent-side tunnel watchdog views.
// The agent watches every registered tunnel service (systemd state +
// listen port + restart counters) and reports state changes; the fleet
// page shows a per-tunnel shield badge from these.
data class WatchdogTunnelState(
    val id: String,
    val name: String,
    val core: String,
    val role: String,
    val state: String, // "unknown" | "up" | "down" | "degraded" | "crash_loop"
    val active: Boolean,
    val port: Int, // 0 = this role does not listen
    val portOk: Boolean,
    val nRestarts: Int,
    val uptimeSec: Int,
    val changedAtMs: Long, // 0 = not observed yet
    val detail: String
)

data class WatchdogStatus(
    val enabled: Boolean,
    val intervalMs: Long,
    val tunnels: List<WatchdogTunnelState>
)

data class AutoDeployServerResult(
    val serverName: String,
    val host: String,
    val role: String, // "iran" or "foreign"
    val success: Boolean,
    val status: String, // "active", "failed", "unreachable", "no_agent"
    val message: String,
    val logs: String = ""
)

data class AutoDeployResult(
    val iranResult: AutoDeployServerResult?,
    val foreignResult: AutoDeployServerResult?,
    val overallSuccess: Boolean,
    val summaryMessage: String,
    val validationFailed: Boolean = false
) {
    fun localizedSummary(copy: CommandCopy): String {
        if (validationFailed) return copy.tunValidationFailed
        if (overallSuccess && iranResult != null && foreignResult != null) return copy.tunDeployBothSuccess
        if (overallSuccess && iranResult?.role == "iran") return copy.tunDeployIranSuccess
        if (overallSuccess && foreignResult?.role == "foreign") return copy.tunDeployForeignSuccess

        val failed = listOfNotNull(iranResult, foreignResult).firstOrNull { !it.success }
        if (failed != null) {
            val role = if (failed.role == "iran") copy.tunIranHost else copy.tunForeignHost
            return copy.tunDeployServerFailure
                .replace("%1", role)
                .replace("%2", failed.message)
        }
        return copy.tunDeployReady
    }
}
