package org.didban.monitor

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket

data class PortMapping(val iranPort: Int, val foreignPort: Int)

object TunnelEngine {

    fun generateRandomToken(length: Int = 16): String = TunnelSecrets.generateRandomToken(length)

    /**
     * Stable secret for [cfg] (H3): generated once (when blank) and written
     * back into `cfg.token`, so every later code generation / redeploy reuses
     * the SAME credential. Without this, each `generateCode` call minted a
     * fresh token that was never persisted, and a redeploy broke the
     * already-deployed pair (server still had the old key).
     */
    fun ensureToken(cfg: TunnelConfig): String =
        TunnelSecrets.ensureToken(cfg.token) { cfg.token = it }

    /**
     * Deterministic key expansion for cores that need two secrets from one
     * persisted one (SpoofTunnel's server/client keys). Same `cfg.token`
     * always yields the same pair, so redeploys keep the pair intact.
     */
    private fun deriveKey(seed: String, domain: String): String =
        TunnelSecrets.deriveKey(seed, domain)

    /**
     * Base64 for generated file contents (H4): the target script writes the
     * config with `printf '%s' '<b64>' | base64 -d > file`, so user-supplied
     * content can never terminate the heredoc early or be interpreted as
     * shell (base64 is a single line of [A-Za-z0-9+/=]).
     */
    private fun b64(content: String): String =
        java.util.Base64.getEncoder().encodeToString(content.toByteArray(Charsets.UTF_8))

    /**
     * Upserts [cfg] (with its materialized secret and sync status) into the
     * persisted tunnel list. Guarantees the secret survives
     * reload-from-disk (e.g. TunnelScreen.refreshTunnels after a deploy).
     */
    fun persistTunnel(ctx: android.content.Context, cfg: TunnelConfig) {
        try {
            val list = Prefs.loadTunnels(ctx).toMutableList()
            val idx = list.indexOfFirst { it.id == cfg.id }
            if (idx >= 0) list[idx] = cfg else list.add(cfg)
            Prefs.saveTunnels(ctx, list)
        } catch (_: Exception) {
            // Best-effort: the deploy is the primary action. A failed save
            // self-heals on the next explicit save (screen save loop /
            // another deploy), and worst case the next deploy re-persists.
        }
    }

    fun parsePortMappings(cfg: TunnelConfig): List<PortMapping> {
        val raw = cfg.multiPorts.trim()
        if (raw.isBlank()) {
            return listOf(PortMapping(cfg.iranPort, cfg.foreignPort))
        }
        val result = mutableListOf<PortMapping>()
        val tokens = raw.split(',', ';', ' ', '\n', '\t').map { it.trim() }.filter { it.isNotEmpty() }
        for (token in tokens) {
            if (token.contains(':') || token.contains('=')) {
                val delim = if (token.contains(':')) ':' else '='
                val parts = token.split(delim)
                val ip = parts[0].trim().toIntOrNull()
                val fp = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: ip
                if (ip != null && fp != null) {
                    result.add(PortMapping(ip, fp))
                }
            } else if (token.contains('-')) {
                val parts = token.split('-')
                val start = parts[0].trim().toIntOrNull()
                val end = parts.getOrNull(1)?.trim()?.toIntOrNull()
                if (start != null && end != null && start <= end && (end - start) <= 50) {
                    for (p in start..end) {
                        result.add(PortMapping(p, p))
                    }
                }
            } else {
                val p = token.toIntOrNull()
                if (p != null) {
                    result.add(PortMapping(p, p))
                }
            }
        }
        return if (result.isNotEmpty()) result else listOf(PortMapping(cfg.iranPort, cfg.foreignPort))
    }

    /**
     * Core-aware pre-deploy validation (H4): only the fields that reach a
     * shell context (iptables arguments) or a systemd ExecStart line are
     * checked - config-file content is written via base64 and cannot be
     * interpreted, so it is validated only by the target binary itself.
     */
    fun validateForDeploy(cfg: TunnelConfig): List<String> = buildList {
        fun addHostError(value: String) {
            TunnelFieldValidation.checkHost(value, "Host")?.let { add(it) }
        }
        when (cfg.core) {
            TunnelCore.IPTABLES -> {
                if (cfg.foreignHost.isNotBlank()) addHostError(cfg.foreignHost)
            }
            TunnelCore.NARNIA -> {
                if (cfg.foreignHost.isNotBlank()) addHostError(cfg.foreignHost)
                if (cfg.virtualIpKharej.isNotBlank()) {
                    // Used in iptables DNAT rules executed on the shell.
                    TunnelFieldValidation.checkIpv4(cfg.virtualIpKharej, "Virtual IP (foreign)")?.let { add(it) }
                }
                if (cfg.token.isNotBlank()) {
                    TunnelFieldValidation.checkToken(cfg.token, "Token")?.let { add(it) }
                }
            }
            TunnelCore.GOST -> {
                if (cfg.foreignHost.isNotBlank()) addHostError(cfg.foreignHost)
            }
            TunnelCore.CHISEL -> {
                if (cfg.foreignHost.isNotBlank()) addHostError(cfg.foreignHost)
                if (cfg.token.isNotBlank()) {
                    TunnelFieldValidation.checkToken(cfg.token, "Token")?.let { add(it) }
                }
            }
            else -> {}
        }
    }

    fun generateCode(cfg: TunnelConfig): GeneratedTunnelCode {
        val errors = validateForDeploy(cfg)
        if (errors.isNotEmpty()) {
            throw IllegalArgumentException(errors.joinToString(" | "))
        }
        return when (cfg.core) {
            TunnelCore.BACKPACK -> generateBackpack(cfg)
            TunnelCore.PAQET -> generatePaqet(cfg)
            TunnelCore.NARNIA -> generateNarnia(cfg)
            TunnelCore.SPOOF_TUNNEL -> generateSpoofTunnel(cfg)
            TunnelCore.BACKHAUL -> generateBackhaul(cfg)
            TunnelCore.RATHOLE -> generateRathole(cfg)
            TunnelCore.GOST -> generateGost(cfg)
            TunnelCore.CHISEL -> generateChisel(cfg)
            TunnelCore.FRP -> generateFrp(cfg)
            TunnelCore.IPTABLES -> generateIptables(cfg)
        }
    }

    // ── 0. BackPack Generator (AminMGMT/BackPack) ─────────────────────────────

    private fun generateBackpack(cfg: TunnelConfig): GeneratedTunnelCode {
        val ports = parsePortMappings(cfg)
        val transportStr = when (cfg.transport) {
            TunnelTransport.STEALTH -> "stealth"
            TunnelTransport.PCK -> "pck"
            TunnelTransport.KCP_FEC -> "kcp"
            TunnelTransport.QUIC -> "quic"
            TunnelTransport.WS -> "ws"
            TunnelTransport.WSMUX -> "wsmux"
            TunnelTransport.WSSMUX -> "wssmux"
            TunnelTransport.TCPMUX -> "tcpmux"
            TunnelTransport.XDI -> "xdi"
            TunnelTransport.SPOOF -> "spoof"
            TunnelTransport.UDP -> "udp"
            else -> "tcp"
        }
        val token = ensureToken(cfg)
        val iranIp = cfg.iranHost.ifBlank { "IRAN_IP" }
        val preset = cfg.preset.ifBlank { "turbo" }
        val acceptUdpStr = if (cfg.acceptUdp) "accept_udp = true\n" else ""
        val proxyProtoStr = if (cfg.proxyProtocol) "proxy_protocol = true\n" else ""

        val portsJson = ports.joinToString(", ") { "\"${it.iranPort}=127.0.0.1:${it.foreignPort}\"" }

        // Server Config (Iran Node)
        val iranConfig = """
[server]
bind_addr = "0.0.0.0:${cfg.corePort}"
transport = "$transportStr"
token = "$token"
ports = [$portsJson]
nodelay = true
keepalive_period = 30
preset = "$preset"
${acceptUdpStr}${proxyProtoStr}channel_size = 2048
log_level = "info"
web_port = 0
""".trimIndent()

        // Client Config (Kharej Node)
        val foreignConfig = """
[client]
remote_addr = "$iranIp:${cfg.corePort}"
transport = "$transportStr"
token = "$token"
connection_pool = 8
retry_interval = 3
nodelay = true
keepalive_period = 30
preset = "$preset"
log_level = "info"
web_port = 0
""".trimIndent()

        val iranInstall = """
sudo mkdir -p /etc/backpack /usr/local/bin && \
ARCH=$(uname -m | sed 's/x86_64/amd64/' | sed 's/aarch64/arm64/' | sed 's/armv7l/armv7/') && \
(curl -fsSL https://github.com/AminMGMT/BackPack/releases/latest/download/backpack_linux_${'$'}ARCH.tar.gz -o /tmp/backpack.tar.gz && \
tar -xzf /tmp/backpack.tar.gz -C /usr/local/bin/ backpack && chmod +x /usr/local/bin/backpack) || true && \
printf '%s' '${b64(iranConfig)}' | base64 -d > /etc/backpack/server.toml
cat << 'EOF' > /etc/systemd/system/backpack-server.service
[Unit]
Description=Backpack Tunnel Server (Iran Node)
After=network.target

[Service]
Type=simple
ExecStart=/usr/local/bin/backpack server -c /etc/backpack/server.toml
Restart=always
RestartSec=3
LimitNOFILE=1048576

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload && systemctl enable --now backpack-server && systemctl status backpack-server --no-pager
""".trimIndent()

        val foreignInstall = """
sudo mkdir -p /etc/backpack /usr/local/bin && \
ARCH=$(uname -m | sed 's/x86_64/amd64/' | sed 's/aarch64/arm64/' | sed 's/armv7l/armv7/') && \
(curl -fsSL https://github.com/AminMGMT/BackPack/releases/latest/download/backpack_linux_${'$'}ARCH.tar.gz -o /tmp/backpack.tar.gz && \
tar -xzf /tmp/backpack.tar.gz -C /usr/local/bin/ backpack && chmod +x /usr/local/bin/backpack) || true && \
printf '%s' '${b64(foreignConfig)}' | base64 -d > /etc/backpack/client.toml
cat << 'EOF' > /etc/systemd/system/backpack-client.service
[Unit]
Description=Backpack Tunnel Client (Kharej Node)
After=network.target

[Service]
Type=simple
ExecStart=/usr/local/bin/backpack client -c /etc/backpack/client.toml
Restart=always
RestartSec=3
LimitNOFILE=1048576

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload && systemctl enable --now backpack-client && systemctl status backpack-client --no-pager
""".trimIndent()

        val dockerIran = """
version: '3.8'
services:
  backpack-server:
    image: ghcr.io/aminmgmt/backpack:latest
    container_name: backpack_server
    restart: always
    network_mode: host
    volumes:
      - ./server.toml:/etc/backpack/server.toml
    command: server -c /etc/backpack/server.toml
""".trimIndent()

        val dockerForeign = """
version: '3.8'
services:
  backpack-client:
    image: ghcr.io/aminmgmt/backpack:latest
    container_name: backpack_client
    restart: always
    network_mode: host
    volumes:
      - ./client.toml:/etc/backpack/client.toml
    command: client -c /etc/backpack/client.toml
""".trimIndent()

        val portsDesc = ports.joinToString(", ") { "${it.iranPort}➔${it.foreignPort}" }
        return GeneratedTunnelCode(
            iranConfig = iranConfig,
            iranInstallCommand = iranInstall,
            foreignConfig = foreignConfig,
            foreignInstallCommand = foreignInstall,
            dockerComposeIran = dockerIran,
            dockerComposeForeign = dockerForeign,
            description = "تانل قدرتمند BackPack (توسعه‌یافته توسط AminMGMT): اتصال پورت‌های [$portsDesc] ایران به خارج با رمزنگاری ${cfg.transport.displayName} و پریست ${cfg.preset}."
        )
    }

