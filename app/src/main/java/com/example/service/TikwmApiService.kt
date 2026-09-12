package com.example.service

import com.example.data.model.TikTokPostType
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
     * Fetches video or photo-post info from TikWM API with 1 automatic retry on network failure.
     */
    suspend fun fetchVideoInfo(rawUrl: String): Result<TikTokVideoInfo> = withContext(Dispatchers.IO) {
        val tiktokUrl = extractTikTokUrl(rawUrl)
            ?: return@withContext Result.failure(
                IllegalArgumentException("Invalid TikTok URL. Please provide a valid TikTok link.")
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
            else -> lastException?.localizedMessage ?: "Failed to fetch TikTok details."
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
                    msg.contains("not found", ignoreCase = true) -> "Post not found or deleted by creator."
                    msg.contains("private", ignoreCase = true) -> "This TikTok post is from a private account."
                    msg.contains("rate", ignoreCase = true) -> "Too many requests. Please wait a moment."
                    else -> "Unable to process TikTok link: $msg"
                }
                return Result.failure(Exception(friendlyMessage))
            }

            val data = json.optJSONObject("data")
                ?: return Result.failure(Exception("Post data not found in response."))

            val videoId = data.optString("id", System.currentTimeMillis().toString())
            val title = data.optString("title", "TikTok Post").trim()
            val rawCoverUrl = data.optString("cover", "")
            val rawPlayUrl = data.optString("play", "")
            val rawHdPlayUrl = data.optString("hdplay", "").ifBlank { null }
            val duration = data.optInt("duration", 0)
            val rawSize = data.optLong("size", 0L)

            val authorObj = data.optJSONObject("author")
            val authorUsername = authorObj?.optString("unique_id", "tiktok_user")?.ifBlank { "tiktok_user" } ?: "tiktok_user"
            val authorNickname = authorObj?.optString("nickname", authorUsername)?.ifBlank { authorUsername } ?: authorUsername
            val rawAvatar = authorObj?.optString("avatar", "") ?: ""

            val musicObj = data.optJSONObject("music_info")
            val musicTitle = musicObj?.optString("title", "")?.ifBlank { null }

            // Extract Photo-post images if available
            val imageList = mutableListOf<String>()
            val imagesJson = data.optJSONArray("images") ?: data.optJSONArray("photo")
            if (imagesJson != null) {
                for (i in 0 until imagesJson.length()) {
                    val rawImg = imagesJson.optString(i, "")
                    if (rawImg.isNotBlank()) {
                        val resolvedImg = if (rawImg.startsWith("/")) "https://www.tikwm.com$rawImg" else rawImg
                        imageList.add(resolvedImg)
                    }
                }
            }

            val isPhotoPost = imageList.isNotEmpty()
            val postType = if (isPhotoPost) TikTokPostType.PHOTO else TikTokPostType.VIDEO

            // Resolve relative URLs if needed
            val resolvedPlayUrl = if (rawPlayUrl.startsWith("/")) "https://www.tikwm.com$rawPlayUrl" else rawPlayUrl
            val resolvedHdPlayUrl = rawHdPlayUrl?.let { if (it.startsWith("/")) "https://www.tikwm.com$it" else it }
            val resolvedCoverUrl = when {
                rawCoverUrl.startsWith("/") -> "https://www.tikwm.com$rawCoverUrl"
                rawCoverUrl.isNotBlank() -> rawCoverUrl
                imageList.isNotEmpty() -> imageList.first()
                else -> ""
            }
            val resolvedAvatar = if (rawAvatar.startsWith("/")) "https://www.tikwm.com$rawAvatar" else rawAvatar

            // If it's a video post, verify stream URL
            if (!isPhotoPost && resolvedPlayUrl.isBlank()) {
                return Result.failure(Exception("No-watermark video stream URL is unavailable for this video."))
            }

            // Estimate size for photo posts if not returned
            val finalEstimatedSize = if (isPhotoPost && rawSize <= 0) {
                imageList.size * 450_000L
            } else {
                rawSize
            }

            val postInfo = TikTokVideoInfo(
                id = videoId,
                title = title.ifBlank { if (isPhotoPost) "TikTok Photo Slideshow" else "TikTok Video" },
                coverUrl = resolvedCoverUrl,
                playUrl = resolvedPlayUrl,
                hdPlayUrl = resolvedHdPlayUrl,
                durationSeconds = duration,
                authorNickname = authorNickname,
                authorUsername = authorUsername,
                authorAvatarUrl = resolvedAvatar,
                musicTitle = musicTitle,
                originalTiktokUrl = tiktokUrl,
                estimatedSizeBytes = finalEstimatedSize,
                postType = postType,
                images = imageList
            )

            return Result.success(postInfo)
        }
    }
}
