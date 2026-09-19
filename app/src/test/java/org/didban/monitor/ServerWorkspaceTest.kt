package org.didban.monitor

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ServerWorkspaceTest {
    private val now = 500_000L
    private fun server(id: Long = 1) = ServerConfig(id, "Node $id", "node$id.example.com", token = "r".repeat(32),
        adminToken = "a".repeat(32), fingerprint = "ab".repeat(32))
    private fun state(cpu: Int = 5, ram: Int = 10, updated: Long = now) = Repo.State(
        metrics = Metrics.fromJson(JSONObject().put("cpu", JSONObject().put("usage", cpu))
            .put("memory", JSONObject().put("usage_pct", ram))), updated = updated)

    @Test fun `unknown state is not counted as online or offline`() {
        assertEquals(FleetHealth.UNKNOWN, fleetHealth(server(), null, now))
        assertEquals(FleetHealth.UNKNOWN, fleetHealth(server(), state(updated = 0), now))
    }
    @Test fun `errors stale metrics and thresholds are classified separately`() {
        assertEquals(FleetHealth.HEALTHY, fleetHealth(server(), state(), now))
        assertEquals(FleetHealth.OFFLINE, fleetHealth(server(), state().copy(error = "HTTP 503"), now))
        assertEquals(FleetHealth.ATTENTION, fleetHealth(server(), state(updated = now - 120_001), now))
        assertEquals(FleetHealth.ATTENTION, fleetHealth(server(), state(cpu = 90), now))
        assertEquals(FleetHealth.ATTENTION, fleetHealth(server(), state(ram = 91), now))
    }
    @Test fun `search matches name and host case insensitively without revealing tokens`() {
        val s = server()
        assertEquals(listOf(s), visibleFleet(listOf(s), emptyMap(), "  NODE1.Example  ", FleetFilter.ALL, now))
        assertTrue(visibleFleet(listOf(s), emptyMap(), s.token, FleetFilter.ALL, now).isEmpty())
    }
    @Test fun `attention includes offline and stale but excludes unknown`() {
        val servers = (1L..4L).map { server(it) }
        val states = mapOf(1L to state(), 2L to Repo.State(error = "offline"), 3L to state(cpu = 95))
        assertEquals(listOf(2L, 3L), visibleFleet(servers, states, "", FleetFilter.ATTENTION, now).map { it.id })
        assertEquals(listOf(2L), visibleFleet(servers, states, "", FleetFilter.OFFLINE, now).map { it.id })
    }
    @Test fun `sorting surfaces failures first with stable ids`() {
        val servers = listOf(server(3), server(1), server(2))
        val states = mapOf(1L to state(), 2L to Repo.State(error = "offline"))
        assertEquals(listOf(2L, 3L, 1L), visibleFleet(servers, states, "", FleetFilter.ALL, now).map { it.id })
    }
    @Test fun `saving one server retains unrelated records added since editor opened`() {
        val original = server(1)
        val late = server(2)
        val changed = original.copy(name = "Edited")
        assertEquals(listOf(changed, late), ServerRecordEdits.save(listOf(original, late), original.copy(), changed))
    }
    @Test fun `stale edits and deleted records cannot be resurrected`() {
        val original = server()
        assertThrows(IllegalStateException::class.java) { ServerRecordEdits.save(emptyList(), original, original.copy(name = "changed")) }
        assertThrows(IllegalStateException::class.java) { ServerRecordEdits.save(listOf(original.copy(port = 9999)), original, original) }
    }
    @Test fun `adding an id collision does not overwrite and deleting checks latest record`() {
        val s = server()
        assertThrows(IllegalStateException::class.java) { ServerRecordEdits.save(listOf(s), null, s) }
        assertThrows(IllegalStateException::class.java) { ServerRecordEdits.delete(listOf(s.copy(adminToken = "b".repeat(32))), s) }
        assertEquals(listOf(server(2)), ServerRecordEdits.delete(listOf(s, server(2)), s.copy()))
    }
    @Test fun `valid encrypted TLS connection passes validation`() {
        assertNull(connectionValidation(server()))
        assertEquals(ConnectionValidation.TLS, connectionValidation(server().copy(useTls = false)))
        assertEquals(ConnectionValidation.FINGERPRINT, connectionValidation(server().copy(fingerprint = "")))
        assertEquals(ConnectionValidation.SAME_TOKENS, connectionValidation(server().copy(adminToken = "r".repeat(32))))
    }
    @Test fun `ports and thresholds fail visibly instead of silently clamping`() {
        assertEquals(ConnectionValidation.PORT, connectionValidation(server().copy(port = 0)))
        assertEquals(ConnectionValidation.PORT, connectionValidation(server().copy(port = 65536)))
        assertEquals(ConnectionValidation.THRESHOLD, connectionValidation(server().copy(cpuAlert = 101)))
        val draft = ServerConnectionDraft.from(server()).copy(port = "", cpuAlert = "")
        assertEquals(0, draft.server().port)
        assertEquals(0, draft.server().cpuAlert)
    }
    @Test fun `RAM draft survives rebinding after rotation without rereading over edits`() {
        val model = ServerEditorViewModel()
        val original = server()
        val loaded = Prefs.ServerLoadResult(mutableListOf(original))
        model.begin(1, loaded)
        model.draft = model.draft!!.copy(name = "Unsaved", token = "s".repeat(32))
        model.begin(1, loaded)
        assertEquals("Unsaved", model.draft!!.name)
        assertTrue(model.dirty)
        assertEquals(original, model.original)
        model.clear()
        assertNull(model.draft)
        assertNull(model.original)
    }
    @Test fun `empty and failed loads are distinct and missing edits are not additions`() {
        val model = ServerEditorViewModel()
        model.begin(null, Prefs.ServerLoadResult(mutableListOf(), IllegalStateException("bad storage")))
        assertTrue(model.blocked)
        model.clear()
        model.begin(7, Prefs.ServerLoadResult(mutableListOf()))
        assertTrue(model.missing)
        model.clear()
        model.begin(null, Prefs.ServerLoadResult(mutableListOf()))
        assertFalse(model.blocked)
        assertFalse(model.missing)
        assertFalse(model.dirty)
    }
    @Test fun `new session does not reuse a discarded credential draft`() {
        val model = ServerEditorViewModel()
        val loaded = Prefs.ServerLoadResult(mutableListOf<ServerConfig>())
        model.begin(null, loaded)
        model.draft = model.draft!!.copy(token = "secret")
        assertFalse(model.draft.toString().contains("secret"))
        model.clear()
        model.begin(null, loaded)
        assertEquals("", model.draft!!.token)
        assertFalse(model.dirty)
    }
    @Test fun `credential draft stays in RAM and dialog obeys screenshot preference`() {
        val dir = File("src/main/java/org/didban/monitor").takeIf { it.isDirectory }
            ?: File("app/src/main/java/org/didban/monitor")
        val source = File(dir, "CommandServerEditor.kt").readText()
        assertFalse(source.contains("rememberSaveable {"))
        assertFalse(source.contains("SavedStateHandle("))
        assertTrue(source.contains("securePolicy = screenshotDialogPolicy(protectScreenshots)"))
        assertTrue(source.contains("SecureWindowEffect()"))
        assertTrue(source.contains("model.dirty"))
        val shell = File(dir, "CommandShell.kt").readText()
        assertTrue(shell.contains("contentStateHolder.SaveableStateProvider(route.key)"))
        assertFalse(shell.contains("-> CommandOverviewScreen("))
        assertFalse(shell.contains("-> CommandFleetScreen("))
        assertFalse(shell.contains("-> CommandServerDossierScreen("))
    }
}