    // ── 1. Paqet Generator (behzadea12 / hanselime) ──────────────────────────

    private fun generatePaqet(cfg: TunnelConfig): GeneratedTunnelCode {
        val ports = parsePortMappings(cfg)
        val token = ensureToken(cfg)
        val foreignIp = cfg.foreignHost.ifBlank { "KHAREJ_IP" }
        val kcpMode = cfg.kcpMode.ifBlank { "fast" }
        val encryption = cfg.encryption.ifBlank { "aes-128-gcm" }

        val forwardsYaml = ports.joinToString("\n") { p ->
            """    - listen: "0.0.0.0:${p.iranPort}"
      target: "127.0.0.1:${p.foreignPort}"
      proto: "tcp/udp""""
        }

        val foreignConfig = """
server:
  listen_port: ${cfg.corePort}
  key: "$token"
  kcp_mode: "$kcpMode"
  conn: 4
  mtu: ${cfg.mtu}
  encryption: "$encryption"
  pcap_sockbuf: 4194304
  tcp_buffer: 4194304
  udp_buffer: 4194304
""".trimIndent()

        val iranConfig = """
client:
  remote_addr: "$foreignIp:${cfg.corePort}"
  key: "$token"
  kcp_mode: "$kcpMode"
  conn: 4
  mtu: ${cfg.mtu}
  encryption: "$encryption"
  pcap_sockbuf: 4194304
  tcp_buffer: 4194304
  udp_buffer: 4194304
  forwards:
$forwardsYaml
""".trimIndent()

        val foreignInstall = """
sudo mkdir -p /etc/paqet /usr/local/bin && \
ARCH=$(uname -m | sed 's/x86_64/amd64/' | sed 's/aarch64/arm64/') && \
(curl -fsSL https://github.com/hanselime/paqet/releases/latest/download/paqet-linux-${'$'}ARCH.tar.gz -o /tmp/paqet.tar.gz || \
curl -fsSL https://github.com/behzadea12/Paqet-Tunnel-Manager/releases/download/PaqetOptimized/paqet-linux-${'$'}ARCH-v2.2.0-optimize.tar.gz -o /tmp/paqet.tar.gz) && \
tar -xzf /tmp/paqet.tar.gz -C /usr/local/bin/ paqet 2>/dev/null || true && chmod +x /usr/local/bin/paqet 2>/dev/null || true && \
printf '%s' '${b64(foreignConfig)}' | base64 -d > /etc/paqet/server.yaml
cat << 'EOF' > /etc/systemd/system/paqet-server.service
[Unit]
Description=Paqet Tunnel Server (Raw Socket KCP)
After=network.target

[Service]
Type=simple
ExecStart=/usr/local/bin/paqet server -c /etc/paqet/server.yaml
Restart=always
RestartSec=3
LimitNOFILE=65535
AmbientCapabilities=CAP_NET_RAW CAP_NET_ADMIN

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload && systemctl enable --now paqet-server && systemctl status paqet-server --no-pager
""".trimIndent()

        val iranInstall = """
sudo mkdir -p /etc/paqet /usr/local/bin && \
ARCH=$(uname -m | sed 's/x86_64/amd64/' | sed 's/aarch64/arm64/') && \
(curl -fsSL https://github.com/hanselime/paqet/releases/latest/download/paqet-linux-${'$'}ARCH.tar.gz -o /tmp/paqet.tar.gz || \
curl -fsSL https://github.com/behzadea12/Paqet-Tunnel-Manager/releases/download/PaqetOptimized/paqet-linux-${'$'}ARCH-v2.2.0-optimize.tar.gz -o /tmp/paqet.tar.gz) && \
tar -xzf /tmp/paqet.tar.gz -C /usr/local/bin/ paqet 2>/dev/null || true && chmod +x /usr/local/bin/paqet 2>/dev/null || true && \
printf '%s' '${b64(iranConfig)}' | base64 -d > /etc/paqet/client.yaml
cat << 'EOF' > /etc/systemd/system/paqet-client.service
[Unit]
Description=Paqet Tunnel Client (Iran Entry)
After=network.target

[Service]
Type=simple
ExecStart=/usr/local/bin/paqet client -c /etc/paqet/client.yaml
Restart=always
RestartSec=3
LimitNOFILE=65535
AmbientCapabilities=CAP_NET_RAW CAP_NET_ADMIN

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload && systemctl enable --now paqet-client && systemctl status paqet-client --no-pager
""".trimIndent()

        val dockerForeign = """
version: '3.8'
services:
  paqet-server:
    image: hanselime/paqet:latest
    container_name: paqet_server
    restart: always
    network_mode: host
    cap_add:
      - NET_RAW
      - NET_ADMIN
    volumes:
      - ./server.yaml:/etc/paqet/server.yaml
    command: server -c /etc/paqet/server.yaml
""".trimIndent()

        val dockerIran = """
version: '3.8'
services:
  paqet-client:
    image: hanselime/paqet:latest
    container_name: paqet_client
    restart: always
    network_mode: host
    cap_add:
      - NET_RAW
      - NET_ADMIN
    volumes:
      - ./client.yaml:/etc/paqet/client.yaml
    command: client -c /etc/paqet/client.yaml
""".trimIndent()

        val portsDesc = ports.joinToString(", ") { "${it.iranPort}➔${it.foreignPort}" }
        return GeneratedTunnelCode(
            iranConfig = iranConfig,
            iranInstallCommand = iranInstall,
            foreignConfig = foreignConfig,
            foreignInstallCommand = foreignInstall,
            dockerComposeIran = dockerIran,
            dockerComposeForeign = dockerForeign,
            description = "تانل فوق سریع Paqet بر بستر Raw Socket و KCP: فوروارد پورت‌های [$portsDesc] با رمزنگاری $encryption و مود $kcpMode."
        )
    }

    // ── 2. Narnia Generator (Dnt3e/Narnia - ICMP Ping Tunnel) ─────────────────
    //
    // H18: Narnia is distributed as a Docker image — the upstream repository
    // has NO binary releases at all, so the old "download raw binary +
    // systemd unit" path could never work: its "fallback" downloaded
    // Narnia.sh but never executed it, and `|| true` swallowed the failure,
    // leaving a service pointed at a binary that does not exist.
    //
    // Deploy now uses the official Docker mechanism (stormotron/narnia image,
    // TUN device, env-var configuration — the same way as upstream
    // Narnia.sh), wrapped in a systemd unit named didban-tunnel-<id> (the
    // unit the agent queries for status and control). Reboot-safe:
    // ip_forward is persisted via an idempotent /etc/sysctl.d drop-in and
    // NAT rules are re-applied by the unit on every start.
    //
    // Constraint: one Narnia tunnel per host (upstream default TAP nvpn).

