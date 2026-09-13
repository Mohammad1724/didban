package org.didban.monitor

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory HostKeyStore for JVM unit tests. */
class InMemoryHostKeyStore : HostKeyStore {
    private val map = mutableMapOf<String, String>()
    override fun getFingerprint(host: String, port: Int): String? = map[hostKeyIdentity(host, port)]
    override fun storeFingerprint(host: String, port: Int, fingerprint: String) {
        map[hostKeyIdentity(host, port)] = fingerprint
    }
    override fun forget(host: String, port: Int) {
        map.remove(hostKeyIdentity(host, port))
    }
}

class HostKeyTrustTest {

    private val keyA = byteArrayOf(1, 2, 3, 4, 5)
    private val keyB = byteArrayOf(9, 9, 9)

    // ── HostKeyFingerprint ──────────────────────────────────────────────────

    @Test
    fun `fingerprint is deterministic 64-char hex sha256`() {
        val fp1 = HostKeyFingerprint.of(keyA)
        val fp2 = HostKeyFingerprint.of(keyA)
        assertEquals(fp1, fp2)
        assertEquals(64, fp1.length)
        assertTrue(fp1.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `different keys produce different fingerprints`() {
        assertNotEquals(HostKeyFingerprint.of(keyA), HostKeyFingerprint.of(keyB))
    }

    @Test
    fun `verify accepts matching and rejects mismatching keys`() {
        val stored = HostKeyFingerprint.of(keyA)
        assertTrue(HostKeyFingerprint.verify(stored, keyA))
        assertFalse(HostKeyFingerprint.verify(stored, keyB))
    }

    @Test
    fun `verify is case-insensitive on the stored value`() {
        val stored = HostKeyFingerprint.of(keyA).uppercase()
        assertTrue(HostKeyFingerprint.verify(stored, keyA))
    }

    // ── identity normalization ──────────────────────────────────────────────

    @Test
    fun `host identity is case-insensitive and port-normalized`() {
        val store = InMemoryHostKeyStore()
        store.storeFingerprint("MyHost.example.com", 22, "abc")
        assertEquals("abc", store.getFingerprint("myhost.example.com", 22))
        assertEquals(null, store.getFingerprint("myhost.example.com", 2222))
        // port <= 0 normalizes to 22
        store.storeFingerprint("other", 0, "def")
        assertEquals("def", store.getFingerprint("OTHER", 22))
    }

    // ── AutoTrustPolicy ─────────────────────────────────────────────────────

    @Test
    fun `auto trust records the key on first contact`() = runBlocking {
        val store = InMemoryHostKeyStore()
        val policy = AutoTrustPolicy(store)
        assertTrue(policy.verify("h1", 22, keyA))
        assertEquals(HostKeyFingerprint.of(keyA), store.getFingerprint("h1", 22))
    }

    @Test
    fun `auto trust enforces the key on later contacts`() = runBlocking {
        val store = InMemoryHostKeyStore()
        val policy = AutoTrustPolicy(store)
        assertTrue(policy.verify("h1", 22, keyA))
        assertTrue(policy.verify("h1", 22, keyA))
        // A different key for the same host must be rejected (possible MITM).
        assertFalse(policy.verify("h1", 22, keyB))
        // The stored key must be unchanged after a rejection.
        assertEquals(HostKeyFingerprint.of(keyA), store.getFingerprint("h1", 22))
    }

    @Test
    fun `auto trust keeps hosts independent`() = runBlocking {
        val store = InMemoryHostKeyStore()
        val policy = AutoTrustPolicy(store)
        assertTrue(policy.verify("h1", 22, keyA))
        // A different host with a different key is trusted independently.
        assertTrue(policy.verify("h2", 22, keyB))
    }

    // ── ConfirmingHostKeyPolicy ─────────────────────────────────────────────

    @Test
    fun `first contact asks for confirmation and stores on approval`() = runBlocking {
        val store = InMemoryHostKeyStore()
        val seen = mutableListOf<HostKeyPrompt>()
        val policy = ConfirmingHostKeyPolicy(store) { prompt ->
            seen.add(prompt); true
        }
        assertTrue(policy.verify("h1", 22, keyA))
        assertEquals(1, seen.size)
        assertFalse(seen[0].keyChanged)
        assertEquals(HostKeyFingerprint.of(keyA), store.getFingerprint("h1", 22))
    }

    @Test
    fun `first contact rejection stores nothing`() = runBlocking {
        val store = InMemoryHostKeyStore()
        val policy = ConfirmingHostKeyPolicy(store) { false }
        assertFalse(policy.verify("h1", 22, keyA))
        assertEquals(null, store.getFingerprint("h1", 22))
    }

    @Test
    fun `known key passes without prompting`() = runBlocking {
        val store = InMemoryHostKeyStore()
        val prompts = mutableListOf<HostKeyPrompt>()
        val policy = ConfirmingHostKeyPolicy(store) { prompt ->
            prompts.add(prompt); true
        }
        assertTrue(policy.verify("h1", 22, keyA))
        assertTrue(policy.verify("h1", 22, keyA))
        assertEquals(1, prompts.size) // only the first contact prompted
    }

    @Test
    fun `changed key prompts with keyChanged and reset stores new key`() = runBlocking {
        val store = InMemoryHostKeyStore()
        val prompts = mutableListOf<HostKeyPrompt>()
        val policy = ConfirmingHostKeyPolicy(store) { prompt ->
            prompts.add(prompt); true
        }
        assertTrue(policy.verify("h1", 22, keyA))
        assertTrue(policy.verify("h1", 22, keyB))
        assertEquals(2, prompts.size)
        assertTrue(prompts[1].keyChanged)
        assertEquals(HostKeyFingerprint.of(keyB), store.getFingerprint("h1", 22))
    }

    @Test
    fun `changed key rejection keeps the original trust`() = runBlocking {
        val store = InMemoryHostKeyStore()
        val prompts = mutableListOf<HostKeyPrompt>()
        // Decision queue: approve the initial trust, then reject the key change.
        val decisions = listOf(true, false)
        var call = 0
        val policy = ConfirmingHostKeyPolicy(store) { prompt ->
            prompts.add(prompt)
            decisions[call++]
        }
        assertTrue(policy.verify("h1", 22, keyA))
        assertFalse(policy.verify("h1", 22, keyB))
        assertTrue(prompts.last().keyChanged)
        assertEquals(HostKeyFingerprint.of(keyA), store.getFingerprint("h1", 22))
        // The original key still passes, without any further prompt.
        assertTrue(policy.verify("h1", 22, keyA))
        assertEquals(2, prompts.size)
    }

    @Test
    fun `forget clears trust`() = runBlocking {
        val store = InMemoryHostKeyStore()
        val policy = AutoTrustPolicy(store)
        assertTrue(policy.verify("h1", 22, keyA))
        store.forget("h1", 22)
        // After forget, even a different key is trusted again (fresh TOFU).
        assertTrue(policy.verify("h1", 22, keyB))
        assertEquals(HostKeyFingerprint.of(keyB), store.getFingerprint("h1", 22))
    }
}
