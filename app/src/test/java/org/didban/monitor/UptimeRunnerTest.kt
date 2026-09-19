package org.didban.monitor

import android.app.Application
import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.net.ServerSocket

/** Real local TCP probes only; no internet or private user server is contacted. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class UptimeRunnerTest {
    private val context get() = RuntimeEnvironment.getApplication()
    @Before fun reset() {
        UptimeEngine.stop(); UptimeEngine.liveTargets.value = emptyList()
        context.getSharedPreferences("didban", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @After fun cleanUp() { UptimeEngine.stop(); UptimeEngine.liveTargets.value = emptyList() }

    @Test fun `scheduled checks repeat publish new observable snapshots respect pause and stop`() = runBlocking {
        ServerSocket(0, 20, java.net.InetAddress.getByName("127.0.0.1")).use { socket ->
            val original = UptimeTarget(901, "local fixture", "TCP", "127.0.0.1", socket.localPort, 5, allowPrivateNetwork = true)
            val paused = original.copy(id = 902, name = "paused", isPaused = true, heartbeats = mutableListOf(), incidents = mutableListOf())
            UptimeEngine.upsert(context, original); UptimeEngine.upsert(context, paused)
            UptimeEngine.start(context)
            val first = withTimeout(5_000) { UptimeEngine.liveTargets.first { it.first().heartbeats.isNotEmpty() } }
            assertEquals(1, first.first().lastStatus)
            assertNotSame(original, first.first())
            assertEquals(-1, original.lastStatus)
            assertTrue(first.last().heartbeats.isEmpty())
            withTimeout(10_000) { UptimeEngine.liveTargets.first { it.first().heartbeats.size >= 2 } }
            UptimeEngine.stop()
            val count = UptimeEngine.liveTargets.value.first().heartbeats.size
            delay(6_500)
            assertFalse(UptimeEngine.monitoring.value)
            assertEquals(count, UptimeEngine.liveTargets.value.first().heartbeats.size)
            assertTrue(UptimeEngine.liveTargets.value.last().heartbeats.isEmpty())
        }
    }
    @Test fun `manual check returns its result and paused toggle emits a new target`() = runBlocking {
        ServerSocket(0, 10, java.net.InetAddress.getByName("127.0.0.1")).use { socket ->
            val original = UptimeTarget(903, "fixture", "TCP", "127.0.0.1", socket.localPort, allowPrivateNetwork = true)
            UptimeEngine.upsert(context, original)
            assertEquals(1, UptimeEngine.checkNow(context, original).status)
            assertEquals(1, UptimeEngine.liveTargets.value.single().lastStatus)
            val before = UptimeEngine.liveTargets.value.single()
            UptimeEngine.togglePause(context, 903)
            assertNotSame(before, UptimeEngine.liveTargets.value.single())
            assertFalse(before.isPaused)
            assertTrue(UptimeEngine.liveTargets.value.single().isPaused)
        }
    }
    @Test fun `cancelled work cannot create a down heartbeat`() = runBlocking {
        val target = UptimeTarget(904, "never sent", "TCP", "127.0.0.1", 1, allowPrivateNetwork = true)
        val job = launch(start = CoroutineStart.LAZY) { UptimeEngine.checkTarget(target, context) }
        job.cancelAndJoin()
        assertEquals(-1, target.lastStatus)
        assertTrue(target.heartbeats.isEmpty()); assertTrue(target.incidents.isEmpty())
    }
}
