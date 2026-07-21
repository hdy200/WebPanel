package com.webpanel.app

import android.content.Intent
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
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
    private var isHidden = false

    private val hideRunnable = Runnable {
        hideApp()
    }

    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (!isHidden && webView.url != null) {
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
            showApp()
            checkContentAndDecide()
        }
    }

    private fun hideApp() {
        isHidden = true
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        window.attributes.format = PixelFormat.TRANSLUCENT
        window.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun showApp() {
        isHidden = false
        handler.removeCallbacks(hideRunnable)
        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        window.setBackgroundDrawableResource(android.R.color.black)
        setupFullScreen()
        if (config.hideDelayMinutes > 0) {
            handler.postDelayed(hideRunnable, config.hideDelayMinutes * 60 * 1000L)
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

    private fun checkContentAndDecide() {
        if (webView.url == null) return

        webView.evaluateJavascript(HashScript) { result ->
            val currentHash = result?.trim('"') ?: ""
            if (currentHash.isEmpty() || currentHash == "0:0" || currentHash == ":") {
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
                webView.reload()
                handler.removeCallbacks(hideRunnable)
                if (config.hideDelayMinutes > 0) {
                    handler.postDelayed(hideRunnable, config.hideDelayMinutes * 60 * 1000L)
                }
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
        setupFullScreen()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (isHidden) {
            showApp()
        } else if (webView.canGoBack()) {
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
