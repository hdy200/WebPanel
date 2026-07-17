package com.webpanel.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.webpanel.app.MainActivity
import com.webpanel.app.SettingsActivity
import com.webpanel.app.WebPanelApp

class ContentCheckService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(1, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val settingsIntent = PendingIntent.getActivity(
            this, 1,
            Intent(this, SettingsActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, WebPanelApp.CHANNEL_ID)
            .setContentTitle("WebPanel")
            .setContentText("正在监控内容更新")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    null, "设置", settingsIntent
                ).build()
            )
            .build()
    }
}
