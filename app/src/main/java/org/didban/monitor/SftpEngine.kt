package org.didban.monitor

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.SftpProgressMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Vector
import java.util.concurrent.TimeUnit

enum class SftpFileCategory {
    DIRECTORY,
    CONFIG,
    STRUCTURED,
    CODE,
    LOG_OR_TEXT,
    CERTIFICATE,
    ARCHIVE,
    IMAGE,
    DATABASE,
    GENERIC_FILE
}

data class SftpFileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val isLink: Boolean = false,
    val linkTarget: String? = null,
    val size: Long = 0L,
    val permissions: String = "",
    val permissionsOctal: String = "0644",
    val uid: Int = 0,
    val gid: Int = 0,
    val modifiedTime: Long = 0L
) {
    val formattedSize: String
        get() {
            if (isDirectory) return "پوشه"
            if (size < 1024) return "$size B"
            if (size < 1024 * 1024) return "${size / 1024} KB"
            if (size < 1024 * 1024 * 1024) return "${String.format(Locale.US, "%.1f", size / (1024.0 * 1024.0))} MB"
            return "${String.format(Locale.US, "%.2f", size / (1024.0 * 1024.0 * 1024.0))} GB"
        }

    val formattedDate: String
        get() {
            if (modifiedTime <= 0) return ""
            val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
            return sdf.format(Date(modifiedTime))
        }

    val category: SftpFileCategory
        get() {
            if (isDirectory) return SftpFileCategory.DIRECTORY
            val ext = name.substringAfterLast('.', "").lowercase()
            return when (ext) {
                "conf", "cnf", "cfg", "ini", "env", "service" -> SftpFileCategory.CONFIG
                "json", "yaml", "yml", "toml", "xml" -> SftpFileCategory.STRUCTURED
                "sh", "bash", "py", "js", "ts", "go", "rs", "php", "c", "cpp" -> SftpFileCategory.CODE
                "log", "txt", "md", "out", "err" -> SftpFileCategory.LOG_OR_TEXT
                "crt", "key", "pem", "pub", "cer", "pfx", "p12" -> SftpFileCategory.CERTIFICATE
                "tar", "gz", "zip", "bz2", "xz", "7z", "rar", "deb", "rpm" -> SftpFileCategory.ARCHIVE
                "png", "jpg", "jpeg", "svg", "gif", "webp", "ico" -> SftpFileCategory.IMAGE
                "db", "sqlite", "sqlite3", "sql" -> SftpFileCategory.DATABASE
                else -> SftpFileCategory.GENERIC_FILE
            }
        }
}

enum class SftpSortMode {
    NAME_ASC,
    NAME_DESC,
    SIZE_DESC,
    SIZE_ASC,
    DATE_DESC,
    DATE_ASC
}

object SftpEngine {

