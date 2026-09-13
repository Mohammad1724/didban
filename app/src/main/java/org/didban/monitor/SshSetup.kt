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

    suspend fun installAgent(
        host: String,
        sshPort: Int,
        user: String,
        password: String,
        hostKeyPolicy: HostKeyPolicy = AutoTrustPolicy(HostKeyTrustStore)
    ): Result = withContext(Dispatchers.IO) {
            var session: com.jcraft.jsch.Session? = null
            try {
                CryptoSecurity.ensureInitialized()
                val jsch = JSch()
                session = jsch.getSession(user, host, sshPort)
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

                // TOFU: verify the host key before running the installer.
                val hostKeyError = verifySessionHostKey(session, host, sshPort, hostKeyPolicy)
                if (hostKeyError != null) {
                    session.disconnect()
                    return@withContext Result(success = false, error = hostKeyError)
                }

                val channel = session.openChannel("exec") as ChannelExec
                channel.setCommand(INSTALL_CMD)
                // M10: bounded streams — the installer banner is small, but a
                // misbehaving remote (huge curl error, verbose bash) must not
                // be able to OOM the device either.
                val out = CappedOutputStream(MAX_SSH_OUTPUT_BYTES)
                val err = CappedOutputStream(MAX_SSH_OUTPUT_BYTES)
                channel.setOutputStream(out)
                channel.setErrStream(err)
                channel.connect(TimeUnit.SECONDS.toMillis(20).toInt())

                val deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5)
                while (!channel.isClosed && System.currentTimeMillis() < deadline) {
                    Thread.sleep(150)
                }
                // M10: tell the user the install timed out rather than
                // reporting a vague "could not be parsed".
                val timedOut = !channel.isClosed
                channel.disconnect()

                val text = out.toUtf8String() + "\n" +
                    err.toUtf8String() +
                    (if (out.isTruncated || err.isTruncated)
                        "\n…[خروجی بریده شد — فقط ${MAX_SSH_OUTPUT_BYTES / 1024}KB نگه داشته شد]" else "")
                val token = parseField(text, "Token:")
                val fp = parseField(text, "Cert SHA256:")
                val url = parseField(text, "URL:")

                if (token != null) {
                    Result(success = true, output = text, token = token, fingerprint = fp, port = portFromUrl(url))
                } else {
                    Result(
                        success = false,
                        output = text,
                        error = if (timedOut)
                            "زمان‌بندی نصب تمام شد (۵ دقیقه) — خروجی کامل دریافت نشد"
                        else
                            "installer output could not be parsed"
                    )
                }
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
        return Regex(":(\\d+)").find(url)?.groupValues?.get(1)?.toIntOrNull()
    }
}
