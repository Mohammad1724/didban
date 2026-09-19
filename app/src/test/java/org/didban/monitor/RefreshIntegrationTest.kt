package org.didban.monitor

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

class RefreshIntegrationTest {
    @Test fun `cancelling metrics closes the socket rather than waiting for read timeout`() = runBlocking {
        val received = CompletableDeferred<Unit>()
        val closed = CompletableDeferred<Boolean>()
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { listener ->
            val server = ServerConfig(1001, "test", "127.0.0.1", listener.localPort, useTls = false)
            val worker = thread(isDaemon = true, name = "metrics-cancellation-test") {
                try {
                    listener.accept().use { socket ->
                        socket.soTimeout = 5000
                        val reader = socket.getInputStream().bufferedReader()
                        while (!reader.readLine().isNullOrEmpty()) { /* consume HTTP headers */ }
                        received.complete(Unit)
                        // Intentionally never send a response. Cancellation must close the peer.
                        closed.complete(reader.read() == -1)
                    }
                } catch (e: Exception) {
                    received.completeExceptionally(e)
                    closed.completeExceptionally(e)
                }
            }
            val request = launch { ApiClient().metrics(server) }
            try {
                withTimeout(3000) { received.await() }
                request.cancelAndJoin()
                assertTrue(withTimeout(3000) { closed.await() })
            } finally {
                request.cancelAndJoin()
                HttpClientPool.evict(HttpClientPool.keyFor(server))
                worker.join(1000)
            }
        }
    }

    @Test fun `parallel refresh results do not overwrite another server in Repo`() = runBlocking {
        val before = Repo.states.value
        try {
            Repo.states.value = emptyMap()
            coroutineScope {
                repeat(200) { id ->
                    launch(Dispatchers.Default) { Repo.set(id.toLong(), error = "test result") }
                }
            }
            assertEquals(200, Repo.states.value.size)
        } finally {
            Repo.states.value = before
        }
    }
}
