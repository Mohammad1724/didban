package org.didban.monitor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.SecureFlagPolicy

/** Observe the preference in Activities AND dialogs; changes release existing flags. */
@Composable
internal fun rememberScreenshotProtection(): Boolean {
    val context = LocalContext.current.applicationContext
    var protected by remember(context) { mutableStateOf(Prefs.isScreenshotProtectionEnabled(context)) }
    DisposableEffect(context) {
        val unsubscribe = Prefs.observeScreenshotProtection(context) { protected = it }
        onDispose { unsubscribe() }
    }
    return protected
}

/** Explicit SecureOff overrides inherited flags for this masked-credential form. */
internal fun screenshotDialogPolicy(protected: Boolean): SecureFlagPolicy =
    if (protected) SecureFlagPolicy.SecureOn else SecureFlagPolicy.SecureOff
