package com.example.service

import com.example.data.model.TikTokVideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object TikwmApiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val TIKTOK_URL_REGEX = Pattern.compile(
        """https?://([a-zA-Z0-9_-]+\.)?tiktok\.com/[^\s"']+""",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Extracts a clean TikTok URL from arbitrary text (e.g., shared via share intent or clipboard).
     */
    fun extractTikTokUrl(text: String): String? {
        val matcher = TIKTOK_URL_REGEX.matcher(text.trim())
        if (matcher.find()) {
            return matcher.group()
        }
        val trimmed = text.trim()
        if (trimmed.contains("tiktok.com", ignoreCase = true)) {
            val startIdx = trimmed.indexOf("http", ignoreCase = true)
            if (startIdx >= 0) {
                val sub = trimmed.substring(startIdx)
                val endIdx = sub.indexOfAny(charArrayOf(' ', '\n', '\t', '"', '\''))
                return if (endIdx > 0) sub.substring(0, endIdx) else sub
            }
        }
        return null
    }

    /**
     * Fetches video info from TikWM API with 1 automatic retry on network failure.
     */
    suspend fun fetchVideoInfo(rawUrl: String): Result<TikTokVideoInfo> = withContext(Dispatchers.IO) {
        val tiktokUrl = extractTikTokUrl(rawUrl)
            ?: return@withContext Result.failure(
                IllegalArgumentException("Invalid TikTok URL. Please provide a valid TikTok video link.")
            )

        var attempts = 0
        var lastException: Exception? = null

        while (attempts < 2) {
            attempts++
            try {
                return@withContext performApiCall(tiktokUrl)
            } catch (e: Exception) {
                lastException = e
                if (attempts < 2) {
                    delay(1000) // Brief pause before retry
                }
            }
        }

        val errorMessage = when (lastException) {
            is IOException -> "Network connection failed. Please check your internet connection."
            else -> lastException?.localizedMessage ?: "Failed to fetch video details."
        }
        Result.failure(Exception(errorMessage, lastException))
    }

    private fun performApiCall(tiktokUrl: String): Result<TikTokVideoInfo> {
        val formBody = FormBody.Builder()
            .add("url", tiktokUrl)
            .add("hd", "1")
            .build()

        val request = Request.Builder()
            .url("https://www.tikwm.com/api/")
            .post(formBody)
            .header("User-Agent", "SnapTok/1.0 (Android)")
            .header("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return Result.failure(
                    IOException("Server returned error code: ${response.code}. Please try again.")
                )
            }

            val bodyString = response.body?.string()
                ?: return Result.failure(IOException("Received empty response from server."))

            val json = try {
                JSONObject(bodyString)
            } catch (e: Exception) {
                return Result.failure(Exception("Failed to parse server response."))
            }

            val code = json.optInt("code", -1)
            val msg = json.optString("msg", "Unknown error")

            if (code != 0) {
                val friendlyMessage = when {
                    msg.contains("url", ignoreCase = true) -> "Invalid or unsupported TikTok link."
                    msg.contains("not found", ignoreCase = true) -> "Video not found or deleted by creator."
                    msg.contains("private", ignoreCase = true) -> "This TikTok video is from a private account."
                    msg.contains("rate", ignoreCase = true) -> "Too many requests. Please wait a moment."
                    else -> "Unable to download video: $msg"
                }
                return Result.failure(Exception(friendlyMessage))
            }

            val data = json.optJSONObject("data")
                ?: return Result.failure(Exception("Video data not found in response."))

            val videoId = data.optString("id", System.currentTimeMillis().toString())
            val title = data.optString("title", "TikTok Video").trim()
            val coverUrl = data.optString("cover", "")
            val playUrl = data.optString("play", "")
            val hdPlayUrl = data.optString("hdplay", "").ifBlank { null }
            val duration = data.optInt("duration", 0)
            val size = data.optLong("size", 0L)

            val authorObj = data.optJSONObject("author")
            val authorUsername = authorObj?.optString("unique_id", "tiktok_user") ?: "tiktok_user"
            val authorNickname = authorObj?.optString("nickname", authorUsername) ?: authorUsername
            val authorAvatar = authorObj?.optString("avatar", "") ?: ""

            val musicObj = data.optJSONObject("music_info")
            val musicTitle = musicObj?.optString("title", null)

            // Resolve relative URLs if needed
            val resolvedPlayUrl = if (playUrl.startsWith("/")) "https://www.tikwm.com$playUrl" else playUrl
            val resolvedHdPlayUrl = hdPlayUrl?.let { if (it.startsWith("/")) "https://www.tikwm.com$it" else it }
            val resolvedCoverUrl = if (coverUrl.startsWith("/")) "https://www.tikwm.com$coverUrl" else coverUrl

            if (resolvedPlayUrl.isBlank()) {
                return Result.failure(Exception("No-watermark video stream URL is unavailable for this video."))
            }

            val videoInfo = TikTokVideoInfo(
                id = videoId,
                title = title,
                coverUrl = resolvedCoverUrl,
                playUrl = resolvedPlayUrl,
                hdPlayUrl = resolvedHdPlayUrl,
                durationSeconds = duration,
                authorNickname = authorNickname,
                authorUsername = authorUsername,
                authorAvatarUrl = authorAvatar,
                musicTitle = musicTitle,
                originalTiktokUrl = tiktokUrl,
                estimatedSizeBytes = size
            )

            return Result.success(videoInfo)
        }
    }
}
