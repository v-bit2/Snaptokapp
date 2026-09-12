package com.example.ui.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.entity.DownloadedVideoEntity
import com.example.data.model.TikTokVideoInfo
import com.example.data.repository.VideoRepository
import com.example.service.DownloadProgressEvent
import com.example.service.TikwmApiService
import com.example.service.VideoDownloadService
import com.example.service.VideoDownloader
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppTab {
    HOME,
    HISTORY,
    SETTINGS
}

sealed interface DownloadUiState {
    data object Idle : DownloadUiState
    data object FetchingInfo : DownloadUiState
    data class InfoLoaded(
        val info: TikTokVideoInfo,
        val selectedImages: Set<String> = emptySet()
    ) : DownloadUiState
    data class Downloading(
        val percent: Int,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val info: TikTokVideoInfo
    ) : DownloadUiState
    data class PhotoDownloading(
        val completedCount: Int,
        val totalCount: Int,
        val percent: Int,
        val info: TikTokVideoInfo
    ) : DownloadUiState
    data class Success(val outcome: VideoDownloader.DownloadOutcome) : DownloadUiState
    data class PhotoSuccess(val outcome: VideoDownloader.PhotoDownloadOutcome) : DownloadUiState
    data class Error(val message: String) : DownloadUiState
}

sealed interface ShareModalState {
    data class Fetching(val rawUrl: String) : ShareModalState
    data class Preview(
        val info: TikTokVideoInfo,
        val selectedImages: Set<String> = emptySet()
    ) : ShareModalState
    data class Downloading(
        val percent: Int,
        val info: TikTokVideoInfo,
        val completedCount: Int = 0,
        val totalCount: Int = 0
    ) : ShareModalState
    data class Success(
        val info: TikTokVideoInfo,
        val uriString: String,
        val filePath: String,
        val savedUris: List<String> = emptyList()
    ) : ShareModalState
    data class Error(val message: String) : ShareModalState
}

data class VideoPlaybackInfo(
    val title: String,
    val author: String,
    val uriString: String,
    val filePath: String
)

