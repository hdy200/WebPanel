package com.webpanel.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsetsController
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

    private val refreshRunnable = object : Runnable {
        override fun run() {
            webView.reload()
            if (config.refreshInterval > 0) {
                handler.postDelayed(this, config.refreshInterval * 1000L)
            }
        }
    }

    private val contentCheckRunnable = object : Runnable {
        override fun run() {
            checkContent()
            if (config.contentCheckInterval > 0) {
                handler.postDelayed(this, config.contentCheckInterval * 1000L)
            }
        }
    }

    private val hideRunnable = Runnable {
        moveTaskToBack(true)
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

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                lastContentHash = null
                handler.postDelayed({ checkContent() }, 3000)
            }
        }

        webView.loadUrl(config.url)

        if (config.refreshInterval > 0) {
            startRefreshTimer()
        }
        if (config.contentCheckInterval > 0) {
            startContentCheckTimer()
        }

        startForegroundService(Intent(this, ContentCheckService::class.java))

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
            checkContentAndDecide()
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

    private fun startContentCheckTimer() {
        handler.removeCallbacks(contentCheckRunnable)
        handler.postDelayed(contentCheckRunnable, config.contentCheckInterval * 1000L)
    }

    private fun checkContent() {
        if (webView.url == null) return

        val script = """
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

        webView.evaluateJavascript(script) { result ->
            val currentHash = result?.trim('"') ?: return@evaluateJavascript
            if (currentHash == "0:0" || currentHash == ":") return@evaluateJavascript

            if (lastContentHash == null) {
                lastContentHash = currentHash
                return@evaluateJavascript
            }

            if (currentHash != lastContentHash) {
                lastContentHash = currentHash
                onContentDetected()
            }
        }
    }

    private fun checkContentAndDecide() {
        if (webView.url == null) return

        val script = """
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

        webView.evaluateJavascript(script) { result ->
            val currentHash = result?.trim('"') ?: return@evaluateJavascript
            if (currentHash == "0:0" || currentHash == ":") return@evaluateJavascript

            if (lastContentHash == null) {
                lastContentHash = currentHash
                ContentCheckService.scheduleCheck(this)
                return@evaluateJavascript
            }

            if (currentHash != lastContentHash) {
                lastContentHash = currentHash
                onContentDetected()
            } else {
                ContentCheckService.scheduleCheck(this)
            }
        }
    }

    private fun onContentDetected() {
        handler.removeCallbacks(hideRunnable)

        if (config.hideDelayMinutes > 0) {
            handler.postDelayed(hideRunnable, config.hideDelayMinutes * 60 * 1000L)
        }
    }

    override fun onResume() {
        super.onResume()
        setupFullScreen()
        ContentCheckService.scheduleCheck(this)
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
