package com.webpanel.app.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.IBinder
import android.os.SystemClock
import com.webpanel.app.MainActivity
import com.webpanel.app.SettingsActivity
import com.webpanel.app.WebPanelApp
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

class ContentCheckService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(1, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SCHEDULE_CHECK -> scheduleNextCheck()
            ACTION_RUN_CHECK -> runContentCheck()
        }
        return START_STICKY
    }

    private fun scheduleNextCheck() {
        val prefs = getSharedPreferences("webpanel_config", MODE_PRIVATE)
        val interval = prefs.getInt("content_check_interval", 30)
        if (interval <= 0) return

        val alarmManager = getSystemService(AlarmManager::class.java)
        val serviceIntent = Intent(this, ContentCheckService::class.java).apply {
            action = ACTION_RUN_CHECK
        }
        val pendingIntent = PendingIntent.getService(
            this, 0, serviceIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + interval * 1000L,
            pendingIntent
        )
    }

    private fun runContentCheck() {
        val prefs = getSharedPreferences("webpanel_config", MODE_PRIVATE)
        val url = prefs.getString("url", "") ?: ""
        val lastHash = prefs.getString("last_content_hash", "") ?: ""

        if (url.isEmpty()) {
            scheduleNextCheck()
            return
        }

        Thread {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                conn.connect()

                val stream = conn.inputStream
                val html = stream.bufferedReader().use(BufferedReader::readText)
                conn.disconnect()

                var text = html.replace(Regex("<script[\\s\\S]*?</script>"), " ")
                text = text.replace(Regex("<style[\\s\\S]*?</style>"), " ")
                text = text.replace(Regex("<[^>]*>"), " ")
                text = try { URLDecoder.decode(text, "UTF-8") } catch (_: Exception) { text }
                text = text.replace(Regex("\\d{1,2}:\\d{2}(:\\d{2})?"), "")
                text = text.replace(Regex("\\s+"), " ").trim()

                if (text.isEmpty()) {
                    scheduleNextCheck()
                    return@Thread
                }

                var hash = 0
                for (c in text) {
                    hash = ((hash shl 5) - hash) + c.code
                    hash = hash or 0
                }
                val currentHash = "${text.length}:$hash"

                if (lastHash.isNotEmpty() && currentHash != lastHash) {
                    prefs.edit().putString("last_content_hash", currentHash).apply()
                    showContentChangedNotification()
                } else {
                    prefs.edit().putString("last_content_hash", currentHash).apply()
                }
            } catch (_: Exception) {
            }
            scheduleNextCheck()
        }.start()
    }

    private fun showContentChangedNotification() {
        val pendingIntent = PendingIntent.getActivity(
            this, 2,
            Intent(this, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                        or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        or Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
                putExtra("CHECK_CONTENT", true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, WebPanelApp.HEADS_UP_CHANNEL_ID)
            .setContentTitle("WebPanel - 内容更新")
            .setContentText("检测到新内容，点击查看")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(Notification.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_VIBRATE)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(99, notification)
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
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
                Notification.Action.Builder(null, "设置", settingsIntent).build()
            )
            .build()
    }

    companion object {
        const val ACTION_SCHEDULE_CHECK = "com.webpanel.app.SCHEDULE_CHECK"
        const val ACTION_RUN_CHECK = "com.webpanel.app.RUN_CHECK"

        fun scheduleCheck(context: Context) {
            val intent = Intent(context, ContentCheckService::class.java).apply {
                action = ACTION_SCHEDULE_CHECK
            }
            context.startForegroundService(intent)
        }
    }
}

class CheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val serviceIntent = Intent(context, ContentCheckService::class.java).apply {
            action = ContentCheckService.ACTION_RUN_CHECK
        }
        context.startForegroundService(serviceIntent)
    }
}
