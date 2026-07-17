package com.webpanel.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class WebPanelApp : Application() {

    companion object {
        const val CHANNEL_ID = "webpanel_service"
        const val HEADS_UP_CHANNEL_ID = "webpanel_alert"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "WebPanel Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Content check service"
        }
        manager.createNotificationChannel(serviceChannel)

        val alertChannel = NotificationChannel(
            HEADS_UP_CHANNEL_ID,
            "WebPanel Alert",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Content update alerts"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 200, 300)
        }
        manager.createNotificationChannel(alertChannel)
    }
}
