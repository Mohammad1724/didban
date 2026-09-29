package org.didban.monitor

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

object CryptoSecurity {

    @Volatile
    private var initialized = false

    /**
     * Remove Android's partial/stripped legacy BC provider and register the
     * full modern Bouncy Castle provider at priority 1.
     *
     * Idempotent and safe to call from any thread. `DidbanApplication` warms
     * this up on a background thread; every call site that depends on Bouncy
     * Castle (the vault, [SecureCipher], SSH, SFTP) calls it too. Whoever
     * arrives first does the work, so a crypto path can never run against a
     * substituted provider.
     *
     * The provider is *constructed* outside the monitor on purpose:
     * `BouncyCastleProvider()` registers ~4200 services and measured 250-310 ms
     * cold on a JVM (worse on a device, where it also pulls DEX). Only the
     * remove/insert swap runs under the lock, so a caller arriving mid-warm-up
     * waits tens of milliseconds instead of a quarter of a second, and the
     * window in which no "BC" is registered at all is a few frames wide
     * rather than the whole startup.
     */
    fun ensureInitialized() {
        if (initialized) return
        // Two threads racing here may each build a provider; one of them is
        // then thrown away. That only ever happens once, at warm-up.
        val provider = BouncyCastleProvider()
        synchronized(this) {
            if (initialized) return
            try {
                Security.removeProvider("BC")
                Security.insertProviderAt(provider, 1)
                check(Security.getProvider("BC") === provider) {
                    "Unexpected BC provider"
                }
                initialized = true
            } catch (_: Throwable) {
                // Continuing with an absent or substituted crypto provider can
                // silently change algorithm behavior. Fail closed, and do not
                // print a stack trace that may contain in-flight secrets.
                throw IllegalStateException("Cryptographic provider initialization failed")
            }
        }
    }
}
