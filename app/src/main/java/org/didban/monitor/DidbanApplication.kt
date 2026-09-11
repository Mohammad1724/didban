package org.didban.monitor

import android.app.Application
import android.util.Log

class DidbanApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("DidbanCrash", "Uncaught exception in thread: ${thread.name}", throwable)
            try {
                getSharedPreferences("didban", MODE_PRIVATE)
                    .edit()
                    .putString("last_crash_msg", throwable.message ?: "Unknown crash")
                    .putString("last_crash_trace", throwable.stackTraceToString())
                    .apply()
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
