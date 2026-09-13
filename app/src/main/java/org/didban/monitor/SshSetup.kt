package org.didban.monitor

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * SSH-assisted setup: connects with password auth, runs the one-line agent
 * installer, and parses the printed URL / token / certificate fingerprint.
 * The SSH password is only held in memory for the duration of the call.
 */
object SshSetup {

    data class Result(
        val success: Boolean,
        val output: String = "",
        val token: String? = null,
        val fingerprint: String? = null,
        val port: Int? = null,
        val error: String? = null
    )

    private const val INSTALL_CMD =
        "curl -fsSL https://raw.githubusercontent.com/Mohammad1724/didban/main/agent/install.sh -o /tmp/didban-install.sh && bash /tmp/didban-install.sh"

    // H14: the installer's journalctl grep can race the agent's startup
    // banner. If the token parsed but the fingerprint did not, retry the
    // fingerprint grab over a fresh SSH session before accepting an
    // unpinned save.
    private const val FP_GRAB_CMD =
        "journalctl -u didban-agent --no-pager 2>/dev/null | grep -o 'Cert SHA256:  [a-f0-9]*' | head -1 | awk '{print \$3}'"
    private const val FP_RETRY_COUNT = 3
    private const val FP_RETRY_DELAY_MS = 2000L
    private val FP_REGEX = Regex("^[0-9a-f]{64}$")

    /** Thrown when the host-key policy rejects the remote (TOFU). */
    private class HostKeyRejected(message: String) : Exception(message)

    /**
     * Connects (password auth, H10 modern-algorithm whitelist) and
     * TOFU-verifies the host key via [hostKeyPolicy]. Throws
     * [HostKeyRejected] on policy rejection. The caller owns disconnect().
     */
    private suspend fun openVerifiedSession(
        host: String,
        sshPort: Int,
        user: String,
        password: String,
        hostKeyPolicy: HostKeyPolicy
    ): com.jcraft.jsch.Session {
        CryptoSecurity.ensureInitialized()
        val jsch = JSch()
        val session = jsch.getSession(user, host, sshPort)
        session.setPassword(password)
        session.setConfig("StrictHostKeyChecking", "no")
        session.setConfig("PreferredAuthentications", "password,keyboard-interactive")

        // H10: shared modern whitelist (see SshAlgorithms).
        session.setConfig("kex", SshAlgorithms.kexConfig())
        session.setConfig("server_host_key", SshAlgorithms.hostKeyConfig())
        session.setConfig("PubkeyAcceptedAlgorithms", SshAlgorithms.hostKeyConfig())
        session.setConfig("cipher.s2c", SshAlgorithms.cipherConfig())
        session.setConfig("cipher.c2s", SshAlgorithms.cipherConfig())

        session.connect(TimeUnit.SECONDS.toMillis(20).toInt())

        // TOFU: verify the host key before anything is sent over the channel.
        val hostKeyError = verifySessionHostKey(session, host, sshPort, hostKeyPolicy)
        if (hostKeyError != null) {
            session.disconnect()
            throw HostKeyRejected(hostKeyError)
        }
        return session
    }

    /** Result of one capped exec: retained stdout/stderr + whether the deadline hit first. */
    private data class CappedRun(val out: String, val err: String, val timedOut: Boolean)

    /** Runs one exec command on [session] with capped streams and a hard deadline. */
    private fun runCappedCommand(
        session: com.jcraft.jsch.Session,
        command: String,
        deadlineMs: Long,
        connectTimeoutMs: Long
    ): CappedRun {
        val channel = session.openChannel("exec") as ChannelExec
        channel.setCommand(command)
        // M10: bounded streams — a misbehaving remote must not be able to
        // OOM the device.
        val out = CappedOutputStream(MAX_SSH_OUTPUT_BYTES)
        val err = CappedOutputStream(MAX_SSH_OUTPUT_BYTES)
        channel.setOutputStream(out)
        channel.setErrStream(err)
        channel.connect(connectTimeoutMs.toInt())

        val deadline = System.currentTimeMillis() + deadlineMs
        while (!channel.isClosed && System.currentTimeMillis() < deadline) {
            Thread.sleep(150)
        }
        val timedOut = !channel.isClosed
        channel.disconnect()
        return CappedRun(out.toUtf8String(), err.toUtf8String(), timedOut)
    }

