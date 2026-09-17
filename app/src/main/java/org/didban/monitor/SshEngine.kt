package org.didban.monitor

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/** M10: max concurrent SSH sessions for batch runs (device resource cap). */
private const val MAX_CONCURRENT_BATCH_SSH = 5

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
     *
     * Host keys are verified against the local trust store (TOFU) via
     * [hostKeyPolicy] right after the key exchange. Default is automatic
     * TOFU; interactive screens pass a [ConfirmingHostKeyPolicy] so the
     * first contact requires explicit user confirmation.
     */
    suspend fun execute(
        host: String,
        sshPort: Int = 22,
        user: String = "root",
        password: String,
        command: String,
        timeoutSec: Int = 25,
        hostKeyPolicy: HostKeyPolicy = AutoTrustPolicy(HostKeyTrustStore)
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

            // H10: the modern algorithm whitelist lives in SshAlgorithms —
            // one shared set for every engine (no CBC/arcfour/3DES, no
            // SHA-1 kex, no ssh-dss/ssh-rsa host keys).
            session.setConfig("kex", SshAlgorithms.kexConfig())
            session.setConfig("server_host_key", SshAlgorithms.hostKeyConfig())
            session.setConfig("PubkeyAcceptedAlgorithms", SshAlgorithms.hostKeyConfig())
            session.setConfig("cipher.s2c", SshAlgorithms.cipherConfig())
            session.setConfig("cipher.c2s", SshAlgorithms.cipherConfig())

            session.connect(TimeUnit.SECONDS.toMillis(12).toInt())

            // TOFU: verify the host key before any command is sent.
            val portForTrust = if (sshPort > 0) sshPort else 22
            val hostKeyError = verifySessionHostKey(session, host, portForTrust, hostKeyPolicy)
            if (hostKeyError != null) {
                session.disconnect()
                return@withContext SshExecResult(
                    exitCode = -1,
                    stdout = "",
                    stderr = hostKeyError,
                    durationMs = System.currentTimeMillis() - t0,
                    isSuccess = false,
                    errorMessage = hostKeyError
                )
            }

            val channel = session.openChannel("exec") as ChannelExec
            channel.setCommand(command)
            // M10: bounded streams — an unbounded remote command (e.g.
            // `cat /dev/urandom`) can no longer OOM the device.
            val out = CappedOutputStream(MAX_SSH_OUTPUT_BYTES)
            val err = CappedOutputStream(MAX_SSH_OUTPUT_BYTES)
            channel.setOutputStream(out)
            channel.setErrStream(err)
            channel.connect(TimeUnit.SECONDS.toMillis(12).toInt())

            val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeoutSec.toLong())
            while (!channel.isClosed && System.currentTimeMillis() < deadline) {
                Thread.sleep(100)
            }
            // M10: distinguish "timed out" from a genuine non-zero exit —
            // otherwise a hung command silently reports exitStatus -1.
            val timedOut = !channel.isClosed
            val exitCode = channel.exitStatus
            channel.disconnect()

            val stdoutStr = cleanAnsi(out.toUtf8String()) +
                if (out.isTruncated) "\n…[خروجی بریده شد — فقط ${MAX_SSH_OUTPUT_BYTES / 1024}KB از ${out.totalBytes / 1024}KB نگه داشته شد]" else ""
            val stderrStr = cleanAnsi(err.toUtf8String()) +
                if (err.isTruncated) "\n…[خطا بریده شد — فقط ${MAX_SSH_OUTPUT_BYTES / 1024}KB از ${err.totalBytes / 1024}KB نگه داشته شد]" else ""
            val dur = System.currentTimeMillis() - t0

            SshExecResult(
                exitCode = exitCode,
                stdout = stdoutStr,
                stderr = stderrStr,
                durationMs = dur,
                isSuccess = exitCode == 0 && !timedOut,
                errorMessage = when {
                    timedOut -> "زمان‌بندی تمام شد (${timeoutSec} ثانیه) — خروجی ممکن است ناقص باشد"
                    exitCode != 0 && stderrStr.isNotEmpty() -> stderrStr
                    else -> null
                }
            )
        } catch (e: Exception) {
            val dur = System.currentTimeMillis() - t0
            val safeError = SecretRedactor.redact(
                e.message ?: "SSH Error",
                listOf(password, command)
            ).take(500)
            SshExecResult(
                exitCode = -1,
                stdout = "",
                stderr = safeError,
                durationMs = dur,
                isSuccess = false,
                errorMessage = safeError
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
        timeoutSec: Int = 30,
        hostKeyPolicy: HostKeyPolicy = AutoTrustPolicy(HostKeyTrustStore)
    ): List<BatchServerResult> = withContext(Dispatchers.IO) {
        // M10: bound the fan-out — N targets must not mean N simultaneous
        // SSH sessions (each with up to 2×1MB of streams) on the phone.
        val sem = Semaphore(MAX_CONCURRENT_BATCH_SSH)
        val deferreds = targets.map { (server, port, pass) ->
            async {
                sem.acquire()
                try {
                    val res = execute(
                        host = server.host,
                        sshPort = port,
                        user = "root",
                        password = pass,
                        command = command,
                        timeoutSec = timeoutSec,
                        hostKeyPolicy = hostKeyPolicy
                    )
                    BatchServerResult(
                        serverId = server.id,
                        serverName = server.name,
                        host = server.host,
                        result = res
                    )
                } finally {
                    sem.release()
                }
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
