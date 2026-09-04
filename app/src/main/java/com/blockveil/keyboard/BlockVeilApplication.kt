package com.blockveil.keyboard

import android.app.Application

// Diagnostic-only: catches any otherwise-silent crash and shows the exact
// error on screen (via CrashDisplayActivity) instead of the system's generic
// "App has stopped" dialog looping with no useful detail. Nothing here is
// collected, logged remotely, or sent anywhere - it only runs on-device so a
// crash can actually be read and reported back.
class BlockVeilApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val trace = android.util.Log.getStackTraceString(throwable)
                val intent = android.content.Intent(this, CrashDisplayActivity::class.java)
                intent.putExtra(CrashDisplayActivity.EXTRA_TRACE, trace)
                intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
            } catch (e: Exception) {
                // If even showing the crash screen fails, fall through to default handling
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
