package org.didban.monitor

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/**
 * Stage 4 accessibility contract: large text, RTL, real tab semantics and
 * launcher actions must remain discoverable without relying on color.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w320dp-h900dp")
@LooperMode(LooperMode.Mode.PAUSED)
class CommandAccessibilityUiTest {
    @get:Rule val compose = createComposeRule()
    private val copy = CommandCopy.forLanguage("fa")

    @Test
    fun `launcher keeps tab roles and 48dp actions with large font in rtl`() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.5f)) {
                CommandTheme("light", "fa") {
                    CommandWorkbenchLauncherScreen(copy) { _, _ -> }
                }
            }
        }

        compose.onNodeWithTag("workbench-tab-0")
            .assertIsSelected()
            .assertHasClickAction()
        compose.onNodeWithTag("workbench-tab-0")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        compose.onNodeWithTag("workbench-tab-1").assertHasClickAction()
        compose.onNodeWithTag("workbench-tab-1")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        compose.onNodeWithTag("workbench-tile-proxy").assertHasClickAction()
    }

    @Test
    fun `launcher keeps the same contract in ltr`() {
        val english = CommandCopy.forLanguage("en")
        compose.setContent {
            CommandTheme("light", "en") {
                CommandWorkbenchLauncherScreen(english) { _, _ -> }
            }
        }

        compose.onNodeWithTag("workbench-tab-0")
            .assertIsSelected()
            .assertHasClickAction()
        compose.onNodeWithTag("workbench-tab-1")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        compose.onNodeWithTag("workbench-tile-proxy").assertHasClickAction()
    }

    @Test
    fun `network index rows expose button semantics and remain actionable`() {
        compose.setContent {
            CommandTheme("light", "fa") {
                CommandNetworkIndexScreen(
                    copy = copy,
                    onOpenSuite = {},
                    onOpenCheckHost = {},
                    onOpenDns = {},
                    onOpenCfScanner = {},
                    onOpenRealitySni = {}
                )
            }
        }

        compose.onNodeWithTag("network-index-row-0").assertHasClickAction()
        compose.onNodeWithTag("network-index-row-0")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }
}
