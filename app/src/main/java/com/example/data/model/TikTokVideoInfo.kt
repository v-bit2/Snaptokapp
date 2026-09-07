package com.example.data.model

data class TikTokVideoInfo(
    val id: String,
    val title: String,
    val coverUrl: String,
    val playUrl: String,
    val hdPlayUrl: String?,
    val durationSeconds: Int,
    val authorNickname: String,
    val authorUsername: String,
    val authorAvatarUrl: String,
    val musicTitle: String? = null,
    val originalTiktokUrl: String,
    val estimatedSizeBytes: Long = 0L
)
