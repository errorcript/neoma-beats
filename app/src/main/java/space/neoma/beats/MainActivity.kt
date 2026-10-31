package space.neoma.beats

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileInputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var offlineManager: NeomaOfflineManager
    private lateinit var bridge: NeomaNativeBridge

    private val targetUrl = "https://beats.neoma.space/tools/music?native=true"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        offlineManager = NeomaOfflineManager(this)

        bridge = NeomaNativeBridge(this, offlineManager) { js ->
            runOnUiThread {
                webView.evaluateJavascript(js, null)
            }
        }

        setupWebView()
        setupBackPress()

        if (savedInstanceState == null) {
            webView.loadUrl(targetUrl)
        }

        handleMediaActionIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleMediaActionIntent(intent)
    }

    private fun handleMediaActionIntent(intent: Intent?) {
        val action = intent?.getStringExtra("media_action") ?: return
        runOnUiThread {
            webView.evaluateJavascript("window.onNativeMediaAction && window.onNativeMediaAction('$action');", null)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(false)

        // Custom User Agent identifying Neoma Native App
        val defaultUserAgent = settings.userAgentString
        settings.userAgentString = "$defaultUserAgent NeomaBeatsNative/2.6.0"

        webView.addJavascriptInterface(bridge, "NeomaNative")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = newProgress
                } else {
                    progressBar.visibility = View.GONE
                }
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                return super.onConsoleMessage(consoleMessage)
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                injectBackgroundPlaybackProtector(view)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                injectBackgroundPlaybackProtector(view)
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url?.toString() ?: return null
                // Intercept offline track requests
                if (url.contains("/offline-audio/")) {
                    val trackId = url.substringAfterLast("/").substringBefore("?")
                    val localPath = offlineManager.getTrackFilePath(trackId)
                    if (localPath != null) {
                        try {
                            val stream = FileInputStream(File(localPath))
                            return WebResourceResponse("audio/mpeg", "UTF-8", stream)
                        } catch (_: Exception) {}
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }
        }
    }

    private fun injectBackgroundPlaybackProtector(view: WebView?) {
        // Spoof Page Visibility API so YouTube / HTML5 Audio NEVER pauses when app is backgrounded or screen locked
        val script = """
            (function() {
                try {
                    Object.defineProperty(document, 'hidden', { get: function() { return false; }, configurable: true });
                    Object.defineProperty(document, 'visibilityState', { get: function() { return 'visible'; }, configurable: true });
                    Object.defineProperty(document, 'webkitVisibilityState', { get: function() { return 'visible'; }, configurable: true });
                    document.addEventListener('visibilitychange', function(e) { e.stopImmediatePropagation(); }, true);
                    window.addEventListener('pagehide', function(e) { e.stopImmediatePropagation(); }, true);
                } catch(e) {}
            })();
        """.trimIndent()
        view?.evaluateJavascript(script, null)
    }

    private fun setupBackPress() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    // Minimize to background instead of destroying the activity, keeping audio playback alive!
                    moveTaskToBack(true)
                }
            }
        })
    }

    // CRITICAL: DO NOT call webView.onPause() or webView.pauseTimers()!
    // Keeping webView running in background allows audio playback to continue while phone screen is locked or in pocket.
    override fun onPause() {
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
