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
            originalUrl = info.originalTiktokUrl
        )
        return dao.insertDownload(entity)
    }

    suspend fun deleteDownload(video: DownloadedVideoEntity) {
        MediaSaver.deleteVideo(context, video.videoUri, video.filePath)
        dao.deleteById(video.id)
    }

    suspend fun clearAll(videos: List<DownloadedVideoEntity>) {
        videos.forEach { video ->
            MediaSaver.deleteVideo(context, video.videoUri, video.filePath)
        }
        dao.clearAll()
    }
}
