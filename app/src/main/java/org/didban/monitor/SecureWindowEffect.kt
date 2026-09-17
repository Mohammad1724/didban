package org.didban.monitor

import android.app.Activity
import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import java.util.WeakHashMap

/** Prevents screenshots, screen recording, and sensitive Recent Apps previews. */
@Composable
fun SecureWindowEffect() {
    val view = LocalView.current
    DisposableEffect(view) {
        val activity = view.context.findActivity()
        if (activity != null) SecureWindowRegistry.acquire(activity)
        onDispose { if (activity != null) SecureWindowRegistry.release(activity) }
    }
}

private object SecureWindowRegistry {
    private val references = WeakHashMap<Activity, Int>()

    @Synchronized
    fun acquire(activity: Activity) {
        val count = references[activity] ?: 0
        references[activity] = count + 1
        if (count == 0) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activity.setRecentsScreenshotEnabled(false)
            }
        }
    }

    @Synchronized
    fun release(activity: Activity) {
        val remaining = (references[activity] ?: 1) - 1
        if (remaining > 0) {
            references[activity] = remaining
        } else {
            references.remove(activity)
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activity.setRecentsScreenshotEnabled(true)
            }
        }
    }
}
