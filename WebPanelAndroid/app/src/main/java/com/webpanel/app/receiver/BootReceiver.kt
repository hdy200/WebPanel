package com.webpanel.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.webpanel.app.service.ContentCheckService
import com.webpanel.app.util.AppConfig

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val config = AppConfig(context)
        if (config.autoStart) {
            val serviceIntent = Intent(context, ContentCheckService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}
