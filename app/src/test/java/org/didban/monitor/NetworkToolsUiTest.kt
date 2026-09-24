package org.didban.monitor

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
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

    private fun openScreen(runner: NetworkToolsRunner) {
        compose.setContent {
            CommandTheme(themeMode = "dark", language = "fa") {
                CommandNetworkToolsScreen(copy, null, {}, runner)
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
        val runner = RecordingNetworkToolsRunner(failNextDiagnosis = true)
        openScreen(runner)
        enterHost()
        runButton().performClick()

        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("offline").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(copy.retry).performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("healthy").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(copy.operationFailed).assertDoesNotExist()
        assertEquals(2, runner.diagnoseCalls)
    }

    @Test
    fun `cancel stops an in-flight operation without converting cancellation into an error`() {
        val runner = BlockingNetworkToolsRunner()
        openScreen(runner)
        enterHost()
        runButton().performClick()
        compose.waitUntil(10_000) { runner.started }
        compose.onNodeWithText(copy.waitingForData).assertIsDisplayed()

        compose.onNodeWithText(copy.stop).performClick()
        compose.waitUntil(10_000) { runner.cancelled }
        compose.onNodeWithText(copy.stop).assertDoesNotExist()
        compose.onNodeWithText(copy.netRunMode.replace("%1", copy.netModeDpi)).assertIsDisplayed()
        compose.onNodeWithText(copy.operationFailed).assertDoesNotExist()
    }

    private class RecordingNetworkToolsRunner(
        private val portResults: List<PortScanResult> = emptyList(),
        private var failNextDiagnosis: Boolean = false
    ) : NetworkToolsRunner {
        var diagnoseCalls = 0
        var scanCalls = 0

        override suspend fun diagnose(host: String, port: Int): CensorshipDiagnosticResult {
            diagnoseCalls++
            if (failNextDiagnosis) {
                failNextDiagnosis = false
                error("offline")
            }
            return CensorshipDiagnosticResult(host, port, true, true, false, "healthy", 12, "details")
        }

        override suspend fun scanPorts(host: String, ports: List<Int>, onResult: (PortScanResult) -> Unit) {
            scanCalls++
            portResults.forEach(onResult)
        }

        override suspend fun inspectCertificate(host: String, port: Int): SslCertInfo = error("unused")

        override suspend fun lookupGeoDns(target: String): GeoIpData = error("unused")
    }

    private class BlockingNetworkToolsRunner : NetworkToolsRunner {
        var started = false
        var cancelled = false

        override suspend fun diagnose(host: String, port: Int): CensorshipDiagnosticResult {
            started = true
            try {
                awaitCancellation()
            } finally {
                cancelled = true
            }
            error("unreachable")
        }

        override suspend fun scanPorts(host: String, ports: List<Int>, onResult: (PortScanResult) -> Unit) =
            error("unused")

        override suspend fun inspectCertificate(host: String, port: Int): SslCertInfo = error("unused")

        override suspend fun lookupGeoDns(target: String): GeoIpData = error("unused")
    }
}
