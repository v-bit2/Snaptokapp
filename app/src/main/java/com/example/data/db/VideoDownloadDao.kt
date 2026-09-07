package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.entity.DownloadedVideoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDownloadDao {
    @Query("SELECT * FROM downloaded_videos ORDER BY downloadedAt DESC")
    fun getAllDownloads(): Flow<List<DownloadedVideoEntity>>

    @Query("SELECT * FROM downloaded_videos WHERE title LIKE '%' || :query || '%' OR authorName LIKE '%' || :query || '%' OR authorHandle LIKE '%' || :query || '%' ORDER BY downloadedAt DESC")
    fun searchDownloads(query: String): Flow<List<DownloadedVideoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(video: DownloadedVideoEntity): Long

    @Query("DELETE FROM downloaded_videos WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM downloaded_videos")
    suspend fun clearAll()
}
