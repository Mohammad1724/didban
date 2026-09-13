package org.didban.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * H18: Narnia generator regression tests.
 *
 * Pins the post-fix contract: deploy via the official Docker mechanism
 * (stormotron/narnia image + TUN device + env-var config), ip_forward
 * persisted via /etc/sysctl.d (not runtime-only), fail-fast install with
 * no `|| true` swallowing, idempotent NAT rules, and shell scripts that
 * pass `bash -n` (including the embedded fw script bodies).
 */
class TunnelNarniaGeneratorTest {

    private fun narniaCfg(multiPorts: String = "80:8080"): TunnelConfig = TunnelConfig(
        id = 7,
        name = "narnia-test",
        core = TunnelCore.NARNIA,
        iranHost = "1.2.3.4",
        iranPort = 443,
        foreignHost = "5.6.7.8",
        foreignPort = 8443,
        multiPorts = multiPorts,
        token = "tok-fixed-123"
    )

    @Test
    fun `deploy uses the official docker mechanism, not the dead binary path`() {
        val g = TunnelEngine.generateCode(narniaCfg())
        for (script in listOf(g.foreignInstallCommand, g.iranInstallCommand)) {
            assertTrue("real image", script.contains("stormotron/narnia:0.0.3"))
            assertFalse("dead binary URL removed", script.contains("releases/latest/download"))
            assertFalse("unexecuted Narnia.sh fallback removed", script.contains("Narnia.sh"))
            assertTrue("TUN device", script.contains("/dev/net/tun"))
            assertTrue("fail-fast docker check", script.contains("command -v docker"))
            assertTrue("unit named for the agent", script.contains("didban-tunnel-7"))
            assertTrue("systemd unit file", script.contains("/etc/systemd/system/"))
        }
    }

    @Test
    fun `ip_forward is persisted via sysctl drop-in, not runtime-only`() {
        val g = TunnelEngine.generateCode(narniaCfg())
        for (script in listOf(g.foreignInstallCommand, g.iranInstallCommand)) {
            assertTrue("sysctl.d drop-in", script.contains("/etc/sysctl.d/99-didban-narnia.conf"))
            assertTrue("apply now", script.contains("sysctl -w net.ipv4.ip_forward=1"))
            assertFalse("runtime-only pattern removed", script.contains("echo 1 > /proc/sys/net/ipv4/ip_forward"))
        }
    }

    @Test
    fun `no failure swallowing on critical steps`() {
        val g = TunnelEngine.generateCode(narniaCfg())
        for (script in listOf(g.foreignInstallCommand, g.iranInstallCommand)) {
            script.lineSequence().forEach { line ->
                if (line.contains("|| true")) {
                    // The only allowed `|| true` is the cosmetic host MTU tweak.
                    assertTrue("unexpected '|| true': $line", line.contains("ip link set"))
                }
            }
        }
    }

    @Test
    fun `nat rules are idempotent and port-mapped`() {
        val g = TunnelEngine.generateCode(narniaCfg())
        val s = g.iranInstallCommand
        assertTrue("DNAT 80 -> 10.200.200.1:8080", s.contains("--dport 80 -j DNAT --to-destination 10.200.200.1:8080"))
        assertTrue("tcp+udp", s.contains("-p udp --dport 80 -j DNAT"))
        assertTrue("delete-before-add (idempotent)", s.contains("iptables -t nat -D PREROUTING -p tcp --dport 80"))
        assertTrue("client fw referenced", s.contains("narnia-7-iran-fw.sh"))
    }

    @Test
    fun `single-port fallback uses the main port pair`() {
        val g = TunnelEngine.generateCode(narniaCfg(multiPorts = ""))
        assertTrue(g.iranInstallCommand.contains("--dport 443 -j DNAT --to-destination 10.200.200.1:8443"))
    }

    @Test
    fun `docker compose uses the real image and the TUN device`() {
        val g = TunnelEngine.generateCode(narniaCfg())
        assertFalse("nonexistent image removed", g.dockerComposeForeign.contains("dnt3e/narnia"))
        assertFalse("nonexistent image removed (iran)", g.dockerComposeIran.contains("dnt3e/narnia"))
        assertTrue(g.dockerComposeForeign.contains("image: stormotron/narnia:0.0.3"))
        assertTrue(g.dockerComposeIran.contains("image: stormotron/narnia:0.0.3"))
        assertTrue(g.dockerComposeForeign.contains("/dev/net/tun:/dev/net/tun"))
        assertTrue(g.dockerComposeIran.contains("REMOTE_IP: \"5.6.7.8\""))
        assertTrue(g.dockerComposeForeign.contains("OPERATING_MODE: \"ip:30:10.200.200.1:10.200.200.2:dynamic:50\""))
        assertTrue(g.dockerComposeForeign.contains("MTU: \"1350\""))
    }

