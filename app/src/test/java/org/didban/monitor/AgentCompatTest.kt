package org.didban.monitor

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentCompatTest {

    @Test
    fun `release older than minimum is outdated`() {
        assertTrue(isAgentOutdated("v0.6.1"))
        assertTrue(isAgentOutdated("v0.6.0"))
        assertTrue(isAgentOutdated("v0.5.0"))
        assertTrue(isAgentOutdated("v0.6.10"))
        assertTrue(isAgentOutdated("v0.1.0"))
    }

    @Test
    fun `minimum release and newer are not outdated`() {
        assertFalse(isAgentOutdated("v0.7.0"))
        assertFalse(isAgentOutdated("v0.7.1"))
        assertFalse(isAgentOutdated("v0.10.0"))
        assertFalse(isAgentOutdated("v1.0.0"))
        assertFalse(isAgentOutdated("v1"))
    }

    @Test
    fun `comparison is numeric not lexicographic`() {
        // "v0.10.0" must beat "v0.7.0" even though "1" < "7" as text.
        assertFalse(isAgentOutdated("v0.10.0"))
        assertTrue(isAgentOutdated("v0.6.9"))
    }

    @Test
    fun `non-release builds are unknown never outdated`() {
        assertFalse(isAgentOutdated("dev"))
        assertFalse(isAgentOutdated("main"))
        assertFalse(isAgentOutdated(""))
        assertFalse(isAgentOutdated("   "))
        assertFalse(isAgentOutdated("garbage!!"))
        assertFalse(isAgentOutdated("v0.6.1.4"))
        assertFalse(isAgentOutdated("vabc"))
    }

    @Test
    fun `tag parsing tolerates case whitespace and suffixes`() {
        assertTrue(isAgentOutdated("V0.6.1"))
        assertTrue(isAgentOutdated("  v0.6.1  "))
        assertTrue(isAgentOutdated("0.6.1"))
        assertTrue(isAgentOutdated("v0.6"))
        assertFalse(isAgentOutdated("v0.7"))
        assertFalse(isAgentOutdated("v0.7.0-rc1"))
        assertFalse(isAgentOutdated("v0.7.0+ci.5"))
    }

    @Test
    fun `only 404 and 405 mean a missing endpoint`() {
        assertTrue(isMissingEndpointFailure(ApiException("not found", 404)))
        assertTrue(isMissingEndpointFailure(ApiException("method not allowed", 405)))
        assertFalse(isMissingEndpointFailure(ApiException("unauthorized", 401)))
        assertFalse(isMissingEndpointFailure(ApiException("forbidden", 403)))
        assertFalse(isMissingEndpointFailure(ApiException("no status", null)))
        assertFalse(isMissingEndpointFailure(Exception("boom")))
    }

    @Test
    fun `metrics parses the agent version`() {
        val withVersion = Metrics.fromJson(JSONObject("""{"hostname":"h","version":"v0.6.1"}"""))
        assertEquals("v0.6.1", withVersion.agentVersion)

        val withoutVersion = Metrics.fromJson(JSONObject("""{"hostname":"h"}"""))
        assertEquals("", withoutVersion.agentVersion)
    }

    @Test
    fun `update command is the documented installer one-liner`() {
        assertTrue(AGENT_UPDATE_COMMAND.startsWith("curl -fsSL "))
        assertTrue(AGENT_UPDATE_COMMAND.contains("didban/main/agent/install.sh"))
        assertTrue(AGENT_UPDATE_COMMAND.contains("sudo bash didban-install.sh"))
    }
}
