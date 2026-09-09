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
        put("isEnabled", isEnabled)
        put("lastStatus", lastStatus)
        put("lastLatencyMs", lastLatencyMs)
        put("lastChecked", lastChecked)
    }

    companion object {
        fun fromJson(o: JSONObject): TunnelConfig {
            val coreStr = o.optString("core", TunnelCore.BACKPACK.name)
            val coreEnum = try { TunnelCore.valueOf(coreStr) } catch (_: Exception) { TunnelCore.BACKPACK }

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
                token = o.optString("token"),
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
