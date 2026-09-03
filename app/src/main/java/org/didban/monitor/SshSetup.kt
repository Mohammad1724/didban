package org.didban.monitor

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
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

    suspend fun installAgent(host: String, sshPort: Int, user: String, password: String): Result =
        withContext(Dispatchers.IO) {
            var session: com.jcraft.jsch.Session? = null
            try {
                val jsch = JSch()
                session = jsch.getSession(user, host, sshPort)
                session.setPassword(password)
                session.setConfig("StrictHostKeyChecking", "no")
                session.setConfig("PreferredAuthentications", "password,keyboard-interactive")
                session.connect(TimeUnit.SECONDS.toMillis(20).toInt())

                val channel = session.openChannel("exec") as ChannelExec
                channel.setCommand(INSTALL_CMD)
                val out = ByteArrayOutputStream()
                val err = ByteArrayOutputStream()
                channel.setOutputStream(out)
                channel.setErrStream(err)
                channel.connect(TimeUnit.SECONDS.toMillis(20).toInt())

                val deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5)
                while (!channel.isClosed && System.currentTimeMillis() < deadline) {
                    Thread.sleep(150)
                }
                channel.disconnect()

                val text = out.toString("UTF-8") + "\n" + err.toString("UTF-8")
                val token = parseField(text, "Token:")
                val fp = parseField(text, "Cert SHA256:")
                val url = parseField(text, "URL:")

                if (token != null) {
                    Result(success = true, output = text, token = token, fingerprint = fp, port = portFromUrl(url))
                } else {
                    Result(success = false, output = text, error = "installer output could not be parsed")
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
