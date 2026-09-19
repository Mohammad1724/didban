package org.didban.monitor

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w400dp-h1000dp")
@LooperMode(LooperMode.Mode.PAUSED)
class ScannerPresetUiTest {
    @get:Rule val compose = createComposeRule()
    private val copy = CommandCopy.forLanguage("fa")
    private fun field(label: String): SemanticsNodeInteraction {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(label, substring = true) and hasSetTextAction())
        return compose.onNode(hasText(label, substring = true) and hasSetTextAction())
    }
    private fun click(text: String) {
        compose.onNode(hasText(text) and hasClickAction() and !hasSetTextAction()).performScrollTo().performClick()
    }

    @Test fun `Cloudflare starts from ready ranges with no manual input`() {
        var scanned = emptyList<CfCandidate>()
        compose.setContent { CommandTheme("dark", "fa") {
            CommandCfScannerScreen(copy, {}, scan = { plan, _, _ -> scanned = plan; emptyList() })
        } }
        compose.runOnIdle { assertTrue(scanned.isEmpty()) }
        click(copy.cfStart)
        compose.runOnIdle {
            assertEquals(256, scanned.size)
            assertTrue(scanned.all { Cidr.inAny(it.ip, CloudflareRanges.V4) })
        }
    }

    @Test fun `range selection and count are read live at Start`() {
        var scanned = emptyList<CfCandidate>()
        compose.setContent { CommandTheme("dark", "fa") {
            CommandCfScannerScreen(copy, {}, scan = { plan, _, _ -> scanned = plan; emptyList() })
        } }
        click(copy.scannerShowList)
        click(copy.scannerClearSelection)
        compose.onNodeWithText("104.16.0.0/13").performScrollTo().assertIsOff().performClick().assertIsOn()
        click(copy.scannerHideList)
        field(copy.cfCount).performTextReplacement("5")
        click(copy.cfStart)
        compose.runOnIdle {
            assertEquals(5, scanned.size)
            assertTrue(scanned.all { Cidr.inAny(it.ip, listOf("104.16.0.0/13")) })
        }
    }

    @Test fun `clearing all ranges never silently scans the default fleet`() {
        var started = false
        compose.setContent { CommandTheme("dark", "fa") {
            CommandCfScannerScreen(copy, {}, scan = { _, _, _ -> started = true; emptyList() })
        } }
        click(copy.scannerShowList); click(copy.scannerClearSelection); click(copy.scannerHideList)
        click(copy.cfStart)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(copy.scannerChooseRange))
        compose.onNodeWithText(copy.scannerChooseRange).assertExists()
        compose.runOnIdle { assertFalse(started) }
    }

    @Test fun `SNI presets scan without entering a domain and do not run on opening`() {
        val scanned = mutableListOf<Pair<String, Int>>()
        compose.setContent { CommandTheme("dark", "fa") {
            CommandRealitySniScreen(copy, {}, probe = { host, port -> scanned += host to port; scannerFixture(host, port) })
        } }
        compose.runOnIdle { assertTrue(scanned.isEmpty()) }
        click(copy.realityCheckAll)
        compose.runOnIdle {
            assertEquals(50, scanned.size)
            assertEquals(ScannerCatalog.sniPlan(ScannerCatalog.Group.ALL, 50, 443).toSet(), scanned.toSet())
        }
    }

    @Test fun `SNI category count and port updates reach the probe`() {
        val scanned = mutableListOf<Pair<String, Int>>()
        compose.setContent { CommandTheme("dark", "fa") {
            CommandRealitySniScreen(copy, {}, probe = { host, port -> scanned += host to port; scannerFixture(host, port) })
        } }
        click("${copy.scannerCategory}: ${copy.scannerCategoryAll}")
        val group = ScannerCatalog.Group.DEVELOPMENT
        compose.onNodeWithText("${group.label(copy)} (${ScannerCatalog.domains(group).size})").performClick()
        field(copy.scannerLimit).performTextReplacement("3")
        field(copy.port).performTextReplacement("8443")
        click(copy.realityCheckAll)
        compose.runOnIdle { assertEquals(ScannerCatalog.sniPlan(group, 3, 8443).toSet(), scanned.toSet()) }
    }

    @Test fun `manual SNI list remains optional and handles duplicates invalid lines and different ports`() {
        val scanned = mutableListOf<Pair<String, Int>>()
        compose.setContent { CommandTheme("dark", "fa") {
            CommandRealitySniScreen(copy, {}, probe = { host, port -> scanned += host to port; scannerFixture(host, port) })
        } }
        click(copy.scannerManual)
        field(copy.scannerManual).performTextInput("example.com\nexample.com\nexample.com:8443\nnot-a-domain")
        click(copy.realityCheckAll)
        compose.runOnIdle { assertEquals(setOf("example.com" to 443, "example.com" to 8443), scanned.toSet()) }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(copy.realityResults))
        compose.onNodeWithText(copy.realityResults).assertExists()
    }

    @Test fun `SNI stop cancels workers and allows another run`() {
        var started = 0
        compose.setContent { CommandTheme("dark", "fa") {
            CommandRealitySniScreen(copy, {}, probe = { _, _ -> started++; awaitCancellation() })
        } }
        click(copy.realityCheckAll)
        click(copy.stop)
        compose.onNodeWithText(copy.realityCheckAll).assertIsEnabled()
        compose.runOnIdle { assertTrue(started in 1..4) }
    }
}
