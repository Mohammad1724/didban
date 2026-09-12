package org.didban.monitor

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

data class SshExecResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val durationMs: Long,
    val isSuccess: Boolean,
    val errorMessage: String? = null
)

data class BatchServerResult(
    val serverId: Long,
    val serverName: String,
    val host: String,
    val result: SshExecResult
)

object SshEngine {

    /**
     * Executes a command over SSH on a remote host.
     */
    suspend fun execute(
        host: String,
        sshPort: Int = 22,
        user: String = "root",
        password: String,
        command: String,
        timeoutSec: Int = 25
    ): SshExecResult = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        var session: com.jcraft.jsch.Session? = null
        try {
            CryptoSecurity.ensureInitialized()
            val jsch = JSch()
            session = jsch.getSession(user.trim(), host.trim(), if (sshPort > 0) sshPort else 22)
            session.setPassword(password)
            session.setConfig("StrictHostKeyChecking", "no")
            session.setConfig("PreferredAuthentications", "password,keyboard-interactive")

            val kexAlgos = "curve25519-sha256,curve25519-sha256@libssh.org,ecdh-sha2-nistp256,ecdh-sha2-nistp384,ecdh-sha2-nistp521,diffie-hellman-group-exchange-sha256,diffie-hellman-group16-sha512,diffie-hellman-group18-sha512,diffie-hellman-group14-sha256,diffie-hellman-group-exchange-sha1,diffie-hellman-group14-sha1,diffie-hellman-group1-sha1"
            val hostKeyAlgos = "ssh-ed25519,ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521,rsa-sha2-512,rsa-sha2-256,ssh-rsa,ssh-dss"
            val cipherAlgos = "chacha20-poly1305@openssh.com,aes128-ctr,aes192-ctr,aes256-ctr,aes128-gcm@openssh.com,aes256-gcm@openssh.com,aes128-cbc,3des-cbc"

            session.setConfig("kex", kexAlgos)
            session.setConfig("server_host_key", hostKeyAlgos)
            session.setConfig("PubkeyAcceptedAlgorithms", hostKeyAlgos)
            session.setConfig("cipher.s2c", cipherAlgos)
            session.setConfig("cipher.c2s", cipherAlgos)

            session.connect(TimeUnit.SECONDS.toMillis(12).toInt())

            val channel = session.openChannel("exec") as ChannelExec
            channel.setCommand(command)
            val out = ByteArrayOutputStream()
            val err = ByteArrayOutputStream()
            channel.setOutputStream(out)
            channel.setErrStream(err)
            channel.connect(TimeUnit.SECONDS.toMillis(12).toInt())

            val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeoutSec.toLong())
            while (!channel.isClosed && System.currentTimeMillis() < deadline) {
                Thread.sleep(100)
            }

            val exitCode = channel.exitStatus
            channel.disconnect()

            val stdoutStr = cleanAnsi(out.toString("UTF-8"))
            val stderrStr = cleanAnsi(err.toString("UTF-8"))
            val dur = System.currentTimeMillis() - t0

            SshExecResult(
                exitCode = exitCode,
                stdout = stdoutStr,
                stderr = stderrStr,
                durationMs = dur,
                isSuccess = exitCode == 0,
                errorMessage = if (exitCode != 0 && stderrStr.isNotEmpty()) stderrStr else null
            )
        } catch (e: Exception) {
            val dur = System.currentTimeMillis() - t0
            SshExecResult(
                exitCode = -1,
                stdout = "",
                stderr = e.message ?: "SSH Error",
                durationMs = dur,
                isSuccess = false,
                errorMessage = e.message ?: "اتصال SSH برقرار نشد"
            )
        } finally {
            try {
                session?.disconnect()
            } catch (_: Exception) {}
        }
    }

    /**
     * Executes a command concurrently across multiple remote servers.
     */
    suspend fun executeBatch(
        targets: List<Triple<ServerConfig, Int, String>>, // ServerConfig, sshPort, password
        command: String,
        timeoutSec: Int = 30
    ): List<BatchServerResult> = withContext(Dispatchers.IO) {
        val deferreds = targets.map { (server, port, pass) ->
            async {
                val res = execute(
                    host = server.host,
                    sshPort = port,
                    user = "root",
                    password = pass,
                    command = command,
                    timeoutSec = timeoutSec
                )
                BatchServerResult(
                    serverId = server.id,
                    serverName = server.name,
                    host = server.host,
                    result = res
                )
            }
        }
        deferreds.awaitAll()
    }

    /**
     * Strips ANSI escape codes from terminal outputs for clean mobile readability.
     */
    fun cleanAnsi(raw: String): String {
        return raw.replace(Regex("\u001B\\[[;\\d]*[ -/]*[@-~]"), "")
    }
}
