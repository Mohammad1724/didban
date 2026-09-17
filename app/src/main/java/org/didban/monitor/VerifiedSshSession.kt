package org.didban.monitor

import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.UserInfo
import java.security.MessageDigest

/** Host-key discovery that deliberately rejects before user authentication. */
private class CaptureAndRejectHostKeys : HostKeyRepository {
    var presentedKey: ByteArray? = null
        private set

    override fun check(host: String?, key: ByteArray?): Int {
        presentedKey = key?.copyOf()
        return HostKeyRepository.NOT_INCLUDED
    }
    override fun add(hostkey: HostKey?, ui: UserInfo?) = Unit
    override fun remove(host: String?, type: String?) = Unit
    override fun remove(host: String?, type: String?, key: ByteArray?) = Unit
    override fun getKnownHostsRepositoryID(): String = "didban-capture-reject"
    override fun getHostKey(): Array<HostKey> = emptyArray()
    override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
}

/** Allows exactly the key the user/persistent policy just approved. */
private class ExactHostKeyRepository(private val approved: ByteArray) : HostKeyRepository {
    override fun check(host: String?, key: ByteArray?): Int =
        if (key != null && MessageDigest.isEqual(approved, key)) HostKeyRepository.OK
        else HostKeyRepository.CHANGED
    override fun add(hostkey: HostKey?, ui: UserInfo?) = Unit
    override fun remove(host: String?, type: String?) = Unit
    override fun remove(host: String?, type: String?, key: ByteArray?) = Unit
    override fun getKnownHostsRepositoryID(): String = "didban-exact-key"
    override fun getHostKey(): Array<HostKey> = emptyArray()
    override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
}

/**
 * Performs a credential-free key exchange first, asks the policy to approve
 * the presented key, then reconnects with the password and an exact-key
 * repository. Passwords are therefore never sent before host authentication.
 */
suspend fun connectVerifiedSshSession(
    host: String,
    port: Int,
    user: String,
    password: String,
    timeoutMs: Int,
    policy: HostKeyPolicy,
    configure: (Session) -> Unit
): Session {
    val actualPort = if (port > 0) port else 22
    val capture = CaptureAndRejectHostKeys()
    val discoveryJsch = JSch().apply { setHostKeyRepository(capture) }
    val discovery = discoveryJsch.getSession(user, host, actualPort)
    configure(discovery)
    discovery.setConfig("StrictHostKeyChecking", "yes")
    discovery.setConfig("PreferredAuthentications", "none")
    try {
        // This normally throws because CaptureAndRejectHostKeys returns
        // NOT_INCLUDED. Either way no password has been configured or sent.
        runCatching { discovery.connect(timeoutMs) }
    } finally {
        discovery.disconnect()
    }

    val key = capture.presentedKey
        ?: throw IllegalStateException("could not retrieve the server host key; refusing authentication")
    if (!policy.verify(host, actualPort, key)) {
        throw IllegalStateException("host key verification failed for $host:$actualPort — possible MITM attack")
    }

    val authenticatedJsch = JSch().apply { setHostKeyRepository(ExactHostKeyRepository(key)) }
    return authenticatedJsch.getSession(user, host, actualPort).apply {
        configure(this)
        setConfig("StrictHostKeyChecking", "yes")
        setConfig("PreferredAuthentications", "password,keyboard-interactive")
        setPassword(password)
        connect(timeoutMs)
    }
}
