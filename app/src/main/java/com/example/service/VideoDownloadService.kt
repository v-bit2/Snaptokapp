package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.TikTokVideoInfo
import com.example.data.storage.MediaSaver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

sealed interface DownloadProgressEvent {
    data class Progress(
        val url: String,
        val percent: Int,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val info: TikTokVideoInfo
    ) : DownloadProgressEvent

    data class Success(
        val url: String,
        val outcome: VideoDownloader.DownloadOutcome
    ) : DownloadProgressEvent

    data class Error(
        val url: String,
        val message: String
    ) : DownloadProgressEvent
}

class VideoDownloadService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var notificationManager: NotificationManager
    private lateinit var downloader: VideoDownloader

    companion object {
        const val CHANNEL_ID = "snaptok_download_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_DOWNLOAD = "ACTION_START_DOWNLOAD"
        const val EXTRA_VIDEO_URL = "EXTRA_VIDEO_URL"
        const val EXTRA_PREFER_HD = "EXTRA_PREFER_HD"

        private val _progressEvents = MutableSharedFlow<DownloadProgressEvent>(replay = 1, extraBufferCapacity = 64)
        val progressEvents: SharedFlow<DownloadProgressEvent> = _progressEvents.asSharedFlow()

        private val preloadedInfoCache = ConcurrentHashMap<String, TikTokVideoInfo>()

        fun startDownload(
            context: Context,
            videoUrl: String,
            preferHd: Boolean = true,
            preloadedInfo: TikTokVideoInfo? = null
        ) {
            if (preloadedInfo != null) {
                preloadedInfoCache[videoUrl] = preloadedInfo
            }
            val intent = Intent(context, VideoDownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_VIDEO_URL, videoUrl)
                putExtra(EXTRA_PREFER_HD, preferHd)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        downloader = VideoDownloader(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_DOWNLOAD) {
            val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL) ?: ""
            val preferHd = intent.getBooleanExtra(EXTRA_PREFER_HD, true)
            if (videoUrl.isNotBlank()) {
                startForeground(NOTIFICATION_ID, buildProgressNotification("Preparing download…", 0, true))
                processDownload(videoUrl, preferHd)
            } else {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun processDownload(url: String, preferHd: Boolean) {
        serviceScope.launch {
            val cached = preloadedInfoCache[url]
            if (cached != null) {
                downloadVideo(url, cached, preferHd)
            } else {
                updateNotification("Fetching video information…", 0, true)
                val infoResult = TikwmApiService.fetchVideoInfo(url)

                infoResult.fold(
                    onSuccess = { info ->
                        downloadVideo(url, info, preferHd)
                    },
                    onFailure = { error ->
                        val errMsg = error.localizedMessage ?: "Failed to fetch video details"
                        showFailedNotification(errMsg)
                        _progressEvents.tryEmit(DownloadProgressEvent.Error(url, errMsg))
                        stopForeground(false)
                        stopSelf()
                    }
                )
            }
        }
    }

    private suspend fun downloadVideo(url: String, info: TikTokVideoInfo, preferHd: Boolean) {
        updateNotification("Downloading: ${info.authorUsername}… 0%", 0, false)
        _progressEvents.tryEmit(DownloadProgressEvent.Progress(url, 0, 0L, info.estimatedSizeBytes, info))

        val result = downloader.downloadVideo(info, preferHd) { percent, bytesWritten, totalBytes ->
            val formattedSize = MediaSaver.formatBytes(bytesWritten)
            updateNotification("Downloading ${info.authorUsername}… $percent% ($formattedSize)", percent, false)
            _progressEvents.tryEmit(DownloadProgressEvent.Progress(url, percent, bytesWritten, totalBytes, info))
        }

        result.fold(
            onSuccess = { outcome ->
                showCompleteNotification(outcome.videoInfo.title, outcome.uriString, outcome.filePath)
                _progressEvents.tryEmit(DownloadProgressEvent.Success(url, outcome))
                preloadedInfoCache.remove(url)
                stopForeground(false)
                stopSelf()
            },
            onFailure = { error ->
                val errMsg = error.localizedMessage ?: "Download failed"
                showFailedNotification(errMsg)
                _progressEvents.tryEmit(DownloadProgressEvent.Error(url, errMsg))
                preloadedInfoCache.remove(url)
                stopForeground(false)
                stopSelf()
            }
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SnapTok Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows download progress and status for SnapTok"
                setSound(null, null)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildProgressNotification(content: String, progress: Int, indeterminate: Boolean) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("SnapTok Downloader")
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setProgress(100, progress, indeterminate)
            .setContentIntent(getOpenAppPendingIntent())
            .build()

    private fun updateNotification(content: String, progress: Int, indeterminate: Boolean) {
        val notification = buildProgressNotification(content, progress, indeterminate)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showCompleteNotification(title: String, uriString: String, filePath: String) {
        val playIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(android.net.Uri.parse(uriString), "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            playIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val displayTitle = title.ifBlank { "TikTok Video" }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Download complete — tap to view")
            .setContentText("Saved to Gallery: $displayTitle")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Saved to Gallery: $displayTitle\nDownload complete — tap to view")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }

    private fun showFailedNotification(errorMessage: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Download Failed")
            .setContentText(errorMessage)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(getOpenAppPendingIntent())
            .build()

        notificationManager.notify(NOTIFICATION_ID + 2, notification)
    }

    private fun getOpenAppPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
