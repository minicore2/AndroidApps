package com.videodownloader.spike

import android.content.Context
import android.net.Uri
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Small EasyList-compatible filter engine.
 *
 * Supported syntax (options after `$` are ignored):
 *  - `||example.com^`      host-anchored network rule
 *  - `|https://x/y`        URL-prefix rule
 *  - `/ads/`, `adserver`       substring rules
 *  - `@@||example.com^`    exception rule
 *  - `##.ad`               generic cosmetic rule
 *  - `example.com##.ad`    domain-scoped cosmetic rule
 *
 * The bundled `assets/adblock/rules.txt` is a curated starter list; replace it
 * with a full EasyList build for production.
 */
class AdBlocker(context: Context) {

    private val networkRules = ArrayList<NetworkRule>()
    private val exceptionRules = ArrayList<NetworkRule>()
    private val genericCosmetic = ArrayList<String>()
    private val hostCosmetic = HashMap<String, MutableList<String>>()

    init {
        val text = context.assets.open("adblock/rules.txt")
            .bufferedReader()
            .use { it.readText() }
        text.lineSequence().forEach { parseLine(it) }
        Log.i(
            TAG,
            "rules loaded: network=${networkRules.size} exceptions=${exceptionRules.size} " +
                "genericCosmetic=${genericCosmetic.size} hostCosmetic=${hostCosmetic.size}"
        )
    }

    /** Returns true when a request should be answered with an empty 204 response. */
    fun shouldBlock(url: String): Boolean {
        val lower = url.lowercase()
        val host = runCatching { Uri.parse(url).host?.lowercase() }.getOrNull() ?: return false
        if (exceptionRules.any { it.matches(lower, host) }) return false
        return networkRules.any { it.matches(lower, host) }
    }

    /**
     * Script injected at document-start. It applies host-aware cosmetic rules and
     * neutralises `window.open` so popups never reach `onCreateWindow`.
     */
    fun documentStartScript(): String {
        val generic = JSONArray()
        genericCosmetic.forEach { generic.put(it) }
        val hosts = JSONObject()
        hostCosmetic.forEach { (domain, selectors) ->
            val array = JSONArray()
            selectors.forEach { array.put(it) }
            hosts.put(domain, array)
        }
        return """
            (function () {
              try {
                var generic = $generic;
                var hostRules = $hosts;
                var h = (location.hostname || '').toLowerCase();
                var sels = generic.slice();
                for (var d in hostRules) {
                  if (h === d || h.slice(-(d.length + 1)) === '.' + d) {
                    sels = sels.concat(hostRules[d]);
                  }
                }
                if (sels.length) {
                  var css = sels.map(function (s) { return s + '{display:none!important}'; }).join(',');
                  var style = document.createElement('style');
                  style.textContent = css;
                  (document.head || document.documentElement).appendChild(style);
                }
                window.open = function () { return null; };
              } catch (e) {}
            })();
        """.trimIndent()
    }

    private fun parseLine(rawLine: String) {
        val line = rawLine.trim()
        if (line.isEmpty() || line.startsWith("!") || line.startsWith("[")) return

        val atHash = line.indexOf("#@#")
        if (atHash >= 0) return // cosmetic exceptions are not supported yet

        val hash = line.indexOf("##")
        if (hash >= 0) {
            val domainsPart = line.substring(0, hash)
            val selector = line.substring(hash + 2).trim()
            if (selector.isEmpty() || selector.startsWith("+js(")) return
            if (domainsPart.isBlank()) {
                genericCosmetic.add(selector)
                return
            }
            domainsPart.split(',').forEach { domain ->
                val d = domain.trim().lowercase()
                if (d.isNotEmpty() && !d.startsWith("~")) {
                    hostCosmetic.getOrPut(d) { ArrayList() }.add(selector)
                }
            }
            return
        }

        var pattern = line.substringBefore('$').trim()
        if (pattern.isEmpty()) return

        val isException = pattern.startsWith("@@")
        if (isException) pattern = pattern.substring(2)

        val hostAnchor = pattern.startsWith("||")
        if (hostAnchor) pattern = pattern.substring(2)

        val prefixAnchor = !hostAnchor && pattern.startsWith("|")
        if (prefixAnchor) pattern = pattern.substring(1)

        pattern = pattern.removeSuffix("|").replace("^", "")
        if (pattern.isEmpty()) return

        val rule = NetworkRule(pattern.lowercase(), hostAnchor, prefixAnchor)
        if (isException) exceptionRules.add(rule) else networkRules.add(rule)
    }

    private class NetworkRule(
        private val pattern: String,
        private val hostAnchor: Boolean,
        private val prefixAnchor: Boolean
    ) {
        fun matches(lowerUrl: String, host: String): Boolean {
            if (hostAnchor) {
                val domain = pattern.substringBefore('/')
                val path = pattern.substringAfter('/', "")
                val hostMatches = host == domain || host.endsWith(".$domain")
                val pathMatches = path.isEmpty() || containsWildcard(lowerUrl, "/$path")
                return hostMatches && pathMatches
            }
            if (prefixAnchor) {
                val prefix = pattern.substringBefore('*')
                return lowerUrl.startsWith(prefix)
            }
            return containsWildcard(lowerUrl, pattern)
        }

        /** EasyList `*` wildcard: all literal segments must appear in order. */
        private fun containsWildcard(haystack: String, needle: String): Boolean {
            if (!needle.contains('*')) return haystack.contains(needle)
            var from = 0
            needle.split('*').forEach { part ->
                if (part.isEmpty()) return@forEach
                val found = haystack.indexOf(part, from)
                if (found < 0) return false
                from = found + part.length
            }
            return true
        }
    }

    companion object {
        private const val TAG = "VD_ADBLOCK"
    }
}