    private fun generateNarnia(cfg: TunnelConfig): GeneratedTunnelCode {
        val ports = parsePortMappings(cfg)
        val key = ensureToken(cfg)
        val foreignIp = cfg.foreignHost.ifBlank { "KHAREJ_IP" }
        val vIpKharej = cfg.virtualIpKharej.ifBlank { "10.200.200.1" }
        val vIpIran = cfg.virtualIpIran.ifBlank { "10.200.200.2" }
        val mtu = cfg.mtu.coerceAtLeast(1200)
        // Pinned upstream image (upstream Narnia.sh installs 0.0.3).
        val image = "stormotron/narnia:0.0.3"
        val unitName = "didban-tunnel-${cfg.id}"
        val iface = "nvpn" // upstream default TAP; one Narnia tunnel per host
        val operatingMode = "ip:30:$vIpKharej:$vIpIran:dynamic:50"
        val fwForeign = "/etc/didban/narnia-${cfg.id}-foreign-fw.sh"
        val fwIran = "/etc/didban/narnia-${cfg.id}-iran-fw.sh"

        // CLI-equivalent form, kept in the config files for reference/discovery.
        val foreignCmd = "narnia -l -k \"$key\" -o $operatingMode -t $mtu"
        val iranCmd = "narnia -r $foreignIp -k \"$key\" -t $mtu"

        val foreignDocker = "docker run --rm --name $unitName --cap-add=NET_ADMIN " +
            "--device /dev/net/tun:/dev/net/tun --net=host " +
            "-e INTERFACE=$iface -e PASSWORD=$key -e SERVER=0.0.0.0 " +
            "-e OPERATING_MODE=$operatingMode -e MTU=$mtu $image"
        val iranDocker = "docker run --rm --name $unitName --cap-add=NET_ADMIN " +
            "--device /dev/net/tun:/dev/net/tun --net=host " +
            "-e INTERFACE=$iface -e PASSWORD=$key -e REMOTE_IP=$foreignIp " +
            "-e MTU=$mtu $image"

        // Client-side (Iran) port mappings, idempotent: delete stale copies
        // first, then add — redeploying never duplicates rules.
        val natBlock = ports.joinToString("\n") { p ->
            "iptables -t nat -D PREROUTING -p tcp --dport ${p.iranPort} -j DNAT --to-destination $vIpKharej:${p.foreignPort} 2>/dev/null\n" +
                "iptables -t nat -D PREROUTING -p udp --dport ${p.iranPort} -j DNAT --to-destination $vIpKharej:${p.foreignPort} 2>/dev/null\n" +
                "iptables -t nat -A PREROUTING -p tcp --dport ${p.iranPort} -j DNAT --to-destination $vIpKharej:${p.foreignPort}\n" +
                "iptables -t nat -A PREROUTING -p udp --dport ${p.iranPort} -j DNAT --to-destination $vIpKharej:${p.foreignPort}"
        }

        // Shared: wait for the TAP (the container creates it in the shared
        // host netns), bring it up, set the MTU. Re-applied on every start.
        val tapUp = """
            |IFACE=$iface
            |i=0
            |while [ ${'$'}i -lt 30 ] && ! ip link show ${'$'}IFACE >/dev/null 2>&1; do sleep 1; i=${'$'}((i+1)); done
            |if ! ip link show ${'$'}IFACE >/dev/null 2>&1; then
            |  echo "narnia: ${'$'}IFACE did not appear within 30s (tunnel down?)" >&2
            |  exit 1
            |fi
            |ip link set ${'$'}IFACE mtu $mtu 2>/dev/null || true
            |ip link set ${'$'}IFACE up
            |DEF_IF=${'$'}(ip -4 route show default | awk '{print ${'$'}5}' | head -n1)
            """.trimMargin().trim()

        val foreignFw = "#!/bin/sh\n" +
            "# Didban Narnia (server): wait for TAP, bring it up, scoped masquerade.\n" +
            "$tapUp\n" +
            """
                |if [ -n "${'$'}DEF_IF" ] && ! iptables -t nat -C POSTROUTING -o "${'$'}DEF_IF" -j MASQUERADE 2>/dev/null; then
                |  iptables -t nat -A POSTROUTING -o "${'$'}DEF_IF" -j MASQUERADE
                |fi
                """.trimMargin().trim() + "\nexit 0\n"

        val iranFw = "#!/bin/sh\n" +
            "# Didban Narnia (client): wait for TAP, bring it up, apply NAT.\n" +
            "$tapUp\n" +
            """|if [ -z "${'$'}DEF_IF" ]; then echo "narnia: no default route found" >&2; exit 1; fi""".trimMargin() + "\n" +
            "$natBlock\n" +
            """
                |if ! iptables -t nat -C POSTROUTING -o "${'$'}DEF_IF" -j MASQUERADE 2>/dev/null; then
                |  iptables -t nat -A POSTROUTING -o "${'$'}DEF_IF" -j MASQUERADE
                |fi
                |if ! iptables -t nat -C POSTROUTING -o "${'$'}IFACE" -j MASQUERADE 2>/dev/null; then
                |  iptables -t nat -A POSTROUTING -o "${'$'}IFACE" -j MASQUERADE
                |fi
                |if ! iptables -C FORWARD -i "${'$'}DEF_IF" -o "${'$'}IFACE" -j ACCEPT 2>/dev/null; then
                |  iptables -I FORWARD -i "${'$'}DEF_IF" -o "${'$'}IFACE" -j ACCEPT
                |fi
                |if ! iptables -C FORWARD -i "${'$'}IFACE" -o "${'$'}DEF_IF" -j ACCEPT 2>/dev/null; then
                |  iptables -I FORWARD -i "${'$'}IFACE" -o "${'$'}DEF_IF" -j ACCEPT
                |fi
                """.trimMargin().trim() + "\nexit 0\n"

        val foreignUnit = """
            |[Unit]
            |Description=Didban Narnia tunnel server (container)
            |After=network-online.target docker.service
            |Wants=network-online.target
            |Requires=docker.service
            |
            |[Service]
            |Type=simple
            |Restart=always
            |RestartSec=3
            |LimitNOFILE=65535
            |ExecStartPre=-docker rm -f $unitName
            |ExecStart=$foreignDocker
            |ExecStop=-docker stop $unitName
            |ExecStartPost=$fwForeign
            |
            |[Install]
            |WantedBy=multi-user.target
            """.trimMargin().trim()

        val iranUnit = """
            |[Unit]
            |Description=Didban Narnia tunnel client (container)
            |After=network-online.target docker.service
            |Wants=network-online.target
            |Requires=docker.service
            |
            |[Service]
            |Type=simple
            |Restart=always
            |RestartSec=3
            |LimitNOFILE=65535
            |ExecStartPre=-docker rm -f $unitName
            |ExecStart=$iranDocker
            |ExecStop=-docker stop $unitName
            |ExecStartPost=/bin/sh -c '$fwIran >> /var/log/didban-narnia-${cfg.id}.log 2>&1 &'
            |
            |[Install]
            |WantedBy=multi-user.target
            """.trimMargin().trim()

        // Fail-fast install: docker must exist, the image must be present and
        // /dev/net/tun must be available — otherwise exit non-zero with a
        // clear reason (no more `|| true` swallowing a broken deploy).
        val foreignInstall = buildString {
            appendLine("set -u")
            appendLine("IMG=$image")
            appendLine("UNIT=$unitName")
            appendLine("command -v docker >/dev/null 2>&1 || { echo \"ERROR: docker is required for Narnia but is not installed on this host\" >&2; exit 1; }")
            appendLine("sudo mkdir -p /etc/didban /etc/sysctl.d")
            appendLine("# H18: persist ip_forward across reboots (idempotent drop-in) and apply now")
            appendLine("""printf 'net.ipv4.ip_forward=1\n' | sudo tee /etc/sysctl.d/99-didban-narnia.conf >/dev/null""")
            appendLine("sudo sysctl -w net.ipv4.ip_forward=1 >/dev/null || { echo \"ERROR: cannot enable net.ipv4.ip_forward\" >&2; exit 1; }")
            appendLine("if [ ! -e /dev/net/tun ]; then")
            appendLine("  sudo mknod -m 600 /dev/net/tun c 10 200 || { echo \"ERROR: /dev/net/tun is missing and mknod failed\" >&2; exit 1; }")
            appendLine("fi")
            appendLine("if ! docker image inspect ${'$'}IMG >/dev/null 2>&1; then")
            appendLine("  docker pull ${'$'}IMG || { echo \"ERROR: failed to pull ${'$'}IMG\" >&2; exit 1; }")
            appendLine("fi")
            appendLine("sudo tee $fwForeign >/dev/null <<'FW'")
            append(foreignFw)
            appendLine("FW")
            appendLine("sudo chmod +x $fwForeign")
            appendLine("printf '%s' '${b64(foreignUnit)}' | base64 -d | sudo tee /etc/systemd/system/${'$'}UNIT.service >/dev/null")
            appendLine("sudo systemctl daemon-reload")
            appendLine("sudo systemctl enable ${'$'}UNIT")
            appendLine("sudo systemctl restart ${'$'}UNIT")
            appendLine("sudo systemctl status ${'$'}UNIT --no-pager")
        }

        val iranInstall = buildString {
            appendLine("set -u")
            appendLine("IMG=$image")
            appendLine("UNIT=$unitName")
            appendLine("command -v docker >/dev/null 2>&1 || { echo \"ERROR: docker is required for Narnia but is not installed on this host\" >&2; exit 1; }")
            appendLine("sudo mkdir -p /etc/didban /etc/sysctl.d")
            appendLine("# H18: persist ip_forward across reboots (idempotent drop-in) and apply now")
            appendLine("""printf 'net.ipv4.ip_forward=1\n' | sudo tee /etc/sysctl.d/99-didban-narnia.conf >/dev/null""")
            appendLine("sudo sysctl -w net.ipv4.ip_forward=1 >/dev/null || { echo \"ERROR: cannot enable net.ipv4.ip_forward\" >&2; exit 1; }")
            appendLine("if [ ! -e /dev/net/tun ]; then")
            appendLine("  sudo mknod -m 600 /dev/net/tun c 10 200 || { echo \"ERROR: /dev/net/tun is missing and mknod failed\" >&2; exit 1; }")
            appendLine("fi")
            appendLine("if ! docker image inspect ${'$'}IMG >/dev/null 2>&1; then")
            appendLine("  docker pull ${'$'}IMG || { echo \"ERROR: failed to pull ${'$'}IMG\" >&2; exit 1; }")
            appendLine("fi")
            appendLine("sudo tee $fwIran >/dev/null <<'FW'")
            append(iranFw)
            appendLine("FW")
            appendLine("sudo chmod +x $fwIran")
            appendLine("printf '%s' '${b64(iranUnit)}' | base64 -d | sudo tee /etc/systemd/system/${'$'}UNIT.service >/dev/null")
            appendLine("sudo systemctl daemon-reload")
            appendLine("sudo systemctl enable ${'$'}UNIT")
            appendLine("sudo systemctl restart ${'$'}UNIT")
            appendLine("sudo systemctl status ${'$'}UNIT --no-pager")
        }

        val dockerForeign = """
            |# Narnia server — official Docker mechanism (upstream Narnia.sh equivalent).
            |# Note: one Narnia tunnel per host (TAP $iface). If the link stays down:
            |#   ip link set $iface mtu $mtu && ip link set $iface up
            |services:
            |  narnia_server:
            |    image: $image
            |    container_name: narnia_server
            |    restart: always
            |    network_mode: host
            |    cap_add:
            |      - NET_ADMIN
            |    devices:
            |      - /dev/net/tun:/dev/net/tun
            |    environment:
            |      INTERFACE: $iface
            |      PASSWORD: "$key"
            |      SERVER: "0.0.0.0"
            |      OPERATING_MODE: "$operatingMode"
            |      MTU: "$mtu"
            """.trimMargin().trim()

        val dockerIran = """
            |# Narnia client — official Docker mechanism (upstream Narnia.sh equivalent).
            |# Note: one Narnia tunnel per host (TAP $iface). If the link stays down:
            |#   ip link set $iface mtu $mtu && ip link set $iface up
            |services:
            |  narnia_client:
            |    image: $image
            |    container_name: narnia_client
            |    restart: always
            |    network_mode: host
            |    cap_add:
            |      - NET_ADMIN
            |    devices:
            |      - /dev/net/tun:/dev/net/tun
            |    environment:
            |      INTERFACE: $iface
            |      PASSWORD: "$key"
            |      REMOTE_IP: "$foreignIp"
            |      MTU: "$mtu"
            """.trimMargin().trim()

        val portsDesc = ports.joinToString(", ") { "${it.iranPort}➔${it.foreignPort}" }
        return GeneratedTunnelCode(
            iranConfig = "# Narnia client (ICMP) — container $unitName, unit $unitName.service\n$iranCmd\n",
            iranInstallCommand = iranInstall,
            foreignConfig = "# Narnia server (ICMP) — container $unitName, unit $unitName.service\n$foreignCmd\n",
            foreignInstallCommand = foreignInstall,
            dockerComposeIran = dockerIran,
            dockerComposeForeign = dockerForeign,
            description = "تانل اختصاصی Narnia پنهان درون پکت‌های ICMP (Ping): روتینگ پورت‌های [$portsDesc] روی شبکه مجازی $vIpIran به $vIpKharej با رمزنگاری ChaCha20 — استقرار از طریق Docker (stormotron/narnia) و systemd، با فورواردینگ دائمی."
        )
    }
    // ── 3. Spoof Tunnel Generator (ParsaKSH/spoof-tunnel & forks) ────────────

