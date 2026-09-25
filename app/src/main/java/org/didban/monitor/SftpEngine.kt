package org.didban.monitor

import com.jcraft.jsch.ChannelSftp
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
    fun formattedSize(copy: CommandCopy): String {
        if (isDirectory) return copy.wtDirectory
        if (size < 1024) return "$size B"
        if (size < 1024 * 1024) return "${size / 1024} KB"
        if (size < 1024 * 1024 * 1024) return "${String.format(Locale.US, "%.1f", size / (1024.0 * 1024.0))} MB"
        return "${String.format(Locale.US, "%.2f", size / (1024.0 * 1024.0 * 1024.0))} GB"
    }

    /** Legacy Persian projection kept for compatibility; screens use the locale-aware overload. */
    @Deprecated("Use formattedSize(copy) so the active locale is explicit")
    val formattedSize: String get() = formattedSize(CommandCopyFa)

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

enum class SftpFailureKind {
    BROWSE,
    FILE_TOO_LARGE,
    READ,
    SAVE,
    CREATE_FILE,
    CREATE_DIRECTORY,
    RENAME,
    CHMOD,
    DELETE,
    UPLOAD,
    DOWNLOAD
}

data class SftpFailure(
    val kind: SftpFailureKind,
    val detail: String = ""
) {
    fun localized(copy: CommandCopy): String = when (kind) {
        SftpFailureKind.BROWSE -> copy.wtSftpBrowseFailed
        SftpFailureKind.FILE_TOO_LARGE -> copy.wtSftpFileTooLarge.replace("%1", detail)
        SftpFailureKind.READ -> copy.wtFileReadFailed
        SftpFailureKind.SAVE -> copy.wtFileSaveFailed
        SftpFailureKind.CREATE_FILE -> copy.wtSftpCreateFileFailed
        SftpFailureKind.CREATE_DIRECTORY -> copy.wtSftpCreateDirectoryFailed
        SftpFailureKind.RENAME -> copy.wtSftpRenameFailed
        SftpFailureKind.CHMOD -> copy.wtSftpChmodFailed
        SftpFailureKind.DELETE -> copy.wtSftpDeleteFailed
        SftpFailureKind.UPLOAD -> copy.wtSftpUploadFailed
        SftpFailureKind.DOWNLOAD -> copy.wtSftpDownloadFailed
    }
}

class SftpFailureException(val failure: SftpFailure) : Exception(failure.kind.name)

object SftpEngine {

    private fun safeFailure(kind: SftpFailureKind, error: Exception, password: String): SftpFailureException {
        val detail = SecretRedactor.redact(error.message ?: "SFTP error", listOf(password)).take(500)
        return SftpFailureException(SftpFailure(kind, detail))
    }

    private suspend fun createSession(
        host: String,
        port: Int,
        user: String,
        pass: String,
        hostKeyPolicy: HostKeyPolicy = StoredHostKeyPolicy(HostKeyTrustStore)
    ): com.jcraft.jsch.Session {
        CryptoSecurity.ensureInitialized()
        return connectVerifiedSshSession(
            host = host.trim(),
            port = port,
            user = user.trim().ifBlank { "root" },
            password = pass,
            timeoutMs = TimeUnit.SECONDS.toMillis(15).toInt(),
            policy = hostKeyPolicy
        ) { candidate ->
            candidate.setConfig("kex", SshAlgorithms.kexConfig())
            candidate.setConfig("server_host_key", SshAlgorithms.hostKeyConfig())
            candidate.setConfig("PubkeyAcceptedAlgorithms", SshAlgorithms.hostKeyConfig())
            candidate.setConfig("cipher.s2c", SshAlgorithms.cipherConfig())
            candidate.setConfig("cipher.c2s", SshAlgorithms.cipherConfig())
            candidate.setConfig("CheckCiphers", SshAlgorithms.cipherConfig())
        }
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
        sortMode: SftpSortMode = SftpSortMode.NAME_ASC,
        hostKeyPolicy: HostKeyPolicy = StoredHostKeyPolicy(HostKeyTrustStore)
    ): List<SftpFileItem> = withContext(Dispatchers.IO) {
        var session: com.jcraft.jsch.Session? = null
        var sftp: ChannelSftp? = null
        val items = mutableListOf<SftpFileItem>()

        try {
            session = createSession(host, port, user, pass, hostKeyPolicy)
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
            throw safeFailure(SftpFailureKind.BROWSE, e, pass)
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
        maxBytes: Long = 4 * 1024 * 1024, // 4 MB limit for editor safety
        hostKeyPolicy: HostKeyPolicy = StoredHostKeyPolicy(HostKeyTrustStore)
    ): String = withContext(Dispatchers.IO) {
        var session: com.jcraft.jsch.Session? = null
        var sftp: ChannelSftp? = null

        try {
            session = createSession(host, port, user, pass, hostKeyPolicy)
            sftp = session.openChannel("sftp") as ChannelSftp
            sftp.connect(TimeUnit.SECONDS.toMillis(12).toInt())

            val stat = sftp.stat(remotePath)
            if (stat.size > maxBytes) {
                throw SftpFailureException(SftpFailure(SftpFailureKind.FILE_TOO_LARGE, (stat.size / 1024).toString()))
            }

            val out = ByteArrayOutputStream()
            sftp.get(remotePath, out)
            out.toString("UTF-8")
        } catch (e: SftpFailureException) {
            throw e
        } catch (e: Exception) {
            throw safeFailure(SftpFailureKind.READ, e, pass)
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
        content: String,
        hostKeyPolicy: HostKeyPolicy = StoredHostKeyPolicy(HostKeyTrustStore)
    ): Boolean = withContext(Dispatchers.IO) {
        var session: com.jcraft.jsch.Session? = null
        var sftp: ChannelSftp? = null

        try {
            session = createSession(host, port, user, pass, hostKeyPolicy)
            sftp = session.openChannel("sftp") as ChannelSftp
            sftp.connect(TimeUnit.SECONDS.toMillis(12).toInt())

            val inStream = ByteArrayInputStream(content.toByteArray(Charsets.UTF_8))
            sftp.put(inStream, remotePath, ChannelSftp.OVERWRITE)
            true
        } catch (e: Exception) {
            throw safeFailure(SftpFailureKind.SAVE, e, pass)
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
            throw safeFailure(SftpFailureKind.CREATE_FILE, e, pass)
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
            throw safeFailure(SftpFailureKind.CREATE_DIRECTORY, e, pass)
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
            throw safeFailure(SftpFailureKind.RENAME, e, pass)
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
            throw safeFailure(SftpFailureKind.CHMOD, e, pass)
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
            throw safeFailure(SftpFailureKind.DELETE, e, pass)
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
            throw safeFailure(SftpFailureKind.UPLOAD, e, pass)
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
            throw safeFailure(SftpFailureKind.DOWNLOAD, e, pass)
        } finally {
            try { outputStream.close() } catch (_: Exception) {}
            try { sftp?.disconnect() } catch (_: Exception) {}
            try { session?.disconnect() } catch (_: Exception) {}
        }
    }
}
