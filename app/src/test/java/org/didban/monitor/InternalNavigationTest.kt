package org.didban.monitor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InternalNavigationTest {
    @Test
    fun `capability comparison requires an exact value`() {
        assertTrue(InternalNavigation.constantTimeEquals("a".repeat(64), "a".repeat(64)))
        assertFalse(InternalNavigation.constantTimeEquals("a".repeat(64), "a".repeat(63) + "b"))
        assertFalse(InternalNavigation.constantTimeEquals("a".repeat(64), "a".repeat(63)))
    }
}
