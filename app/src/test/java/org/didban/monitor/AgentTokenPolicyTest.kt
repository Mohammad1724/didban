package org.didban.monitor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentTokenPolicyTest {
    @Test fun `accepts generated token format`() {
        assertTrue(AgentTokenPolicy.isValid("0123456789abcdef".repeat(4)))
    }

    @Test fun `rejects short oversized whitespace and non ASCII tokens`() {
        assertFalse(AgentTokenPolicy.isValid("short"))
        assertFalse(AgentTokenPolicy.isValid("a".repeat(AgentTokenPolicy.MAX_LENGTH + 1)))
        assertFalse(AgentTokenPolicy.isValid("a".repeat(31) + " "))
        assertFalse(AgentTokenPolicy.isValid("a".repeat(31) + "\n"))
        assertFalse(AgentTokenPolicy.isValid("a".repeat(31) + "✓"))
    }
}
