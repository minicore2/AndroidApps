package com.videodownloader.spike

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Message
import android.text.InputType
import android.text.TextUtils
import android.util.Log
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebViewCompat
import java.io.ByteArrayInputStream

/**
 * Prototype browser that:
 *  - navigates arbitrary URLs (back/forward/reload/URL bar),
 *  - blocks ads at the network and cosmetic layers (see [AdBlocker]),
 *  - blocks every popup,
 *  - sniffs video URLs and exports downloads to Movies/VideoDownloader
 *    (see [VideoDownloader]).
 *
 * The bundled `assets/spike.html` is still the default page for smoke testing.
 */
class SpikeActivity : ComponentActivity() {

    private lateinit var web: WebView
    private lateinit var urlInput: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var statusView: TextView
    private lateinit var downloadsPanel: LinearLayout
    private lateinit var downloadsHeader: TextView
    private lateinit var downloadsList: LinearLayout
    private lateinit var backButton: Button
    private lateinit var forwardButton: Button
    private lateinit var reloadButton: Button

    private lateinit var adBlocker: AdBlocker
    private lateinit var downloader: VideoDownloader
    private lateinit var resolver: MediaResolver

    private val detected = LinkedHashMap<String, DetectedMedia>()
    private val rowLabels = HashMap<String, TextView>()

    @Volatile
    private var currentPageUrl: String? = null

