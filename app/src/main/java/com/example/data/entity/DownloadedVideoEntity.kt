package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloaded_videos")
data class DownloadedVideoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val authorName: String,
    val authorHandle: String,
    val authorAvatarUrl: String,
    val coverUrl: String,
    val videoUri: String,
    val filePath: String,
    val fileSizeBytes: Long,
    val durationSeconds: Int,
    val originalUrl: String,
    val downloadedAt: Long = System.currentTimeMillis(),
    val postType: String = "video",
    val photoUrls: String = "",
    val photoCount: Int = 1
) {
    val isPhotoPost: Boolean
        get() = postType == "photo" || photoCount > 1 || photoUrls.isNotBlank()

    fun getPhotoUris(): List<String> {
        if (photoUrls.isBlank()) {
            return if (videoUri.isNotBlank()) listOf(videoUri) else emptyList()
        }
        return photoUrls.split("|").filter { it.isNotBlank() }
    }
}
