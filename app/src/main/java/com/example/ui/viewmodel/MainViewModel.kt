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
    data class InfoLoaded(val info: TikTokVideoInfo) : DownloadUiState
    data class Downloading(
        val percent: Int,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val info: TikTokVideoInfo
    ) : DownloadUiState
    data class Success(val outcome: VideoDownloader.DownloadOutcome) : DownloadUiState
    data class Error(val message: String) : DownloadUiState
}

sealed interface ShareModalState {
    data class Fetching(val rawUrl: String) : ShareModalState
    data class Downloading(val percent: Int, val info: TikTokVideoInfo) : ShareModalState
    data class Success(val info: TikTokVideoInfo, val uriString: String, val filePath: String) : ShareModalState
    data class Error(val message: String) : ShareModalState
}

data class VideoPlaybackInfo(
    val title: String,
    val author: String,
    val uriString: String,
    val filePath: String
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
                    is DownloadProgressEvent.Error -> {
                        val current = _downloadState.value
                        if (current is DownloadUiState.Downloading && current.info.originalTiktokUrl == event.url) {
                            _downloadState.value = DownloadUiState.Error(
                                event.message
                            )
                        }
                        val shareCurrent = _shareModalState.value
                        if (shareCurrent is ShareModalState.Downloading && shareCurrent.info.originalTiktokUrl == event.url) {
                            _shareModalState.value = ShareModalState.Error(
                                event.message
                            )
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
            // Clipboard access might be restricted on background or certain devices
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
            _downloadState.value = DownloadUiState.Error("Please enter or paste a TikTok video link.")
            return
        }

        _downloadState.value = DownloadUiState.FetchingInfo

        viewModelScope.launch {
            val result = TikwmApiService.fetchVideoInfo(raw)
            result.fold(
                onSuccess = { info ->
                    _downloadState.value = DownloadUiState.InfoLoaded(info)
                },
                onFailure = { error ->
                    _downloadState.value = DownloadUiState.Error(
                        error.localizedMessage ?: "Failed to fetch video. Please check the URL and try again."
                    )
                }
            )
        }
    }

    fun startDownload() {
        val state = _downloadState.value
        val info = when (state) {
            is DownloadUiState.InfoLoaded -> state.info
            is DownloadUiState.Success -> state.outcome.videoInfo
            else -> {
                fetchVideoInfo()
                return
            }
        }

        _downloadState.value = DownloadUiState.Downloading(
            percent = 0,
            downloadedBytes = 0L,
            totalBytes = info.estimatedSizeBytes,
            info = info
        )

        VideoDownloadService.startDownload(
            context = getApplication(),
            videoUrl = info.originalTiktokUrl,
            preferHd = _preferHd.value,
            preloadedInfo = info
        )
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
                    _shareModalState.value = ShareModalState.Downloading(0, info)
                    VideoDownloadService.startDownload(
                        context = getApplication(),
                        videoUrl = info.originalTiktokUrl,
                        preferHd = _preferHd.value,
                        preloadedInfo = info
                    )
                },
                onFailure = { err ->
                    _shareModalState.value = ShareModalState.Error(
                        err.localizedMessage ?: "Failed to extract TikTok link."
                    )
                }
            )
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
