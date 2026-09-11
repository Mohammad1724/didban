package org.didban.monitor

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log

class DidbanApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("DidbanCrash", "Uncaught exception in thread: ${thread.name}", throwable)
            try {
                val sp = getSharedPreferences("didban", Context.MODE_PRIVATE)
                sp.edit()
                    .putString("last_crash_msg", throwable.message ?: "Unknown error")
                    .putString("last_crash_trace", throwable.stackTraceToString())
                    .commit()
            } catch (_: Throwable) {}

            try {
                val intent = Intent(applicationContext, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra("is_crash_launch", true)
                }
                startActivity(intent)
                android.os.Process.killProcess(android.os.Process.myPid())
                System.exit(10)
            } catch (_: Throwable) {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }
}
