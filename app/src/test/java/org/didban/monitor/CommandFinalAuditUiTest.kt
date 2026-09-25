package org.didban.monitor

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/**
 * Final stage 4 smoke contract for the product-level owners called out in the
 * audit: DNS, Tunnel, server management and SFTP. These are local empty/error
 * states, so the checks do not depend on Cloudflare, SSH or an Agent.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w320dp-h900dp")
@LooperMode(LooperMode.Mode.PAUSED)
class CommandFinalAuditUiTest {
    @get:Rule val compose = createComposeRule()
    private val copy = CommandCopy.forLanguage("fa")

    @Before
    fun resetPreferences() {
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("didban", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun render(content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.5f)) {
                CommandTheme("light", "fa", content)
            }
        }
    }

    @Test
    fun `DNS empty state remains readable at 320dp and large font`() {
        render { CommandDnsManagerScreen(copy, {}) }
        compose.onNodeWithText(copy.dnsNoZonesYet).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `Tunnel editor keeps its empty contract at 320dp and large font`() {
        render { CommandTunnelEditorScreen(copy, {}) }
        compose.onNodeWithText(copy.tunEmptyBody).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `server management keeps its local empty contract at 320dp and large font`() {
        render { CommandManageServersScreen(copy, {}, {}) }
        compose.onNodeWithText(copy.noServersBody).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `SFTP keeps its no-server state at 320dp and large font`() {
        render { CommandSftpScreen(copy, null, {}, {}) }
        compose.onNodeWithText(copy.noServersBody).performScrollTo().assertIsDisplayed()
    }
}
