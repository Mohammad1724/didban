package org.didban.monitor

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
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
class MonitoringUiTest {
    @get:Rule val compose = createComposeRule()
    private val copy = CommandCopy.forLanguage("fa")
    private var state by mutableStateOf(MonitoringStatus())
    private var pending by mutableStateOf(false)
    private var starts = 0
    private var stops = 0
    private var settings = 0
    private fun open(notifications: Boolean = true) {
        compose.setContent { CommandTheme("light", "fa") {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                CommandMonitoringCard(copy, state, 2, notifications, pending,
                    { starts++ }, { stops++ }, { settings++ })
            }
        } }
    }
    @Test fun `opening never starts and pressing start does not fabricate a running result`() {
        open()
        compose.onNodeWithText(copy.monitorOff).assertExists()
        compose.runOnIdle { assertEquals(0, starts) }
        compose.onNodeWithText(copy.monitorStart).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, starts) }
        compose.onNodeWithText(copy.monitorOff).assertExists()
        compose.runOnIdle { state = MonitoringStatus(MonitoringPhase.STARTING) }
        compose.onNode(hasText(copy.monitorStarting) and hasClickAction()).assertIsNotEnabled()
        compose.onNodeWithText(copy.monitorOn).assertDoesNotExist()
        compose.runOnIdle { state = MonitoringStatus(MonitoringPhase.RUNNING, true) }
        compose.onNodeWithText(copy.monitorOn).assertExists()
    }
    @Test fun `stop dispatches action and waits for service state`() {
        state = MonitoringStatus(MonitoringPhase.RUNNING, true); open()
        compose.onNodeWithText(copy.monitorStop).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, stops) }
        compose.onNodeWithText(copy.monitorOn).assertExists()
        compose.runOnIdle { state = MonitoringStatus() }
        compose.onNodeWithText(copy.monitorOff).assertExists()
    }
    @Test fun `notification denial shows warning and settings without hiding start`() {
        open(notifications = false)
        compose.onNodeWithText(copy.monitorStart).performScrollTo().assertIsEnabled()
        compose.onNodeWithText(copy.monitorPermission).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(copy.monitorPermissionSettings).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, settings) }
    }
    @Test fun `failure is explained and allows retry rather than success`() {
        state = MonitoringStatus(MonitoringPhase.FAILED, failure = MonitoringFailure.START); open()
        compose.onNodeWithText(copy.monitorStartError).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(copy.monitorStart).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, starts) }
        compose.onNodeWithText(copy.monitorOn).assertDoesNotExist()
    }
    @Test fun `permission request blocks duplicate start while pending`() {
        pending = true; open(false)
        compose.onNode(hasText(copy.monitorPermissionPending) and hasClickAction()).assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, starts) }
    }
}
