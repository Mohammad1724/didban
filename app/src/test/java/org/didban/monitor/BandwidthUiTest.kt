package org.didban.monitor

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w400dp-h900dp")
@LooperMode(LooperMode.Mode.PAUSED)
class BandwidthUiTest {
    @get:Rule val compose = createComposeRule()
    private val copy = CommandCopy.forLanguage("fa")
    private val first = ServerConfig(1, "Node A", "a.example")

    @Test fun `benchmark uses the selected connection and latest callback input`() {
        val selected = mutableStateOf(first)
        var measured: Long? = null
        compose.setContent {
            CommandTheme("dark", "fa") {
                CommandBandwidthScreen(copy, selected.value, {}, {}, benchmark = { server, _ ->
                    measured = server.id
                    BenchmarkResult(20f, 10f, 15, 1, 0)
                })
            }
        }
        compose.runOnIdle { selected.value = first.copy(id = 2, name = "Node B", host = "b.example") }
        compose.onNodeWithText(copy.run).performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(copy.bandwidthResult))
        compose.onNodeWithText(copy.bandwidthResult).assertIsDisplayed()
        compose.runOnIdle { assertEquals(2L, measured) }
    }

    @Test fun `cancel releases the run and does not leave a persisted busy state`() {
        var cancelled = false
        compose.setContent {
            CommandTheme("dark", "fa") {
                CommandBandwidthScreen(copy, first, {}, {}, benchmark = { _, _ ->
                    try { awaitCancellation() } finally { cancelled = true }
                })
            }
        }
        compose.onNodeWithText(copy.run).performClick()
        compose.onNodeWithText(copy.cancel).performClick()
        compose.onNodeWithText(copy.run).assertIsEnabled()
        compose.onNodeWithText(copy.operationFailed).assertDoesNotExist()
        compose.runOnIdle { assertTrue(cancelled) }
    }

    @Test fun `404 displays endpoint guidance rather than an opaque network error`() {
        compose.setContent {
            CommandTheme("dark", "fa") {
                CommandBandwidthScreen(copy, first, {}, {}, benchmark = { _, _ ->
                    throw BenchmarkFailure(BenchmarkStage.DOWNLOAD, ApiException("HTTP 404", 404))
                })
            }
        }
        compose.onNodeWithText(copy.run).performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(copy.operationFailed))
        compose.onNodeWithText("${copy.download}: ${copy.bandwidthUnsupported}").assertExists()
        compose.onNodeWithText(copy.run).assertIsEnabled()
    }

    @Test fun `restoring the screen cancels the old run instead of restoring a stuck busy flag`() {
        var cancelled = false
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            CommandTheme("dark", "fa") {
                CommandBandwidthScreen(copy, first, {}, {}, benchmark = { _, _ ->
                    try { awaitCancellation() } finally { cancelled = true }
                })
            }
        }
        compose.onNodeWithText(copy.run).performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText(copy.run).assertIsEnabled()
        compose.runOnIdle { assertTrue(cancelled) }
    }

}
