package com.example.data.youtube

import com.example.data.model.YouTubeSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class YouTubeSearchRepository(private val appOrigin: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun searchTracks(query: String, maxResults: Int = 12): Result<List<YouTubeSearchResult>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val trimmed = query.trim()
                if (trimmed.isEmpty()) return@runCatching emptyList()

                extractVideoId(trimmed)?.let { directId ->
                    val resolved = resolveYouTubeUrlInternal(directId)
                    val embedOk = isVideoEmbeddable(directId)
                    if (!embedOk) {
                        throw IllegalStateException("This YouTube video has embedding disabled (Error 150) and cannot be added.")
                    }
                    return@runCatching listOf(resolved.copy(isEmbeddable = true))
                }

                val encodedQuery = URLEncoder.encode(trimmed, "UTF-8")
                val url = "https://www.youtube.com/results?search_query=$encodedQuery&sp=EgIQAQ%253D%253D"
                val request = Request.Builder()
                    .url(url)
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                    )
                    .header("Accept-Language", "en-US,en;q=0.9,el;q=0.8")
                    .build()

                val html = client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("YouTube HTTP ${response.code}")
                    }
                    response.body?.string().orEmpty()
                }

                val jsonStr = extractYtInitialDataJson(html)
                    ?: throw IllegalStateException("No YouTube results found")

                val root = JSONObject(jsonStr)
                val rawResults = mutableListOf<YouTubeSearchResult>()
                val seenIds = mutableSetOf<String>()
                collectVideoRenderers(root, rawResults, seenIds, 24)

                // Verify playableInEmbed in parallel and completely exclude any restricted (Error 150) items
                val embeddableOnly = coroutineScope {
                    rawResults.map { item ->
                        async {
                            if (isVideoEmbeddable(item.videoId)) item.copy(isEmbeddable = true) else null
                        }
                    }.awaitAll().filterNotNull()
                }
                embeddableOnly.take(maxResults)
            }
        }

    /**
     * Searches YouTube and picks the first candidate whose watch page confirms playableInEmbed=true.
     */
    suspend fun findFirstEmbeddableTrack(
        query: String,
        excludeVideoIds: Set<String> = emptySet()
    ): YouTubeSearchResult? = withContext(Dispatchers.IO) {
        val candidates = searchTracks(query, maxResults = 10).getOrNull().orEmpty()
            .filterNot { it.videoId in excludeVideoIds }

        candidates.firstOrNull()
    }

    suspend fun isVideoEmbeddable(videoId: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val watchUrl = "https://www.youtube.com/watch?v=$videoId"
            val req = Request.Builder()
                .url(watchUrl)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                )
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching false
                val html = resp.body?.string().orEmpty()
                when {
                    html.contains("\"playableInEmbed\":false", ignoreCase = true) -> false
                    html.contains("\"playableInEmbed\":true", ignoreCase = true) -> {
                        !html.contains("\"status\":\"LOGIN_REQUIRED\"", ignoreCase = true) &&
                            !html.contains("\"status\":\"UNPLAYABLE\"", ignoreCase = true)
                    }
                    else -> false
                }
            }
        }.getOrDefault(false)
    }

    suspend fun resolveYouTubeUrl(urlOrId: String): Result<YouTubeSearchResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val videoId = extractVideoId(urlOrId.trim())
                    ?: throw IllegalArgumentException("Invalid YouTube link")
                resolveYouTubeUrlInternal(videoId)
            }
        }

    private fun resolveYouTubeUrlInternal(videoId: String): YouTubeSearchResult {
        val oembedUrl = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$videoId&format=json"
        val request = Request.Builder()
            .url(oembedUrl)
            .header("User-Agent", "Mozilla/5.0")
            .build()

        var title = "YouTube Track ($videoId)"
        var author = "YouTube"
        var thumb = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

        runCatching {
            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    if (body.isNotBlank()) {
                        val json = JSONObject(body)
                        title = json.optString("title", title)
                        author = json.optString("author_name", author)
                        thumb = json.optString("thumbnail_url", thumb)
                    }
                }
            }
        }

        return YouTubeSearchResult(
            videoId = videoId,
            title = title,
            channelName = author,
            durationText = "3:30",
            durationSeconds = 210,
            thumbnailUrl = thumb,
            estimatedBpm = estimateBpmFromTitle(title)
        )
    }

    private fun extractYtInitialDataJson(html: String): String? {
        val markers = listOf("var ytInitialData = ", "window[\"ytInitialData\"] = ")
        for (marker in markers) {
            val startIdx = html.indexOf(marker)
            if (startIdx >= 0) {
                val braceStart = html.indexOf('{', startIdx + marker.length)
                if (braceStart >= 0) {
                    var depth = 0
                    var inString = false
                    var escaped = false
                    for (i in braceStart until html.length) {
                        val c = html[i]
                        if (escaped) {
                            escaped = false
                            continue
                        }
                        if (c == '\\' && inString) {
                            escaped = true
                            continue
                        }
                        if (c == '"') {
                            inString = !inString
                            continue
                        }
                        if (!inString) {
                            if (c == '{') depth++
                            else if (c == '}') {
                                depth--
                                if (depth == 0) {
                                    return html.substring(braceStart, i + 1)
                                }
                            }
                        }
                    }
                }
            }
        }
        return null
    }

    private fun collectVideoRenderers(
        node: Any?,
        out: MutableList<YouTubeSearchResult>,
        seenIds: MutableSet<String>,
        maxResults: Int
    ) {
        if (out.size >= maxResults || node == null) return
        when (node) {
            is JSONObject -> {
                if (node.has("videoRenderer")) {
                    val renderer = node.optJSONObject("videoRenderer")
                    if (renderer != null) {
                        parseVideoRenderer(renderer)?.let { item ->
                            if (seenIds.add(item.videoId)) {
                                out.add(item)
                            }
                        }
                    }
                }
                val keys = node.keys()
                while (keys.hasNext() && out.size < maxResults) {
                    val key = keys.next()
                    if (key != "videoRenderer") {
                        collectVideoRenderers(node.opt(key), out, seenIds, maxResults)
                    }
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    if (out.size >= maxResults) break
                    collectVideoRenderers(node.opt(i), out, seenIds, maxResults)
                }
            }
        }
    }

    private fun parseVideoRenderer(renderer: JSONObject): YouTubeSearchResult? {
        val videoId = renderer.optString("videoId").takeIf { it.length == 11 } ?: return null
        val title = readTextField(renderer.optJSONObject("title"))?.takeIf { it.isNotBlank() } ?: return null
        val channel = readTextField(renderer.optJSONObject("ownerText"))
            ?: readTextField(renderer.optJSONObject("longBylineText"))
            ?: "YouTube"
        val lengthText = readTextField(renderer.optJSONObject("lengthText")) ?: "3:30"
        val durationSeconds = parseDurationToSeconds(lengthText)
        if (durationSeconds <= 0) return null

        val thumbs = renderer.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        val thumbUrl = if (thumbs != null && thumbs.length() > 0) {
            thumbs.optJSONObject(thumbs.length() - 1)?.optString("url")
                ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        } else {
            "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        }

        return YouTubeSearchResult(
            videoId = videoId,
            title = title,
            channelName = channel,
            durationText = lengthText,
            durationSeconds = durationSeconds,
            thumbnailUrl = thumbUrl,
            estimatedBpm = estimateBpmFromTitle(title)
        )
    }

    private fun readTextField(obj: JSONObject?): String? {
        if (obj == null) return null
        val simple = obj.optString("simpleText")
        if (simple.isNotBlank()) return simple
        val runs = obj.optJSONArray("runs") ?: return null
        val sb = StringBuilder()
        for (i in 0 until runs.length()) {
            sb.append(runs.optJSONObject(i)?.optString("text").orEmpty())
        }
        return sb.toString().takeIf { it.isNotBlank() }
    }

    private fun parseDurationToSeconds(text: String): Int {
        val parts = text.trim().split(":").mapNotNull { it.toIntOrNull() }
        return when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> 0
        }
    }

    private fun estimateBpmFromTitle(title: String): Int {
        val lower = title.lowercase()
        return when {
            lower.contains("techno") || lower.contains("hard") -> 130
            lower.contains("drum") || lower.contains("dnb") -> 170
            lower.contains("hip hop") || lower.contains("trap") -> 94
            lower.contains("lofi") || lower.contains("chill") -> 92
            lower.contains("afro") || lower.contains("deep") -> 120
            lower.contains("house") || lower.contains("remix") -> 124
            else -> 120
        }
    }

    companion object {
        private val VIDEO_ID_REGEX = Regex(
            "(?:youtube\\.com/(?:watch\\?(?:.*&)?v=|embed/|shorts/)|youtu\\.be/|music\\.youtube\\.com/watch\\?(?:.*&)?v=)([A-Za-z0-9_-]{11})"
        )

        fun extractVideoId(input: String): String? {
            if (input.matches(Regex("^[A-Za-z0-9_-]{11}$"))) return input
            return VIDEO_ID_REGEX.find(input)?.groupValues?.getOrNull(1)
        }
    }
}
