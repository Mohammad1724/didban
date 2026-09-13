package org.didban.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

/**
 * H19: IPTables generator regression tests.
 *
 * Pins the post-fix contract: foreignHost is validated (required, strict
 * IPv4 — no "KHAREJ_IP" placeholder in rules), ip_forward persisted via an
 * idempotent sysctl.d drop-in, and NAT rules managed through dedicated
 * per-tunnel chains that are flushed before repopulation (redeploy never
 * duplicates rules; dropped ports self-heal).
 */
class TunnelIptablesGeneratorTest {

    private fun iptCfg(multiPorts: String = "", foreignHost: String = "5.6.7.8"): TunnelConfig =
        TunnelConfig(
            id = 9,
            name = "ipt-test",
            core = TunnelCore.IPTABLES,
            iranHost = "1.2.3.4",
            iranPort = 443,
            foreignHost = foreignHost,
            foreignPort = 8443,
            multiPorts = multiPorts
        )

    private fun expectedHash(id: Long): String {
        val d = MessageDigest.getInstance("SHA-256").digest(id.toString().toByteArray())
        return d.take(8).joinToString("") { "%02x".format(it) }
    }

    @Test
    fun `blank foreignHost is rejected with a clear error`() {
        val e = try {
            TunnelEngine.generateCode(iptCfg(foreignHost = ""))
            null
        } catch (ex: IllegalArgumentException) {
            ex
        }
        assertNotNull("blank foreignHost must be rejected", e)
        assertTrue("no placeholder in error", !e!!.message!!.contains("KHAREJ_IP"))
    }

    @Test
    fun `hostname foreignHost is rejected (iptables has no DNS)`() {
        val e = try {
            TunnelEngine.generateCode(iptCfg(foreignHost = "kharej.example.com"))
            null
        } catch (ex: IllegalArgumentException) {
            ex
        }
        assertNotNull("hostname must be rejected", e)
    }

    @Test
    fun `generated rules are chain-based, idempotent and reboot-safe`() {
        val g = TunnelEngine.generateCode(iptCfg())
        val s = g.iranInstallCommand
        val h = expectedHash(9)
        assertFalse("no placeholder ever", s.contains("KHAREJ_IP"))
        assertFalse("no tee -a sysctl.conf", s.contains("tee -a /etc/sysctl.conf"))
        assertTrue("sysctl.d drop-in", s.contains("/etc/sysctl.d/99-didban-iptables.conf"))
        assertTrue("apply now", s.contains("sysctl -w net.ipv4.ip_forward=1"))
        assertTrue("no flaky apt-get iptables-persistent", !s.contains("iptables-persistent"))
        assertTrue("dedicated PREROUTING chain", s.contains("didban-tun-$h"))
        assertTrue("dedicated POSTROUTING chain", s.contains("didban-tunp-$h"))
        assertTrue("chain names within the 28-char limit", "didban-tun-$h".length <= 28 && "didban-tunp-$h".length <= 28)
        assertTrue("flush before repopulate", s.contains("iptables -t nat -F \"\$CHAIN\""))
        assertTrue("DNAT into the chain", s.contains("-A \"\$CHAIN\" -p tcp --dport 443 -j DNAT --to-destination 5.6.7.8:8443"))
        assertTrue("MASQUERADE scoped", s.contains("-A \"\$CHAIN_PO\" -p tcp -d 5.6.7.8 --dport 8443 -j MASQUERADE"))
        assertTrue("jump rule check-then-add", s.contains("-C PREROUTING -j \"\$CHAIN\""))
        assertTrue("unit for the agent", s.contains("didban-tunnel-9"))

        val unit = decodeUnit(s)
        assertTrue("oneshot stay-active", unit.contains("Type=oneshot") && unit.contains("RemainAfterExit=yes"))
        assertTrue("stop removes the chains", unit.contains("ExecStop=-iptables -t nat -X didban-tun-$h"))
        assertTrue("stop removes the jump rules", unit.contains("ExecStop=-iptables -t nat -D PREROUTING -j didban-tun-$h"))
    }