    private fun generateSpoofTunnel(cfg: TunnelConfig): GeneratedTunnelCode {
        val ports = parsePortMappings(cfg)
        val foreignIp = cfg.foreignHost.ifBlank { "KHAREJ_IP" }
        val iranIp = cfg.iranHost.ifBlank { "IRAN_IP" }
        val spoofSrc = cfg.spoofSrcIp.ifBlank { "1.1.1.1" }
        val spoofPeer = cfg.spoofPeerIp.ifBlank { "8.8.8.8" }
        val isIcmp = cfg.transport == TunnelTransport.IP_SPOOF_ICMP
        val transportType = if (isIcmp) "icmp" else "udp"

        // Both keys derive from the single persisted cfg.token (H3): the
        // pair is stable across code regeneration / redeploy.
        val serverPrivKey = deriveKey(ensureToken(cfg), "server")
        val clientPrivKey = deriveKey(ensureToken(cfg), "client")

        val firstPort = ports.firstOrNull() ?: PortMapping(cfg.iranPort, cfg.foreignPort)

        val foreignConfig = """
{
  "mode": "server",
  "listen": {
    "address": "0.0.0.0",
    "port": ${cfg.corePort}
  },
  "transport": {
    "type": "$transportType",
    "icmp_mode": "reply"
  },
  "spoof": {
    "source_ip": "$spoofPeer",
    "peer_spoof_ip": "$spoofSrc",
    "client_real_ip": "$iranIp"
  },
  "crypto": {
    "private_key": "$serverPrivKey",
    "peer_public_key": "$clientPrivKey"
  },
  "performance": {
    "buffer_size": 4194304,
    "mtu": ${cfg.mtu},
    "workers": 4
  },
  "reliability": {
    "enabled": true,
    "window_size": 128,
    "retransmit_timeout_ms": 200,
    "ack_interval_ms": 20
  },
  "fec": {
    "enabled": true,
    "data_shards": 10,
    "parity_shards": 3
  }
}
""".trimIndent()

        val iranConfig = """
{
  "mode": "client",
  "listen": {
    "address": "0.0.0.0",
    "port": ${firstPort.iranPort}
  },
  "remote": "$foreignIp",
  "remote_port": ${cfg.corePort},
  "forward": "127.0.0.1:${firstPort.foreignPort}",
  "transport": {
    "type": "$transportType",
    "icmp_mode": "echo"
  },
  "spoof": {
    "source_ip": "$spoofSrc",
    "peer_spoof_ip": "$spoofPeer"
  },
  "crypto": {
    "private_key": "$clientPrivKey",
    "peer_public_key": "$serverPrivKey"
  },
  "performance": {
    "buffer_size": 4194304,
    "mtu": ${cfg.mtu},
    "workers": 4
  },
  "reliability": {
    "enabled": true,
    "window_size": 128,
    "retransmit_timeout_ms": 200,
    "ack_interval_ms": 20
  },
  "fec": {
    "enabled": true,
    "data_shards": 10,
    "parity_shards": 3
  }
}
""".trimIndent()

        val foreignInstall = """
sudo mkdir -p /etc/spoof-tunnel /usr/local/bin && \
ARCH=$(uname -m | sed 's/x86_64/amd64/' | sed 's/aarch64/arm64/') && \
(curl -fsSL https://github.com/ParsaKSH/spoof-tunnel/releases/latest/download/spoof-tunnel-linux-${'$'}ARCH.tar.gz -o /tmp/spoof.tar.gz && \
tar -xzf /tmp/spoof.tar.gz -C /usr/local/bin/ spoof-tunnel 2>/dev/null || \
curl -fsSL https://raw.githubusercontent.com/ParsaKSH/spoof-tunnel/main/install.sh -o /tmp/install.sh) && \
chmod +x /usr/local/bin/spoof-tunnel 2>/dev/null || true && \
printf '%s' '${b64(foreignConfig)}' | base64 -d > /etc/spoof-tunnel/server.json
cat << 'EOF' > /etc/systemd/system/spoof-tunnel.service
[Unit]
Description=Mutual IP Spoofing Tunnel Server
After=network.target

[Service]
Type=simple
ExecStart=/usr/local/bin/spoof-tunnel -c /etc/spoof-tunnel/server.json
Restart=always
RestartSec=3
LimitNOFILE=65535
AmbientCapabilities=CAP_NET_RAW CAP_NET_ADMIN CAP_BPF

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload && systemctl enable --now spoof-tunnel && systemctl status spoof-tunnel --no-pager
""".trimIndent()

        val iranInstall = """
sudo mkdir -p /etc/spoof-tunnel /usr/local/bin && \
ARCH=$(uname -m | sed 's/x86_64/amd64/' | sed 's/aarch64/arm64/') && \
(curl -fsSL https://github.com/ParsaKSH/spoof-tunnel/releases/latest/download/spoof-tunnel-linux-${'$'}ARCH.tar.gz -o /tmp/spoof.tar.gz && \
tar -xzf /tmp/spoof.tar.gz -C /usr/local/bin/ spoof-tunnel 2>/dev/null || \
curl -fsSL https://raw.githubusercontent.com/ParsaKSH/spoof-tunnel/main/install.sh -o /tmp/install.sh) && \
chmod +x /usr/local/bin/spoof-tunnel 2>/dev/null || true && \
printf '%s' '${b64(iranConfig)}' | base64 -d > /etc/spoof-tunnel/client.json
cat << 'EOF' > /etc/systemd/system/spoof-tunnel.service
[Unit]
Description=Mutual IP Spoofing Tunnel Client
After=network.target

[Service]
Type=simple
ExecStart=/usr/local/bin/spoof-tunnel -c /etc/spoof-tunnel/client.json
Restart=always
RestartSec=3
LimitNOFILE=65535
AmbientCapabilities=CAP_NET_RAW CAP_NET_ADMIN CAP_BPF

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload && systemctl enable --now spoof-tunnel && systemctl status spoof-tunnel --no-pager
""".trimIndent()

        val dockerForeign = """
version: '3.8'
services:
  spoof-server:
    image: parsaksh/spoof-tunnel:latest
    container_name: spoof_tunnel_server
    restart: always
    network_mode: host
    cap_add:
      - NET_RAW
      - NET_ADMIN
    volumes:
      - ./server.json:/etc/spoof-tunnel/server.json
    command: -c /etc/spoof-tunnel/server.json
""".trimIndent()

        val dockerIran = """
version: '3.8'
services:
  spoof-client:
    image: parsaksh/spoof-tunnel:latest
    container_name: spoof_tunnel_client
    restart: always
    network_mode: host
    cap_add:
      - NET_RAW
      - NET_ADMIN
    volumes:
      - ./client.json:/etc/spoof-tunnel/client.json
    command: -c /etc/spoof-tunnel/client.json
""".trimIndent()

        return GeneratedTunnelCode(
            iranConfig = iranConfig,
            iranInstallCommand = iranInstall,
            foreignConfig = foreignConfig,
            foreignInstallCommand = foreignInstall,
            dockerComposeIran = dockerIran,
            dockerComposeForeign = dockerForeign,
            description = "تانل جعل دوطرفه IP مبدا (Mutual IP Spoofing): تغییر فیلد Source IP در سطح Raw Socket با لایه تضمین تحویل پکت‌ها و بازیابی خطای Reed-Solomon FEC."
        )
    }

    // ── 4. Backhaul Generator ────────────────────────────────────────────────

