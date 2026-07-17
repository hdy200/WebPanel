package com.webpanel.app.util

import android.content.Context
import android.content.SharedPreferences

class AppConfig(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("webpanel_config", Context.MODE_PRIVATE)

    var url: String
        get() = prefs.getString("url", "https://news.qq.com") ?: "https://news.qq.com"
        set(value) = prefs.edit().putString("url", value).apply()

    var refreshInterval: Int
        get() = prefs.getInt("refresh_interval", 0)
        set(value) = prefs.edit().putInt("refresh_interval", value).apply()

    var contentCheckInterval: Int
        get() = prefs.getInt("content_check_interval", 30)
        set(value) = prefs.edit().putInt("content_check_interval", value).apply()

    var hideDelayMinutes: Int
        get() = prefs.getInt("hide_delay_minutes", 10)
        set(value) = prefs.edit().putInt("hide_delay_minutes", value).apply()

    var autoStart: Boolean
        get() = prefs.getBoolean("auto_start", true)
        set(value) = prefs.edit().putBoolean("auto_start", value).apply()
}
