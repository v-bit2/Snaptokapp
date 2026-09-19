package com.example.service

import android.content.Context
import android.util.Log
import com.example.data.model.TikTokVideoInfo
import com.example.data.repository.VideoRepository
import com.example.data.storage.MediaSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

class VideoDownloader(
    private val context: Context,
    private val repository: VideoRepository = VideoRepository(context)
) {
    companion object {
        private const val TAG = "VideoDownloader"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    data class DownloadOutcome(
        val uriString: String,
        val filePath: String,
        val fileSize: Long,
        val videoInfo: TikTokVideoInfo
    )

    data class PhotoDownloadOutcome(
        val savedUris: List<String>,
        val primaryFilePath: String,
        val totalSize: Long,
        val postInfo: TikTokVideoInfo,
        val failedUrls: List<String> = emptyList()
    ) {
        val successfulCount: Int get() = savedUris.size
        val failedCount: Int get() = failedUrls.size
    }

    /**
     * Downloads a video stream with:
     * - Immediate download to prevent CDN link expiry
     * - HTTP response status and Content-Type inspection (rejecting HTML/JSON error payloads)
     * - Automatic refresh if CDN token has expired (HTTP 401/403/410)
     * - Stream flush & fsync before MediaStore insertion
     * - Magic bytes verification (MP4/WebM)
     * - MediaStore metadata synchronization
     */
    suspend fun downloadVideo(
        info: TikTokVideoInfo,
        preferHd: Boolean = true,
        onProgress: (percent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<DownloadOutcome> = withContext(Dispatchers.IO) {
        // Attempt download, with 1 retry on refreshed URL if CDN link expired
        var activeInfo = info
        var outcome = attemptDownloadVideo(activeInfo, preferHd, onProgress)

        if (outcome.isFailure) {
            val err = outcome.exceptionOrNull()
            Log.w(TAG, "[DL-RETRY] Initial download attempt failed: ${err?.message}. Checking if CDN link expired...")

            // If link expired or server returned HTML error, attempt to refresh URL once
            if (activeInfo.originalTiktokUrl.isNotBlank()) {
                val refreshResult = TikwmApiService.fetchVideoInfo(activeInfo.originalTiktokUrl)
                if (refreshResult.isSuccess) {
                    val freshInfo = refreshResult.getOrNull()
                    if (freshInfo != null && (freshInfo.playUrl != activeInfo.playUrl || freshInfo.hdPlayUrl != activeInfo.hdPlayUrl)) {
                        Log.i(TAG, "[DL-RETRY] Obtained fresh CDN link. Retrying download...")
                        activeInfo = freshInfo
                        outcome = attemptDownloadVideo(activeInfo, preferHd, onProgress)
                    }
                }
            }
        }

        outcome
    }

    private suspend fun attemptDownloadVideo(
        info: TikTokVideoInfo,
        preferHd: Boolean,
        onProgress: (percent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<DownloadOutcome> {
        val downloadUrl = if (preferHd && !info.hdPlayUrl.isNullOrBlank()) {
            info.hdPlayUrl
        } else {
            info.playUrl
        }

        if (downloadUrl.isBlank()) {
            return Result.failure(IOException("No download URL available for this video."))
        }

        Log.d(TAG, "[DL-START] Downloading video from URL: $downloadUrl")

        val request = Request.Builder()
            .url(downloadUrl)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 SnapTok/1.0")
            .header("Referer", "https://www.tiktok.com/")
            .header("Accept", "*/*")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                Log.d(TAG, "[DL-HTTP] Response code: ${response.code}, Content-Type: ${response.header("Content-Type")}")

                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()?.take(200) ?: ""
                    return Result.failure(
                        IOException("HTTP ${response.code} error from video server: $errorBody")
                    )
                }

                val body = response.body
                    ?: return Result.failure(IOException("Empty response body from video server"))

                // Check Content-Type: reject HTML, JSON, or plain text error pages
                val contentType = response.header("Content-Type")?.lowercase() ?: ""
                if (contentType.startsWith("text/html") ||
                    contentType.startsWith("application/json") ||
                    contentType.startsWith("text/plain") ||
                    contentType.startsWith("application/xml")
                ) {
                    val snippet = body.string().take(200)
                    Log.e(TAG, "[DL-ERR] Non-media Content-Type ($contentType): $snippet")
                    return Result.failure(
                        IOException("Server returned error page ($contentType) instead of video: $snippet")
                    )
                }

                val serverLength = body.contentLength()
                val declaredLength = if (serverLength > 0) serverLength else info.estimatedSizeBytes
                var lastReportedPercent = -1

                // Write and validate via MediaSaver pipeline
                val saveResult = MediaSaver.saveVideoToGallery(
                    context = context,
                    inputStream = body.byteStream(),
                    title = info.title,
                    expectedContentLength = serverLength
                ) { bytesWritten ->
                    val percent = if (declaredLength > 0) {
                        ((bytesWritten * 100) / declaredLength).toInt().coerceIn(0, 100)
                    } else {
                        50
                    }
                    if (percent != lastReportedPercent) {
                        lastReportedPercent = percent
                        onProgress(percent, bytesWritten, declaredLength)
                    }
                }

                // Final progress notification
                onProgress(100, saveResult.sizeBytes, saveResult.sizeBytes)

                // Record in Room Database
                repository.recordDownload(
                    info = info,
                    uriString = saveResult.uri.toString(),
                    filePath = saveResult.filePath,
                    fileSizeBytes = saveResult.sizeBytes
                )

                Log.i(TAG, "[DL-DONE] Video successfully saved & recorded: ${saveResult.filePath} (${saveResult.sizeBytes} bytes)")

                return Result.success(
                    DownloadOutcome(
                        uriString = saveResult.uri.toString(),
                        filePath = saveResult.filePath,
                        fileSize = saveResult.sizeBytes,
                        videoInfo = info
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "[DL-FAIL] Video download failed", e)
            return Result.failure(e)
        }
    }

    /**
     * Downloads photos in a photo post with:
     * - Isolated streams and temp files per image (no shared buffers or descriptors)
     * - Content-Type and HTTP status validation
     * - Image magic bytes signature detection (JPEG, PNG, WebP)
     * - True MIME type and file extension matching
     * - Per-image MediaStore sync
     */
    suspend fun downloadPhotoPost(
        info: TikTokVideoInfo,
        selectedUrls: List<String>,
        onProgress: (completedCount: Int, totalCount: Int, percent: Int) -> Unit
    ): Result<PhotoDownloadOutcome> = withContext(Dispatchers.IO) {
        if (selectedUrls.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("No photos selected for download."))
        }

        val totalCount = selectedUrls.size
        val savedUris = mutableListOf<String>()
        val failedUrls = mutableListOf<String>()
        var primaryFilePath = ""
        var totalBytes = 0L

        Log.d(TAG, "[DL-PHOTO-START] Downloading $totalCount photos for post: ${info.title}")

        for (index in selectedUrls.indices) {
            val imgUrl = selectedUrls[index]
            val outcome = attemptDownloadSingleImage(info, imgUrl, index, totalCount)

            outcome.fold(
                onSuccess = { saveResult ->
                    savedUris.add(saveResult.uri.toString())
                    if (primaryFilePath.isBlank()) {
                        primaryFilePath = saveResult.filePath
                    }
                    totalBytes += saveResult.sizeBytes
                },
                onFailure = { err ->
                    Log.w(TAG, "[DL-PHOTO-ERR] Failed downloading photo $index: ${err.message}")
                    failedUrls.add(imgUrl)
                }
            )

            val completed = index + 1
            val percent = ((completed * 100) / totalCount).coerceIn(0, 100)
            onProgress(completed, totalCount, percent)
        }

        if (savedUris.isEmpty()) {
            return@withContext Result.failure(
                IOException("Failed to download any of the selected photos. Please check your network or refresh the link.")
            )
        }

        // Record batch in Room Database
        repository.recordPhotoDownload(
            info = info,
            savedUris = savedUris,
            primaryFilePath = primaryFilePath,
            totalSizeBytes = totalBytes
        )

        Log.i(TAG, "[DL-PHOTO-DONE] Completed photo batch: ${savedUris.size}/$totalCount saved successfully.")

        Result.success(
            PhotoDownloadOutcome(
                savedUris = savedUris,
                primaryFilePath = primaryFilePath,
                totalSize = totalBytes,
                postInfo = info,
                failedUrls = failedUrls
            )
        )
    }

    private fun attemptDownloadSingleImage(
        info: TikTokVideoInfo,
        imgUrl: String,
        index: Int,
        totalCount: Int
    ): Result<MediaSaver.SaveResult> {
        val request = Request.Builder()
            .url(imgUrl)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 SnapTok/1.0")
            .header("Referer", "https://www.tiktok.com/")
            .header("Accept", "image/*,*/*")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string()?.take(150) ?: ""
                    return Result.failure(IOException("HTTP ${response.code} downloading image $index: $err"))
                }

                val body = response.body
                    ?: return Result.failure(IOException("Empty response body for image $index"))

                val contentType = response.header("Content-Type")?.lowercase() ?: ""
                if (contentType.startsWith("text/html") || contentType.startsWith("application/json")) {
                    val snippet = body.string().take(150)
                    return Result.failure(IOException("Server returned error page ($contentType) instead of image: $snippet"))
                }

                val serverLength = body.contentLength()

                val saveResult = MediaSaver.saveImageToGallery(
                    context = context,
                    inputStream = body.byteStream(),
                    title = info.title,
                    index = index,
                    total = totalCount,
                    expectedContentLength = serverLength
                )

                Result.success(saveResult)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
