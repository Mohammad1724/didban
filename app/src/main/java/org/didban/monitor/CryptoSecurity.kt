package org.didban.monitor

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

object CryptoSecurity {
    private var initialized = false

    @Synchronized
    fun ensureInitialized() {
        if (!initialized) {
            try {
                // Remove Android's partial/stripped legacy BC provider and register full modern Bouncy Castle provider at priority 1.
                Security.removeProvider("BC")
                Security.insertProviderAt(BouncyCastleProvider(), 1)
                check(Security.getProvider("BC") is BouncyCastleProvider) {
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
