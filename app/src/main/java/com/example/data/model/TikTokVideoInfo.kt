package com.example.data.model

enum class TikTokPostType {
    VIDEO,
    PHOTO
}

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
    val estimatedSizeBytes: Long = 0L,
    val postType: TikTokPostType = TikTokPostType.VIDEO,
    val images: List<String> = emptyList()
) {
    val isPhotoPost: Boolean
        get() = postType == TikTokPostType.PHOTO || images.isNotEmpty()

    val totalImageCount: Int
        get() = images.size
}
