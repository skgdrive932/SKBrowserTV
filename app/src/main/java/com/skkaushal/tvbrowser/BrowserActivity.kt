package com.skkaushal.tvbrowser

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.text.TextUtils
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.ByteArrayInputStream

/** The browser: toolbar, WebView with ad blocking, remote-friendly cursor, downloads, fullscreen video. */
class BrowserActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var urlBar: EditText
    private lateinit var progress: ProgressBar
    private lateinit var toolbar: View
    private lateinit var cursor: CursorView
    private lateinit var defaultUa: String

    private var cursorOn = true
    @Volatile private var currentHost: String = ""
    private var customView: View? = null
    private var customCb: WebChromeClient.CustomViewCallback? = null
    private lateinit var chrome: WebChromeClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browser)
        AdBlocker.load(this)

        webView = findViewById(R.id.webView)
        urlBar = findViewById(R.id.urlBar)
        progress = findViewById(R.id.progress)
        toolbar = findViewById(R.id.toolbar)
        cursor = findViewById(R.id.cursor)
        cursorOn = Prefs.cursorMode(this)
        cursor.visibility = if (cursorOn) View.VISIBLE else View.GONE

        setupWebView()

        findViewById<Button>(R.id.btnBack).setOnClickListener { if (webView.canGoBack()) webView.goBack() }
        findViewById<Button>(R.id.btnForward).setOnClickListener { if (webView.canGoForward()) webView.goForward() }
        findViewById<Button>(R.id.btnReload).setOnClickListener { webView.reload() }
        findViewById<Button>(R.id.btnHome).setOnClickListener { goHome() }
        findViewById<Button>(R.id.btnMenu).setOnClickListener { showMenu() }

        urlBar.setOnEditorActionListener { v, actionId, event ->
            val go = actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            if (go) {
                load(v.text.toString())
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(v.windowToken, 0)
                webView.requestFocus()
                true
            } else false
        }

        val start = intent.dataString
            ?: intent.getStringExtra("url")
            ?: Prefs.homeUrl(this).ifEmpty { "https://www.google.com" }
        load(start)
        webView.requestFocus()
    }

    // ---------------------------------------------------------------- WebView

    private fun setupWebView() {
        val ws = webView.settings
        ws.javaScriptEnabled = Prefs.javascript(this)
        ws.domStorageEnabled = true
        ws.mediaPlaybackRequiresUserGesture = false
        ws.setSupportMultipleWindows(false)
        ws.javaScriptCanOpenWindowsAutomatically = false
        ws.loadWithOverviewMode = true
        ws.useWideViewPort = true
        ws.setSupportZoom(true)
        ws.builtInZoomControls = true
        ws.displayZoomControls = false
        ws.allowFileAccess = false
        ws.allowContentAccess = false
        defaultUa = ws.userAgentString
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (Prefs.adBlock(this@BrowserActivity) &&
                    !isWhitelisted(currentHost) &&
                    AdBlocker.isBlocked(request.url.host)
                ) {
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
                return null
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                return when (u.scheme) {
                    "http", "https", "about", "data", "blob", "javascript" -> false
                    "file", "content" -> true
                    else -> {
                        openExternal(u)
                        true
                    }
                }
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                currentHost = Uri.parse(url).host ?: ""
                if (!urlBar.hasFocus() && !url.startsWith("data:")) urlBar.setText(url)
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (Prefs.adBlock(this@BrowserActivity) && Prefs.cosmetic(this@BrowserActivity) &&
                    !isWhitelisted(currentHost)
                ) {
                    AdBlocker.cosmeticScript()?.let { view.evaluateJavascript(it, null) }
                }
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    val msg = TextUtils.htmlEncode(error.description?.toString() ?: "Unknown error")
                    view.loadData(
                        "<html><body style='background:#121212;color:#fff;font-family:sans-serif;padding:48px'>" +
                            "<h2>Cannot open this page</h2><p>$msg</p></body></html>",
                        "text/html", "utf-8"
                    )
                }
            }
        }

        chrome = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
            }

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (customView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                customView = view
                customCb = callback
                (window.decorView as FrameLayout).addView(
                    view,
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                )
                WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
            }

            override fun onHideCustomView() {
                val v = customView ?: return
                (window.decorView as FrameLayout).removeView(v)
                customView = null
                customCb?.onCustomViewHidden()
                customCb = null
                WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
        webView.webChromeClient = chrome

        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            startDownload(url, userAgent, contentDisposition, mimeType)
        }
    }

    private fun openExternal(u: Uri) {
        try {
            val i = if (u.scheme == "intent") Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME)
            else Intent(Intent.ACTION_VIEW, u)
            i.addCategory(Intent.CATEGORY_BROWSABLE)
            i.component = null
            i.selector = null
            startActivity(i)
        } catch (_: Exception) {
        }
    }

    private fun applyUa(host: String) {
        val desktop = Prefs.desktop(this) || host.endsWith("youtube.com")
        webView.settings.userAgentString = if (desktop) Prefs.DESKTOP_UA else defaultUa
    }

    private fun load(raw: String) {
        val url = Nav.toUrl(this, raw)
        applyUa(Uri.parse(url).host ?: "")
        webView.loadUrl(url)
    }

    private fun goHome() {
        val home = Prefs.homeUrl(this)
        if (home.isNotEmpty()) {
            load(home)
        } else {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            finish()
        }
    }

    // --------------------------------------------------------------- Ad block

    private fun isWhitelisted(host: String): Boolean = host.isNotEmpty() && Prefs.whitelist(this).contains(host)

    // -------------------------------------------------------------- Downloads

    private fun startDownload(url: String, ua: String?, cd: String?, mime: String?) {
        if (url.startsWith("blob:") || url.startsWith("data:")) {
            Toast.makeText(this, "This type of download is not supported", Toast.LENGTH_LONG).show()
            return
        }
        val name = URLUtil.guessFileName(url, cd, mime)
        val go = {
            try {
                val req = DownloadManager.Request(Uri.parse(url))
                    .setTitle(name)
                    .setMimeType(mime)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                ua?.let { req.addRequestHeader("User-Agent", it) }
                CookieManager.getInstance().getCookie(url)?.let { req.addRequestHeader("Cookie", it) }
                (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
                Toast.makeText(this, "Downloading $name", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        if (Prefs.askDownload(this)) {
            AlertDialog.Builder(this)
                .setTitle("Download file?")
                .setMessage(name)
                .setPositiveButton("Download") { _, _ -> go() }
                .setNegativeButton("Cancel", null)
                .show()
        } else go()
    }

    // ------------------------------------------------------------------- Menu

    private fun showMenu() {
        val host = currentHost
        val allowed = isWhitelisted(host)
        val items = arrayOf(
            "Add this site to home shortcuts",
            "Cursor mode: " + if (cursorOn) "ON" else "OFF",
            "Desktop site: " + if (Prefs.desktop(this)) "ON" else "OFF",
            if (allowed) "Block ads on this site" else "Allow ads on this site",
            "Downloads",
            "Settings"
        )
        AlertDialog.Builder(this)
            .setTitle("SK Browser")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> addCurrentToShortcuts()
                    1 -> {
                        cursorOn = !cursorOn
                        Prefs.sp(this).edit().putBoolean("cursor_mode", cursorOn).apply()
                        cursor.visibility = if (cursorOn) View.VISIBLE else View.GONE
                    }
                    2 -> {
                        Prefs.sp(this).edit().putBoolean("desktop_mode", !Prefs.desktop(this)).apply()
                        applyUa(host)
                        webView.reload()
                    }
                    3 -> if (host.isNotEmpty()) {
                        val set = Prefs.whitelist(this)
                        if (allowed) set.remove(host) else set.add(host)
                        Prefs.saveWhitelist(this, set)
                        webView.reload()
                    }
                    4 -> startActivity(Intent(this, DownloadsActivity::class.java))
                    5 -> startActivity(
                        Intent(this, SettingsActivity::class.java)
                            .putExtra("current_url", webView.url)
                            .putExtra("current_title", webView.title)
                    )
                }
            }
            .show()
    }

    private fun addCurrentToShortcuts() {
        val url = webView.url
        if (url.isNullOrEmpty() || !url.startsWith("http")) {
            Toast.makeText(this, "Open a website first", Toast.LENGTH_SHORT).show()
            return
        }
        val title = webView.title?.takeIf { it.isNotBlank() } ?: Nav.hostOf(url)
        val ok = ShortcutStore.add(this, title.take(24), url)
        Toast.makeText(this, if (ok) "Added to home shortcuts" else "Already on the home screen", Toast.LENGTH_SHORT).show()
    }

    // ----------------------------------------------------------- Remote / keys

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (e.keyCode == KeyEvent.KEYCODE_MENU) {
            if (e.action == KeyEvent.ACTION_UP) showMenu()
            return true
        }
        if (customView != null) return super.dispatchKeyEvent(e)

        if (toolbar.hasFocus()) {
            if (e.keyCode == KeyEvent.KEYCODE_DPAD_DOWN && e.action == KeyEvent.ACTION_DOWN) {
                webView.requestFocus()
                return true
            }
            return super.dispatchKeyEvent(e)
        }

        if (cursorOn) {
            when (e.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (e.action == KeyEvent.ACTION_DOWN) moveCursor(e.keyCode, e.repeatCount)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    if (e.action == KeyEvent.ACTION_UP) clickCursor()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(e)
    }

    private fun moveCursor(code: Int, repeat: Int) {
        val d = resources.displayMetrics.density
        val step = (10 + minOf(repeat * 3, 40)) * d
        val w = cursor.width.toFloat()
        val h = cursor.height.toFloat()
        when (code) {
            KeyEvent.KEYCODE_DPAD_LEFT -> cursor.cx = maxOf(0f, cursor.cx - step)
            KeyEvent.KEYCODE_DPAD_RIGHT -> cursor.cx = minOf(w - 1, cursor.cx + step)
            KeyEvent.KEYCODE_DPAD_UP ->
                if (cursor.cy - step < 0) {
                    if (webView.scrollY > 0) webView.scrollBy(0, -(step * 2).toInt())
                    else findViewById<View>(R.id.btnBack).requestFocus()
                } else cursor.cy -= step
            KeyEvent.KEYCODE_DPAD_DOWN ->
                if (cursor.cy + step > h - 1) webView.scrollBy(0, (step * 2).toInt())
                else cursor.cy += step
        }
        cursor.invalidate()
    }

    private fun clickCursor() {
        val t = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, cursor.cx, cursor.cy, 0)
        val up = MotionEvent.obtain(t, t + 50, MotionEvent.ACTION_UP, cursor.cx, cursor.cy, 0)
        down.source = InputDevice.SOURCE_TOUCHSCREEN
        up.source = InputDevice.SOURCE_TOUCHSCREEN
        webView.dispatchTouchEvent(down)
        webView.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        when {
            customView != null -> chrome.onHideCustomView()
            webView.canGoBack() -> webView.goBack()
            else -> super.onBackPressed()
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
