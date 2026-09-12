package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.TikTokVideoInfo
import com.example.data.storage.MediaSaver
import kotlinx.coroutines.CoroutineExceptionHandler
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

    data class PhotoProgress(
        val url: String,
        val completedCount: Int,
        val totalCount: Int,
        val percent: Int,
        val info: TikTokVideoInfo
    ) : DownloadProgressEvent

    data class Success(
        val url: String,
        val outcome: VideoDownloader.DownloadOutcome
    ) : DownloadProgressEvent

    data class PhotoSuccess(
        val url: String,
        val outcome: VideoDownloader.PhotoDownloadOutcome
    ) : DownloadProgressEvent

    data class Error(
        val url: String,
        val message: String
    ) : DownloadProgressEvent
}

class VideoDownloadService : Service() {

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("VideoDownloadService", "Uncaught coroutine exception", throwable)
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + exceptionHandler)
    private lateinit var notificationManager: NotificationManager
    private lateinit var downloader: VideoDownloader
    private val activeDownloads = java.util.concurrent.atomic.AtomicInteger(0)

    companion object {
        const val CHANNEL_ID = "snaptok_download_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_DOWNLOAD = "ACTION_START_DOWNLOAD"
        const val EXTRA_VIDEO_URL = "EXTRA_VIDEO_URL"
        const val EXTRA_PREFER_HD = "EXTRA_PREFER_HD"
        const val EXTRA_SELECTED_IMAGES = "EXTRA_SELECTED_IMAGES"

        private val _progressEvents = MutableSharedFlow<DownloadProgressEvent>(replay = 1, extraBufferCapacity = 64)
        val progressEvents: SharedFlow<DownloadProgressEvent> = _progressEvents.asSharedFlow()

        private val preloadedInfoCache = ConcurrentHashMap<String, TikTokVideoInfo>()
        private val selectedImagesCache = ConcurrentHashMap<String, List<String>>()

        fun startDownload(
            context: Context,
            videoUrl: String,
            preferHd: Boolean = true,
            preloadedInfo: TikTokVideoInfo? = null,
            selectedImages: List<String>? = null
        ): Boolean {
            if (preloadedInfo != null) {
                preloadedInfoCache[videoUrl] = preloadedInfo
            }
            if (selectedImages != null) {
                selectedImagesCache[videoUrl] = selectedImages
            }
            return try {
                val intent = Intent(context, VideoDownloadService::class.java).apply {
                    action = ACTION_START_DOWNLOAD
                    putExtra(EXTRA_VIDEO_URL, videoUrl)
                    putExtra(EXTRA_PREFER_HD, preferHd)
                    if (selectedImages != null) {
                        putStringArrayListExtra(EXTRA_SELECTED_IMAGES, ArrayList(selectedImages))
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                true
            } catch (t: Throwable) {
                Log.e("VideoDownloadService", "Failed to start VideoDownloadService", t)
                false
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
            val selectedImages = intent.getStringArrayListExtra(EXTRA_SELECTED_IMAGES) ?: selectedImagesCache[videoUrl]

            if (videoUrl.isNotBlank()) {
                activeDownloads.incrementAndGet()
                tryStartForeground()
                processDownload(videoUrl, preferHd, selectedImages)
            } else {
                if (activeDownloads.get() <= 0) {
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun tryStartForeground() {
        try {
            val notification = buildProgressNotification("Preparing download…", 0, true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (t: Throwable) {
            Log.e("VideoDownloadService", "Could not startForeground with notification", t)
        }
    }

    private fun safeStopForeground() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_DETACH)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(false)
            }
        } catch (t: Throwable) {
            Log.e("VideoDownloadService", "Error in safeStopForeground", t)
        }
    }

    private fun processDownload(url: String, preferHd: Boolean, explicitSelectedImages: List<String>?) {
        serviceScope.launch {
            val cached = preloadedInfoCache[url]
            val info = if (cached != null) {
                cached
            } else {
                updateNotification("Fetching post information…", 0, true)
                val infoResult = TikwmApiService.fetchVideoInfo(url)
                if (infoResult.isFailure) {
                    val errMsg = infoResult.exceptionOrNull()?.localizedMessage ?: "Failed to fetch post details"
                    showFailedNotification(errMsg)
                    _progressEvents.tryEmit(DownloadProgressEvent.Error(url, errMsg))
                    safeStopForeground()
                    stopSelf()
                    return@launch
                }
                infoResult.getOrThrow()
            }

            if (info.isPhotoPost) {
                val imagesToDownload = explicitSelectedImages?.takeIf { it.isNotEmpty() }
                    ?: selectedImagesCache[url]?.takeIf { it.isNotEmpty() }
                    ?: info.images

                downloadPhotos(url, info, imagesToDownload)
            } else {
                downloadVideo(url, info, preferHd)
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
                showCompleteNotification(outcome.videoInfo.title, outcome.uriString, outcome.filePath, isPhoto = false)
                _progressEvents.tryEmit(DownloadProgressEvent.Success(url, outcome))
                cleanup(url)
                checkFinishService()
            },
            onFailure = { error ->
                val errMsg = error.localizedMessage ?: "Download failed"
                showFailedNotification(errMsg)
                _progressEvents.tryEmit(DownloadProgressEvent.Error(url, errMsg))
                cleanup(url)
                checkFinishService()
            }
        )
    }

    private suspend fun downloadPhotos(url: String, info: TikTokVideoInfo, images: List<String>) {
        val total = images.size
        updateNotification("Downloading 0 of $total photos… (0%)", 0, false)
        _progressEvents.tryEmit(DownloadProgressEvent.PhotoProgress(url, 0, total, 0, info))

        val result = downloader.downloadPhotoPost(info, images) { completed, totalCount, percent ->
            updateNotification("Downloading $completed of $totalCount photos… ($percent%)", percent, false)
            _progressEvents.tryEmit(DownloadProgressEvent.PhotoProgress(url, completed, totalCount, percent, info))
        }

        result.fold(
            onSuccess = { outcome ->
                val uriString = outcome.savedUris.firstOrNull() ?: ""
                showCompleteNotification(
                    "${outcome.successfulCount} photos by @${info.authorUsername}",
                    uriString,
                    outcome.primaryFilePath,
                    isPhoto = true
                )
                _progressEvents.tryEmit(DownloadProgressEvent.PhotoSuccess(url, outcome))
                cleanup(url)
                checkFinishService()
            },
            onFailure = { error ->
                val errMsg = error.localizedMessage ?: "Photo download failed"
                showFailedNotification(errMsg)
                _progressEvents.tryEmit(DownloadProgressEvent.Error(url, errMsg))
                cleanup(url)
                checkFinishService()
            }
        )
    }

    private fun checkFinishService() {
        if (activeDownloads.decrementAndGet() <= 0) {
            safeStopForeground()
            stopSelf()
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.i("VideoDownloadService", "App swiped away from recent tasks. Active downloads: ${activeDownloads.get()}")
        if (activeDownloads.get() <= 0) {
            safeStopForeground()
            stopSelf()
        }
    }

    private fun cleanup(url: String) {
        preloadedInfoCache.remove(url)
        selectedImagesCache.remove(url)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
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
            } catch (t: Throwable) {
                Log.e("VideoDownloadService", "Failed to create notification channel", t)
            }
        }
    }

    private fun buildProgressNotification(content: String, progress: Int, indeterminate: Boolean) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download_notification)
            .setContentTitle("SnapTok Downloader")
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setProgress(100, progress, indeterminate)
            .setContentIntent(getOpenAppPendingIntent())
            .build()

    private fun updateNotification(content: String, progress: Int, indeterminate: Boolean) {
        try {
            val notification = buildProgressNotification(content, progress, indeterminate)
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (t: Throwable) {
            Log.e("VideoDownloadService", "Failed to update notification", t)
        }
    }

    private fun showCompleteNotification(title: String, uriString: String, filePath: String, isPhoto: Boolean) {
        try {
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                val uri = try {
                    val parsed = Uri.parse(uriString)
                    if (parsed.scheme == "content") parsed else Uri.fromFile(java.io.File(filePath))
                } catch (e: Exception) {
                    Uri.fromFile(java.io.File(filePath))
                }
                setDataAndType(uri, if (isPhoto) "image/*" else "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val pendingIntent = PendingIntent.getActivity(
                this,
                System.currentTimeMillis().toInt(),
                viewIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val displayTitle = title.ifBlank { if (isPhoto) "TikTok Photos" else "TikTok Video" }
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_download_notification)
                .setContentTitle(if (isPhoto) "Photos saved to Gallery!" else "Download complete — tap to play")
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
        } catch (t: Throwable) {
            Log.e("VideoDownloadService", "Failed to show complete notification", t)
        }
    }

    private fun showFailedNotification(errorMessage: String) {
        try {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_download_notification)
                .setContentTitle("Download Failed")
                .setContentText(errorMessage)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(getOpenAppPendingIntent())
                .build()

            notificationManager.notify(NOTIFICATION_ID + 2, notification)
        } catch (t: Throwable) {
            Log.e("VideoDownloadService", "Failed to show failed notification", t)
        }
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
