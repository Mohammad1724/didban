package org.didban.monitor

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

object CryptoSecurity {
    private var initialized = false

    @Synchronized
    fun ensureInitialized() {
        if (!initialized) {
            try {
                // Remove Android's partial/stripped legacy BC provider and register full modern Bouncy Castle provider at priority 1
                Security.removeProvider("BC")
                Security.insertProviderAt(BouncyCastleProvider(), 1)
                initialized = true
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }
}