    suspend fun installAgent(
        host: String,
        sshPort: Int,
        user: String,
        password: String,
        hostKeyPolicy: HostKeyPolicy = AutoTrustPolicy(HostKeyTrustStore)
    ): Result = withContext(Dispatchers.IO) {
        var session: com.jcraft.jsch.Session? = null
        try {
            session = openVerifiedSession(host, sshPort, user, password, hostKeyPolicy)

            val run = runCappedCommand(
                session = session,
                command = INSTALL_CMD,
                deadlineMs = TimeUnit.MINUTES.toMillis(5),
                connectTimeoutMs = TimeUnit.SECONDS.toMillis(20)
            )
            // M10: tell the user the install timed out rather than
            // reporting a vague "could not be parsed".
            val text = run.out + "\n" + run.err
            val token = parseField(text, "Token:")
            var fp = parseField(text, "Cert SHA256:")
            val url = parseField(text, "URL:")

            if (token != null && fp == null) {
                fp = fetchFingerprintRetry(host, sshPort, user, password, hostKeyPolicy)
            }

            if (token != null) {
                Result(success = true, output = text, token = token, fingerprint = fp, port = portFromUrl(url))
            } else {
                Result(
                    success = false,
                    output = text,
                    error = if (run.timedOut)
                        "زمان‌بندی نصب تمام شد (۵ دقیقه) — خروجی کامل دریافت نشد"
                    else
                        "installer output could not be parsed"
                )
            }
        } catch (e: HostKeyRejected) {
            Result(success = false, error = e.message)
        } catch (e: Exception) {
            Result(success = false, error = e.message ?: "SSH error")
        } finally {
            try {
                session?.disconnect()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    /**
     * H14 fallback: after a successful install whose banner lost the cert
     * line, re-grab the fingerprint over fresh SSH sessions with a small
     * backoff. Returns null when every attempt comes back empty — the
     * caller then saves the server TLS-but-unpinned with a visible warning
     * (never silently plaintext).
     */
    private suspend fun fetchFingerprintRetry(
        host: String,
        sshPort: Int,
        user: String,
        password: String,
        hostKeyPolicy: HostKeyPolicy
    ): String? {
        for (attempt in 1..FP_RETRY_COUNT) {
            Thread.sleep(FP_RETRY_DELAY_MS * attempt)
            var session: com.jcraft.jsch.Session? = null
            try {
                session = openVerifiedSession(host, sshPort, user, password, hostKeyPolicy)
                val run = runCappedCommand(
                    session = session,
                    command = FP_GRAB_CMD,
                    deadlineMs = TimeUnit.SECONDS.toMillis(10),
                    connectTimeoutMs = TimeUnit.SECONDS.toMillis(10)
                )
                val fp = run.out.trim()
                if (fp.matches(FP_REGEX)) return fp
            } catch (_: Exception) {
                // transient SSH/TOFU failure — try again
            } finally {
                try {
                    session?.disconnect()
                } catch (_: Exception) {}
            }
        }
        return null
    }

    /** Finds "  Token:       VALUE" style lines from the installer banner. */
    private fun parseField(text: String, key: String): String? {
        for (line in text.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith(key)) {
                val value = trimmed.removePrefix(key).trim()
                if (value.isNotEmpty()) return value
            }
        }
        return null
    }

    private fun portFromUrl(url: String?): Int? {
        if (url == null) return null
        return Regex(":\\d+").find(url)?.groupValues?.get(0)?.drop(1)?.toIntOrNull()
    }
}
