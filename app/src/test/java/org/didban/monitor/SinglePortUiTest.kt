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

/** Single-Port guidance: the screen must explain the concept before any tap. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class SinglePortUiTest {
    @get:Rule val compose = createComposeRule()
    private val copy = CommandCopy.forLanguage("fa")

    @Before fun resetPreferences() {
        RuntimeEnvironment.getApplication().getSharedPreferences("didban", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun openScreen() {
        compose.setContent {
            CommandTheme(themeMode = "dark", language = "fa") {
                CommandSinglePortScreen(copy, {})
            }
        }
    }

    private fun scrollListTo(text: String) {
        // hasScrollToIndexAction matches only the LazyColumn, not the artifact row.
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text))
    }

    @Test fun `single port explains itself without any tap`() {
        openScreen()
        scrollListTo(copy.spIntroTitle)
        compose.onNodeWithText(copy.spIntroTitle).assertExists()
        compose.onNodeWithText(copy.spIntroBody).assertExists()
        scrollListTo(copy.spFormHint)
        compose.onNodeWithText(copy.spFormHint).assertExists()
        scrollListTo(copy.spArtifactHint)
        compose.onNodeWithText(copy.spArtifactHint).assertExists()
        compose.onNodeWithText(copy.spGenerateVerb + " HAProxy").assertExists()
        scrollListTo(copy.spNextHint)
        compose.onNodeWithText(copy.spNextHint).assertExists()
    }

    @Test fun `generating haproxy shows the config`() {
        openScreen()
        val label = copy.spGenerateVerb + " HAProxy"
        scrollListTo(label)
        compose.onNode(hasText(label) and hasClickAction()).performScrollTo().performClick()
        scrollListTo(copy.wtGeneratedArtifact)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("backend_panel", substring = true))
    }
}
