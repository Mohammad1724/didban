package org.didban.monitor

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Check-Host state coverage: local validation, loading and retry without a fleet server. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w400dp-h900dp")
@LooperMode(LooperMode.Mode.PAUSED)
class CheckHostUiTest {
    @get:Rule val compose = createComposeRule()
    private val copy = CommandCopy.forLanguage("fa")

    private fun openScreen(
        startCheck: suspend (String, String, Int) -> Pair<String, List<CheckHostNode>>,
        pollResults: suspend (String, String, List<CheckHostNode>) -> Boolean = { _, _, _ -> true }
    ) {
        compose.setContent {
            CommandTheme(themeMode = "dark", language = "fa") {
                CommandCheckHostScreen(copy, {}, startCheck, pollResults)
            }
        }
    }

    private fun targetField() =
        compose.onNode(hasText(copy.netHostDomain, substring = true) and hasSetTextAction())

    @Test
    fun `empty target is rejected locally without calling Check-Host`() {
        var calls = 0
        openScreen(
            startCheck = { _, _, _ ->
                calls++
                error("must not be called")
            }
        )

        compose.onNodeWithText(copy.test).performClick()
        compose.onNodeWithText(copy.netNoHost).performScrollTo().assertIsDisplayed()
        assertEquals(0, calls)
    }

    @Test
    fun `offline Check-Host error exposes retry and a retry can render nodes`() {
        var failNext = true
        val node = CheckHostNode("node-1", "DE", "Germany", "Frankfurt", "🇩🇪")
        openScreen(
            startCheck = { _, _, _ ->
                if (failNext) {
                    failNext = false
                    error("offline")
                }
                "request-1" to listOf(node)
            },
            pollResults = { _, _, nodes ->
                nodes.forEach {
                    it.state = 1
                    it.result = CheckHostResult(
                        CheckHostResultKind.PING_SUMMARY,
                        first = "1",
                        second = "1",
                        milliseconds = 12
                    )
                }
                true
            }
        )

        targetField().performTextInput("example.com")
        compose.onNodeWithText(copy.test).performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("offline").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(copy.retry).performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(copy.online).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(copy.operationFailed).assertDoesNotExist()
    }
}
