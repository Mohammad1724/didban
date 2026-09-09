package org.didban.monitor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom

object TunnelEngine {

    fun generateRandomToken(length: Int = 16): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val rnd = SecureRandom()
        val sb = StringBuilder()
        for (i in 0 until length) {
            sb.append(chars[rnd.nextInt(chars.length)])
        }
        return sb.toString()
    }

    fun generateCode(cfg: TunnelConfig): GeneratedTunnelCode {
        return when (cfg.core) {
            TunnelCore.BACKPACK -> generateBackpack(cfg)
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
        val token = cfg.token.ifBlank { generateRandomToken(24) }
        val iranIp = cfg.iranHost.ifBlank { "IRAN_IP" }
        val preset = cfg.preset.ifBlank { "turbo" }
        val acceptUdpStr = if (cfg.acceptUdp) "accept_udp = true\n" else ""
        val proxyProtoStr = if (cfg.proxyProtocol) "proxy_protocol = true\n" else ""

        // Server Config (Iran Node)
        val iranConfig = """
[server]
bind_addr = "0.0.0.0:${cfg.corePort}"
transport = "$transportStr"
token = "$token"
ports = ["${cfg.iranPort}=127.0.0.1:${cfg.foreignPort}"]
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
# ── نصب خودکار BackPack روی سرور ایران ──
sudo mkdir -p /etc/backpack /root/BackPack /usr/local/bin && \
ARCH=$(uname -m | sed 's/x86_64/amd64/' | sed 's/aarch64/arm64/' | sed 's/armv7l/armv7/') && \
curl -fsSL https://github.com/AminMGMT/BackPack/releases/latest/download/backpack_linux_${'$'}ARCH.tar.gz -o /tmp/backpack.tar.gz && \
tar -xzf /tmp/backpack.tar.gz -C /usr/local/bin/ backpack && chmod +x /usr/local/bin/backpack && \
cat << 'EOF' > /etc/backpack/server.toml
$iranConfig
EOF
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
# ── نصب خودکار BackPack روی سرور خارج ──
sudo mkdir -p /etc/backpack /root/BackPack /usr/local/bin && \
ARCH=$(uname -m | sed 's/x86_64/amd64/' | sed 's/aarch64/arm64/' | sed 's/armv7l/armv7/') && \
curl -fsSL https://github.com/AminMGMT/BackPack/releases/latest/download/backpack_linux_${'$'}ARCH.tar.gz -o /tmp/backpack.tar.gz && \
tar -xzf /tmp/backpack.tar.gz -C /usr/local/bin/ backpack && chmod +x /usr/local/bin/backpack && \
cat << 'EOF' > /etc/backpack/client.toml
$foreignConfig
EOF
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

        return GeneratedTunnelCode(
            iranConfig = iranConfig,
            iranInstallCommand = iranInstall,
            foreignConfig = foreignConfig,
            foreignInstallCommand = foreignInstall,
            dockerComposeIran = dockerIran,
            dockerComposeForeign = dockerForeign,
            description = "تانل قدرتمند BackPack (توسعه‌یافته توسط AminMGMT): اتصال پورت ${cfg.iranPort} ایران به ${cfg.foreignPort} خارج با رمزنگاری ${cfg.transport.displayName} و پریست ${cfg.preset}."
        )
    }

    // ── 1. Backhaul Generator ────────────────────────────────────────────────

    private fun generateBackhaul(cfg: TunnelConfig): GeneratedTunnelCode {
        val transportStr = when (cfg.transport) {
            TunnelTransport.WS -> "ws"
            TunnelTransport.WSMUX -> "wsmux"
            TunnelTransport.TCPMUX -> "tcpmux"
            else -> "tcp"
        }
        val token = cfg.token.ifBlank { "didban_backhaul_secret" }
        val foreignIp = cfg.foreignHost.ifBlank { "KHAREJ_IP" }

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

[[server.ports]]
listen_port = ${cfg.iranPort}
target_addr = "127.0.0.1:${cfg.foreignPort}"
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
curl -fsSL https://github.com/MusLatest/backhaul/releases/latest/download/backhaul_linux_${'$'}ARCH.tar.gz -o /tmp/backhaul.tar.gz && \
tar -xzf /tmp/backhaul.tar.gz -C /usr/local/bin/ && chmod +x /usr/local/bin/backhaul && \
cat << 'EOF' > /etc/backhaul/config.toml
$foreignConfig
EOF
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
curl -fsSL https://github.com/MusLatest/backhaul/releases/latest/download/backhaul_linux_${'$'}ARCH.tar.gz -o /tmp/backhaul.tar.gz && \
tar -xzf /tmp/backhaul.tar.gz -C /usr/local/bin/ && chmod +x /usr/local/bin/backhaul && \
cat << 'EOF' > /etc/backhaul/config.toml
$iranConfig
EOF
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

        return GeneratedTunnelCode(
            iranConfig = iranConfig,
            iranInstallCommand = iranInstall,
            foreignConfig = foreignConfig,
            foreignInstallCommand = foreignInstall,
            dockerComposeIran = dockerIran,
            dockerComposeForeign = dockerForeign,
            description = "تانل معکوس Backhaul: پورت ${cfg.iranPort} ایران را به پورت ${cfg.foreignPort} خارج با پروتکل ${cfg.transport.displayName} متصل می‌کند."
        )
    }

    // ── 2. Rathole Generator ─────────────────────────────────────────────────

    private fun generateRathole(cfg: TunnelConfig): GeneratedTunnelCode {
        val token = cfg.token.ifBlank { "didban_rathole_token" }
        val foreignIp = cfg.foreignHost.ifBlank { "KHAREJ_IP" }

        val foreignConfig = """
[server]
bind_addr = "0.0.0.0:${cfg.corePort}"
default_token = "$token"

[server.services.app]
token = "$token"
bind_addr = "0.0.0.0:${cfg.iranPort}"
""".trimIndent()

        val iranConfig = """
[client]
remote_addr = "$foreignIp:${cfg.corePort}"
default_token = "$token"

[client.services.app]
token = "$token"
local_addr = "127.0.0.1:${cfg.foreignPort}"
""".trimIndent()

        val foreignInstall = """
sudo mkdir -p /etc/rathole /usr/local/bin && \
ARCH=$(uname -m) && \
ZIP_ARCH="x86_64-unknown-linux-musl" && \
if [ "${'$'}ARCH" = "aarch64" ] || [ "${'$'}ARCH" = "arm64" ]; then ZIP_ARCH="aarch64-unknown-linux-musl"; fi && \
curl -fsSL https://github.com/rapiz1/rathole/releases/latest/download/rathole-${'$'}ZIP_ARCH.zip -o /tmp/rathole.zip && \
apt-get install -y unzip >/dev/null 2>&1 || yum install -y unzip >/dev/null 2>&1 && \
unzip -o /tmp/rathole.zip -d /usr/local/bin/ && chmod +x /usr/local/bin/rathole && \
cat << 'EOF' > /etc/rathole/server.toml
$foreignConfig
EOF
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
cat << 'EOF' > /etc/rathole/client.toml
$iranConfig
EOF
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

        return GeneratedTunnelCode(
            iranConfig = iranConfig,
            iranInstallCommand = iranInstall,
            foreignConfig = foreignConfig,
            foreignInstallCommand = foreignInstall,
            dockerComposeIran = dockerIran,
            dockerComposeForeign = dockerForeign,
            description = "تانل سبک و امن Rathole نوشته شده با Rust: پورت ${cfg.iranPort} ایران را با کمترین مصرف رم به پورت ${cfg.foreignPort} خارج متصل می‌کند."
        )
    }

    // ── 3. GOST Generator ────────────────────────────────────────────────────

    private fun generateGost(cfg: TunnelConfig): GeneratedTunnelCode {
        val foreignIp = cfg.foreignHost.ifBlank { "KHAREJ_IP" }
        val proto = when (cfg.transport) {
            TunnelTransport.WS -> "relay+ws"
            TunnelTransport.GRPC -> "relay+grpc"
            TunnelTransport.TCPMUX -> "relay+tcpmux"
            TunnelTransport.UDP -> "udp"
            else -> "tcp"
        }

        val foreignCmd = if (proto == "tcp" || proto == "udp") {
            "# GOST on Kharej runs your target application on port ${cfg.foreignPort}"
        } else {
            "/usr/local/bin/gost -L \"$proto://:${cfg.corePort}\""
        }

        val iranCmd = if (proto == "tcp") {
            "/usr/local/bin/gost -L \"tcp://:${cfg.iranPort}/$foreignIp:${cfg.foreignPort}\" -L \"udp://:${cfg.iranPort}/$foreignIp:${cfg.foreignPort}\""
        } else {
            "/usr/local/bin/gost -L \"tcp://:${cfg.iranPort}/127.0.0.1:${cfg.foreignPort}\" -F \"$proto://$foreignIp:${cfg.corePort}\""
        }

        val foreignInstall = """
sudo mkdir -p /usr/local/bin && \
ARCH=$(uname -m | sed 's/x86_64/amd64/' | sed 's/aarch64/arm64/') && \
curl -fsSL https://github.com/go-gost/gost/releases/latest/download/gost_3.0.0_linux_${'$'}ARCH.tar.gz -o /tmp/gost.tar.gz && \
tar -xzf /tmp/gost.tar.gz -C /usr/local/bin/ && chmod +x /usr/local/bin/gost && \
cat << 'EOF' > /etc/systemd/system/gost.service
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
EOF
systemctl daemon-reload && systemctl enable --now gost && systemctl status gost --no-pager
""".trimIndent()

        val iranInstall = """
sudo mkdir -p /usr/local/bin && \
ARCH=$(uname -m | sed 's/x86_64/amd64/' | sed 's/aarch64/arm64/') && \
curl -fsSL https://github.com/go-gost/gost/releases/latest/download/gost_3.0.0_linux_${'$'}ARCH.tar.gz -o /tmp/gost.tar.gz && \
tar -xzf /tmp/gost.tar.gz -C /usr/local/bin/ && chmod +x /usr/local/bin/gost && \
cat << 'EOF' > /etc/systemd/system/gost.service
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
EOF
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
    command: -L "tcp://:${cfg.iranPort}/127.0.0.1:${cfg.foreignPort}" -F "$proto://$foreignIp:${cfg.corePort}"
""".trimIndent()

        return GeneratedTunnelCode(
            iranConfig = iranCmd,
            iranInstallCommand = iranInstall,
            foreignConfig = foreignCmd,
            foreignInstallCommand = foreignInstall,
            dockerComposeIran = dockerIran,
            dockerComposeForeign = dockerForeign,
            description = "تانل همه‌کاره GOST: رله و فوروارد ترافیک پورت ${cfg.iranPort} ایران به ${cfg.foreignPort} خارج با بستر ${cfg.transport.displayName}."
        )
    }

    // ── 4. Chisel Generator ──────────────────────────────────────────────────

    private fun generateChisel(cfg: TunnelConfig): GeneratedTunnelCode {
        val foreignIp = cfg.foreignHost.ifBlank { "KHAREJ_IP" }
        val auth = "admin:${cfg.token.ifBlank { "didban_chisel_secret" }}"

        val foreignCmd = "/usr/local/bin/chisel server --port ${cfg.corePort} --auth \"$auth\" --reverse"
        val iranCmd = "/usr/local/bin/chisel client --auth \"$auth\" http://$foreignIp:${cfg.corePort} R:${cfg.iranPort}:127.0.0.1:${cfg.foreignPort}"

        val foreignInstall = """
sudo mkdir -p /usr/local/bin && \
ARCH=$(uname -m | sed 's/x86_64/amd64/' | sed 's/aarch64/arm64/') && \
curl -fsSL https://github.com/jpillora/chisel/releases/latest/download/chisel_linux_${'$'}ARCH.gz -o /tmp/chisel.gz && \
gzip -d -f /tmp/chisel.gz && mv /tmp/chisel /usr/local/bin/chisel && chmod +x /usr/local/bin/chisel && \
cat << 'EOF' > /etc/systemd/system/chisel.service
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
EOF
systemctl daemon-reload && systemctl enable --now chisel && systemctl status chisel --no-pager
""".trimIndent()

        val iranInstall = """
sudo mkdir -p /usr/local/bin && \
ARCH=$(uname -m | sed 's/x86_64/amd64/' | sed 's/aarch64/arm64/') && \
curl -fsSL https://github.com/jpillora/chisel/releases/latest/download/chisel_linux_${'$'}ARCH.gz -o /tmp/chisel.gz && \
gzip -d -f /tmp/chisel.gz && mv /tmp/chisel /usr/local/bin/chisel && chmod +x /usr/local/bin/chisel && \
cat << 'EOF' > /etc/systemd/system/chisel.service
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
EOF
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
    command: client --auth "$auth" http://$foreignIp:${cfg.corePort} R:${cfg.iranPort}:127.0.0.1:${cfg.foreignPort}
""".trimIndent()

        return GeneratedTunnelCode(
            iranConfig = iranCmd,
            iranInstallCommand = iranInstall,
            foreignConfig = foreignCmd,
            foreignInstallCommand = foreignInstall,
            dockerComposeIran = dockerIran,
            dockerComposeForeign = dockerForeign,
            description = "تانل امن Chisel از بستر WebSocket و HTTP: عبور آسان از فیلترینگ با پوشش ترافیک معمولی وب."
        )
    }

    // ── 5. FRP Generator ─────────────────────────────────────────────────────

    private fun generateFrp(cfg: TunnelConfig): GeneratedTunnelCode {
        val foreignIp = cfg.foreignHost.ifBlank { "KHAREJ_IP" }
        val token = cfg.token.ifBlank { "didban_frp_secret" }

        val foreignConfig = """
bindPort = ${cfg.corePort}
auth.token = "$token"
""".trimIndent()

        val iranConfig = """
serverAddr = "$foreignIp"
serverPort = ${cfg.corePort}
auth.token = "$token"

[[proxies]]
name = "tcp_tunnel"
type = "tcp"
localIP = "127.0.0.1"
localPort = ${cfg.foreignPort}
remotePort = ${cfg.iranPort}
""".trimIndent()

        val foreignInstall = """
sudo mkdir -p /etc/frp /usr/local/bin && \
ARCH=$(uname -m | sed 's/x86_64/amd64/' | sed 's/aarch64/arm64/') && \
curl -fsSL https://github.com/fatedier/frp/releases/latest/download/frp_0.58.1_linux_${'$'}ARCH.tar.gz -o /tmp/frp.tar.gz && \
tar -xzf /tmp/frp.tar.gz -C /tmp/ && cp /tmp/frp_*/frps /usr/local/bin/ && chmod +x /usr/local/bin/frps && \
cat << 'EOF' > /etc/frp/frps.toml
$foreignConfig
EOF
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
curl -fsSL https://github.com/fatedier/frp/releases/latest/download/frp_0.58.1_linux_${'$'}ARCH.tar.gz -o /tmp/frp.tar.gz && \
tar -xzf /tmp/frp.tar.gz -C /tmp/ && cp /tmp/frp_*/frpc /usr/local/bin/ && chmod +x /usr/local/bin/frpc && \
cat << 'EOF' > /etc/frp/frpc.toml
$iranConfig
EOF
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

        return GeneratedTunnelCode(
            iranConfig = iranConfig,
            iranInstallCommand = iranInstall,
            foreignConfig = foreignConfig,
            foreignInstallCommand = foreignInstall,
            dockerComposeIran = dockerIran,
            dockerComposeForeign = dockerForeign,
            description = "تانل ریورس FRP: رله ترافیک با هسته باسابقه و پایدار FRP."
        )
    }

    // ── 6. IPTables Port Forwarding Generator ────────────────────────────────

    private fun generateIptables(cfg: TunnelConfig): GeneratedTunnelCode {
        val foreignIp = cfg.foreignHost.ifBlank { "KHAREJ_IP" }

        val iranInstall = """
sudo sysctl -w net.ipv4.ip_forward=1
echo "net.ipv4.ip_forward=1" | sudo tee -a /etc/sysctl.conf
sudo iptables -t nat -A PREROUTING -p tcp --dport ${cfg.iranPort} -j DNAT --to-destination $foreignIp:${cfg.foreignPort}
sudo iptables -t nat -A PREROUTING -p udp --dport ${cfg.iranPort} -j DNAT --to-destination $foreignIp:${cfg.foreignPort}
sudo iptables -t nat -A POSTROUTING -p tcp -d $foreignIp --dport ${cfg.foreignPort} -j MASQUERADE
sudo iptables -t nat -A POSTROUTING -p udp -d $foreignIp --dport ${cfg.foreignPort} -j MASQUERADE
sudo apt-get install -y iptables-persistent >/dev/null 2>&1 && sudo netfilter-persistent save
""".trimIndent()

        val foreignInfo = "# سرور خارج نیازی به تنظیمات خاصی ندارد؛ فقط سرویس اصلی شما روی پورت ${cfg.foreignPort} در حال اجرا باشد."

        return GeneratedTunnelCode(
            iranConfig = "# IPTables Kernel Forwarding Rules",
            iranInstallCommand = iranInstall,
            foreignConfig = foreignInfo,
            foreignInstallCommand = "# No setup required on Foreign server",
            dockerComposeIran = "# IPTables runs directly in Linux kernel",
            dockerComposeForeign = "# No setup required",
            description = "فوروارد مستقیم در سطح هسته لینوکس با IPTables: حداکثر سرعت سخت‌افزاری شبکه بدون هیچ رم یا پردازش اضافه."
        )
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
}
