package com.example.data.repository

import android.content.Context
import com.example.data.db.AppDatabase
import com.example.data.db.VideoDownloadDao
import com.example.data.entity.DownloadedVideoEntity
import com.example.data.model.TikTokVideoInfo
import com.example.data.storage.MediaSaver
import kotlinx.coroutines.flow.Flow

class VideoRepository(
    private val context: Context,
    private val dao: VideoDownloadDao = AppDatabase.getInstance(context).videoDownloadDao()
) {
    val allDownloads: Flow<List<DownloadedVideoEntity>> = dao.getAllDownloads()

    fun searchDownloads(query: String): Flow<List<DownloadedVideoEntity>> {
        return if (query.isBlank()) dao.getAllDownloads() else dao.searchDownloads(query.trim())
    }

    suspend fun recordDownload(
        info: TikTokVideoInfo,
        uriString: String,
        filePath: String,
        fileSizeBytes: Long
    ): Long {
        val entity = DownloadedVideoEntity(
            title = info.title.ifBlank { "TikTok Video by @${info.authorUsername}" },
            authorName = info.authorNickname.ifBlank { info.authorUsername },
            authorHandle = info.authorUsername,
            authorAvatarUrl = info.authorAvatarUrl,
            coverUrl = info.coverUrl,
            videoUri = uriString,
            filePath = filePath,
            fileSizeBytes = fileSizeBytes,
            durationSeconds = info.durationSeconds,
            originalUrl = info.originalTiktokUrl,
            postType = "video",
            photoUrls = "",
            photoCount = 1
        )
        return dao.insertDownload(entity)
    }

    suspend fun recordPhotoDownload(
        info: TikTokVideoInfo,
        savedUris: List<String>,
        primaryFilePath: String,
        totalSizeBytes: Long
    ): Long {
        val entity = DownloadedVideoEntity(
            title = info.title.ifBlank { "TikTok Photos by @${info.authorUsername}" },
            authorName = info.authorNickname.ifBlank { info.authorUsername },
            authorHandle = info.authorUsername,
            authorAvatarUrl = info.authorAvatarUrl,
            coverUrl = info.coverUrl.ifBlank { savedUris.firstOrNull() ?: "" },
            videoUri = savedUris.firstOrNull() ?: "",
            filePath = primaryFilePath,
            fileSizeBytes = totalSizeBytes,
            durationSeconds = 0,
            originalUrl = info.originalTiktokUrl,
            postType = "photo",
            photoUrls = savedUris.joinToString("|"),
            photoCount = savedUris.size
        )
        return dao.insertDownload(entity)
    }

    suspend fun deleteDownload(video: DownloadedVideoEntity) {
        if (video.isPhotoPost) {
            val uris = video.getPhotoUris()
            for (u in uris) {
                MediaSaver.deleteVideo(context, u, "")
            }
        } else {
            MediaSaver.deleteVideo(context, video.videoUri, video.filePath)
        }
        dao.deleteById(video.id)
    }

    suspend fun clearAll(videos: List<DownloadedVideoEntity>) {
        videos.forEach { video ->
            if (video.isPhotoPost) {
                video.getPhotoUris().forEach { u ->
                    MediaSaver.deleteVideo(context, u, "")
                }
            } else {
                MediaSaver.deleteVideo(context, video.videoUri, video.filePath)
            }
        }
        dao.clearAll()
    }
}
