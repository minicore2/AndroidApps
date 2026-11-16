package com.videodownloader.spike

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.webkit.CookieManager
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.MimeTypes
import androidx.media3.muxer.Mp4Muxer
import androidx.media3.muxer.Muxer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableList
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Downloads a sniffed media URL and publishes it to
 * `Movies/VideoDownloader` through MediaStore.
 *
 * - Progressive files are streamed directly (cookies/referer preserved).
 * - HLS/DASH playlists are downloaded and remuxed to MP4 with Media3 Transformer.
 */
class VideoDownloader(private val context: Context) {

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun download(
        media: DetectedMedia,
        pageUrl: String?,
        onProgress: (Int) -> Unit,
        onSuccess: (Uri) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        when (media.kind) {
            MediaKind.PROGRESSIVE -> downloadProgressive(media, pageUrl, onProgress, onSuccess, onError)
            MediaKind.HLS, MediaKind.DASH -> downloadWithTransformer(media, onProgress, onSuccess, onError)
        }
    }

    private fun downloadProgressive(
        media: DetectedMedia,
        pageUrl: String?,
        onProgress: (Int) -> Unit,
        onSuccess: (Uri) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        executor.execute {
            val tmp = File(context.cacheDir, "dl_${System.currentTimeMillis()}.bin")
            try {
                val connection = (URL(media.url).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    setRequestProperty("User-Agent", USER_AGENT)
                    pageUrl?.let { setRequestProperty("Referer", it) }
                    CookieManager.getInstance().getCookie(media.url)?.let {
                        setRequestProperty("Cookie", it)
                    }
                }
                connection.connect()
                val code = connection.responseCode
                if (code !in 200..299) throw IllegalStateException("HTTP $code")

                val total = connection.contentLengthLong
                var read = 0L
                connection.inputStream.use { input ->
                    FileOutputStream(tmp).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            read += count
                            if (total > 0) {
                                val percent = ((read * 100) / total).toInt().coerceIn(0, 100)
                                main.post { onProgress(percent) }
                            }
                        }
                    }
                }

                val extension = extensionOf(media.url)
                val uri = exportToMediaStore(
                    tmp,
                    nameFor(media.url, extension, media.quality),
                    mimeFor(extension)
                )
                main.post { onSuccess(uri) }
            } catch (t: Throwable) {
                main.post { onError(t) }
            } finally {
                tmp.delete()
            }
        }
    }

    private fun downloadWithTransformer(
        media: DetectedMedia,
        onProgress: (Int) -> Unit,
        onSuccess: (Uri) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        main.post {
            val output = File(context.cacheDir, "remux_${System.currentTimeMillis()}.mp4")
            val progressHolder = ProgressHolder()
            lateinit var transformer: Transformer

            val poller = object : Runnable {
                override fun run() {
                    val state = transformer.getProgress(progressHolder)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress(progressHolder.progress.coerceIn(0, 100))
                    }
                    main.postDelayed(this, 500)
                }
            }

            transformer = Transformer.Builder(context)
                .setMuxerFactory(muxerFactory())
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, result: ExportResult) {
                        main.removeCallbacks(poller)
                        executor.execute {
                            try {
                                val uri = exportToMediaStore(
                                    output,
                                    nameFor(media.url, "mp4", media.quality),
                                    "video/mp4"
                                )
                                main.post { onSuccess(uri) }
                            } catch (t: Throwable) {
                                main.post { onError(t) }
                            } finally {
                                output.delete()
                            }
                        }
                    }

                    override fun onError(
                        composition: Composition,
                        result: ExportResult,
                        exception: ExportException
                    ) {
                        main.removeCallbacks(poller)
                        output.delete()
                        main.post { onError(exception) }
                    }
                })
                .build()

            main.postDelayed(poller, 500)
            val item = MediaItem.Builder().setUri(media.url).build()
            transformer.start(item, output.absolutePath)
        }
    }

    private fun exportToMediaStore(source: File, displayName: String, mimeType: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, mimeType)
            put(
                MediaStore.Video.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_MOVIES}/VideoDownloader"
            )
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("MediaStore insert failed")
        try {
            resolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IllegalStateException("MediaStore openOutputStream failed")

            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            Log.i(TAG, "exported $displayName -> $uri")
            return uri
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        }
    }

    /**
     * Pure-Java MP4 muxer. The platform MediaMuxer aborts on 16KB-page Android
     * images (API 37 x86_64); Media3's muxer is the safe default. Metadata
     * entries from Transformer are ignored because Media3 1.4.1 rejects them.
     */
    private fun muxerFactory(): Muxer.Factory = object : Muxer.Factory {
        override fun create(path: String): Muxer {
            val delegate = Mp4Muxer.Builder(FileOutputStream(path)).build()
            return object : Muxer by delegate {
                override fun addMetadataEntry(entry: Metadata.Entry) {}
            }
        }

        override fun getSupportedSampleMimeTypes(trackType: Int): ImmutableList<String> =
            when (trackType) {
                C.TRACK_TYPE_VIDEO -> ImmutableList.of(MimeTypes.VIDEO_H264)
                C.TRACK_TYPE_AUDIO -> ImmutableList.of(MimeTypes.AUDIO_AAC)
                else -> ImmutableList.of()
            }
    }

    private fun extensionOf(url: String): String {
        val path = url.substringBefore('?').substringBefore('#')
        val ext = path.substringAfterLast('.', "").lowercase()
        return if (ext in PROGRESSIVE_EXTENSIONS) ext else "mp4"
    }

    private fun mimeFor(extension: String): String = when (extension) {
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        else -> "video/mp4"
    }

    private fun nameFor(url: String, extension: String, quality: String?): String {
        val base = url.substringBefore('?').substringBefore('#').substringAfterLast('/')
        val stem = base.substringBeforeLast('.', base)
        val safe = stem.replace(Regex("[^A-Za-z0-9._-]"), "_").take(60).ifBlank { "video" }
        val qualitySuffix = quality
            ?.replace(Regex("[^A-Za-z0-9._-]"), "_")
            ?.take(16)
            ?.takeIf { it.isNotBlank() }
            ?.let { "_$it" }
            ?: ""
        return "vd_${System.currentTimeMillis()}_$safe$qualitySuffix.$extension"
    }

    companion object {
        private const val TAG = "VD_DOWNLOAD"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
        private val PROGRESSIVE_EXTENSIONS =
            setOf("mp4", "m4v", "mov", "webm", "mkv")
    }
}
