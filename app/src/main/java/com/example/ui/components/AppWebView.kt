package com.example.ui.components

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

private const val TARGET_URL =
    "https://script.google.com/macros/s/AKfycbz33ToJXvNxsH9jVJVselOwD6vJ_akXVVnxNYPlL3TJntwETb66h9Mk6pL0ouc0R1dc/exec"

private val INJECT_CLEAN_UI_SCRIPT = """
    (function() {
        var css = `
            #warning, .warning-bar, #warning-bar-table tr:first-child, .docs-butterbar-container, div[role="alert"] {
                display: none !important;
                height: 0px !important;
                max-height: 0px !important;
                visibility: hidden !important;
                overflow: hidden !important;
                padding: 0px !important;
                margin: 0px !important;
                border: none !important;
            }
            #warning-bar-table {
                height: 100vh !important;
                width: 100vw !important;
                margin: 0 !important;
                padding: 0 !important;
                border-collapse: collapse !important;
            }
            #sandboxFrame {
                height: 100vh !important;
                width: 100vw !important;
                border: none !important;
            }
            #intro-splash {
                display: none !important;
            }
        `;
        function applyStyles() {
            var head = document.head || document.getElementsByTagName('head')[0] || document.documentElement;
            if (head) {
                var style = document.getElementById('gas-clean-ui-style');
                if (!style) {
                    style = document.createElement('style');
                    style.id = 'gas-clean-ui-style';
                    style.type = 'text/css';
                    style.appendChild(document.createTextNode(css));
                    head.appendChild(style);
                }
            }
            var w = document.getElementById('warning');
            if (w) {
                w.style.display = 'none';
                if (w.parentElement) w.parentElement.style.display = 'none';
                if (w.closest('tr')) w.closest('tr').style.display = 'none';
            }
            var wb = document.querySelectorAll('.warning-bar, .docs-butterbar-container');
            wb.forEach(function(el) { el.style.display = 'none'; });
            
            var table = document.getElementById('warning-bar-table');
            if (table && table.rows && table.rows.length > 0) {
                table.rows[0].style.display = 'none';
            }
            
            var splash = document.getElementById('intro-splash');
            if (splash) {
                splash.style.display = 'none';
                document.documentElement.style.overflow = '';
                document.body.style.overflow = '';
            }
        }
        applyStyles();
        if (window.MutationObserver) {
            var observer = new MutationObserver(applyStyles);
            if (document.body) {
                observer.observe(document.body, { childList: true, subtree: true });
            } else {
                document.addEventListener('DOMContentLoaded', function() {
                    applyStyles();
                    if (document.body) {
                        observer.observe(document.body, { childList: true, subtree: true });
                    }
                });
            }
        }
    })();
""".trimIndent()

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AppWebView(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var canGoBack by remember { mutableStateOf(false) }
    var filePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        filePathCallback?.onReceiveValue(uris)
        filePathCallback = null
    }

    val webView = remember {
        WebView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = true
                displayZoomControls = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                mediaPlaybackRequiresUserGesture = false
                cacheMode = WebSettings.LOAD_DEFAULT
                setSupportZoom(true)
            }

            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)

            setDownloadListener { url, _, _, mimeType, _ ->
                try {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.parse(url), mimeType)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {
                    try {
                        val fallback = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(fallback)
                    } catch (_: Exception) { }
                }
            }
        }
    }

    BackHandler(enabled = canGoBack) {
        if (webView.canGoBack()) {
            webView.goBack()
        }
    }

    DisposableEffect(webView) {
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                // Inject script early during loading to strip warning bar before display
                if (newProgress > 15) {
                    view?.evaluateJavascript(INJECT_CLEAN_UI_SCRIPT, null)
                }
            }

            override fun onJsAlert(
                view: WebView?,
                url: String?,
                message: String?,
                result: android.webkit.JsResult?
            ): Boolean {
                AlertDialog.Builder(context)
                    .setTitle("Pemberitahuan")
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok) { _, _ -> result?.confirm() }
                    .setOnCancelListener { result?.cancel() }
                    .show()
                return true
            }

            override fun onJsConfirm(
                view: WebView?,
                url: String?,
                message: String?,
                result: android.webkit.JsResult?
            ): Boolean {
                AlertDialog.Builder(context)
                    .setTitle("Konfirmasi")
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok) { _, _ -> result?.confirm() }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> result?.cancel() }
                    .setOnCancelListener { result?.cancel() }
                    .show()
                return true
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallbackParam: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = filePathCallbackParam
                try {
                    val intent = fileChooserParams?.createIntent()
                    if (intent != null) {
                        fileChooserLauncher.launch(intent)
                        return true
                    }
                } catch (_: Exception) {
                    filePathCallback = null
                }
                return false
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                val scheme = request.url.scheme?.lowercase()

                if (scheme == "http" || scheme == "https") {
                    return false
                }

                // Handle external apps (e.g. WhatsApp, phone, email)
                return try {
                    val intent = Intent(Intent.ACTION_VIEW, request.url).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    true
                } catch (_: Exception) {
                    true
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                view?.evaluateJavascript(INJECT_CLEAN_UI_SCRIPT, null)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                canGoBack = view?.canGoBack() ?: false
                // Execute cleanup to guarantee GAS warning bar and any intro overlay are removed
                view?.evaluateJavascript(INJECT_CLEAN_UI_SCRIPT, null)
            }
        }

        webView.loadUrl(TARGET_URL)

        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    AndroidView(
        factory = { webView },
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    )
}