    @Test
    fun `rules script is idempotent under a stateful iptables emulator`() {
        val g = TunnelEngine.generateCode(iptCfg())
        val fake = Files.createTempDirectory("didban-ipt-e2e").toFile()
        try {
            val bin = File(fake, "bin").apply { mkdirs() }
            val state = File(fake, "state").apply { mkdirs() }
            fun stub(name: String, body: String) {
                val f = File(bin, name); f.writeText(body); f.setExecutable(true)
            }
            stub("sudo", "#!/bin/sh\nexec \"\$@\"\n")
            stub("sysctl", "#!/bin/sh\necho \"sysctl \$*\" >> \"${'$'}DIDBAN_TEST_LOG\"\nexit 0\n")
            stub("systemctl", "#!/bin/sh\necho \"systemctl \$*\" >> \"${'$'}DIDBAN_TEST_LOG\"\nexit 0\n")
            // Mini iptables emulator with on-disk state (chains + jump rules).
            stub("iptables", iptablesEmulator())

            // Run the generated install script with sandboxed paths.
            val sandboxed = g.iranInstallCommand
                .replace("/etc/didban", "${fake}/etc/didban")
                .replace("/etc/sysctl.d", "${fake}/etc/sysctl.d")
                .replace("/etc/systemd/system", "${fake}/etc/systemd/system")
            File(fake, "etc/systemd/system").mkdirs()
            val f = File(fake, "install.sh"); f.writeText(sandboxed)
            val pb = ProcessBuilder("bash", f.absolutePath).apply {
                environment()["DIDBAN_TEST_STATE"] = state.absolutePath
                environment()["DIDBAN_TEST_LOG"] = File(fake, "calls.log").absolutePath
                environment()["PATH"] = "${bin.absolutePath}:${System.getenv("PATH")}"
            }.redirectErrorStream(true)
            val p = pb.start()
            val out = p.inputStream.bufferedReader().readText()
            assertEquals("install must exit 0. Output: $out", 0, p.waitFor())
            assertTrue("sysctl drop-in written", File(fake, "etc/sysctl.d/99-didban-iptables.conf").canRead())

            // Execute the generated rules script (the unit's ExecStart) twice.
            val h = expectedHash(9)
            val rules = File(fake, "etc/didban/iptables-$h-rules.sh")
            assertTrue("rules script written", rules.canRead())
            bashCheck(rules)
            fun runRules() {
                val r = ProcessBuilder("bash", rules.absolutePath).apply {
                    environment()["DIDBAN_TEST_STATE"] = state.absolutePath
                    environment()["PATH"] = "${bin.absolutePath}:${System.getenv("PATH")}"
                }.redirectErrorStream(true).start()
                val o = r.inputStream.bufferedReader().readText()
                assertEquals("rules script must exit 0. Output: $o", 0, r.waitFor())
            }
            runRules()
            val after1 = snapshot(state, h)
            runRules()
            val after2 = snapshot(state, h)
            assertEquals("redeploy must converge (no duplicate rules)", after1, after2)
            assertEquals("two DNAT rules (tcp+udp)", 2, after1["ch_didban-tun-$h"]!!.lines().count { it.isNotBlank() })
            assertEquals("two MASQUERADE rules (tcp+udp)", 2, after1["ch_didban-tunp-$h"]!!.lines().count { it.isNotBlank() })
            assertEquals("one PREROUTING jump", 1, after1["jump_PREROUTING"]!!.lines().count { it.isNotBlank() })
            assertEquals("one POSTROUTING jump", 1, after1["jump_POSTROUTING"]!!.lines().count { it.isNotBlank() })

            // Simulate the unit's ExecStop: flush, remove jumps, delete chains.
            val stop = ProcessBuilder("/bin/sh", "-c",
                "iptables -t nat -F didban-tun-$h; iptables -t nat -F didban-tunp-$h; " +
                    "iptables -t nat -D PREROUTING -j didban-tun-$h; iptables -t nat -D POSTROUTING -j didban-tunp-$h; " +
                    "iptables -t nat -X didban-tun-$h; iptables -t nat -X didban-tunp-$h").apply {
                environment()["DIDBAN_TEST_STATE"] = state.absolutePath
                environment()["PATH"] = "${bin.absolutePath}:${System.getenv("PATH")}"
            }.redirectErrorStream(true).start()
            assertEquals("stop cleanup must exit 0", 0, stop.waitFor())
            assertFalse("PREROUTING chain gone", File(state, "ch_didban-tun-$h").exists())
            assertFalse("POSTROUTING chain gone", File(state, "ch_didban-tunp-$h").exists())
            assertEquals("PREROUTING jump removed", 0, File(state, "jump_PREROUTING").readLines().count { it.isNotBlank() })
            assertEquals("POSTROUTING jump removed", 0, File(state, "jump_POSTROUTING").readLines().count { it.isNotBlank() })
        } finally {
            fake.deleteRecursively()
        }
    }