    private fun generateBackhaul(cfg: TunnelConfig): GeneratedTunnelCode {
        val ports = parsePortMappings(cfg)
        val transportStr = when (cfg.transport) {
            TunnelTransport.WS -> "ws"
            TunnelTransport.WSMUX -> "wsmux"
            TunnelTransport.TCPMUX -> "tcpmux"
            else -> "tcp"
        }
        val token = ensureToken(cfg)
        val foreignIp = cfg.foreignHost.ifBlank { "KHAREJ_IP" }

        val foreignPortsBlock = ports.joinToString("\n\n") { p ->
            """[[server.ports]]
listen_port = ${p.iranPort}
target_addr = "127.0.0.1:${p.foreignPort}""""
        }

        // Server Config (Foreign Server)
        val foreignConfig = """
[server]
bind_addr = "0.0.0.0:${cfg.corePort}"
transport = "$transportStr"
token = "$token"
keepalive_period = 20
heartbeat = 40
channel_size = 2048
sniffer = false
web_port = 0

$foreignPortsBlock
""".trimIndent()

        // Client Config (Iran Server)
        val iranConfig = """
[client]
remote_addr = "$foreignIp:${cfg.corePort}"
transport = "$transportStr"
token = "$token"
connection_pool = 8
aggressive_pool = true
keepalive_period = 20
heartbeat = 40
retry_interval = 3
sniffer = false
web_port = 0
""".trimIndent()

        val foreignInstall = """
sudo mkdir -p /etc/backhaul /usr/local/bin && \
ARCH=$(uname -m | sed 's/x86_64/amd64/' | sed 's/aarch64/arm64/') && \
(curl -fsSL https://github.com/MusLatest/backhaul/releases/latest/download/backhaul_linux_${'$'}ARCH.tar.gz -o /tmp/backhaul.tar.gz && \
tar -xzf /tmp/backhaul.tar.gz -C /usr/local/bin/ && chmod +x /usr/local/bin/backhaul) || true && \
printf '%s' '${b64(foreignConfig)}' | base64 -d > /etc/backhaul/config.toml
cat << 'EOF' > /etc/systemd/system/backhaul.service
[Unit]
Description=Backhaul Server Tunnel
After=network.target

[Service]
Type=simple
ExecStart=/usr/local/bin/backhaul -c /etc/backhaul/config.toml
Restart=always
RestartSec=3
LimitNOFILE=65535

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload && systemctl enable --now backhaul && systemctl status backhaul --no-pager
""".trimIndent()

        val iranInstall = """
sudo mkdir -p /etc/backhaul /usr/local/bin && \
ARCH=$(uname -m | sed 's/x86_64/amd64/' | sed 's/aarch64/arm64/') && \
(curl -fsSL https://github.com/MusLatest/backhaul/releases/latest/download/backhaul_linux_${'$'}ARCH.tar.gz -o /tmp/backhaul.tar.gz && \
tar -xzf /tmp/backhaul.tar.gz -C /usr/local/bin/ && chmod +x /usr/local/bin/backhaul) || true && \
printf '%s' '${b64(iranConfig)}' | base64 -d > /etc/backhaul/config.toml
cat << 'EOF' > /etc/systemd/system/backhaul.service
[Unit]
Description=Backhaul Client Tunnel
After=network.target

[Service]
Type=simple
ExecStart=/usr/local/bin/backhaul -c /etc/backhaul/config.toml
Restart=always
RestartSec=3
LimitNOFILE=65535

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload && systemctl enable --now backhaul && systemctl status backhaul --no-pager
""".trimIndent()

        val dockerForeign = """
version: '3.8'
services:
  backhaul-server:
    image: muslatest/backhaul:latest
    container_name: backhaul_server
    restart: always
    network_mode: host
    volumes:
      - ./config.toml:/etc/backhaul/config.toml
    command: -c /etc/backhaul/config.toml
""".trimIndent()

        val dockerIran = """
version: '3.8'
services:
  backhaul-client:
    image: muslatest/backhaul:latest
    container_name: backhaul_client
    restart: always
    network_mode: host
    volumes:
      - ./config.toml:/etc/backhaul/config.toml
    command: -c /etc/backhaul/config.toml
""".trimIndent()

        val portsDesc = ports.joinToString(", ") { "${it.iranPort}➔${it.foreignPort}" }
        return GeneratedTunnelCode(
            iranConfig = iranConfig,
            iranInstallCommand = iranInstall,
            foreignConfig = foreignConfig,
            foreignInstallCommand = foreignInstall,
            dockerComposeIran = dockerIran,
            dockerComposeForeign = dockerForeign,
            description = "تانل معکوس Backhaul: اتصال پورت‌های [$portsDesc] ایران به خارج با پروتکل ${cfg.transport.displayName}."
        )
    }

    // ── 5. Rathole Generator ─────────────────────────────────────────────────

    private fun generateRathole(cfg: TunnelConfig): GeneratedTunnelCode {
        val ports = parsePortMappings(cfg)
        val token = ensureToken(cfg)
        val foreignIp = cfg.foreignHost.ifBlank { "KHAREJ_IP" }

        val serverServices = ports.joinToString("\n\n") { p ->
            """[server.services.app_${p.iranPort}]
token = "$token"
bind_addr = "0.0.0.0:${p.iranPort}""""
        }

        val clientServices = ports.joinToString("\n\n") { p ->
            """[client.services.app_${p.iranPort}]
token = "$token"
local_addr = "127.0.0.1:${p.foreignPort}""""
        }

        val foreignConfig = """
[server]
bind_addr = "0.0.0.0:${cfg.corePort}"
default_token = "$token"

$serverServices
""".trimIndent()

        val iranConfig = """
[client]
remote_addr = "$foreignIp:${cfg.corePort}"
default_token = "$token"

$clientServices
""".trimIndent()

        val foreignInstall = """
sudo mkdir -p /etc/rathole /usr/local/bin && \
ARCH=$(uname -m) && \
ZIP_ARCH="x86_64-unknown-linux-musl" && \
if [ "${'$'}ARCH" = "aarch64" ] || [ "${'$'}ARCH" = "arm64" ]; then ZIP_ARCH="aarch64-unknown-linux-musl"; fi && \
curl -fsSL https://github.com/rapiz1/rathole/releases/latest/download/rathole-${'$'}ZIP_ARCH.zip -o /tmp/rathole.zip && \
apt-get install -y unzip >/dev/null 2>&1 || yum install -y unzip >/dev/null 2>&1 && \
unzip -o /tmp/rathole.zip -d /usr/local/bin/ && chmod +x /usr/local/bin/rathole && \
printf '%s' '${b64(foreignConfig)}' | base64 -d > /etc/rathole/server.toml
cat << 'EOF' > /etc/systemd/system/rathole.service
[Unit]
Description=Rathole Server
After=network.target

[Service]
Type=simple
ExecStart=/usr/local/bin/rathole /etc/rathole/server.toml
Restart=always
RestartSec=3
LimitNOFILE=65535

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload && systemctl enable --now rathole && systemctl status rathole --no-pager
""".trimIndent()

        val iranInstall = """
sudo mkdir -p /etc/rathole /usr/local/bin && \
ARCH=$(uname -m) && \
ZIP_ARCH="x86_64-unknown-linux-musl" && \
if [ "${'$'}ARCH" = "aarch64" ] || [ "${'$'}ARCH" = "arm64" ]; then ZIP_ARCH="aarch64-unknown-linux-musl"; fi && \
curl -fsSL https://github.com/rapiz1/rathole/releases/latest/download/rathole-${'$'}ZIP_ARCH.zip -o /tmp/rathole.zip && \
apt-get install -y unzip >/dev/null 2>&1 || yum install -y unzip >/dev/null 2>&1 && \
unzip -o /tmp/rathole.zip -d /usr/local/bin/ && chmod +x /usr/local/bin/rathole && \
printf '%s' '${b64(iranConfig)}' | base64 -d > /etc/rathole/client.toml
cat << 'EOF' > /etc/systemd/system/rathole.service
[Unit]
Description=Rathole Client
After=network.target

[Service]
Type=simple
ExecStart=/usr/local/bin/rathole /etc/rathole/client.toml
Restart=always
RestartSec=3
LimitNOFILE=65535

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload && systemctl enable --now rathole && systemctl status rathole --no-pager
""".trimIndent()

        val dockerForeign = """
version: '3.8'
services:
  rathole-server:
    image: rapiz1/rathole:latest
    container_name: rathole_server
    restart: always
    network_mode: host
    volumes:
      - ./server.toml:/app/server.toml
    command: /app/server.toml
""".trimIndent()

        val dockerIran = """
version: '3.8'
services:
  rathole-client:
    image: rapiz1/rathole:latest
    container_name: rathole_client
    restart: always
    network_mode: host
    volumes:
      - ./client.toml:/app/client.toml
    command: /app/client.toml
""".trimIndent()

        val portsDesc = ports.joinToString(", ") { "${it.iranPort}➔${it.foreignPort}" }
        return GeneratedTunnelCode(
            iranConfig = iranConfig,
            iranInstallCommand = iranInstall,
            foreignConfig = foreignConfig,
            foreignInstallCommand = foreignInstall,
            dockerComposeIran = dockerIran,
            dockerComposeForeign = dockerForeign,
            description = "تانل سبک و امن Rathole نوشته شده با Rust: رله پورت‌های [$portsDesc] با کمترین مصرف رم."
        )
    }

    // ── 6. GOST Generator ────────────────────────────────────────────────────

