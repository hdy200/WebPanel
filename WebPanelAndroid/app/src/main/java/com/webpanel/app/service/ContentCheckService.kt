package com.webpanel.app.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import com.webpanel.app.MainActivity
import com.webpanel.app.SettingsActivity
import com.webpanel.app.WebPanelApp
import com.webpanel.app.util.AppConfig
import java.net.HttpURLConnection
import java.net.URL

class ContentCheckService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(1, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SCHEDULE_CHECK -> scheduleNextCheck()
            ACTION_CHECK_AND_SHOW -> checkAndShow()
        }
        return START_STICKY
    }

    private fun scheduleNextCheck() {
        val config = AppConfig(this)
        if (config.contentCheckInterval <= 0) return

        val alarmManager = getSystemService(AlarmManager::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this, 0,
            Intent(this, CheckReceiver::class.java).apply {
                action = ACTION_CHECK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.set(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + config.contentCheckInterval * 1000L,
            pendingIntent
        )
    }

    private fun checkAndShow() {
        Thread {
            acquireWakeLock()
            try {
                val config = AppConfig(this)
                val url = config.url
                if (url.isEmpty()) {
                    scheduleNextCheck()
                    return@Thread
                }

                val html = fetchPage(url)
                if (html.isEmpty()) {
                    scheduleNextCheck()
                    return@Thread
                }

                val currentHash = computeHash(html)
                val lastHash = config.lastContentHash

                if (lastHash.isEmpty()) {
                    config.lastContentHash = currentHash
                    scheduleNextCheck()
                    return@Thread
                }

                if (currentHash != lastHash) {
                    config.lastContentHash = currentHash
                    showActivity()
                }
            } catch (_: Exception) {
            } finally {
                releaseWakeLock()
                scheduleNextCheck()
            }
        }.start()
    }

    private fun fetchPage(urlStr: String): String {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")
        return try {
            val stream = conn.inputStream
            val bytes = stream.readBytes()
            stream.close()
            String(bytes, Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        } finally {
            conn.disconnect()
        }
    }

    private fun computeHash(html: String): String {
        val text = html
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\d{1,2}:\\d{2}(:\\d{2})?"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

        var hash = 0
        for (i in text.indices) {
            hash = ((hash shl 5) - hash) + text.codePointAt(i)
            hash = hash.toInt()
        }
        return "${text.length}:$hash"
    }

    private fun showActivity() {
        val activityIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                    or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    or Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra("CHECK_CONTENT", true)
        }
        try {
            startActivity(activityIntent)
        } catch (_: Exception) {
            showHeadsUpNotification()
        }
    }

    private fun showHeadsUpNotification() {
        val pendingIntent = PendingIntent.getActivity(
            this, 2,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("CHECK_CONTENT", true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, WebPanelApp.HEADS_UP_CHANNEL_ID)
            .setContentTitle("WebPanel")
            .setContentText("检测到内容更新，点击查看")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(Notification.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_ALL)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(2, notification)
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(PowerManager::class.java)
        wakeLock?.release()
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "webpanel:check_wakelock"
        ).apply {
            acquire(15 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
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
        const val ACTION_CHECK = "com.webpanel.app.CHECK"
        const val ACTION_CHECK_AND_SHOW = "com.webpanel.app.CHECK_AND_SHOW"
        const val ACTION_SCHEDULE_CHECK = "com.webpanel.app.SCHEDULE_CHECK"

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
            action = ContentCheckService.ACTION_CHECK_AND_SHOW
        }
        context.startForegroundService(serviceIntent)
    }
}