    @Test
    fun `port list changes self-heal (stale rules removed on redeploy)`() {
        val g1 = TunnelEngine.generateCode(iptCfg(multiPorts = "80:8080"))
        val g2 = TunnelEngine.generateCode(iptCfg()) // different port set
        val fake = Files.createTempDirectory("didban-ipt-rot").toFile()
        try {
            val bin = File(fake, "bin").apply { mkdirs() }
            val state = File(fake, "state").apply { mkdirs() }
            fun stub(name: String, body: String) {
                val f = File(bin, name); f.writeText(body); f.setExecutable(true)
            }
            stub("sudo", "#!/bin/sh\nexec \"\$@\"\n")
            stub("sysctl", "#!/bin/sh\nexit 0\n")
            stub("systemctl", "#!/bin/sh\nexit 0\n")
            // Reuse the same emulator as the idempotency test.
            stub("iptables", iptablesEmulator())

            fun applyRulesOf(installScript: String, tag: String) {
                val sandboxed = installScript
                    .replace("/etc/didban", "${fake}/etc/$tag-didban")
                    .replace("/etc/sysctl.d", "${fake}/etc/sysctl.d")
                    .replace("/etc/systemd/system", "${fake}/etc/systemd/system")
                File(fake, "etc/$tag-didban").mkdirs()
                File(fake, "etc/systemd/system").mkdirs()
                val f = File(fake, "install_$tag.sh"); f.writeText(sandboxed)
                val p = ProcessBuilder("bash", f.absolutePath).apply {
                    environment()["DIDBAN_TEST_STATE"] = state.absolutePath
                    environment()["PATH"] = "${bin.absolutePath}:${System.getenv("PATH")}"
                }.redirectErrorStream(true).start()
                assertEquals("install_$tag must exit 0", 0, p.waitFor())
            }

            // Same tunnel id => same chains: deploying a changed port set on
            // top of an earlier one must leave ONLY the new rules.
            val h = expectedHash(9)
            val chFile = File(state, "ch_didban-tun-$h")
            applyRulesOf(g1.iranInstallCommand, "a")
            runRulesFrom("a", fake, bin, state)
            assertTrue("first deploy has port 80", chFile.readText().contains("--dport 80"))
            applyRulesOf(g2.iranInstallCommand, "b")
            runRulesFrom("b", fake, bin, state)
            val rules = chFile.readText()
            assertFalse("stale port 80 rule gone", rules.contains("--dport 80 "))
            assertTrue("new port 443 rule present", rules.contains("--dport 443"))
        } finally {
            fake.deleteRecursively()
        }
    }

    private fun runRulesFrom(tag: String, fake: File, bin: File, state: File) {
        val h = expectedHash(9)
        val rules = File(fake, "etc/$tag-didban/iptables-$h-rules.sh")
        val r = ProcessBuilder("bash", rules.absolutePath).apply {
            environment()["DIDBAN_TEST_STATE"] = state.absolutePath
            environment()["PATH"] = "${bin.absolutePath}:${System.getenv("PATH")}"
        }.redirectErrorStream(true).start()
        val o = r.inputStream.bufferedReader().readText()
        assertEquals("rules script must exit 0. Output: $o", 0, r.waitFor())
    }

    private fun iptablesEmulator(): String = """#!/bin/sh
STATE="${'$'}DIDBAN_TEST_STATE"
if [ "${'$'}1" = "-t" ]; then shift; shift; fi
op="${'$'}1"; shift
case "${'$'}op" in
  -nL)
    ch="${'$'}1"
    case "${'$'}ch" in PREROUTING|POSTROUTING|INPUT|FORWARD|OUTPUT) exit 0;; esac
    [ -f "${'$'}STATE/ch_${'$'}ch" ] && exit 0 || exit 1
    ;;
  -N)
    ch="${'$'}1"
    if [ -f "${'$'}STATE/ch_${'$'}ch" ]; then echo "exists" >&2; exit 1; fi
    : > "${'$'}STATE/ch_${'$'}ch"
    ;;
  -F)
    ch="${'$'}1"
    [ -f "${'$'}STATE/ch_${'$'}ch" ] && : > "${'$'}STATE/ch_${'$'}ch"
    ;;
  -A)
    ch="${'$'}1"; shift
    [ -f "${'$'}STATE/ch_${'$'}ch" ] || exit 1
    echo "${'$'}ch: ${'$'}*" >> "${'$'}STATE/ch_${'$'}ch"
    ;;
  -C)
    ch="${'$'}1"; shift
    grep -qx "${'$'}ch: ${'$'}*" "${'$'}STATE/ch_${'$'}ch" 2>/dev/null && exit 0 || exit 1
    ;;
  -I)
    ch="${'$'}1"; shift
    touch "${'$'}STATE/jump_${'$'}ch"
    if ! grep -qx "${'$'}ch: ${'$'}*" "${'$'}STATE/jump_${'$'}ch"; then echo "${'$'}ch: ${'$'}*" >> "${'$'}STATE/jump_${'$'}ch"; fi
    ;;
  -D)
    ch="${'$'}1"; shift
    [ -f "${'$'}STATE/jump_${'$'}ch" ] || exit 0
    grep -vx "${'$'}ch: ${'$'}*" "${'$'}STATE/jump_${'$'}ch" > "${'$'}STATE/tmp" || true
    mv "${'$'}STATE/tmp" "${'$'}STATE/jump_${'$'}ch"
    ;;
  -X)
    rm -f "${'$'}STATE/ch_${'$'}1"
    ;;
  *)
    exit 0
    ;;
esac
exit 0
"""

    private fun snapshot(state: File, h: String): Map<String, String> = mapOf(
        "ch_didban-tun-$h" to File(state, "ch_didban-tun-$h").readText(),
        "ch_didban-tunp-$h" to File(state, "ch_didban-tunp-$h").readText(),
        "jump_PREROUTING" to File(state, "jump_PREROUTING").readText(),
        "jump_POSTROUTING" to File(state, "jump_POSTROUTING").readText()
    )

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