    private fun generateGost(cfg: TunnelConfig): GeneratedTunnelCode {
        val ports = parsePortMappings(cfg)
        val foreignIp = cfg.foreignHost.ifBlank { "KHAREJ_IP" }
        val proto = when (cfg.transport) {
            TunnelTransport.WS -> "relay+ws"
            TunnelTransport.GRPC -> "relay+grpc"
            TunnelTransport.TCPMUX -> "relay+tcpmux"
            TunnelTransport.UDP -> "udp"
            else -> "tcp"
        }

        val foreignCmd = if (proto == "tcp" || proto == "udp") {
            "# GOST on Kharej runs your target services"
        } else {
            "/usr/local/bin/gost -L \"$proto://:${cfg.corePort}\""
        }

        val iranCmd = if (proto == "tcp") {
            ports.joinToString(" ") { p ->
                "-L \"tcp://:${p.iranPort}/$foreignIp:${p.foreignPort}\" -L \"udp://:${p.iranPort}/$foreignIp:${p.foreignPort}\""
            }.let { "/usr/local/bin/gost $it" }
        } else {
            val listeners = ports.joinToString(" ") { p ->
                "-L \"tcp://:${p.iranPort}/127.0.0.1:${p.foreignPort}\""
            }
            "/usr/local/bin/gost $listeners -F \"$proto://$foreignIp:${cfg.corePort}\""
        }

        val ForeignGostUnit = """
[Unit]
Description=GOST Server
After=network.target

[Service]
Type=simple
ExecStart=$foreignCmd
Restart=always
RestartSec=3
LimitNOFILE=65535

[Install]
WantedBy=multi-user.target
""".trimIndent()
        val foreignInstall = """
sudo mkdir -p /usr/local/bin && \
ARCH=$(uname -m | sed 's/x86_64/amd64/' | sed 's/aarch64/arm64/') && \
(curl -fsSL https://github.com/go-gost/gost/releases/latest/download/gost_3.0.0_linux_${'$'}ARCH.tar.gz -o /tmp/gost.tar.gz && \
tar -xzf /tmp/gost.tar.gz -C /usr/local/bin/ && chmod +x /usr/local/bin/gost) || true && \
printf '%s' '${b64(ForeignGostUnit)}' | base64 -d > /etc/systemd/system/gost.service
systemctl daemon-reload && systemctl enable --now gost && systemctl status gost --no-pager
""".trimIndent()

        val IranGostUnit = """
[Unit]
Description=GOST Client Forwarder
After=network.target

[Service]
Type=simple
ExecStart=$iranCmd
Restart=always
RestartSec=3
LimitNOFILE=65535

[Install]
WantedBy=multi-user.target
""".trimIndent()
        val iranInstall = """
sudo mkdir -p /usr/local/bin && \
ARCH=$(uname -m | sed 's/x86_64/amd64/' | sed 's/aarch64/arm64/') && \
(curl -fsSL https://github.com/go-gost/gost/releases/latest/download/gost_3.0.0_linux_${'$'}ARCH.tar.gz -o /tmp/gost.tar.gz && \
tar -xzf /tmp/gost.tar.gz -C /usr/local/bin/ && chmod +x /usr/local/bin/gost) || true && \
printf '%s' '${b64(IranGostUnit)}' | base64 -d > /etc/systemd/system/gost.service
systemctl daemon-reload && systemctl enable --now gost && systemctl status gost --no-pager
""".trimIndent()

        val dockerForeign = """
version: '3.8'
services:
  gost-server:
    image: gogost/gost:latest
    container_name: gost_server
    restart: always
    network_mode: host
    command: -L "$proto://:${cfg.corePort}"
""".trimIndent()

        val dockerIran = """
version: '3.8'
services:
  gost-client:
    image: gogost/gost:latest
    container_name: gost_client
    restart: always
    network_mode: host
    command: ${iranCmd.removePrefix("/usr/local/bin/gost ")}
""".trimIndent()

        val portsDesc = ports.joinToString(", ") { "${it.iranPort}➔${it.foreignPort}" }
        return GeneratedTunnelCode(
            iranConfig = iranCmd,
            iranInstallCommand = iranInstall,
            foreignConfig = foreignCmd,
            foreignInstallCommand = foreignInstall,
            dockerComposeIran = dockerIran,
            dockerComposeForeign = dockerForeign,
            description = "تانل همه‌کاره GOST: رله و فوروارد پورت‌های [$portsDesc] با پروتکل ${cfg.transport.displayName}."
        )
    }

    // ── 7. Chisel Generator ──────────────────────────────────────────────────

    private fun generateChisel(cfg: TunnelConfig): GeneratedTunnelCode {
        val ports = parsePortMappings(cfg)
        val foreignIp = cfg.foreignHost.ifBlank { "KHAREJ_IP" }
        val auth = "admin:${ensureToken(cfg)}"

        val reverseArgs = ports.joinToString(" ") { p -> "R:${p.iranPort}:127.0.0.1:${p.foreignPort}" }

        val foreignCmd = "/usr/local/bin/chisel server --port ${cfg.corePort} --auth \"$auth\" --reverse"
        val iranCmd = "/usr/local/bin/chisel client --auth \"$auth\" http://$foreignIp:${cfg.corePort} $reverseArgs"

        val ForeignChiselUnit = """
[Unit]
Description=Chisel Server
After=network.target

[Service]
Type=simple
ExecStart=$foreignCmd
Restart=always
RestartSec=3
LimitNOFILE=65535

[Install]
WantedBy=multi-user.target
""".trimIndent()
        val foreignInstall = """
sudo mkdir -p /usr/local/bin && \
ARCH=$(uname -m | sed 's/x86_64/amd64/' | sed 's/aarch64/arm64/') && \
(curl -fsSL https://github.com/jpillora/chisel/releases/latest/download/chisel_linux_${'$'}ARCH.gz -o /tmp/chisel.gz && \
gzip -d -f /tmp/chisel.gz && mv /tmp/chisel /usr/local/bin/chisel && chmod +x /usr/local/bin/chisel) || true && \
printf '%s' '${b64(ForeignChiselUnit)}' | base64 -d > /etc/systemd/system/chisel.service
systemctl daemon-reload && systemctl enable --now chisel && systemctl status chisel --no-pager
""".trimIndent()

        val IranChiselUnit = """
[Unit]
Description=Chisel Client
After=network.target

[Service]
Type=simple
ExecStart=$iranCmd
Restart=always
RestartSec=3
LimitNOFILE=65535

[Install]
WantedBy=multi-user.target
""".trimIndent()
        val iranInstall = """
sudo mkdir -p /usr/local/bin && \
ARCH=$(uname -m | sed 's/x86_64/amd64/' | sed 's/aarch64/arm64/') && \
(curl -fsSL https://github.com/jpillora/chisel/releases/latest/download/chisel_linux_${'$'}ARCH.gz -o /tmp/chisel.gz && \
gzip -d -f /tmp/chisel.gz && mv /tmp/chisel /usr/local/bin/chisel && chmod +x /usr/local/bin/chisel) || true && \
printf '%s' '${b64(IranChiselUnit)}' | base64 -d > /etc/systemd/system/chisel.service
systemctl daemon-reload && systemctl enable --now chisel && systemctl status chisel --no-pager
""".trimIndent()

        val dockerForeign = """
version: '3.8'
services:
  chisel-server:
    image: jpillora/chisel:latest
    container_name: chisel_server
    restart: always
    network_mode: host
    command: server --port ${cfg.corePort} --auth "$auth" --reverse
""".trimIndent()

        val dockerIran = """
version: '3.8'
services:
  chisel-client:
    image: jpillora/chisel:latest
    container_name: chisel_client
    restart: always
    network_mode: host
    command: client --auth "$auth" http://$foreignIp:${cfg.corePort} $reverseArgs
""".trimIndent()

        val portsDesc = ports.joinToString(", ") { "${it.iranPort}➔${it.foreignPort}" }
        return GeneratedTunnelCode(
            iranConfig = iranCmd,
            iranInstallCommand = iranInstall,
            foreignConfig = foreignCmd,
            foreignInstallCommand = foreignInstall,
            dockerComposeIran = dockerIran,
            dockerComposeForeign = dockerForeign,
            description = "تانل امن Chisel بر بستر WebSocket و HTTP: رله پورت‌های [$portsDesc] با پوشش ترافیک عادی وب."
        )
    }

    // ── 8. FRP Generator ─────────────────────────────────────────────────────

    private fun generateFrp(cfg: TunnelConfig): GeneratedTunnelCode {
        val ports = parsePortMappings(cfg)
        val foreignIp = cfg.foreignHost.ifBlank { "KHAREJ_IP" }
        val token = ensureToken(cfg)

        val frpProxies = ports.joinToString("\n\n") { p ->
            """[[proxies]]
name = "tcp_${p.iranPort}"
type = "tcp"
localIP = "127.0.0.1"
localPort = ${p.foreignPort}
remotePort = ${p.iranPort}"""
        }

        val foreignConfig = """
bindPort = ${cfg.corePort}
auth.token = "$token"
""".trimIndent()

        val iranConfig = """
serverAddr = "$foreignIp"
serverPort = ${cfg.corePort}
auth.token = "$token"

$frpProxies
""".trimIndent()

        val foreignInstall = """
sudo mkdir -p /etc/frp /usr/local/bin && \
ARCH=$(uname -m | sed 's/x86_64/amd64/' | sed 's/aarch64/arm64/') && \
(curl -fsSL https://github.com/fatedier/frp/releases/latest/download/frp_0.58.1_linux_${'$'}ARCH.tar.gz -o /tmp/frp.tar.gz && \
tar -xzf /tmp/frp.tar.gz -C /tmp/ && cp /tmp/frp_*/frps /usr/local/bin/ && chmod +x /usr/local/bin/frps) || true && \
printf '%s' '${b64(foreignConfig)}' | base64 -d > /etc/frp/frps.toml
cat << 'EOF' > /etc/systemd/system/frps.service
[Unit]
Description=FRP Server
After=network.target

[Service]
Type=simple
ExecStart=/usr/local/bin/frps -c /etc/frp/frps.toml
Restart=always
RestartSec=3
LimitNOFILE=65535

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload && systemctl enable --now frps && systemctl status frps --no-pager
""".trimIndent()

        val iranInstall = """
sudo mkdir -p /etc/frp /usr/local/bin && \
ARCH=$(uname -m | sed 's/x86_64/amd64/' | sed 's/aarch64/arm64/') && \
(curl -fsSL https://github.com/fatedier/frp/releases/latest/download/frp_0.58.1_linux_${'$'}ARCH.tar.gz -o /tmp/frp.tar.gz && \
tar -xzf /tmp/frp.tar.gz -C /tmp/ && cp /tmp/frp_*/frpc /usr/local/bin/ && chmod +x /usr/local/bin/frpc) || true && \
printf '%s' '${b64(iranConfig)}' | base64 -d > /etc/frp/frpc.toml
cat << 'EOF' > /etc/systemd/system/frpc.service
[Unit]
Description=FRP Client
After=network.target

[Service]
Type=simple
ExecStart=/usr/local/bin/frpc -c /etc/frp/frpc.toml
Restart=always
RestartSec=3
LimitNOFILE=65535

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload && systemctl enable --now frpc && systemctl status frpc --no-pager
""".trimIndent()

        val dockerForeign = """
version: '3.8'
services:
  frps:
    image: fatedier/frps:latest
    container_name: frps_server
    restart: always
    network_mode: host
    volumes:
      - ./frps.toml:/etc/frp/frps.toml
""".trimIndent()

        val dockerIran = """
version: '3.8'
services:
  frpc:
    image: fatedier/frpc:latest
    container_name: frpc_client
    restart: always
    network_mode: host
    volumes:
      - ./frpc.toml:/etc/frp/frpc.toml
""".trimIndent()

        val portsDesc = ports.joinToString(", ") { "${it.iranPort}➔${it.foreignPort}" }
        return GeneratedTunnelCode(
            iranConfig = iranConfig,
            iranInstallCommand = iranInstall,
            foreignConfig = foreignConfig,
            foreignInstallCommand = foreignInstall,
            dockerComposeIran = dockerIran,
            dockerComposeForeign = dockerForeign,
            description = "تانل ریورس FRP: رله پورت‌های [$portsDesc] با هسته کلاسیک و باسابقه FRP."
        )
    }

