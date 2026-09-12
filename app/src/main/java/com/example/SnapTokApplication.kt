package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log

class SnapTokApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Global top-level uncaught exception handler
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("SnapTokApplication", "FATAL EXCEPTION in thread: ${thread.name}", throwable)
            // Delegate or prevent silent crash
            defaultHandler?.uncaughtException(thread, throwable)
        }

        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channel = NotificationChannel(
                    "snaptok_download_channel",
                    "SnapTok Downloads",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows download progress and status for SnapTok"
                    setSound(null, null)
                    enableVibration(false)
                }
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                manager?.createNotificationChannel(channel)
            } catch (t: Throwable) {
                Log.e("SnapTokApplication", "Failed to create notification channel", t)
            }
        }
    }
}
