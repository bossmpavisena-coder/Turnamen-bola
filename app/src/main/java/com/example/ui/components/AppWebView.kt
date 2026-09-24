package com.example.ui.components

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
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
import com.example.util.WebAppInterface
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL

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

        // Setup share & vibrate polyfills in main frame
        function setupMainPolyfills(win) {
            if (!win) return;
            try {
                win.navigator.vibrate = function(pat) {
                    if (window.AndroidBridge) window.AndroidBridge.vibrate(JSON.stringify(pat));
                    return true;
                };
                win.navigator.canShare = function() { return true; };
                win.navigator.share = async function(data) {
                    if (!data) return;
                    if (data.files && data.files.length > 0) {
                        var file = data.files[0];
                        var reader = new FileReader();
                        reader.onload = function(e) {
                            if (window.AndroidBridge) {
                                window.AndroidBridge.shareFile(
                                    e.target.result,
                                    file.name || 'hasil-pertandingan.png',
                                    file.type || 'image/png',
                                    data.title || '',
                                    data.text || ''
                                );
                            }
                        };
                        reader.readAsDataURL(file);
                        return;
                    }
                    if (window.AndroidBridge) {
                        window.AndroidBridge.shareText(data.title || '', data.text || '', data.url || '');
                    }
                };
            } catch(e) {}
        }
        setupMainPolyfills(window);
    })();
""".trimIndent()

private val INJECT_FRAME_BRIDGE_SCRIPT = """
    <script>
    (function() {
        function initBridge() {
            var f = document.getElementById('userHtmlFrame');
            if (!f || !f.contentWindow) return;
            var win = f.contentWindow;
            try {
                var doc = f.contentDocument || win.document;
                if (!win.__abInjected) {
                    win.__abInjected = true;

                    // 1. Polyfill vibrate
                    win.navigator.vibrate = function(pat) {
                        if (window.AndroidBridge) {
                            window.AndroidBridge.vibrate(JSON.stringify(pat));
                        }
                        return true;
                    };

                    // 2. Polyfill Web Share API
                    win.navigator.canShare = function(data) {
                        return true;
                    };

                    win.navigator.share = async function(data) {
                        if (!data) return;
                        var title = data.title || '';
                        var text = data.text || '';
                        var url = data.url || '';

                        if (data.files && data.files.length > 0) {
                            var file = data.files[0];
                            var reader = new win.FileReader();
                            reader.onload = function(e) {
                                if (window.AndroidBridge) {
                                    window.AndroidBridge.shareFile(
                                        e.target.result,
                                        file.name || 'hasil-pertandingan.png',
                                        file.type || 'image/png',
                                        title,
                                        text
                                    );
                                }
                            };
                            reader.readAsDataURL(file);
                            return;
                        }

                        if (window.AndroidBridge) {
                            window.AndroidBridge.shareText(title, text, url);
                        }
                    };

                    // 3. Fallback intercept for poster download -> trigger share sheet
                    var origCreate = win.URL.createObjectURL;
                    win.URL.createObjectURL = function(blob) {
                        var u = origCreate.apply(this, arguments);
                        if (blob && (blob.type === 'image/png' || blob.type === 'image/jpeg')) {
                            win.__lastShareBlob = blob;
                        }
                        return u;
                    };

                    var origClick = win.HTMLAnchorElement.prototype.click;
                    win.HTMLAnchorElement.prototype.click = function() {
                        if (this.download && win.__lastShareBlob) {
                            var fileName = this.download;
                            var reader = new win.FileReader();
                            reader.onload = function(e) {
                                if (window.AndroidBridge) {
                                    window.AndroidBridge.shareFile(
                                        e.target.result,
                                        fileName,
                                        'image/png',
                                        'Hasil Pertandingan',
                                        'Hasil Pertandingan Turnamen'
                                    );
                                }
                            };
                            reader.readAsDataURL(win.__lastShareBlob);
                            return;
                        }
                        return origClick.apply(this, arguments);
                    };

                    // 4. Observer for live Goal overlay -> trigger goal vibration
                    function watchGoalOverlay() {
                        var go = doc.getElementById('goalOverlay');
                        if (go && !go.__abObs) {
                            go.__abObs = true;
                            var obs = new win.MutationObserver(function(mutations) {
                                if (go.classList.contains('show-goal')) {
                                    if (window.AndroidBridge) {
                                        window.AndroidBridge.triggerGoalVibration();
                                    }
                                }
                            });
                            obs.observe(go, { attributes: true, attributeFilter: ['class'] });
                        }
                    }
                    watchGoalOverlay();
                    win.setInterval(watchGoalOverlay, 800);
                }
            } catch(e) {}
        }
        setInterval(initBridge, 200);
    })();
    </script>
""".trimIndent()

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AppWebView(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val webAppInterface = remember { WebAppInterface(context) }

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

            addJavascriptInterface(webAppInterface, "AndroidBridge")

            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)

            setDownloadListener { url, _, _, mimeType, _ ->
                if (url.startsWith("data:")) {
                    webAppInterface.shareFile(
                        base64Data = url,
                        fileNameParam = "hasil-pertandingan.png",
                        mimeTypeParam = mimeType.ifBlank { "image/png" },
                        titleParam = "Hasil Pertandingan",
                        textParam = "Hasil Pertandingan Turnamen"
                    )
                } else if (url.startsWith("blob:")) {
                    val js = """
                        (function() {
                            fetch('$url')
                                .then(function(r) { return r.blob(); })
                                .then(function(b) {
                                    var reader = new FileReader();
                                    reader.onload = function(e) {
                                        if (window.AndroidBridge) {
                                            window.AndroidBridge.shareFile(
                                                e.target.result,
                                                'hasil-pertandingan.png',
                                                '$mimeType',
                                                'Hasil Pertandingan',
                                                'Hasil Pertandingan'
                                            );
                                        }
                                    };
                                    reader.readAsDataURL(b);
                                })
                                .catch(function(e) { console.error(e); });
                        })();
                    """.trimIndent()
                    evaluateJavascript(js, null)
                } else {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(Uri.parse(url), mimeType)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        try {
                            val fallback = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(fallback)
                        } catch (_: Exception) { }
                    }
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
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val urlStr = request?.url?.toString() ?: return null
                if (urlStr.contains("userCodeAppPanel")) {
                    try {
                        val connection = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                            requestMethod = "GET"
                            connectTimeout = 8000
                            readTimeout = 8000
                            instanceFollowRedirects = true
                            request.requestHeaders?.forEach { (k, v) ->
                                setRequestProperty(k, v)
                            }
                        }
                        if (connection.responseCode in 200..299) {
                            var bodyString = connection.inputStream.bufferedReader().use { it.readText() }
                            if (bodyString.contains("</body>")) {
                                bodyString = bodyString.replace("</body>", "$INJECT_FRAME_BRIDGE_SCRIPT</body>")
                            } else {
                                bodyString += INJECT_FRAME_BRIDGE_SCRIPT
                            }
                            val mimeType = connection.contentType?.substringBefore(";") ?: "text/html"
                            val encoding = "utf-8"
                            val inputStream = ByteArrayInputStream(bodyString.toByteArray(Charsets.UTF_8))
                            return WebResourceResponse(mimeType, encoding, inputStream)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val scheme = request?.url?.scheme?.lowercase() ?: return false

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
