package org.didban.monitor

/**
 * Identity of the HTTP client for one server connection (H7).
 *
 * Two OkHttpClients are interchangeable only when they share the same key:
 * same host, port, TLS mode and (normalized) pinned fingerprint. In
 * particular, when the user re-pins a different fingerprint or toggles TLS,
 * the previously cached client MUST be evicted before the new key is used —
 * the old client would reject the new certificate, or enforce a pin that no
 * longer matches the stored config.
 *
 * [fingerprint] is always in normalized form (lowercase hex, no colons) —
 * see [HttpClientPool.normalizedFingerprint].
 */
data class ClientKey(
    val host: String,
    val port: Int,
    val useTls: Boolean,
    val fingerprint: String
)

/**
 * Minimal thread-safe bounded LRU cache.
 *
 * When capacity is exceeded the least-recently-used entry is evicted and
 * [onEvict] is invoked with it, so the owner can release resources (e.g.
 * shut down an evicted OkHttp client's dispatcher).
 *
 * Pure logic — unit-tested on the JVM ([ClientCacheTest]).
 */
class BoundedLru<K : Any, V>(
    val capacity: Int,
    private val onEvict: (K, V) -> Unit = { _, _ -> }
) {
    init {
        require(capacity > 0) { "capacity must be > 0, got $capacity" }
    }

    // accessOrder = true: every read moves the entry to the tail;
    // removeEldestEntry evicts the head once size > capacity.
    private val map = object : LinkedHashMap<K, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>): Boolean {
            if (size > capacity) {
                onEvict(eldest.key, eldest.value)
                return true
            }
            return false
        }
    }

    /** Returns the value and marks the entry most-recently-used. */
    @Synchronized
    fun get(key: K): V? = map[key]

    /** Inserts or replaces; evicts the LRU entry when full. */
    @Synchronized
    fun put(key: K, value: V) {
        map[key] = value
    }

    /** Removes and returns the value, invoking [onEvict] when present. */
    @Synchronized
    fun remove(key: K): V? = map.remove(key)?.also { onEvict(key, it) }

    @Synchronized
    fun contains(key: K): Boolean = map.containsKey(key)

    @Synchronized
    fun size(): Int = map.size

    /** All keys, LRU (oldest) first. */
    @Synchronized
    fun keys(): List<K> = map.keys.toList()
}