    // ── 9. IPTables Port Forwarding Generator ────────────────────────────────

    private fun generateIptables(cfg: TunnelConfig): GeneratedTunnelCode {
        val ports = parsePortMappings(cfg)
        val foreignIp = cfg.foreignHost.ifBlank { "KHAREJ_IP" }

        val rules = ports.joinToString("\n") { p ->
            """sudo iptables -t nat -A PREROUTING -p tcp --dport ${p.iranPort} -j DNAT --to-destination $foreignIp:${p.foreignPort}
sudo iptables -t nat -A PREROUTING -p udp --dport ${p.iranPort} -j DNAT --to-destination $foreignIp:${p.foreignPort}
sudo iptables -t nat -A POSTROUTING -p tcp -d $foreignIp --dport ${p.foreignPort} -j MASQUERADE
sudo iptables -t nat -A POSTROUTING -p udp -d $foreignIp --dport ${p.foreignPort} -j MASQUERADE"""
        }

        val iranInstall = """
sudo sysctl -w net.ipv4.ip_forward=1
echo "net.ipv4.ip_forward=1" | sudo tee -a /etc/sysctl.conf
$rules
(sudo apt-get install -y iptables-persistent >/dev/null 2>&1 && sudo netfilter-persistent save) || true
""".trimIndent()

        val portsDesc = ports.joinToString(", ") { "${it.iranPort}➔${it.foreignPort}" }
        val foreignInfo = "# سرور خارج نیازی به تنظیمات خاصی ندارد؛ فقط سرویس‌های شما روی پورت‌های مقصد [$portsDesc] در حال اجرا باشند."

        return GeneratedTunnelCode(
            iranConfig = "# IPTables Kernel Forwarding Rules:\n$rules",
            iranInstallCommand = iranInstall,
            foreignConfig = foreignInfo,
            foreignInstallCommand = "# No setup required on Foreign server",
            dockerComposeIran = "# IPTables runs directly in Linux kernel",
            dockerComposeForeign = "# No setup required",
            description = "فوروارد مستقیم در سطح هسته لینوکس با IPTables: روتینگ فوق سریع پورت‌های [$portsDesc] بدون پردازش اضافه."
        )
    }

    // ── Zero-Touch Auto-Deploy & Sync (Smite Panel Style) ─────────────────────

    suspend fun autoDeployTunnel(
        ctx: Context,
        cfg: TunnelConfig,
        apiClient: ApiClient = ApiClient()
    ): AutoDeployResult = withContext(Dispatchers.IO) {
        val servers = Prefs.loadServers(ctx)
        val code: GeneratedTunnelCode
        try {
            code = generateCode(cfg)
        } catch (e: IllegalArgumentException) {
            // H4: invalid field values - deploy nothing, surface the reason.
            return@withContext AutoDeployResult(
                null, null, false,
                "خطای اعتبارسنجی فیلدهای تانل — هیچ چیزی deploy نشد: ${e.message}"
            )
        }

        // Find Iran server in registered Didban servers.
        // H16: strict normalized host match — the old substring `contains`
        // could pick a DIFFERENT registered server (e.g. "example.com" for a
        // tunnel field "sub.example.com") and deploy onto the wrong machine.
        val iranServer = servers.firstOrNull { s ->
            (cfg.iranServerId != null && s.id == cfg.iranServerId) ||
            TunnelFieldValidation.hostMatchesServer(cfg.iranHost, s.host)
        }

        // Find Foreign server in registered Didban servers.
        val foreignServer = servers.firstOrNull { s ->
            (cfg.foreignServerId != null && s.id == cfg.foreignServerId) ||
            TunnelFieldValidation.hostMatchesServer(cfg.foreignHost, s.host)
        }

        var iranRes: AutoDeployServerResult? = null
        var foreignRes: AutoDeployServerResult? = null

        // 1. Deploy to Foreign Server first (if server has agent and core is not IPTables)
        if (foreignServer != null && cfg.core != TunnelCore.IPTABLES) {
            try {
                val reqJson = JSONObject().apply {
                    put("id", cfg.id.toString())
                    put("name", "${cfg.name} (خارج)")
                    put("core", cfg.core.name)
                    put("role", "foreign")
                    put("config_content", code.foreignConfig)
                    put("config_path", "/etc/didban/tunnels/${cfg.id}_foreign.conf")
                    put("service_name", "didban-tunnel-${cfg.id}")
                    put("exec_script", code.foreignInstallCommand)
                    put("multi_ports", cfg.multiPorts)
                }
                val resp = apiClient.tunnelApply(foreignServer, reqJson)
                val success = resp.optBoolean("success", resp.optBoolean("active", false))
                val status = resp.optString("status", if (success) "active" else "failed")
                val msg = resp.optString("message", if (success) "سرویس سرور خارج فعال شد" else "خطا در استقرار")
                val logs = resp.optString("logs", "")
                foreignRes = AutoDeployServerResult(foreignServer.name, foreignServer.host, "foreign", success, status, msg, logs)
                cfg.syncStatusForeign = if (success) "active" else "failed"
            } catch (e: Exception) {
                foreignRes = AutoDeployServerResult(foreignServer.name, foreignServer.host, "foreign", false, "unreachable", "عدم برقراری ارتباط با ایجنت سرور خارج: ${e.message}")
                cfg.syncStatusForeign = "failed"
            }
        } else if (cfg.core == TunnelCore.IPTABLES) {
            foreignRes = AutoDeployServerResult("سرور خارج", cfg.foreignHost, "foreign", true, "not_required", "سرور خارج برای IPTables نیاز به ایجنت ندارد")
            cfg.syncStatusForeign = "active"
        }

        // 2. Deploy to Iran Server
        if (iranServer != null) {
            try {
                val reqJson = JSONObject().apply {
                    put("id", cfg.id.toString())
                    put("name", "${cfg.name} (ایران)")
                    put("core", cfg.core.name)
                    put("role", "iran")
                    put("config_content", code.iranConfig)
                    put("config_path", "/etc/didban/tunnels/${cfg.id}_iran.conf")
                    put("service_name", "didban-tunnel-${cfg.id}")
                    put("exec_script", code.iranInstallCommand)
                    put("multi_ports", cfg.multiPorts)
                }
                val resp = apiClient.tunnelApply(iranServer, reqJson)
                val success = resp.optBoolean("success", resp.optBoolean("active", false))
                val status = resp.optString("status", if (success) "active" else "failed")
                val msg = resp.optString("message", if (success) "سرویس سرور ایران فعال شد" else "خطا در استقرار")
                val logs = resp.optString("logs", "")
                iranRes = AutoDeployServerResult(iranServer.name, iranServer.host, "iran", success, status, msg, logs)
                cfg.syncStatusIran = if (success) "active" else "failed"
            } catch (e: Exception) {
                iranRes = AutoDeployServerResult(iranServer.name, iranServer.host, "iran", false, "unreachable", "عدم برقراری ارتباط با ایجنت سرور ایران: ${e.message}")
                cfg.syncStatusIran = "failed"
            }
        }

        // Persist the materialized secret + final sync status so a later
        // reload (refreshTunnels) or redeploy reuses the same credential (H3).
        persistTunnel(ctx, cfg)

        val overall = (iranRes?.success != false) && (foreignRes?.success != false)
        val summary = when {
            iranRes != null && foreignRes != null && overall -> "✅ تانل با موفقیت روی هر دو سرور ایران و خارج راه‌اندازی و روشن شد!"
            iranRes != null && overall -> "✅ تانل روی سرور ایران با موفقیت فعال شد!"
            iranRes?.success == false -> "❌ خطا در سرور ایران: ${iranRes.message}"
            foreignRes?.success == false -> "❌ خطا در سرور خارج: ${foreignRes.message}"
            else -> "دستورات آماده شد (جهت همگام‌سازی خودکار سرورها را در لیست سرورهای دیدبان اضافه کنید)."
        }

        AutoDeployResult(iranRes, foreignRes, overall, summary)
    }

