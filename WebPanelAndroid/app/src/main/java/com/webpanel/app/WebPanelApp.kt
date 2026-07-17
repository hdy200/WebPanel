package com.webpanel.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class WebPanelApp : Application() {

    companion object {
        const val CHANNEL_ID = "webpanel_service"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "WebPanel Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Content check service"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
