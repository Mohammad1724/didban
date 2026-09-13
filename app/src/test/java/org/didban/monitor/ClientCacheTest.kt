package org.didban.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Unit tests for [BoundedLru] and [ClientKey] — the pure core of the H7
 * client cache (JVM).
 */
class ClientCacheTest {

    private class Item(val id: Int)

    @Test
    fun putThenGetReturnsSameInstance() {
        val cache = BoundedLru<ClientKey, Item>(4)
        val key = ClientKey("h", 1, true, "")
        val item = Item(1)
        cache.put(key, item)
        assertSame(item, cache.get(key))
        assertEquals(1, cache.size())
        assertTrue(cache.contains(key))
    }

    @Test
    fun getMissReturnsNull() {
        val cache = BoundedLru<ClientKey, Item>(4)
        assertNull(cache.get(ClientKey("h", 1, true, "")))
        assertEquals(0, cache.size())
    }

    @Test
    fun putSameKeyReplacesWithoutGrowing() {
        val cache = BoundedLru<ClientKey, Item>(4)
        val key = ClientKey("h", 1, true, "")
        cache.put(key, Item(1))
        cache.put(key, Item(2))
        assertEquals(1, cache.size())
        assertEquals(2, cache.get(key)!!.id)
    }

    @Test
    fun evictsLruWhenOverCapacity() {
        val evicted = mutableListOf<Pair<ClientKey, Item>>()
        val cache = BoundedLru<ClientKey, Item>(2) { k, v -> evicted.add(k to v) }
        val a = ClientKey("a", 1, true, "")
        val b = ClientKey("b", 2, true, "")
        val c = ClientKey("c", 3, true, "")
        val itemA = Item(1)
        cache.put(a, itemA)
        cache.put(b, Item(2))
        cache.put(c, Item(3)) // evicts a (LRU)
        assertEquals(2, cache.size())
        assertNull(cache.get(a))
        assertEquals(listOf(a to itemA), evicted)
    }

    @Test
    fun readMovesEntryToMostRecentlyUsed() {
        val cache = BoundedLru<ClientKey, Item>(2)
        val a = ClientKey("a", 1, true, "")
        val b = ClientKey("b", 2, true, "")
        val c = ClientKey("c", 3, true, "")
        cache.put(a, Item(1))
        cache.put(b, Item(2))
        cache.get(a) // a becomes MRU
        cache.put(c, Item(3)) // must evict b now, not a
        assertEquals("b evicted, a kept", true, cache.contains(a))
        assertFalse(cache.contains(b))
        assertTrue(cache.contains(c))
    }

    @Test
    fun removeInvokesEvictAndReturnsValue() {
        val evicted = mutableListOf<Pair<ClientKey, Item>>()
        val cache = BoundedLru<ClientKey, Item>(2) { k, v -> evicted.add(k to v) }
        val a = ClientKey("a", 1, true, "")
        cache.put(a, Item(1))
        val removed = cache.remove(a)
        assertEquals(1, removed!!.id)
        assertFalse(cache.contains(a))
        assertEquals(1, evicted.size)
    }

    @Test
    fun removeAbsentReturnsNullWithoutEvict() {
        val evicted = mutableListOf<Pair<ClientKey, Item>>()
        val cache = BoundedLru<ClientKey, Item>(2) { k, v -> evicted.add(k to v) }
        assertNull(cache.remove(ClientKey("a", 1, true, "")))
        assertTrue(evicted.isEmpty())
    }

    @Test
    fun keysAreOrderedLruFirst() {
        val cache = BoundedLru<ClientKey, Item>(4)
        val a = ClientKey("a", 1, true, "")
        val b = ClientKey("b", 2, true, "")
        val c = ClientKey("c", 3, true, "")
        cache.put(a, Item(1))
        cache.put(b, Item(2))
        cache.put(c, Item(3))
        cache.get(a) // a -> MRU, so LRU order is b, c, a
        assertEquals(listOf(b, c, a), cache.keys())
    }

    @Test
    fun rejectsNonPositiveCapacity() {
        try {
            BoundedLru<ClientKey, Item>(0)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("capacity"))
        }
    }

    @Test
    fun clientKeyEqualityDistinguishesFingerprintAndTls() {
        val base = ClientKey("h", 8686, true, "ab")
        assertEquals(base, ClientKey("h", 8686, true, "ab"))
        assertEquals(base.hashCode(), ClientKey("h", 8686, true, "ab").hashCode())
        // same server, different pin -> different key (stale client must not be served)
        val repinned = ClientKey("h", 8686, true, "cd")
        val plainHttp = ClientKey("h", 8686, false, "")
        val otherPort = ClientKey("h", 8687, true, "ab")
        if (base == repinned || base == plainHttp || base == otherPort) fail("keys must differ")
    }

    @Test
    fun concurrentAccessStaysWithinCapacity() {
        val cache = BoundedLru<ClientKey, Item>(8)
        val threads = (0 until 8).map { t ->
            Thread {
                repeat(2000) { i ->
                    val key = ClientKey("h$t", i % 50, i % 2 == 0, "")
                    cache.put(key, Item(i))
                    cache.get(key)
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertTrue(cache.size() <= 8)
    }
}