    private fun createSession(host: String, port: Int, user: String, pass: String): com.jcraft.jsch.Session {
        CryptoSecurity.ensureInitialized()
        val jsch = JSch()
        val session = jsch.getSession(user.trim().ifBlank { "root" }, host.trim(), if (port > 0) port else 22)
        session.setPassword(pass)
        session.setConfig("StrictHostKeyChecking", "no")
        session.setConfig("PreferredAuthentications", "password,keyboard-interactive")

        val kexAlgos = listOf(
            "curve25519-sha256",
            "curve25519-sha256@libssh.org",
            "ecdh-sha2-nistp256",
            "ecdh-sha2-nistp384",
            "ecdh-sha2-nistp521",
            "diffie-hellman-group-exchange-sha256",
            "diffie-hellman-group16-sha512",
            "diffie-hellman-group18-sha512",
            "diffie-hellman-group14-sha256",
            "diffie-hellman-group-exchange-sha1",
            "diffie-hellman-group14-sha1",
            "diffie-hellman-group1-sha1"
        ).joinToString(",")

        val hostKeyAlgos = listOf(
            "ssh-ed25519",
            "ecdsa-sha2-nistp256",
            "ecdsa-sha2-nistp384",
            "ecdsa-sha2-nistp521",
            "rsa-sha2-512",
            "rsa-sha2-256",
            "ssh-rsa",
            "ssh-dss"
        ).joinToString(",")

        val cipherAlgos = listOf(
            "chacha20-poly1305@openssh.com",
            "aes128-ctr",
            "aes192-ctr",
            "aes256-ctr",
            "aes128-gcm@openssh.com",
            "aes256-gcm@openssh.com",
            "arcfour256",
            "arcfour128",
            "aes128-cbc",
            "3des-cbc",
            "blowfish-cbc",
            "cast128-cbc",
            "aes192-cbc",
            "aes256-cbc",
            "arcfour"
        ).joinToString(",")

        session.setConfig("kex", kexAlgos)
        session.setConfig("server_host_key", hostKeyAlgos)
        session.setConfig("PubkeyAcceptedAlgorithms", hostKeyAlgos)
        session.setConfig("cipher.s2c", cipherAlgos)
        session.setConfig("cipher.c2s", cipherAlgos)
        session.setConfig("CheckCiphers", "chacha20-poly1305@openssh.com,aes128-ctr,aes192-ctr,aes256-ctr,aes128-gcm@openssh.com,aes256-gcm@openssh.com")

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
        remotePath: String = "/etc",
        showHidden: Boolean = true,
        sortMode: SftpSortMode = SftpSortMode.NAME_ASC
    ): List<SftpFileItem> = withContext(Dispatchers.IO) {
        var session: com.jcraft.jsch.Session? = null
        var sftp: ChannelSftp? = null
        val items = mutableListOf<SftpFileItem>()

        try {
            session = createSession(host, port, user, pass)
            sftp = session.openChannel("sftp") as ChannelSftp
            sftp.connect(TimeUnit.SECONDS.toMillis(12).toInt())

            val normalizedPath = when {
                remotePath.isBlank() -> "/"
                remotePath == "/" -> "/"
                remotePath.endsWith("/") -> remotePath.removeSuffix("/")
                else -> remotePath
            }

            @Suppress("UNCHECKED_CAST")
            val vector = sftp.ls(normalizedPath) as Vector<ChannelSftp.LsEntry>

            for (entry in vector) {
                val name = entry.filename
                if (name == "." || name == "..") continue
                if (!showHidden && name.startsWith(".")) continue

                val fullPath = if (normalizedPath == "/") "/$name" else "$normalizedPath/$name"
                val attrs = entry.attrs

                val octal = String.format(Locale.US, "%04o", attrs.permissions and 511)

                items.add(
                    SftpFileItem(
                        name = name,
                        path = fullPath,
                        isDirectory = attrs.isDir,
                        isLink = attrs.isLink,
                        size = attrs.size,
                        permissions = attrs.permissionsString ?: "",
                        permissionsOctal = octal,
                        uid = attrs.uId,
                        gid = attrs.gId,
                        modifiedTime = attrs.mTime.toLong() * 1000L
                    )
                )
            }

            // Sort with directories first, then user's sort preference
            val sortedList = items.sortedWith { a, b ->
                if (a.isDirectory != b.isDirectory) {
                    if (a.isDirectory) -1 else 1
                } else {
                    when (sortMode) {
                        SftpSortMode.NAME_ASC -> a.name.compareTo(b.name, ignoreCase = true)
                        SftpSortMode.NAME_DESC -> b.name.compareTo(a.name, ignoreCase = true)
                        SftpSortMode.SIZE_DESC -> b.size.compareTo(a.size)
                        SftpSortMode.SIZE_ASC -> a.size.compareTo(b.size)
                        SftpSortMode.DATE_DESC -> b.modifiedTime.compareTo(a.modifiedTime)
                        SftpSortMode.DATE_ASC -> a.modifiedTime.compareTo(b.modifiedTime)
                    }
                }
            }
            sortedList
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
        remotePath: String,
        maxBytes: Long = 4 * 1024 * 1024 // 4 MB limit for editor safety
    ): String = withContext(Dispatchers.IO) {
        var session: com.jcraft.jsch.Session? = null
        var sftp: ChannelSftp? = null

        try {
            session = createSession(host, port, user, pass)
            sftp = session.openChannel("sftp") as ChannelSftp
            sftp.connect(TimeUnit.SECONDS.toMillis(12).toInt())

            val stat = sftp.stat(remotePath)
            if (stat.size > maxBytes) {
                throw Exception("حجم فایل (${stat.size / 1024} KB) برای ادیتور متنی بسیار بزرگ است.")
            }

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
            sftp.connect(TimeUnit.SECONDS.toMillis(12).toInt())

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
     * Creates an empty remote file.
     */
    suspend fun createFile(
        host: String,
        port: Int = 22,
        user: String = "root",
        pass: String,
        remotePath: String
    ): Boolean = withContext(Dispatchers.IO) {
        var session: com.jcraft.jsch.Session? = null
        var sftp: ChannelSftp? = null

        try {
            session = createSession(host, port, user, pass)
            sftp = session.openChannel("sftp") as ChannelSftp
            sftp.connect(TimeUnit.SECONDS.toMillis(12).toInt())

            val emptyIn = ByteArrayInputStream(ByteArray(0))
            sftp.put(emptyIn, remotePath, ChannelSftp.OVERWRITE)
            true
        } catch (e: Exception) {
            throw Exception("خطا در ایجاد فایل: ${e.message}")
        } finally {
            try { sftp?.disconnect() } catch (_: Exception) {}
            try { session?.disconnect() } catch (_: Exception) {}
        }
    }

    /**
     * Creates a new remote directory.
     */
    suspend fun createDirectory(
        host: String,
        port: Int = 22,
        user: String = "root",
        pass: String,
        remotePath: String
    ): Boolean = withContext(Dispatchers.IO) {
        var session: com.jcraft.jsch.Session? = null
        var sftp: ChannelSftp? = null

        try {
            session = createSession(host, port, user, pass)
            sftp = session.openChannel("sftp") as ChannelSftp
            sftp.connect(TimeUnit.SECONDS.toMillis(12).toInt())

            sftp.mkdir(remotePath)
            true
        } catch (e: Exception) {
            throw Exception("خطا در ایجاد پوشه: ${e.message}")
        } finally {
            try { sftp?.disconnect() } catch (_: Exception) {}
            try { session?.disconnect() } catch (_: Exception) {}
        }
    }

    /**
     * Renames or moves a file / directory.
     */
    suspend fun renameItem(
        host: String,
        port: Int = 22,
        user: String = "root",
        pass: String,
        oldPath: String,
        newPath: String
    ): Boolean = withContext(Dispatchers.IO) {
        var session: com.jcraft.jsch.Session? = null
        var sftp: ChannelSftp? = null

        try {
            session = createSession(host, port, user, pass)
            sftp = session.openChannel("sftp") as ChannelSftp
            sftp.connect(TimeUnit.SECONDS.toMillis(12).toInt())

            sftp.rename(oldPath, newPath)
            true
        } catch (e: Exception) {
            throw Exception("خطا در تغییر نام / انتقال: ${e.message}")
        } finally {
            try { sftp?.disconnect() } catch (_: Exception) {}
            try { session?.disconnect() } catch (_: Exception) {}
        }
    }

    /**
     * Changes Linux file permissions (chmod).
     * @param octalPermissions Int parsed from octal e.g. Integer.parseInt("755", 8)
     */
    suspend fun chmodItem(
        host: String,
        port: Int = 22,
        user: String = "root",
        pass: String,
        remotePath: String,
        octalPermissions: Int
    ): Boolean = withContext(Dispatchers.IO) {
        var session: com.jcraft.jsch.Session? = null
        var sftp: ChannelSftp? = null

        try {
            session = createSession(host, port, user, pass)
            sftp = session.openChannel("sftp") as ChannelSftp
            sftp.connect(TimeUnit.SECONDS.toMillis(12).toInt())

            sftp.chmod(octalPermissions, remotePath)
            true
        } catch (e: Exception) {
            throw Exception("خطا در تغییر دسترسی (chmod): ${e.message}")
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
            sftp.connect(TimeUnit.SECONDS.toMillis(12).toInt())

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

    /**
     * Streams local data into a remote file.
     */
    suspend fun uploadStream(
        host: String,
        port: Int = 22,
        user: String = "root",
        pass: String,
        remotePath: String,
        inputStream: InputStream,
        totalBytes: Long,
        onProgress: (bytesUploaded: Long, total: Long) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        var session: com.jcraft.jsch.Session? = null
        var sftp: ChannelSftp? = null

        try {
            session = createSession(host, port, user, pass)
            sftp = session.openChannel("sftp") as ChannelSftp
            sftp.connect(TimeUnit.SECONDS.toMillis(15).toInt())

            val monitor = object : SftpProgressMonitor {
                var count = 0L
                override fun init(op: Int, src: String?, dest: String?, max: Long) {
                    count = 0L
                }
                override fun count(c: Long): Boolean {
                    count += c
                    onProgress(count, totalBytes)
                    return true
                }
                override fun end() {
                    onProgress(totalBytes, totalBytes)
                }
            }

            sftp.put(inputStream, remotePath, monitor, ChannelSftp.OVERWRITE)
            true
        } catch (e: Exception) {
            throw Exception("خطا در آپلود فایل: ${e.message}")
        } finally {
            try { inputStream.close() } catch (_: Exception) {}
            try { sftp?.disconnect() } catch (_: Exception) {}
            try { session?.disconnect() } catch (_: Exception) {}
        }
    }

    /**
     * Downloads a remote file to an OutputStream.
     */
    suspend fun downloadStream(
        host: String,
        port: Int = 22,
        user: String = "root",
        pass: String,
        remotePath: String,
        outputStream: OutputStream,
        onProgress: (bytesDownloaded: Long, total: Long) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        var session: com.jcraft.jsch.Session? = null
        var sftp: ChannelSftp? = null

        try {
            session = createSession(host, port, user, pass)
            sftp = session.openChannel("sftp") as ChannelSftp
            sftp.connect(TimeUnit.SECONDS.toMillis(15).toInt())

            val stat = sftp.stat(remotePath)
            val totalBytes = stat.size

            val monitor = object : SftpProgressMonitor {
                var count = 0L
                override fun init(op: Int, src: String?, dest: String?, max: Long) {
                    count = 0L
                }
                override fun count(c: Long): Boolean {
                    count += c
                    onProgress(count, totalBytes)
                    return true
                }
                override fun end() {
                    onProgress(totalBytes, totalBytes)
                }
            }

            sftp.get(remotePath, outputStream, monitor)
            outputStream.flush()
            true
        } catch (e: Exception) {
            throw Exception("خطا در دانلود فایل: ${e.message}")
        } finally {
            try { outputStream.close() } catch (_: Exception) {}
            try { sftp?.disconnect() } catch (_: Exception) {}
            try { session?.disconnect() } catch (_: Exception) {}
        }
    }
}
