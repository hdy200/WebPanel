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
    private var isInFront = true

    private val hideRunnable = Runnable {
        isInFront = false
        moveTaskToBack(true)
    }

    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (isInFront && webView.url != null) {
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

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                lastContentHash = null
            }
        }

        webView.loadUrl(config.url)

        if (config.refreshInterval > 0) {
            startRefreshTimer()
        }

        startForegroundService(Intent(this, ContentCheckService::class.java))
        ContentCheckService.scheduleCheck(this)

        if (config.hideDelayMinutes > 0) {
            handler.postDelayed(hideRunnable, config.hideDelayMinutes * 60 * 1000L)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("CHECK_CONTENT", false)) {
            intent.removeExtra("CHECK_CONTENT")
            ContentCheckService.scheduleCheck(this)
            checkContentAndDecide(hideIfUnchanged = true)
        }
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

    private fun checkContentAndDecide(hideIfUnchanged: Boolean = false) {
        if (webView.url == null) {
            if (hideIfUnchanged) moveTaskToBack(true)
            return
        }

        webView.evaluateJavascript(HashScript) { result ->
            val currentHash = result?.trim('"') ?: ""
            if (currentHash.isEmpty() || currentHash == "0:0" || currentHash == ":") {
                if (hideIfUnchanged) moveTaskToBack(true)
                return@evaluateJavascript
            }

            val changed = if (lastContentHash == null) {
                true
            } else {
                currentHash != lastContentHash
            }
            lastContentHash = currentHash

            if (changed) {
                handler.removeCallbacks(hideRunnable)
                if (config.hideDelayMinutes > 0) {
                    handler.postDelayed(hideRunnable, config.hideDelayMinutes * 60 * 1000L)
                }
            } else if (hideIfUnchanged) {
                moveTaskToBack(true)
            }
        }
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

    override fun onResume() {
        super.onResume()
        isInFront = true
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
