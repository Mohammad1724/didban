package org.didban.monitor

import android.app.Application
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w400dp-h900dp")
@LooperMode(LooperMode.Mode.PAUSED)
class CommandRedesignUiTest {
    @get:Rule val compose = createComposeRule()
    private val copy = CommandCopy.forLanguage("fa")
    @Before fun reset() {
        RuntimeEnvironment.getApplication().getSharedPreferences("didban", Context.MODE_PRIVATE).edit().clear().commit()
    }
    private fun app() {
        compose.setContent { CommandCenterApp(mutableStateOf(null)) {
            Prefs.ServerLoadResult(mutableListOf(ServerConfig(71, "UI Node", "node.example")))
        } }
    }
    @Test fun `default is light and existing explicit choices survive`() {
        val context = RuntimeEnvironment.getApplication()
        assertEquals("light", Prefs.getThemeMode(context))
        listOf("dark", "auto", "light").forEach { Prefs.setThemeMode(context, it); assertEquals(it, Prefs.getThemeMode(context)) }
    }
    @Test fun `launcher lists network tools then utilities without a workspace menu`() {
        app()
        compose.onNodeWithTag("primary-tools").assertIsSelected()
        compose.onAllNodesWithText(copy.uiTools).assertCountEquals(2) // page title + selected bottom navigation item
        compose.onNodeWithText(copy.networkTools).assertIsDisplayed() // first section header
        compose.onNodeWithTag("workbench-tile-network-reachability").assertIsDisplayed()
        compose.onNodeWithTag("workbench-launcher")
            .performScrollToNode(hasText(copy.shareTitle))
        compose.onNodeWithText(copy.toolsOtherHeader).assertIsDisplayed()
        compose.onNodeWithText(copy.shareTitle).assertIsDisplayed()
        compose.onNodeWithTag("primary-settings").performClick().assertIsSelected()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(copy.vault))
        compose.onNodeWithText(copy.vault).assertIsDisplayed()
    }
    @Test fun `launcher sections list network tools before utility tools`() {
        // One page, two sections: network diagnostics first, then utilities.
        val (network, utilities) = workbenchLauncherSections(copy)
        assertEquals(network.map { it.key } + utilities.map { it.key },
            networkLauncherItemKeys + workbenchUtilityRoutes.map { it.key })
    }

    @Test fun `launcher renders network tiles above utility tiles`() {
        app()
        val network = compose.onNodeWithTag("workbench-tile-network-reachability").getUnclippedBoundsInRoot()
        compose.onNodeWithTag("workbench-launcher").performScrollToNode(hasText(copy.shareTitle))
        val utility = compose.onNodeWithTag("workbench-tile-share").getUnclippedBoundsInRoot()
        assertTrue(network.top < utility.top)
    }

    @Test
    @Config(qualifiers = "w320dp-h900dp")
    fun `network tools uses the shared icon launcher at narrow width`() {
        app()
        compose.onNodeWithTag("primary-tools").performClick()
        compose.onNodeWithText(copy.networkTools).performClick()
        compose.onAllNodesWithText(copy.networkTools).assertCountEquals(1)
        compose.onNodeWithTag("workbench-tile-network-reachability").assertIsDisplayed()
        compose.onNodeWithText(copy.netQNetworkLayer).assertIsDisplayed()
    }

    @Test fun `details replace fleet rather than opening a sheet and retain scoped tools`() {
        app()
        compose.onNodeWithTag("primary-servers").performClick()
        compose.onNodeWithText("UI Node").performScrollTo().performClick()
        compose.onNodeWithText(copy.fleetDetails).assertIsDisplayed()
        compose.onNodeWithText(copy.serversSearchPlaceholder).assertDoesNotExist()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(copy.docker))
        compose.onNodeWithText(copy.docker).assertIsDisplayed()
        compose.onNodeWithContentDescription(copy.back).performClick()
        compose.onNodeWithText(copy.uiMyServers).assertExists()
    }
    @Test fun `embedded editor intercepts hardware Back and preserves the draft on cancel`() {
        lateinit var activity: ComponentActivity
        var closed = false
        compose.setContent {
            activity = LocalContext.current.findActivity() as ComponentActivity
            CommandTheme("light", "fa") { CommandServerEditor(copy, null, {}, { closed = true }, embedded = true) }
        }
        compose.onNodeWithText(copy.fleetName).performScrollTo().performTextInput("Keep me")
        compose.runOnIdle { activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText(copy.fleetDiscardTitle).assertIsDisplayed()
        compose.onNodeWithText(copy.cancel).performClick()
        compose.onNodeWithText(copy.fleetName).assertTextContains("Keep me")
        compose.runOnIdle { assertFalse(closed); activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText(copy.fleetDiscard).performClick()
        compose.runOnIdle { assertTrue(closed) }
    }
    @Test fun `glass surface supplies readable default content color in dark mode`() {
        var color = androidx.compose.ui.graphics.Color.Transparent
        compose.setContent { CommandTheme("dark", "fa") {
            CommandLayerSurface {
                color = androidx.compose.material3.LocalContentColor.current
                androidx.compose.material3.Text("Glass content")
            }
        } }
        compose.onNodeWithText("Glass content").assertIsDisplayed()
        compose.runOnIdle { assertEquals(CommandDarkPalette.textPrimary, color) }
    }
}
