package org.didban.monitor

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** State-contract coverage for network tools: loading, empty, retry and cancel. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w400dp-h900dp")
@LooperMode(LooperMode.Mode.PAUSED)
class NetworkToolsUiTest {
    @get:Rule val compose = createComposeRule()
    private val copy = CommandCopy.forLanguage("fa")

    /** A run the engine would return; the screen renders it, it does not invent it. */
    private fun cannedAssessment(verdict: DpiConclusion = DpiConclusion.NO_FILTERING_SEEN) = DpiAssessment(
        verdict = verdict,
        confidence = Confidence.MEDIUM,
        summary = "No filtering was observed on this path for example.com:443.",
        evidence = listOf(Evidence("handshake", "TLS completed in 120ms")),
        limitations = listOf("Only the path from this device, on this operator, at this moment was measured."),
        nextSteps = listOf("If the real client still fails, check the transport, the SNI and the server logs.")
    )

    private fun openScreen(
        runner: NetworkToolsRunner = RecordingNetworkToolsRunner(),
        dpiRun: suspend (DpiRun.Config) -> DpiAssessment = { cannedAssessment() }
    ) {
        compose.setContent {
            CommandTheme(themeMode = "dark", language = "fa") {
                CommandNetworkToolsScreen(copy, null, {}, runner, dpiRun)
            }
        }
    }

    private fun enterHost() {
        compose.onNode(hasText(copy.netHostDomain, substring = true) and hasSetTextAction())
            .performTextInput("example.com")
    }

    private fun runButton(mode: String = copy.netModeDpi) =
        compose.onNodeWithText(copy.netRunMode.replace("%1", mode))

    @Test
    fun `empty port scan has an explicit empty state and retry action`() {
        val runner = RecordingNetworkToolsRunner(portResults = emptyList())
        openScreen(runner)
        compose.onNodeWithText(copy.netModePorts).performClick()
        enterHost()
        runButton(copy.netModePorts).performClick()

        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(copy.netNoOpenPorts).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(copy.retry).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(2, runner.scanCalls) }
    }

    @Test
    fun `offline failure exposes retry and a successful retry replaces the error`() {
        var failNext = true
        var calls = 0
        openScreen(dpiRun = {
            calls++
            if (failNext) {
                failNext = false
                error("offline")
            }
            cannedAssessment()
        })
        enterHost()
        runButton().performClick()

        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("offline").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(copy.retry).performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(copy.netVerdictNoFilteringSeen).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(copy.operationFailed).assertDoesNotExist()
        assertEquals(2, calls)
    }

    @Test
    fun `the verdict arrives with its evidence, its limits and a next step`() {
        openScreen(dpiRun = { cannedAssessment(DpiConclusion.FILTERED_ADDRESS) })
        enterHost()
        runButton().performClick()

        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(copy.netVerdictFilteredAddress).fetchSemanticsNodes().isNotEmpty()
        }
        // Scrolled to rather than assumed visible: the card sits below the
        // verdict in a lazy column, so on a phone the lower sections are off-screen.
        compose.onNodeWithText(copy.netDpiEvidence).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(copy.netDpiLimitations).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(copy.netDpiNextSteps).performScrollTo().assertIsDisplayed()
        // The honesty check: a "filtered" verdict still states the confidence
        // it was reached with, and lists what this run did not check.
        compose.onNodeWithText(copy.netConfidenceMedium).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Only the path from this device", substring = true)
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `cancel stops an in-flight operation without converting cancellation into an error`() {
        var started = false
        var cancelled = false
        openScreen(dpiRun = {
            started = true
            try {
                awaitCancellation()
            } finally {
                cancelled = true
            }
        })
        enterHost()
        runButton().performClick()
        compose.waitUntil(10_000) { started }
        assertTrue(compose.onAllNodesWithText(copy.waitingForData).fetchSemanticsNodes().isNotEmpty())

        compose.onNodeWithText(copy.stop).performClick()
        compose.waitUntil(10_000) { cancelled }
        compose.onNodeWithText(copy.stop).assertDoesNotExist()
        compose.onNodeWithText(copy.netRunMode.replace("%1", copy.netModeDpi)).assertIsDisplayed()
        compose.onNodeWithText(copy.operationFailed).assertDoesNotExist()
    }

    private class RecordingNetworkToolsRunner(
        private val portResults: List<PortScanResult> = emptyList()
    ) : NetworkToolsRunner {
        var scanCalls = 0

        override suspend fun scanPorts(host: String, ports: List<Int>, onResult: (PortScanResult) -> Unit) {
            scanCalls++
            portResults.forEach(onResult)
        }

        override suspend fun inspectCertificate(host: String, port: Int): SslCertInfo = error("unused")
    }
}