    @Test
    fun `unit env carries the role-specific parameters`() {
        val g = TunnelEngine.generateCode(narniaCfg())
        val foreignUnit = decodeUnit(g.foreignInstallCommand)
        val iranUnit = decodeUnit(g.iranInstallCommand)
        assertTrue("server listen env", foreignUnit.contains("-e SERVER=0.0.0.0"))
        assertTrue("client remote env", iranUnit.contains("-e REMOTE_IP=5.6.7.8"))
        assertTrue("key in env (server)", foreignUnit.contains("-e PASSWORD=tok-fixed-123"))
        assertTrue("key in env (client)", iranUnit.contains("-e PASSWORD=tok-fixed-123"))
        assertTrue("operating mode", foreignUnit.contains("-e OPERATING_MODE=ip:30:10.200.200.1:10.200.200.2:dynamic:50"))
        assertTrue("TUN device mapping (server)", foreignUnit.contains("--device /dev/net/tun:/dev/net/tun"))
        assertTrue("TUN device mapping (client)", iranUnit.contains("--device /dev/net/tun:/dev/net/tun"))
        assertTrue("host networking", foreignUnit.contains("--net=host"))
        assertTrue("container cleanup before start", foreignUnit.contains("docker rm -f didban-tunnel-7"))
        assertTrue("container stop on service stop", iranUnit.contains("docker stop didban-tunnel-7"))
        assertTrue("fw hook (server, sync)", foreignUnit.contains("ExecStartPost=/etc/didban/narnia-7-foreign-fw.sh"))
        assertTrue(
            "fw hook (client, async log)",
            iranUnit.contains("ExecStartPost=/bin/sh -c '/etc/didban/narnia-7-iran-fw.sh >> /var/log/didban-narnia-7.log 2>&1 &'")
        )
        assertTrue("restart always (server)", foreignUnit.contains("Restart=always"))
    }