data class PhotoGalleryInfo(
    val title: String,
    val author: String,
    val photoUris: List<String>,
    val initialIndex: Int = 0
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VideoRepository(application)
    private val downloader = VideoDownloader(application, repository)

    // Navigation & Input
    private val _currentTab = MutableStateFlow(AppTab.HOME)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    private val _clipboardUrl = MutableStateFlow<String?>(null)
    val clipboardUrl: StateFlow<String?> = _clipboardUrl.asStateFlow()

    // Settings
    private val _preferHd = MutableStateFlow(true)
    val preferHd: StateFlow<Boolean> = _preferHd.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(true)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    private val _autoFetchClipboard = MutableStateFlow(true)
    val autoFetchClipboard: StateFlow<Boolean> = _autoFetchClipboard.asStateFlow()

    // Download States
    private val _downloadState = MutableStateFlow<DownloadUiState>(DownloadUiState.Idle)
    val downloadState: StateFlow<DownloadUiState> = _downloadState.asStateFlow()

    // Share Intent Modal State
    private val _shareModalState = MutableStateFlow<ShareModalState?>(null)
    val shareModalState: StateFlow<ShareModalState?> = _shareModalState.asStateFlow()

    // Video Player In-App
    private val _playingVideo = MutableStateFlow<VideoPlaybackInfo?>(null)
    val playingVideo: StateFlow<VideoPlaybackInfo?> = _playingVideo.asStateFlow()

    // Photo Gallery In-App View
    private val _viewingPhotos = MutableStateFlow<PhotoGalleryInfo?>(null)
    val viewingPhotos: StateFlow<PhotoGalleryInfo?> = _viewingPhotos.asStateFlow()

    // History & Search
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val historyList: StateFlow<List<DownloadedVideoEntity>> = _searchQuery
        .flatMapLatest { query -> repository.searchDownloads(query) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        checkClipboard()
        observeDownloadEvents()
    }

    private fun observeDownloadEvents() {
        viewModelScope.launch {
            VideoDownloadService.progressEvents.collect { event ->
                when (event) {
                    is DownloadProgressEvent.Progress -> {
                        val current = _downloadState.value
                        if (current is DownloadUiState.Downloading && current.info.originalTiktokUrl == event.url) {
                            _downloadState.value = DownloadUiState.Downloading(
                                percent = event.percent,
                                downloadedBytes = event.downloadedBytes,
                                totalBytes = event.totalBytes,
                                info = event.info
                            )
                        }
                        val shareCurrent = _shareModalState.value
                        if (shareCurrent is ShareModalState.Downloading && shareCurrent.info.originalTiktokUrl == event.url) {
                            _shareModalState.value = ShareModalState.Downloading(
                                percent = event.percent,
                                info = event.info
                            )
                        }
                    }
                    is DownloadProgressEvent.PhotoProgress -> {
                        val current = _downloadState.value
                        if ((current is DownloadUiState.PhotoDownloading || current is DownloadUiState.Downloading) &&
                            (current as? DownloadUiState.PhotoDownloading)?.info?.originalTiktokUrl == event.url ||
                            (current as? DownloadUiState.Downloading)?.info?.originalTiktokUrl == event.url
                        ) {
                            _downloadState.value = DownloadUiState.PhotoDownloading(
                                completedCount = event.completedCount,
                                totalCount = event.totalCount,
                                percent = event.percent,
                                info = event.info
                            )
                        }
                        val shareCurrent = _shareModalState.value
                        if (shareCurrent is ShareModalState.Downloading && shareCurrent.info.originalTiktokUrl == event.url) {
                            _shareModalState.value = ShareModalState.Downloading(
                                percent = event.percent,
                                info = event.info,
                                completedCount = event.completedCount,
                                totalCount = event.totalCount
                            )
                        }
                    }
                    is DownloadProgressEvent.Success -> {
                        val current = _downloadState.value
                        if (current is DownloadUiState.Downloading && current.info.originalTiktokUrl == event.url) {
                            _downloadState.value = DownloadUiState.Success(event.outcome)
                        }
                        val shareCurrent = _shareModalState.value
                        if (shareCurrent is ShareModalState.Downloading && shareCurrent.info.originalTiktokUrl == event.url) {
                            _shareModalState.value = ShareModalState.Success(
                                info = event.outcome.videoInfo,
                                uriString = event.outcome.uriString,
                                filePath = event.outcome.filePath
                            )
                        }
                    }
                    is DownloadProgressEvent.PhotoSuccess -> {
                        val current = _downloadState.value
                        if ((current is DownloadUiState.PhotoDownloading || current is DownloadUiState.Downloading)) {
                            _downloadState.value = DownloadUiState.PhotoSuccess(event.outcome)
                        }
                        val shareCurrent = _shareModalState.value
                        if (shareCurrent is ShareModalState.Downloading && shareCurrent.info.originalTiktokUrl == event.url) {
                            _shareModalState.value = ShareModalState.Success(
                                info = event.outcome.postInfo,
                                uriString = event.outcome.savedUris.firstOrNull() ?: "",
                                filePath = event.outcome.primaryFilePath,
                                savedUris = event.outcome.savedUris
                            )
                        }
                    }
                    is DownloadProgressEvent.Error -> {
                        val current = _downloadState.value
                        if (current is DownloadUiState.Downloading || current is DownloadUiState.PhotoDownloading) {
                            _downloadState.value = DownloadUiState.Error(event.message)
                        }
                        val shareCurrent = _shareModalState.value
                        if (shareCurrent is ShareModalState.Downloading && shareCurrent.info.originalTiktokUrl == event.url) {
                            _shareModalState.value = ShareModalState.Error(event.message)
                        }
                    }
                }
            }
        }
    }

    fun setTab(tab: AppTab) {
        _currentTab.value = tab
    }

    fun onUrlInputChanged(newUrl: String) {
        _urlInput.value = newUrl
        if (_downloadState.value is DownloadUiState.Error) {
            _downloadState.value = DownloadUiState.Idle
        }
    }

    fun pasteFromClipboard() {
        val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).coerceToText(getApplication()).toString()
            val extracted = TikwmApiService.extractTikTokUrl(text) ?: text.trim()
            _urlInput.value = extracted
            _clipboardUrl.value = null
        }
    }

    fun checkClipboard() {
        if (!_autoFetchClipboard.value) return
        try {
            val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).coerceToText(getApplication()).toString()
                val extracted = TikwmApiService.extractTikTokUrl(text)
                if (extracted != null && extracted != _urlInput.value) {
                    _clipboardUrl.value = extracted
                }
            }
        } catch (e: Exception) {
            // Clipboard access might be restricted
        }
    }

    fun useDetectedClipboardUrl() {
        _clipboardUrl.value?.let {
            _urlInput.value = it
            _clipboardUrl.value = null
            fetchVideoInfo()
        }
    }

    fun dismissClipboardBanner() {
        _clipboardUrl.value = null
    }

    fun togglePreferHd() {
        _preferHd.value = !_preferHd.value
    }

    fun toggleTheme() {
        _isDarkTheme.value = !_isDarkTheme.value
    }

    fun toggleAutoFetchClipboard() {
        _autoFetchClipboard.value = !_autoFetchClipboard.value
    }

    fun fetchVideoInfo() {
        val raw = _urlInput.value.trim()
        if (raw.isBlank()) {
            _downloadState.value = DownloadUiState.Error("Please enter or paste a TikTok link.")
            return
        }

        _downloadState.value = DownloadUiState.FetchingInfo

        viewModelScope.launch {
            val result = TikwmApiService.fetchVideoInfo(raw)
            result.fold(
                onSuccess = { info ->
                    val initialSelected = if (info.isPhotoPost) info.images.toSet() else emptySet()
                    _downloadState.value = DownloadUiState.InfoLoaded(info, initialSelected)
                },
                onFailure = { error ->
                    _downloadState.value = DownloadUiState.Error(
                        error.localizedMessage ?: "Failed to fetch TikTok details. Please check the URL and try again."
                    )
                }
            )
        }
    }

    fun toggleImageSelection(imageUrl: String) {
        val current = _downloadState.value
        if (current is DownloadUiState.InfoLoaded) {
            val updated = if (current.selectedImages.contains(imageUrl)) {
                current.selectedImages - imageUrl
            } else {
                current.selectedImages + imageUrl
            }
            _downloadState.value = current.copy(selectedImages = updated)
        }
    }

    fun selectAllImages() {
        val current = _downloadState.value
        if (current is DownloadUiState.InfoLoaded) {
            _downloadState.value = current.copy(selectedImages = current.info.images.toSet())
        }
    }

    fun deselectAllImages() {
        val current = _downloadState.value
        if (current is DownloadUiState.InfoLoaded) {
            _downloadState.value = current.copy(selectedImages = emptySet())
        }
    }

    fun startDownload() {
        val state = _downloadState.value
        val (info, selectedImages) = when (state) {
            is DownloadUiState.InfoLoaded -> Pair(state.info, state.selectedImages.toList())
            is DownloadUiState.Success -> Pair(state.outcome.videoInfo, emptyList())
            is DownloadUiState.PhotoSuccess -> Pair(state.outcome.postInfo, state.outcome.postInfo.images)
            else -> {
                fetchVideoInfo()
                return
            }
        }

        if (info.isPhotoPost) {
            val imagesToDownload = if (selectedImages.isNotEmpty()) selectedImages else info.images
            if (imagesToDownload.isEmpty()) {
                _downloadState.value = DownloadUiState.Error("Please select at least 1 photo to download.")
                return
            }

            _downloadState.value = DownloadUiState.PhotoDownloading(
                completedCount = 0,
                totalCount = imagesToDownload.size,
                percent = 0,
                info = info
            )

            val serviceStarted = try {
                VideoDownloadService.startDownload(
                    context = getApplication(),
                    videoUrl = info.originalTiktokUrl,
                    preferHd = _preferHd.value,
                    preloadedInfo = info,
                    selectedImages = imagesToDownload
                )
            } catch (t: Throwable) {
                false
            }

            if (!serviceStarted) {
                runDirectPhotoDownload(info, imagesToDownload)
            }
        } else {
            _downloadState.value = DownloadUiState.Downloading(
                percent = 0,
                downloadedBytes = 0L,
                totalBytes = info.estimatedSizeBytes,
                info = info
            )

            val serviceStarted = try {
                VideoDownloadService.startDownload(
                    context = getApplication(),
                    videoUrl = info.originalTiktokUrl,
                    preferHd = _preferHd.value,
                    preloadedInfo = info
                )
            } catch (t: Throwable) {
                false
            }

            if (!serviceStarted) {
                runDirectVideoDownload(info)
            }
        }
    }

    private fun runDirectVideoDownload(info: TikTokVideoInfo) {
        viewModelScope.launch {
            val result = downloader.downloadVideo(info, _preferHd.value) { percent, downloaded, total ->
                _downloadState.value = DownloadUiState.Downloading(
                    percent = percent,
                    downloadedBytes = downloaded,
                    totalBytes = total,
                    info = info
                )
            }

            result.fold(
                onSuccess = { outcome ->
                    _downloadState.value = DownloadUiState.Success(outcome)
                },
                onFailure = { error ->
                    _downloadState.value = DownloadUiState.Error(
                        error.localizedMessage ?: "Failed to download video. Please try again."
                    )
                }
            )
        }
    }

    private fun runDirectPhotoDownload(info: TikTokVideoInfo, images: List<String>) {
        viewModelScope.launch {
            val result = downloader.downloadPhotoPost(info, images) { completed, total, percent ->
                _downloadState.value = DownloadUiState.PhotoDownloading(
                    completedCount = completed,
                    totalCount = total,
                    percent = percent,
                    info = info
                )
            }

            result.fold(
                onSuccess = { outcome ->
                    _downloadState.value = DownloadUiState.PhotoSuccess(outcome)
                },
                onFailure = { error ->
                    _downloadState.value = DownloadUiState.Error(
                        error.localizedMessage ?: "Failed to download photos. Please try again."
                    )
                }
            )
        }
    }

    fun resetDownloadState() {
        _downloadState.value = DownloadUiState.Idle
        _urlInput.value = ""
    }

    // Share Intent handling
    fun handleIncomingShare(sharedText: String) {
        val extractedUrl = TikwmApiService.extractTikTokUrl(sharedText) ?: sharedText.trim()
        if (extractedUrl.isBlank()) return

        _shareModalState.value = ShareModalState.Fetching(extractedUrl)

        viewModelScope.launch {
            val infoResult = TikwmApiService.fetchVideoInfo(extractedUrl)
            infoResult.fold(
                onSuccess = { info ->
                    val selected = if (info.isPhotoPost) info.images.toSet() else emptySet()
                    _shareModalState.value = ShareModalState.Preview(info, selected)
                },
                onFailure = { err ->
                    _shareModalState.value = ShareModalState.Error(
                        err.localizedMessage ?: "Failed to extract TikTok link."
                    )
                }
            )
        }
    }

    fun toggleShareModalImageSelection(imageUrl: String) {
        val current = _shareModalState.value
        if (current is ShareModalState.Preview) {
            val updated = if (current.selectedImages.contains(imageUrl)) {
                current.selectedImages - imageUrl
            } else {
                current.selectedImages + imageUrl
            }
            _shareModalState.value = current.copy(selectedImages = updated)
        }
    }

    fun selectAllShareModalImages() {
        val current = _shareModalState.value
        if (current is ShareModalState.Preview) {
            _shareModalState.value = current.copy(selectedImages = current.info.images.toSet())
        }
    }

    fun deselectAllShareModalImages() {
        val current = _shareModalState.value
        if (current is ShareModalState.Preview) {
            _shareModalState.value = current.copy(selectedImages = emptySet())
        }
    }

    fun startShareModalDownload() {
        val current = _shareModalState.value
        if (current !is ShareModalState.Preview) return
        val info = current.info

        if (info.isPhotoPost) {
            val selected = current.selectedImages.toList().ifEmpty { info.images }
            if (selected.isEmpty()) {
                _shareModalState.value = ShareModalState.Error("Please select at least 1 image to download.")
                return
            }

            _shareModalState.value = ShareModalState.Downloading(
                percent = 0,
                info = info,
                completedCount = 0,
                totalCount = selected.size
            )

            val serviceStarted = try {
                VideoDownloadService.startDownload(
                    context = getApplication(),
                    videoUrl = info.originalTiktokUrl,
                    preferHd = _preferHd.value,
                    preloadedInfo = info,
                    selectedImages = selected
                )
            } catch (t: Throwable) {
                false
            }

            if (!serviceStarted) {
                viewModelScope.launch {
                    val result = downloader.downloadPhotoPost(info, selected) { completed, total, percent ->
                        _shareModalState.value = ShareModalState.Downloading(
                            percent = percent,
                            info = info,
                            completedCount = completed,
                            totalCount = total
                        )
                    }
                    result.fold(
                        onSuccess = { outcome ->
                            _shareModalState.value = ShareModalState.Success(
                                info = info,
                                uriString = outcome.savedUris.firstOrNull() ?: "",
                                filePath = outcome.primaryFilePath,
                                savedUris = outcome.savedUris
                            )
                        },
                        onFailure = { err ->
                            _shareModalState.value = ShareModalState.Error(
                                err.localizedMessage ?: "Failed to download photos."
                            )
                        }
                    )
                }
            }
        } else {
            _shareModalState.value = ShareModalState.Downloading(0, info)
            val serviceStarted = try {
                VideoDownloadService.startDownload(
                    context = getApplication(),
                    videoUrl = info.originalTiktokUrl,
                    preferHd = _preferHd.value,
                    preloadedInfo = info
                )
            } catch (t: Throwable) {
                false
            }

            if (!serviceStarted) {
                viewModelScope.launch {
                    val downloadResult = downloader.downloadVideo(info, _preferHd.value) { percent, _, _ ->
                        _shareModalState.value = ShareModalState.Downloading(percent, info)
                    }
                    downloadResult.fold(
                        onSuccess = { outcome ->
                            _shareModalState.value = ShareModalState.Success(
                                info = info,
                                uriString = outcome.uriString,
                                filePath = outcome.filePath
                            )
                        },
                        onFailure = { dlErr ->
                            _shareModalState.value = ShareModalState.Error(
                                dlErr.localizedMessage ?: "Failed to download video."
                            )
                        }
                    )
                }
            }
        }
    }

    fun dismissShareModal() {
        _shareModalState.value = null
    }

    fun openInAppPlayer(title: String, author: String, uriString: String, filePath: String) {
        _playingVideo.value = VideoPlaybackInfo(
            title = title,
            author = author,
            uriString = uriString,
            filePath = filePath
        )
    }

    fun closeInAppPlayer() {
        _playingVideo.value = null
    }

    fun openPhotoGallery(title: String, author: String, photoUris: List<String>, initialIndex: Int = 0) {
        _viewingPhotos.value = PhotoGalleryInfo(
            title = title,
            author = author,
            photoUris = photoUris,
            initialIndex = initialIndex
        )
    }

    fun closePhotoGallery() {
        _viewingPhotos.value = null
    }

    // History Actions
    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun deleteHistoryItem(video: DownloadedVideoEntity) {
        viewModelScope.launch {
            repository.deleteDownload(video)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            val list = historyList.value
            repository.clearAll(list)
        }
    }

    fun clearAppCache() {
        try {
            val cacheDir = getApplication<Application>().cacheDir
            cacheDir.deleteRecursively()
        } catch (ignored: Exception) {}
    }
}
