package com.videodownloader.spike

enum class MediaKind(val label: String) {
    HLS("HLS"),
    DASH("DASH"),
    PROGRESSIVE("影片檔"),
}

/**
 * One downloadable item. For a stream master playlist, every resolution
 * becomes a separate [DetectedMedia] with [quality] set (for example `1080p`).
 */
data class DetectedMedia(
    val url: String,
    val kind: MediaKind,
    val quality: String? = null,
) {
    val displayKind: String
        get() = if (quality.isNullOrBlank()) kind.label else "${kind.label} $quality"
}
