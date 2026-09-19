package org.didban.monitor

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
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
class RadarNavigationUiTest {
    @get:Rule val compose = createComposeRule()
    private val copy = CommandCopy.forLanguage("fa")
    // Invalid/missing pins fail locally before any request; these tests need no live Agent.
    private val fleet = listOf(ServerConfig(1, "Node A", "node-a.example"), ServerConfig(2, "Node B", "node-b.example"))

    @Before fun resetPreferences() {
        RuntimeEnvironment.getApplication().getSharedPreferences("didban", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun openRadar(servers: List<ServerConfig> = fleet, wide: Boolean = false) {
        compose.setContent { CommandCenterApp(mutableStateOf(null)) { Prefs.ServerLoadResult(servers.toMutableList()) } }
        compose.onNodeWithTag("primary-monitoring").performClick()
        compose.onNodeWithText(copy.radar).performScrollTo().performClick()
        compose.onAllNodesWithText(copy.radar).assertCountEquals(1)
    }

    private fun chooseButton() = compose.onNode(hasText(copy.selectServer) and
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))

    @Test fun `select existing server stays in Radar instead of navigating to Servers`() {
        openRadar()
        chooseButton().performScrollTo().performClick()
        compose.onAllNodesWithText(copy.radar).assertCountEquals(1)
        compose.onNodeWithText("Node A").assertIsDisplayed().performClick()
        compose.onAllNodesWithText(copy.radar).assertCountEquals(1)
        compose.onNodeWithText(copy.addTarget).assertExists()
        compose.onNodeWithText(copy.addServer).assertDoesNotExist()
    }

    @Test fun `one existing server is automatically used by Radar`() {
        openRadar(fleet.take(1))
        chooseButton().assertDoesNotExist()
        compose.onNode(hasText("Node A") and hasClickAction()).assertIsDisplayed()
        compose.onNodeWithText(copy.addTarget).assertExists()
        compose.onNodeWithText(copy.addServer).assertDoesNotExist()
    }

    @Test fun `scope dropdown changes the agent without leaving Radar and back returns to origin`() {
        openRadar()
        chooseButton().performScrollTo().performClick()
        compose.onNodeWithText("Node A").performClick()
        compose.onNode(hasText("Node A") and hasClickAction()).performClick()
        compose.onNodeWithText("Node B").performClick()
        compose.onAllNodesWithText(copy.radar).assertCountEquals(1)
        compose.onNode(hasText("Node B") and hasClickAction()).assertIsDisplayed()
        compose.onAllNodesWithText("Node A").assertCountEquals(0)
        compose.onNodeWithContentDescription(copy.back).performClick()
        compose.onNodeWithTag("primary-monitoring").assertIsSelected()
        compose.onNodeWithText(copy.addMonitor).assertExists()
    }

    @Test fun `cancelling the picker does not navigate or open an editor`() {
        openRadar()
        chooseButton().performScrollTo().performClick()
        compose.onNodeWithText(copy.cancel).performClick()
        compose.onAllNodesWithText(copy.radar).assertCountEquals(1)
        chooseButton().assertExists()
        compose.onNodeWithText(copy.srvQuickConnectLabel).assertDoesNotExist()
    }

    @Test fun `no servers offers Add only after an explicit action in the picker`() {
        openRadar(emptyList())
        chooseButton().performScrollTo().performClick()
        compose.onAllNodesWithText(copy.radar).assertCountEquals(1)
        compose.onNodeWithText(copy.noServersBody).assertIsDisplayed()
        compose.onNodeWithText(copy.addServer).performClick()
        compose.onNodeWithText(copy.srvQuickConnectLabel).assertExists()
    }

    @Test fun `storage failure is shown as a retry not an invitation to overwrite connections`() {
        compose.setContent {
            CommandCenterApp(mutableStateOf(null)) {
                Prefs.ServerLoadResult(mutableListOf(), IllegalStateException("unavailable storage"))
            }
        }
        compose.onNodeWithTag("primary-monitoring").performClick()
        compose.onNodeWithText(copy.radar).performScrollTo().performClick()
        chooseButton().performScrollTo().performClick()
        compose.onNodeWithText(securityMessage("fa", SecurityMessage.SERVER_READ_FAILED)).assertIsDisplayed()
        compose.onNodeWithText(copy.retry).assertExists()
        compose.onNodeWithText(copy.addServer).assertDoesNotExist()
    }


    @Test @Config(qualifiers = "w1000dp-h900dp")
    fun `tablet scope selection also stays in Radar`() {
        openRadar(wide = true)
        chooseButton().performScrollTo().performClick()
        compose.onNodeWithText("Node A").performClick()
        compose.onNode(hasText("Node A") and hasClickAction()).performClick()
        compose.onNodeWithText("Node B").performClick()
        compose.onAllNodesWithText(copy.radar).assertCountEquals(1)
        compose.onNode(hasText("Node B") and hasClickAction()).assertIsDisplayed()
        compose.onNodeWithText(copy.addTarget).assertExists()
        compose.onNodeWithText(copy.addServer).assertDoesNotExist()
    }

}
