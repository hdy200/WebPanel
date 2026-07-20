package com.webpanel.app

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.webpanel.app.service.ContentCheckService
import com.webpanel.app.util.AppConfig

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var config: AppConfig
    private val handler = Handler(Looper.getMainLooper())
    private var isAppVisible = true

    private val hideRunnable = Runnable {
        hideApp()
    }

    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (isAppVisible && webView.url != null) {
                webView.reload()
            }
            if (config.refreshInterval > 0) {
                handler.postDelayed(this, config.refreshInterval * 1000L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        config = AppConfig(this)
        webView = findViewById(R.id.webView)

        findViewById<View>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        setupFullScreen()
        setupWebView()
        setupWindowFlags()

        webView.loadUrl(config.url)

        if (config.refreshInterval > 0) {
            startRefreshTimer()
        }

        startForegroundService(Intent(this, ContentCheckService::class.java))
        ContentCheckService.scheduleCheck(this)

        handleCheckIntent(intent)

        if (config.hideDelayMinutes > 0) {
            handler.removeCallbacks(hideRunnable)
            handler.postDelayed(hideRunnable, config.hideDelayMinutes * 60 * 1000L)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleCheckIntent(intent)
    }

    private fun handleCheckIntent(intent: Intent) {
        if (intent.getBooleanExtra("CHECK_CONTENT", false)) {
            intent.removeExtra("CHECK_CONTENT")
            showApp()
        }
    }

    private fun showApp() {
        isAppVisible = true
        handler.removeCallbacks(hideRunnable)

        webView.visibility = View.VISIBLE
        webView.alpha = 1f

        window.decorView.setBackgroundColor(Color.BLACK)
        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)

        setupFullScreen()

        if (config.hideDelayMinutes > 0) {
            handler.postDelayed(hideRunnable, config.hideDelayMinutes * 60 * 1000L)
        }
    }

    private fun hideApp() {
        isAppVisible = false
        handler.removeCallbacks(hideRunnable)

        webView.visibility = View.INVISIBLE
        webView.alpha = 0f

        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        )
    }

    private fun setupFullScreen() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(android.view.WindowInsets.Type.systemBars())
                it.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    private fun setupWindowFlags() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            cacheMode = WebSettings.LOAD_DEFAULT
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                return false
            }
        }
        webView.webChromeClient = WebChromeClient()
    }

    private fun startRefreshTimer() {
        handler.removeCallbacks(refreshRunnable)
        handler.postDelayed(refreshRunnable, config.refreshInterval * 1000L)
    }

    override fun onResume() {
        super.onResume()
        if (isAppVisible) {
            setupFullScreen()
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            moveTaskToBack(true)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        webView.destroy()
        super.onDestroy()
    }
}