    @Test
    fun `generated shell passes bash -n for install scripts and embedded fw bodies`() {
        val g = TunnelEngine.generateCode(narniaCfg())
        val dir = Files.createTempDirectory("didban-narnia-test").toFile()
        try {
            for ((name, script) in mapOf(
                "foreign" to g.foreignInstallCommand,
                "iran" to g.iranInstallCommand
            )) {
                val f = File(dir, "$name.sh")
                f.writeText(script)
                bashCheck(f)
                // The fw script hides inside a <<'FW' heredoc: extract it and
                // syntax-check it as standalone shell too.
                val lines = script.lines()
                val start = lines.indexOfFirst { it.contains("<<'FW'") }
                assertTrue("heredoc found in $name", start >= 0)
                val end = lines.drop(start + 1).indexOfFirst { it.trim() == "FW" } + start + 1
                assertTrue("heredoc terminator in $name", end > start)
                val fw = File(dir, "$name-fw.sh")
                fw.writeText(lines.subList(start + 1, end).joinToString("\n") + "\n")
                bashCheck(fw)
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `install script runs end-to-end with stubbed system tools`() {
        val g = TunnelEngine.generateCode(narniaCfg())
        val fake = Files.createTempDirectory("didban-narnia-e2e").toFile()
        try {
            val bin = File(fake, "bin").apply { mkdirs() }
            // On a real system /etc/systemd/system always exists; mirror that.
            File(fake, "etc/systemd/system").mkdirs()
            val log = File(fake, "calls.log")
            fun stub(name: String, body: String) {
                val f = File(bin, name); f.writeText(body); f.setExecutable(true)
            }
            stub("sudo", "#!/bin/sh\nexec \"\$@\"\n")
            stub("docker", """#!/bin/sh
echo "docker ${'$'}*" >> "${'$'}DIDBAN_TEST_LOG"
if [ "${'$'}1" = "image" ]; then exit 1; fi  # image not present -> install must pull it
exit 0
""")
            stub("ip", """#!/bin/sh
echo "ip ${'$'}*" >> "${'$'}DIDBAN_TEST_LOG"
if [ "${'$'}1" = "route" ]; then echo "default via 10.0.0.1 dev eth0"; fi
exit 0
""")
            stub("iptables", "#!/bin/sh\necho \"iptables ${'$'}*\" >> \"${'$'}DIDBAN_TEST_LOG\"\nexit 0\n")
            stub("systemctl", "#!/bin/sh\necho \"systemctl ${'$'}*\" >> \"${'$'}DIDBAN_TEST_LOG\"\nexit 0\n")
            stub("sysctl", "#!/bin/sh\necho \"sysctl ${'$'}*\" >> \"${'$'}DIDBAN_TEST_LOG\"\nexit 0\n")
            stub("mknod", "#!/bin/sh\necho \"mknod ${'$'}*\" >> \"${'$'}DIDBAN_TEST_LOG\"\nexit 0\n")

            fun runScript(scriptText: String, name: String) {
                // Sandbox the hardcoded system paths into the temp dir.
                val sandboxed = scriptText
                    .replace("/etc/didban", "${fake}/etc/didban")
                    .replace("/etc/sysctl.d", "${fake}/etc/sysctl.d")
                    .replace("/etc/systemd/system", "${fake}/etc/systemd/system")
                    .replace("/dev/net/tun", "${fake}/tun")
                    .replace("/var/log/didban-narnia", "${fake}/varlog/didban-narnia")
                val f = File(fake, name); f.writeText(sandboxed)
                val pb = ProcessBuilder("bash", f.absolutePath).apply {
                    environment()["DIDBAN_TEST_LOG"] = log.absolutePath
                    environment()["PATH"] = "${bin.absolutePath}:${System.getenv("PATH")}"
                }.redirectErrorStream(true)
                val p = pb.start()
                val out = p.inputStream.bufferedReader().readText()
                assertEquals("$name must exit 0. Output: $out", 0, p.waitFor())
            }

            runScript(g.foreignInstallCommand, "install_foreign.sh")
            val unitAfterForeign = File(fake, "etc/systemd/system/didban-tunnel-7.service").readText()
            assertTrue("foreign unit runs the container", unitAfterForeign.contains("docker run --rm --name didban-tunnel-7"))
            assertTrue("foreign unit server env", unitAfterForeign.contains("-e SERVER=0.0.0.0"))
            assertTrue("foreign unit operating mode", unitAfterForeign.contains("OPERATING_MODE=ip:30:10.200.200.1:10.200.200.2:dynamic:50"))

            runScript(g.iranInstallCommand, "install_iran.sh")
            val unitAfterIran = File(fake, "etc/systemd/system/didban-tunnel-7.service").readText()
            assertTrue("iran unit client env", unitAfterIran.contains("-e REMOTE_IP=5.6.7.8"))
            assertFalse("iran unit is not the server", unitAfterIran.contains("-e SERVER=0.0.0.0"))

            // Artifacts on disk
            val fwForeign = File(fake, "etc/didban/narnia-7-foreign-fw.sh")
            val fwIran = File(fake, "etc/didban/narnia-7-iran-fw.sh")
            assertTrue("foreign fw written", fwForeign.canRead())
            assertTrue("iran fw written", fwIran.canRead())
            assertTrue("iran fw has the DNAT mapping", fwIran.readText().contains("--dport 80 -j DNAT --to-destination 10.200.200.1:8080"))
            assertEquals("sysctl drop-in content", "net.ipv4.ip_forward=1\n", File(fake, "etc/sysctl.d/99-didban-narnia.conf").readText())
            bashCheck(fwForeign)
            bashCheck(fwIran)

            // Call sequence
            val calls = log.readText()
            assertTrue("docker pull", calls.contains("docker pull stormotron/narnia:0.0.3"))
            assertTrue("sysctl -w", calls.contains("sysctl -w net.ipv4.ip_forward=1"))
            assertTrue("daemon-reload", calls.contains("systemctl daemon-reload"))
            assertTrue("enable", calls.contains("systemctl enable didban-tunnel-7"))
            assertTrue("restart", calls.contains("systemctl restart didban-tunnel-7"))
        } finally {
            fake.deleteRecursively()
        }
    }

    @Test
    fun `install fails fast and loudly when docker is missing`() {
        val g = TunnelEngine.generateCode(narniaCfg())
        val fake = Files.createTempDirectory("didban-narnia-nodocker").toFile()
        try {
            val bin = File(fake, "bin").apply { mkdirs() }
            val sudo = File(bin, "sudo")
            sudo.writeText("#!/bin/sh\nexec \"\$@\"\n")
            sudo.setExecutable(true)
            val f = File(fake, "install.sh")
            f.writeText(g.foreignInstallCommand.replace("/etc/didban", "${fake}/etc/didban"))
            val pb = ProcessBuilder("bash", f.absolutePath).apply {
                environment()["PATH"] = "${bin.absolutePath}:${System.getenv("PATH")}"
            }.redirectErrorStream(true)
            val p = pb.start()
            val out = p.inputStream.bufferedReader().readText()
            val rc = p.waitFor()
            assertTrue("non-zero exit expected, got $rc: $out", rc != 0)
            assertTrue("clear reason: $out", out.contains("docker is required"))
        } finally {
            fake.deleteRecursively()
        }
    }

    /** The unit is written via `printf '%s' '<b64>' | base64 -d`: decode it. */
    private fun decodeUnit(installScript: String): String {
        val m = Regex("""printf '%s' '([A-Za-z0-9+/=]+)' \| base64 -d""").find(installScript)
        assertNotNull("b64 unit payload found", m)
        return String(java.util.Base64.getDecoder().decode(m!!.groupValues[1]), Charsets.UTF_8)
    }

    private fun bashCheck(f: File) {
        val p = ProcessBuilder("bash", "-n", f.absolutePath).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        assertEquals("bash -n ${f.name} failed: $out", 0, p.waitFor())
    }
}
