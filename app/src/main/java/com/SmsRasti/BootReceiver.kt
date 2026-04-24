package com.SmsRasti

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (
            action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val prefs = PrefsHelper(context)
            if (prefs.autoStart() || prefs.serviceRunning()) {
                try {
                    ContextCompat.startForegroundService(
                        context,
                        Intent(context, SmsService::class.java).apply { action = SmsService.ACTION_START }
                    )
                    Log.d("RASTISMS", "Service started by BootReceiver")
                } catch (e: Exception) {
                    Log.e("RASTISMS", "Boot start error: ${e.message}")
                }
            }
        }
    }
}
