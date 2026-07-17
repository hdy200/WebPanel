package com.webpanel.app

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

    private var lastContentHash: String? = null
    private var pendingCheck: Boolean = false

    private val refreshRunnable = object : Runnable {
        override fun run() {
            webView.reload()
            if (config.refreshInterval > 0) {
                handler.postDelayed(this, config.refreshInterval * 1000L)
            }
        }
    }

    private val hideRunnable = Runnable {
        hideApp()
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

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                lastContentHash = null
                if (pendingCheck) {
                    pendingCheck = false
                    handler.postDelayed({ checkContentAndDecide() }, 500)
                }
            }
        }

        webView.loadUrl(config.url)

        if (config.refreshInterval > 0) {
            startRefreshTimer()
        }

        startForegroundService(Intent(this, ContentCheckService::class.java))
        ContentCheckService.scheduleCheck(this)

        handleCheckIntent(intent)
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
            handler.postDelayed({ checkContentAndDecide() }, 1000)
        }
    }

    private fun setupFullScreen() {
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
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
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
        webView.webChromeClient = WebChromeClient()
    }

    private fun startRefreshTimer() {
        handler.removeCallbacks(refreshRunnable)
        handler.postDelayed(refreshRunnable, config.refreshInterval * 1000L)
    }

    private fun showApp() {
        handler.removeCallbacks(hideRunnable)
        webView.alpha = 1f
        webView.visibility = View.VISIBLE
        window.setFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
    }

    private fun hideApp() {
        webView.alpha = 0f
        webView.visibility = View.INVISIBLE
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private val HashScript = """
        (function() {
            var text = document.body ? document.body.innerText : '';
            text = text.replace(/\d{1,2}:\d{2}(:\d{2})?/g, '').replace(/\s+/g, ' ').trim();
            var hash = 0;
            for (var i = 0; i < text.length; i++) {
                hash = ((hash << 5) - hash) + text.charCodeAt(i);
                hash |= 0;
            }
            return text.length + ':' + hash;
        })()
    """.trimIndent()

    private fun checkContentAndDecide() {
        if (webView.url == null) {
            ContentCheckService.scheduleCheck(this)
            return
        }

        webView.evaluateJavascript(HashScript) { result ->
            val currentHash = result?.trim('"') ?: ""
            if (currentHash.isEmpty() || currentHash == "0:0" || currentHash == ":") {
                ContentCheckService.scheduleCheck(this)
                return@evaluateJavascript
            }

            val changed = if (lastContentHash == null) {
                true
            } else {
                currentHash != lastContentHash
            }
            lastContentHash = currentHash

            if (changed) {
                showApp()
                handler.removeCallbacks(hideRunnable)
                if (config.hideDelayMinutes > 0) {
                    handler.postDelayed(hideRunnable, config.hideDelayMinutes * 60 * 1000L)
                }
            } else {
                hideApp()
            }

            ContentCheckService.scheduleCheck(this)
        }
    }

    override fun onResume() {
        super.onResume()
        setupFullScreen()
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