    suspend fun controlRemoteTunnel(
        ctx: Context,
        cfg: TunnelConfig,
        action: String, // "start", "stop", "restart", "delete"
        apiClient: ApiClient = ApiClient()
    ): Boolean = withContext(Dispatchers.IO) {
        val servers = Prefs.loadServers(ctx)
        // H16: same strict matcher as autoDeploy — start/stop/delete must
        // resolve to the machine the tunnel was actually deployed on.
        val iranServer = servers.firstOrNull { s ->
            (cfg.iranServerId != null && s.id == cfg.iranServerId) ||
            TunnelFieldValidation.hostMatchesServer(cfg.iranHost, s.host)
        }
        val foreignServer = servers.firstOrNull { s ->
            (cfg.foreignServerId != null && s.id == cfg.foreignServerId) ||
            TunnelFieldValidation.hostMatchesServer(cfg.foreignHost, s.host)
        }

        var ok = true
        val idStr = cfg.id.toString()
        if (iranServer != null) {
            try {
                when (action) {
                    "start" -> apiClient.tunnelStart(iranServer, idStr)
                    "stop" -> apiClient.tunnelStop(iranServer, idStr)
                    "restart" -> apiClient.tunnelRestart(iranServer, idStr)
                    "delete" -> apiClient.tunnelDelete(iranServer, idStr)
                }
            } catch (_: Exception) { ok = false }
        }
        if (foreignServer != null && cfg.core != TunnelCore.IPTABLES) {
            try {
                when (action) {
                    "start" -> apiClient.tunnelStart(foreignServer, idStr)
                    "stop" -> apiClient.tunnelStop(foreignServer, idStr)
                    "restart" -> apiClient.tunnelRestart(foreignServer, idStr)
                    "delete" -> apiClient.tunnelDelete(foreignServer, idStr)
                }
            } catch (_: Exception) { ok = false }
        }
        ok
    }

    // ── Test Tunnel Reachability ─────────────────────────────────────────────

    suspend fun testTunnel(cfg: TunnelConfig): Pair<Boolean, Long> = withContext(Dispatchers.IO) {
        val targetHost = cfg.iranHost.ifBlank { cfg.foreignHost }
        val targetPort = cfg.iranPort
        if (targetHost.isBlank()) return@withContext Pair(false, -1L)

        val t0 = System.currentTimeMillis()
        try {
            Socket().use { s ->
                s.connect(InetSocketAddress(targetHost.trim(), targetPort), 4000)
                val lat = System.currentTimeMillis() - t0
                Pair(true, lat)
            }
        } catch (_: Exception) {
            Pair(false, -1L)
        }
    }

    // ── Intelligent Tunnel Auto-Discovery (Server Process & Docker Scanner) ──

    data class DiscoveredTunnelItem(
        val serverName: String,
        val core: TunnelCore,
        val transport: TunnelTransport,
        val port: Int,
        val rawDetail: String
    )

    data class DiscoveryResult(
        val newCount: Int,
        val discoveredItems: List<DiscoveredTunnelItem>,
        val summary: String,
        val success: Boolean
    )

    suspend fun discoverTunnels(
        ctx: Context,
        apiClient: ApiClient = ApiClient()
    ): DiscoveryResult = withContext(Dispatchers.IO) {
        val servers = Prefs.loadServers(ctx)
        if (servers.isEmpty()) {
            return@withContext DiscoveryResult(
                newCount = 0,
                discoveredItems = emptyList(),
                summary = "هیچ سروری در دیدبان ثبت نشده است. ابتدا سرورهای خود را در تب «سرورها» اضافه کنید.",
                success = false
            )
        }

        val existingTunnels = Prefs.loadTunnels(ctx).toMutableList()
        val discoveredList = mutableListOf<DiscoveredTunnelItem>()
        var newTunnelsAdded = 0

        for (server in servers) {
            try {
                val procs = try { apiClient.processes(server) } catch (_: Exception) { emptyList() }
                val docker = try { apiClient.dockerContainers(server) } catch (_: Exception) { null }

                // 1. Scan Processes
                for (proc in procs) {
                    val pName = proc.name.lowercase()
                    val pCmd = proc.cmd.lowercase()

                    val detectedCore = when {
                        "gost" in pName || "gost" in pCmd -> TunnelCore.GOST
                        "backhaul" in pName || "backhaul" in pCmd -> TunnelCore.BACKHAUL
                        "rathole" in pName || "rathole" in pCmd -> TunnelCore.RATHOLE
                        "chisel" in pName || "chisel" in pCmd -> TunnelCore.CHISEL
                        "frpc" in pName || "frps" in pName || "frpc" in pCmd || "frps" in pCmd -> TunnelCore.FRP
                        "paqet" in pName || "paqet" in pCmd -> TunnelCore.PAQET
                        "narnia" in pName || "narnia" in pCmd -> TunnelCore.NARNIA
                        "backpack" in pName || "backpack" in pCmd -> TunnelCore.BACKPACK
                        else -> null
                    }

                    if (detectedCore != null) {
                        val detectedPort = run {
                            val portRegex = Regex("""(?::|-p\s+|-L\s+\w+://:?)(\d{2,5})""")
                            val match = portRegex.find(pCmd)
                            match?.groupValues?.get(1)?.toIntOrNull() ?: 443
                        }
                        val detectedTransport = when {
                            "grpc" in pCmd -> TunnelTransport.GRPC
                            "mwss" in pCmd || "wsmux" in pCmd -> TunnelTransport.WSMUX
                            "ws" in pCmd || "wss" in pCmd -> TunnelTransport.WS
                            "kcp" in pCmd -> TunnelTransport.KCP_FEC
                            else -> TunnelTransport.TCP
                        }

                        val alreadyExists = existingTunnels.any {
                            it.core == detectedCore && (it.iranHost == server.host || it.foreignHost == server.host || it.corePort == detectedPort || it.iranPort == detectedPort)
                        }

                        val isIran = server.name.lowercase().let { "ir" in it || "iran" in it || "teh" in it || "mci" in it || "mtn" in it }

                        if (!alreadyExists) {
                            val newTun = TunnelConfig(
                                id = System.currentTimeMillis() + existingTunnels.size + 1,
                                name = "${server.name} — ${detectedCore.displayName}",
                                core = detectedCore,
                                transport = detectedTransport,
                                iranHost = if (isIran) server.host else "",
                                foreignHost = if (!isIran) server.host else "",
                                iranPort = detectedPort,
                                foreignPort = detectedPort,
                                corePort = if (detectedPort != 443) detectedPort else 3080,
                                token = "auto-detected",
                                autoSync = false,
                                isEnabled = true,
                                lastStatus = 1,
                                lastChecked = System.currentTimeMillis(),
                                iranServerId = if (isIran) server.id else null,
                                foreignServerId = if (!isIran) server.id else null
                            )
                            existingTunnels.add(0, newTun)
                            newTunnelsAdded++
                        }

                        discoveredList.add(
                            DiscoveredTunnelItem(
                                serverName = server.name,
                                core = detectedCore,
                                transport = detectedTransport,
                                port = detectedPort,
                                rawDetail = "پردازه: ${proc.name} (PID: ${proc.pid})"
                            )
                        )
                    }
                }

                // 2. Scan Docker Containers
                docker?.containers?.forEach { container ->
                    val cImage = container.image.lowercase()
                    val cName = container.name.lowercase()

                    val detectedCore = when {
                        "gost" in cImage || "gost" in cName -> TunnelCore.GOST
                        "backhaul" in cImage || "backhaul" in cName -> TunnelCore.BACKHAUL
                        "rathole" in cImage || "rathole" in cName -> TunnelCore.RATHOLE
                        "chisel" in cImage || "chisel" in cName -> TunnelCore.CHISEL
                        "frp" in cImage || "frp" in cName -> TunnelCore.FRP
                        "paqet" in cImage || "paqet" in cName -> TunnelCore.PAQET
                        else -> null
                    }

                    if (detectedCore != null) {
                        val alreadyExists = existingTunnels.any {
                            it.core == detectedCore && (it.iranHost == server.host || it.foreignHost == server.host)
                        }
                        val isIran = server.name.lowercase().let { "ir" in it || "iran" in it || "teh" in it }
                        val port = container.ports.firstOrNull()?.publicPort ?: 443

                        if (!alreadyExists) {
                            val newTun = TunnelConfig(
                                id = System.currentTimeMillis() + existingTunnels.size + 1,
                                name = "${server.name} — ${detectedCore.displayName} (Docker)",
                                core = detectedCore,
                                transport = TunnelTransport.TCP,
                                iranHost = if (isIran) server.host else "",
                                foreignHost = if (!isIran) server.host else "",
                                iranPort = port,
                                foreignPort = port,
                                corePort = port,
                                token = "auto-detected",
                                autoSync = false,
                                isEnabled = true,
                                lastStatus = 1,
                                lastChecked = System.currentTimeMillis(),
                                iranServerId = if (isIran) server.id else null,
                                foreignServerId = if (!isIran) server.id else null
                            )
                            existingTunnels.add(0, newTun)
                            newTunnelsAdded++
                        }

                        discoveredList.add(
                            DiscoveredTunnelItem(
                                serverName = server.name,
                                core = detectedCore,
                                transport = TunnelTransport.TCP,
                                port = port,
                                rawDetail = "داکر: ${container.name} (${container.status})"
                            )
                        )
                    }
                }
            } catch (_: Exception) {}
        }

        if (newTunnelsAdded > 0) {
            Prefs.saveTunnels(ctx, existingTunnels)
        }

        val summaryMsg = when {
            newTunnelsAdded > 0 -> "تعداد $newTunnelsAdded تانل فعال جدید روی سرورهای شما کشف و به لیست تانل‌ها اضافه شد!"
            discoveredList.isNotEmpty() -> "تعداد ${discoveredList.size} تانل فعال روی سرورها در حال اجراست و قبلاً در لیست ثبت شده‌اند."
            else -> "هیچ تانل فعالی روی سرورهای متصل کشف نشد."
        }

        DiscoveryResult(
            newCount = newTunnelsAdded,
            discoveredItems = discoveredList,
            summary = summaryMsg,
            success = discoveredList.isNotEmpty()
        )
    }
}
