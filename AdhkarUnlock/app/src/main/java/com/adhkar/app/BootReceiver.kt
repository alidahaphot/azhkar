package com.adhkar.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            if (Prefs.isEnabled(context)) {
                try {
                    context.startForegroundService(Intent(context, DhikrService::class.java))
                } catch (e: Exception) {
                    // Service could not be started now; it will start when the app is opened.
                }
            }
        }
    }
}
