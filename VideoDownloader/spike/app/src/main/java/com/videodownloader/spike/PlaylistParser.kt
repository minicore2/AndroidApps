package com.videodownloader.spike

import java.io.StringReader
import java.net.URI
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToInt
import org.w3c.dom.Element
import org.xml.sax.InputSource

/**
 * Pure playlist parsing (no Android APIs) so both formats can be unit-tested.
 *
 * An HLS master playlist is expanded into one [DetectedMedia] per variant.
 * A DASH manifest is expanded per `Representation` only when that representation
 * has a standalone `BaseURL`; `SegmentTemplate`-based manifests stay a single
 * item because there is no standalone URL to hand to Media3.
 */
object PlaylistParser {

    private val resolutionRegex =
        Regex("""(?:^|,)\s*RESOLUTION=(\d+)x(\d+)""", RegexOption.IGNORE_CASE)
    private val bandwidthRegex =
        Regex("""(?:^|,)\s*BANDWIDTH=(\d+)""", RegexOption.IGNORE_CASE)
    private val widthHeightInUrlRegex =
        Regex("""(?:^|[^0-9])(\d{3,4})x(\d{3,4})(?:[^0-9]|$)""", RegexOption.IGNORE_CASE)
    private val qualityInUrlRegex =
        Regex("""(?:^|[^0-9])(\d{3,4})p(?:\d{1,3})?(?:[^0-9]|$)""", RegexOption.IGNORE_CASE)

    /** Best-effort quality hint from a filename such as `movie_1080p.mp4`. */
    fun qualityFromUrl(url: String): String? {
        widthHeightInUrlRegex.find(url)?.let { match ->
            match.groupValues.getOrNull(2)?.toIntOrNull()?.let { return "${it}p" }
        }
        qualityInUrlRegex.find(url)?.let { match ->
            match.groupValues.getOrNull(1)?.toIntOrNull()?.let { return "${it}p" }
        }
        return null
    }

    fun parseHls(baseUrl: String, text: String): List<DetectedMedia> {
        val lines = text.lineSequence().map { it.trim() }.toList()
        val variants = mutableListOf<DetectedMedia>()
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            if (!line.startsWith("#EXT-X-STREAM-INF:", ignoreCase = true)) {
                index++
                continue
            }

            val attributes = line.substringAfter(':')
            val resolution = resolutionRegex.find(attributes)
            val bandwidth = bandwidthRegex.find(attributes)
                ?.groupValues
                ?.getOrNull(1)
                ?.toLongOrNull()

            // The URI is the next non-blank, non-tag line.
            var uriIndex = index + 1
            while (uriIndex < lines.size &&
                (lines[uriIndex].isBlank() || lines[uriIndex].startsWith("#"))
            ) {
                uriIndex++
            }
            if (uriIndex >= lines.size) break

            val variantUrl = resolveUrl(baseUrl, lines[uriIndex])
            if (variantUrl != null) {
                val height = resolution?.groupValues?.getOrNull(2)?.toIntOrNull()
                val quality = when {
                    height != null -> "${height}p"
                    bandwidth != null -> bandwidthLabel(bandwidth)
                    else -> qualityFromUrl(variantUrl)
                }
                variants += DetectedMedia(variantUrl, MediaKind.HLS, quality)
            }
            index = uriIndex + 1
        }
        return variants.distinctBy { it.url }
    }

    fun parseDash(baseUrl: String, text: String): List<DetectedMedia> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isExpandEntityReferences = false
            // The manifest is remote, untrusted XML; disable external entities
            // where the platform parser supports it.
            runCatching {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            }
            runCatching {
                setFeature("http://xml.org/sax/features/external-general-entities", false)
            }
            runCatching {
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            }
            runCatching {
                setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "")
            }
            runCatching {
                setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "")
            }
        }
        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(text)))
        val representations = document.getElementsByTagName("Representation")
        val variants = mutableListOf<DetectedMedia>()

        for (i in 0 until representations.length) {
            val representation = representations.item(i) as? Element ?: continue
            val adaptationSet = representation.parentNode as? Element
            val mimeType = representation.getAttribute("mimeType").takeIf { it.isNotBlank() }
                ?: adaptationSet?.getAttribute("mimeType")?.takeIf { it.isNotBlank() }
            val height = representation.getAttribute("height").toIntOrNull() ?: 0
            val bandwidth = representation.getAttribute("bandwidth").toLongOrNull() ?: 0L

            if (mimeType != null && !mimeType.startsWith("video/")) continue
            if (mimeType == null && height <= 0) continue

            // A SegmentTemplate/SegmentList representation is assembled from
            // many segment URLs; its BaseURL (if any) is not a standalone file.
            if (representation.getElementsByTagName("SegmentTemplate").length > 0 ||
                representation.getElementsByTagName("SegmentList").length > 0
            ) {
                continue
            }

            val baseUrls = representation.getElementsByTagName("BaseURL")
            if (baseUrls.length == 0) continue
            val base = baseUrls.item(0)?.textContent?.trim().orEmpty()
            if (base.isBlank()) continue
            val resolved = resolveUrl(baseUrl, base) ?: continue
            if (resolved.endsWith("/")) continue

            val quality = when {
                height > 0 -> "${height}p"
                bandwidth > 0 -> bandwidthLabel(bandwidth)
                else -> qualityFromUrl(resolved)
            }
            variants += DetectedMedia(resolved, MediaKind.DASH, quality)
        }
        return variants.distinctBy { it.url }
    }

    private fun bandwidthLabel(bandwidth: Long): String =
        if (bandwidth >= 1_000_000) {
            "${(bandwidth / 1_000_000.0).roundToInt()}Mbps"
        } else {
            "${bandwidth / 1000}Kbps"
        }

    private fun resolveUrl(baseUrl: String, reference: String): String? {
        val trimmed = reference.trim()
        if (trimmed.isEmpty()) return null
        return try {
            URI(baseUrl).resolve(trimmed).toString()
        } catch (t: Throwable) {
            when {
                trimmed.startsWith("http://", ignoreCase = true) ||
                    trimmed.startsWith("https://", ignoreCase = true) -> trimmed
                else -> null
            }
        }
    }
}