    @Volatile
    private var lastMainFrameMediaUrl: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adBlocker = AdBlocker(this)
        downloader = VideoDownloader(this)
        resolver = MediaResolver()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        // targetSdk 35 draws edge-to-edge; keep the toolbar below the status bar.
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(4, 6, 4, 6)
        }
        backButton = navButton("◀") { if (web.canGoBack()) web.goBack() }
        forwardButton = navButton("▶") { if (web.canGoForward()) web.goForward() }
        reloadButton = navButton("⟳") {
            if (progressBar.progress in 1..99) web.stopLoading() else web.reload()
        }
        urlInput = EditText(this).apply {
            hint = "輸入網址，例如 https://example.com"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            maxLines = 1
            setSingleLine()
            textSize = 14f
        }
        val goButton = navButton("→") { navigate(urlInput.text.toString()) }
        val downloadsButton = navButton("↓") { toggleDownloads() }

        toolbar.addView(backButton, smallButtonParams())
        toolbar.addView(forwardButton, smallButtonParams())
        toolbar.addView(reloadButton, smallButtonParams())
        toolbar.addView(urlInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        toolbar.addView(goButton, smallButtonParams())
        toolbar.addView(downloadsButton, smallButtonParams())

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = ProgressBar.GONE
        }

        web = WebView(this)
        statusView = TextView(this).apply {
            textSize = 10f
            setTextColor(Color.DKGRAY)
            setPadding(8, 2, 8, 2)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }

        downloadsHeader = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.DKGRAY)
            setPadding(8, 4, 8, 4)
        }
        downloadsList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val downloadsScroll = ScrollView(this).apply { addView(downloadsList) }
        downloadsPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = LinearLayout.GONE
        }
        downloadsPanel.addView(
            downloadsHeader,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        downloadsPanel.addView(
            downloadsScroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        updateDownloadsHeader()

        root.addView(
            toolbar,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(
            progressBar,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3))
        )
        root.addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(
            statusView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(
            downloadsPanel,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(180))
        )
        setContentView(root)

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }

        urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                navigate(urlInput.text.toString())
                true
            } else {
                false
            }
        }

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val url = request.url.toString()
                if (!request.isForMainFrame && adBlocker.shouldBlock(url)) {
                    Log.i(TAG, "BLOCKED ad request: $url")
                    return WebResourceResponse(
                        "text/plain",
                        "utf-8",
                        204,
                        "No Content",
                        emptyMap(),
                        ByteArrayInputStream(ByteArray(0))
                    )
                }
                val kind = mediaKindOf(url)
                if (kind != null) {
                    if (request.isForMainFrame && lastMainFrameMediaUrl != url) {
                        // A main-frame media request can arrive before onPageStarted.
                        // Reset here, and let onPageStarted skip its reset for a
                        // direct playlist/video URL, otherwise the expansion
                        // result would be discarded immediately. Same-URL
                        // redirects/reloads keep the in-flight/resolved state.
                        lastMainFrameMediaUrl = url
                        resolver.reset()
                        runOnUiThread {
                            if (!isFinishing && !isDestroyed) clearDetected()
                        }
                    }
                    Log.i(TAG, "SNIFF $kind: $url")
                    val referer = request.requestHeaders["Referer"] ?: currentPageUrl
                    when (kind) {
                        MediaKind.HLS, MediaKind.DASH -> resolver.resolve(
                            url = url,
                            kind = kind,
                            pageUrl = referer,
                            onResult = { items ->
                                runOnUiThread {
                                    if (!isFinishing && !isDestroyed) {
                                        items.forEach(::addDetected)
                                    }
                                }
                            }
                        )
                        MediaKind.PROGRESSIVE -> runOnUiThread {
                            if (!isFinishing && !isDestroyed) {
                                addDetected(
                                    DetectedMedia(
                                        url = url,
                                        kind = kind,
                                        quality = PlaylistParser.qualityFromUrl(url)
                                    )
                                )
                            }
                        }
                    }
                }
                return null
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                currentPageUrl = url
                // A direct playlist/video URL started its resolver fetch in
                // shouldInterceptRequest; resetting here would discard it.
                if (url == null || mediaKindOf(url) == null) {
                    lastMainFrameMediaUrl = null
                    resolver.reset()
                    clearDetected()
                }
                if (url != null) urlInput.setText(url)
                progressBar.visibility = ProgressBar.VISIBLE
                progressBar.progress = 0
                updateNavButtons()
            }

            override fun onPageFinished(view: WebView, url: String?) {
                currentPageUrl = url
                progressBar.visibility = ProgressBar.GONE
                if (url != null) urlInput.setText(url)
                updateNavButtons()
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                currentPageUrl = url
                if (url != null) urlInput.setText(url)
                updateNavButtons()
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message
            ): Boolean {
                log("POPUP BLOCKED (dialog=$isDialog)")
                return true
            }

            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progressBar.progress = newProgress
                if (newProgress >= 100) progressBar.visibility = ProgressBar.GONE
            }
        }

        try {
            WebViewCompat.addDocumentStartJavaScript(
                web,
                adBlocker.documentStartScript(),
                setOf("*")
            )
        } catch (t: Throwable) {
            log("document-start injection unavailable: ${t.message}")
        }

        val launchUrl = intent
            ?.takeIf { it.action == Intent.ACTION_VIEW }
            ?.dataString
        web.loadUrl(launchUrl ?: "file:///android_asset/spike.html")
        log(if (launchUrl != null) "loading $launchUrl" else "loading spike.html")
    }

    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        if (::resolver.isInitialized) resolver.shutdown()
        super.onDestroy()
    }

    private fun navigate(raw: String) {
        val url = normalizeUrl(raw) ?: return
        urlInput.setText(url)
        urlInput.clearFocus()
        log("NAVIGATE: $url")
        web.loadUrl(url)
    }

    private fun normalizeUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val hasScheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(trimmed)
        return if (hasScheme) trimmed else "https://$trimmed"
    }

    private fun mediaKindOf(url: String): MediaKind? {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        return when {
            path.endsWith(".m3u8") -> MediaKind.HLS
            path.endsWith(".mpd") -> MediaKind.DASH
            path.endsWith(".mp4") || path.endsWith(".m4v") || path.endsWith(".mov") ||
                path.endsWith(".webm") || path.endsWith(".mkv") -> MediaKind.PROGRESSIVE
            else -> null
        }
    }

    private fun addDetected(media: DetectedMedia) {
        val existing = detected[media.url]
        if (existing != null) {
            // A variant fetched by the player may arrive before the master
            // playlist is expanded; upgrade its label once quality is known.
            if (existing.quality == null && media.quality != null) {
                detected[media.url] = media
                rowLabels[media.url]?.text = labelFor(media)
            }
            return
        }

        detected[media.url] = media
        downloadsPanel.visibility = LinearLayout.VISIBLE
        updateDownloadsHeader()

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(8, 4, 8, 4)
        }
        val label = TextView(this).apply {
            text = labelFor(media)
            textSize = 11f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.MIDDLE
            setTextColor(Color.DKGRAY)
        }
        rowLabels[media.url] = label
        val button = Button(this).apply {
            text = "下載"
            textSize = 12f
            minWidth = 0
            minimumWidth = 0
        }
        row.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(
            button,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        downloadsList.addView(row)
        button.setOnClickListener { startDownload(media, button) }
    }

    private fun labelFor(media: DetectedMedia): String = "[${media.displayKind}] ${media.url}"

    private fun startDownload(media: DetectedMedia, button: Button) {
        button.isEnabled = false
        button.text = "0%"
        log("DOWNLOAD start: ${media.url}")
        downloader.download(
            media = media,
            pageUrl = web.url,
            onProgress = { percent -> button.text = "$percent%" },
            onSuccess = { uri ->
                button.text = "已儲存"
                log("DOWNLOAD OK: $uri")
                toast("已儲存到 Movies/VideoDownloader")
            },
            onError = { throwable ->
                button.isEnabled = true
                button.text = "重試"
                log("DOWNLOAD ERROR: ${throwable.message ?: throwable}")
                toast("下載失敗：${throwable.message ?: throwable.javaClass.simpleName}")
            }
        )
    }

    private fun clearDetected() {
        detected.clear()
        rowLabels.clear()
        downloadsList.removeAllViews()
        downloadsPanel.visibility = LinearLayout.GONE
        updateDownloadsHeader()
    }

    private fun updateDownloadsHeader() {
        downloadsHeader.text = "偵測到的影片（${detected.size}）"
    }

    private fun toggleDownloads() {
        if (downloadsPanel.visibility == LinearLayout.VISIBLE) {
            downloadsPanel.visibility = LinearLayout.GONE
        } else if (detected.isEmpty()) {
            toast("尚未偵測到影片")
        } else {
            downloadsPanel.visibility = LinearLayout.VISIBLE
        }
    }

    private fun updateNavButtons() {
        backButton.isEnabled = web.canGoBack()
        forwardButton.isEnabled = web.canGoForward()
    }

    private fun navButton(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 12f
            minWidth = 0
            minimumWidth = 0
            setPadding(8, 0, 8, 0)
            setOnClickListener { onClick() }
        }

    private fun smallButtonParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

    private fun log(message: String) {
        Log.i(TAG, message)
        statusView.text = message
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG = "VD_BROWSER"
    }
}
