package org.didban.monitor

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Vector
import java.util.concurrent.TimeUnit

data class SftpFileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val permissions: String,
    val modifiedTime: Long
) {
    val formattedSize: String
        get() {
            if (isDirectory) return "Folder"
            if (size < 1024) return "$size B"
            if (size < 1024 * 1024) return "${size / 1024} KB"
            return "${String.format("%.1f", size / (1024.0 * 1024.0))} MB"
        }
}

object SftpEngine {

    private fun createSession(host: String, port: Int, user: String, pass: String): com.jcraft.jsch.Session {
        val jsch = JSch()
        val session = jsch.getSession(user.trim(), host.trim(), if (port > 0) port else 22)
        session.setPassword(pass)
        session.setConfig("StrictHostKeyChecking", "no")
        session.setConfig("PreferredAuthentications", "password,keyboard-interactive")
        session.connect(TimeUnit.SECONDS.toMillis(15).toInt())
        return session
    }

    /**
     * Lists files and directories in [remotePath].
     */
    suspend fun listFiles(
        host: String,
        port: Int = 22,
        user: String = "root",
        pass: String,
        remotePath: String = "/etc"
    ): List<SftpFileItem> = withContext(Dispatchers.IO) {
        var session: com.jcraft.jsch.Session? = null
        var sftp: ChannelSftp? = null
        val items = mutableListOf<SftpFileItem>()

        try {
            session = createSession(host, port, user, pass)
            sftp = session.openChannel("sftp") as ChannelSftp
            sftp.connect(TimeUnit.SECONDS.toMillis(10).toInt())

            val normalizedPath = if (remotePath.isBlank()) "/" else remotePath
            @Suppress("UNCHECKED_CAST")
            val vector = sftp.ls(normalizedPath) as Vector<ChannelSftp.LsEntry>

            for (entry in vector) {
                val name = entry.filename
                if (name == "." || name == "..") continue

                val fullPath = if (normalizedPath.endsWith("/")) "$normalizedPath$name" else "$normalizedPath/$name"
                val attrs = entry.attrs

                items.add(
                    SftpFileItem(
                        name = name,
                        path = fullPath,
                        isDirectory = attrs.isDir,
                        size = attrs.size,
                        permissions = attrs.permissionsString ?: "",
                        modifiedTime = attrs.mTime.toLong() * 1000L
                    )
                )
            }

            // Sort: directories first, then alphabetical
            items.sortWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            items
        } catch (e: Exception) {
            throw Exception("خطا در مرور پوشه SFTP: ${e.message}")
        } finally {
            try { sftp?.disconnect() } catch (_: Exception) {}
            try { session?.disconnect() } catch (_: Exception) {}
        }
    }

    /**
     * Reads a remote text file content.
     */
    suspend fun readFile(
        host: String,
        port: Int = 22,
        user: String = "root",
        pass: String,
        remotePath: String
    ): String = withContext(Dispatchers.IO) {
        var session: com.jcraft.jsch.Session? = null
        var sftp: ChannelSftp? = null

        try {
            session = createSession(host, port, user, pass)
            sftp = session.openChannel("sftp") as ChannelSftp
            sftp.connect(TimeUnit.SECONDS.toMillis(10).toInt())

            val out = ByteArrayOutputStream()
            sftp.get(remotePath, out)
            out.toString("UTF-8")
        } catch (e: Exception) {
            throw Exception("خطا در خواندن فایل: ${e.message}")
        } finally {
            try { sftp?.disconnect() } catch (_: Exception) {}
            try { session?.disconnect() } catch (_: Exception) {}
        }
    }

    /**
     * Saves text content to a remote file.
     */
    suspend fun saveFile(
        host: String,
        port: Int = 22,
        user: String = "root",
        pass: String,
        remotePath: String,
        content: String
    ): Boolean = withContext(Dispatchers.IO) {
        var session: com.jcraft.jsch.Session? = null
        var sftp: ChannelSftp? = null

        try {
            session = createSession(host, port, user, pass)
            sftp = session.openChannel("sftp") as ChannelSftp
            sftp.connect(TimeUnit.SECONDS.toMillis(10).toInt())

            val inStream = ByteArrayInputStream(content.toByteArray(Charsets.UTF_8))
            sftp.put(inStream, remotePath, ChannelSftp.OVERWRITE)
            true
        } catch (e: Exception) {
            throw Exception("خطا در ذخیره فایل روی سرور: ${e.message}")
        } finally {
            try { sftp?.disconnect() } catch (_: Exception) {}
            try { session?.disconnect() } catch (_: Exception) {}
        }
    }

    /**
     * Deletes a remote file or empty directory.
     */
    suspend fun deleteItem(
        host: String,
        port: Int = 22,
        user: String = "root",
        pass: String,
        remotePath: String,
        isDirectory: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        var session: com.jcraft.jsch.Session? = null
        var sftp: ChannelSftp? = null

        try {
            session = createSession(host, port, user, pass)
            sftp = session.openChannel("sftp") as ChannelSftp
            sftp.connect(TimeUnit.SECONDS.toMillis(10).toInt())

            if (isDirectory) {
                sftp.rmdir(remotePath)
            } else {
                sftp.rm(remotePath)
            }
            true
        } catch (e: Exception) {
            throw Exception("خطا در حذف: ${e.message}")
        } finally {
            try { sftp?.disconnect() } catch (_: Exception) {}
            try { session?.disconnect() } catch (_: Exception) {}
        }
    }
}
