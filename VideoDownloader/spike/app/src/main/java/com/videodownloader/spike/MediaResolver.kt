package com.videodownloader.spike

import android.util.Log
import android.webkit.CookieManager
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Expands a sniffed HLS/DASH master playlist into one [DetectedMedia] per
 * selectable rendition.
 *
 * The fetch runs off the WebView network thread, so a slow playlist never
 * blocks page loading. Results from a previous page are discarded with a
 * generation counter; a reload clears the per-page cache so the master is
 * expanded again.
 */
class MediaResolver {

    private val executor: ExecutorService = Executors.newFixedThreadPool(2)
    private val resolved = ConcurrentHashMap.newKeySet<String>()
    private val resolving = ConcurrentHashMap<String, Int>()

    @Volatile
    private var generation = 0

    fun resolve(
        url: String,
        kind: MediaKind,
        pageUrl: String?,
        onResult: (List<DetectedMedia>) -> Unit
    ) {
        if (resolved.contains(url)) return
        val requestGeneration = generation
        if (resolving.putIfAbsent(url, requestGeneration) != null) return

        executor.execute {
            val result = try {
                val text = fetch(url, pageUrl)
                when (kind) {
                    MediaKind.HLS -> PlaylistParser.parseHls(url, text)
                    MediaKind.DASH -> PlaylistParser.parseDash(url, text)
                    MediaKind.PROGRESSIVE ->
                        listOf(DetectedMedia(url, kind, PlaylistParser.qualityFromUrl(url)))
                }.ifEmpty {
                    // A media playlist (not a master) or an unexpandable DASH
                    // manifest is still downloadable as a single item.
                    listOf(DetectedMedia(url, kind, PlaylistParser.qualityFromUrl(url)))
                }
            } catch (t: Throwable) {
                Log.w(TAG, "resolve failed: $url (${t.message ?: t.javaClass.simpleName})")
                listOf(DetectedMedia(url, kind, PlaylistParser.qualityFromUrl(url)))
            }

            try {
                if (requestGeneration != generation) return@execute
                resolved.add(url)
                result.forEach { resolved.add(it.url) }
                Log.i(TAG, "$kind $url -> ${result.size} item(s)")
                onResult(result)
            } finally {
                resolving.remove(url, requestGeneration)
            }
        }
    }

    /** Call on a new page load; stale callbacks are discarded. */
    fun reset() {
        generation++
        resolved.clear()
        resolving.clear()
    }

    fun shutdown() {
        executor.shutdownNow()
    }

    private fun fetch(url: String, pageUrl: String?): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "*/*")
            setRequestProperty("Accept-Encoding", "identity")
            pageUrl
                ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                ?.let { setRequestProperty("Referer", it) }
            CookieManager.getInstance().getCookie(url)?.let {
                setRequestProperty("Cookie", it)
            }
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")

            val output = ByteArrayOutputStream()
            connection.inputStream.buffered().use { input ->
                val buffer = ByteArray(16 * 1024)
                var total = 0
                while (total < MAX_PLAYLIST_BYTES) {
                    val count = input.read(buffer, 0, minOf(buffer.size, MAX_PLAYLIST_BYTES - total))
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    total += count
                }
            }
            return String(output.toByteArray(), charsetOf(connection.contentType))
        } finally {
            connection.disconnect()
        }
    }

    private fun charsetOf(contentType: String?): Charset {
        val name = contentType
            ?.substringAfter("charset=", "")
            ?.substringBefore(';')
            ?.trim()
            ?.trim('"')
        return if (name.isNullOrBlank()) {
            Charsets.UTF_8
        } else {
            runCatching { Charset.forName(name) }.getOrDefault(Charsets.UTF_8)
        }
    }

    companion object {
        private const val TAG = "VD_RESOLVE"
        private const val MAX_PLAYLIST_BYTES = 4 * 1024 * 1024
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    }
}
