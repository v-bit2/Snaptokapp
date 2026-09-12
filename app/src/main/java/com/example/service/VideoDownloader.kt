package com.example.service

import android.content.Context
import com.example.data.model.TikTokVideoInfo
import com.example.data.repository.VideoRepository
import com.example.data.storage.MediaSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class VideoDownloader(
    private val context: Context,
    private val repository: VideoRepository = VideoRepository(context)
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
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

    suspend fun downloadVideo(
        info: TikTokVideoInfo,
        preferHd: Boolean = true,
        onProgress: (percent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<DownloadOutcome> = withContext(Dispatchers.IO) {
        val downloadUrl = if (preferHd && !info.hdPlayUrl.isNullOrBlank()) {
            info.hdPlayUrl
        } else {
            info.playUrl
        }

        val request = Request.Builder()
            .url(downloadUrl)
            .header("User-Agent", "SnapTok/1.0 (Android)")
            .header("Referer", "https://www.tiktok.com/")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("Failed to download video stream (HTTP ${response.code})")
                    )
                }

                val body = response.body
                    ?: return@withContext Result.failure(IOException("Empty video stream"))

                val contentLength = body.contentLength().let { if (it > 0) it else info.estimatedSizeBytes }
                val inputStream = body.byteStream()

                var lastReportedPercent = -1

                val saveResult = MediaSaver.saveVideoToGallery(
                    context = context,
                    inputStream = inputStream,
                    title = info.title,
                    contentLength = contentLength
                ) { bytesWritten ->
                    val percent = if (contentLength > 0) {
                        ((bytesWritten * 100) / contentLength).toInt().coerceIn(0, 100)
                    } else {
                        50
                    }
                    if (percent != lastReportedPercent) {
                        lastReportedPercent = percent
                        onProgress(percent, bytesWritten, contentLength)
                    }
                }

                // Record in Room Database
                repository.recordDownload(
                    info = info,
                    uriString = saveResult.uri.toString(),
                    filePath = saveResult.filePath,
                    fileSizeBytes = saveResult.sizeBytes
                )

                Result.success(
                    DownloadOutcome(
                        uriString = saveResult.uri.toString(),
                        filePath = saveResult.filePath,
                        fileSize = saveResult.sizeBytes,
                        videoInfo = info
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

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

        for (index in selectedUrls.indices) {
            val imgUrl = selectedUrls[index]
            val request = Request.Builder()
                .url(imgUrl)
                .header("User-Agent", "SnapTok/1.0 (Android)")
                .header("Referer", "https://www.tiktok.com/")
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        failedUrls.add(imgUrl)
                    } else {
                        val body = response.body
                        if (body == null) {
                            failedUrls.add(imgUrl)
                        } else {
                            val saveResult = MediaSaver.saveImageToGallery(
                                context = context,
                                inputStream = body.byteStream(),
                                title = info.title,
                                index = index,
                                total = totalCount
                            )
                            savedUris.add(saveResult.uri.toString())
                            if (primaryFilePath.isBlank()) {
                                primaryFilePath = saveResult.filePath
                            }
                            totalBytes += saveResult.sizeBytes
                        }
                    }
                }
            } catch (e: Exception) {
                failedUrls.add(imgUrl)
            }

            val completed = index + 1
            val percent = ((completed * 100) / totalCount).coerceIn(0, 100)
            onProgress(completed, totalCount, percent)
        }

        if (savedUris.isEmpty()) {
            return@withContext Result.failure(
                IOException("Failed to download any of the selected images. Please check your network and try again.")
            )
        }

        // Record batch in Room Database
        repository.recordPhotoDownload(
            info = info,
            savedUris = savedUris,
            primaryFilePath = primaryFilePath,
            totalSizeBytes = totalBytes
        )

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
}
