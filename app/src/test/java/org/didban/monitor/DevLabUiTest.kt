package org.didban.monitor

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Developer Lab guidance: Generator must explain itself, Subnet must accept domains. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class DevLabUiTest {
    @get:Rule val compose = createComposeRule()
    private val copy = CommandCopy.forLanguage("fa")

    @Before fun resetPreferences() {
        RuntimeEnvironment.getApplication().getSharedPreferences("didban", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun openLab() {
        compose.setContent {
            CommandTheme(themeMode = "dark", language = "fa") {
                CommandDeveloperLabScreen(copy, {})
            }
        }
    }

    private fun selectTool(name: String) {
        // The chip row scrolls horizontally; an off-screen click is a silent no-op.
        compose.onNode(hasText(name) and hasClickAction()).performScrollTo().performClick()
    }

    private fun scrollListTo(text: String) {
        // hasScrollToIndexAction matches only the LazyColumn, not the chip row.
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text))
    }

    private fun runTool(name: String) {
        val label = copy.runVerb + " " + name
        scrollListTo(label)
        compose.onNode(hasText(label) and hasClickAction()).performScrollTo().performClick()
    }

    @Test fun `generator explains its purpose`() {
        openLab()
        selectTool(copy.wtToolGenerator)
        scrollListTo(copy.genTitle)
        compose.onNodeWithText(copy.genTitle).assertExists()
        compose.onNodeWithText(copy.devLabBody).assertExists()
        compose.onNodeWithText(copy.runVerb + " " + copy.wtToolGenerator).assertExists()
    }

    @Test fun `subnet accepts a domain and shows its range`() {
        openLab()
        selectTool(copy.wtToolSubnet)
        scrollListTo(copy.subnetHint)
        compose.onNodeWithText(copy.subnetHint).assertExists()
        compose.onNode(hasText(copy.uiInput) and hasSetTextAction()).performTextInput("localhost")
        runTool(copy.wtToolSubnet)
        // Compose the output card via its stable title, then wait for the async answer.
        scrollListTo(copy.outputTitle)
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("127.0.0.1", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun `subnet rejects a bad prefix with a localized error`() {
        openLab()
        selectTool(copy.wtToolSubnet)
        scrollListTo(copy.subnetHint)
        compose.onNode(hasText(copy.uiInput) and hasSetTextAction()).performTextInput("999.1.1.1/99")
        runTool(copy.wtToolSubnet)
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(copy.subnetErrPrefix).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
