package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.storage.MediaSaver
import com.example.ui.ShareOverlayActivity
import com.example.ui.components.PhotoGalleryViewerDialog
import com.example.ui.components.ShareOverlayDialog
import com.example.ui.components.SnapTokBottomNavBar
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.VideoPlayerScreen
import com.example.ui.theme.SnapTokTheme
import com.example.ui.viewmodel.AppTab
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.ShareModalState

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle initial intent if app opened via share sheet
        handleIntent(intent)

        setContent {
            val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()
            val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
            val urlInput by viewModel.urlInput.collectAsStateWithLifecycle()
            val clipboardUrl by viewModel.clipboardUrl.collectAsStateWithLifecycle()
            val preferHd by viewModel.preferHd.collectAsStateWithLifecycle()
            val autoFetchClipboard by viewModel.autoFetchClipboard.collectAsStateWithLifecycle()
            val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
            val historyList by viewModel.historyList.collectAsStateWithLifecycle()
            val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
            val shareModalState by viewModel.shareModalState.collectAsStateWithLifecycle()
            val playingVideo by viewModel.playingVideo.collectAsStateWithLifecycle()
            val viewingPhotos by viewModel.viewingPhotos.collectAsStateWithLifecycle()

            val context = LocalContext.current

            // Notification permission request for Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) {}

                LaunchedEffect(Unit) {
                    val hasPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!hasPermission) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            SnapTokTheme(darkTheme = isDarkTheme) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = MaterialTheme.colorScheme.background,
                        bottomBar = {
                            SnapTokBottomNavBar(
                                currentTab = currentTab,
                                historyCount = historyList.size,
                                onTabSelected = { viewModel.setTab(it) }
                            )
                        }
                    ) { innerPadding ->
                        AnimatedContent(
                            targetState = currentTab,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                            label = "TabContent"
                        ) { tab ->
                            when (tab) {
                                AppTab.HOME -> {
                                    HomeScreen(
                                        urlInput = urlInput,
                                        clipboardUrl = clipboardUrl,
                                        preferHd = preferHd,
                                        downloadState = downloadState,
                                        onUrlChanged = { viewModel.onUrlInputChanged(it) },
                                        onPasteClick = { viewModel.pasteFromClipboard() },
                                        onUseClipboardUrl = { viewModel.useDetectedClipboardUrl() },
                                        onDismissClipboard = { viewModel.dismissClipboardBanner() },
                                        onTogglePreferHd = { viewModel.togglePreferHd() },
                                        onFetchClick = { viewModel.fetchVideoInfo() },
                                        onStartDownloadClick = { viewModel.startDownload() },
                                        onResetClick = { viewModel.resetDownloadState() },
                                        onPlaySavedVideo = { uri, path, title, author ->
                                            viewModel.openInAppPlayer(title, author, uri, path)
                                        },
                                        onShareSavedVideo = { uri, path, title ->
                                            MediaSaver.shareVideo(context, uri, path, title)
                                        },
                                        onToggleImageSelect = { viewModel.toggleImageSelection(it) },
                                        onSelectAllImages = { viewModel.selectAllImages() },
                                        onDeselectAllImages = { viewModel.deselectAllImages() },
                                        onViewSavedPhotos = { uris, title, author ->
                                            viewModel.openPhotoGallery(title = title, author = author, photoUris = uris)
                                        },
                                        onShareSavedPhotos = { uris, title ->
                                            MediaSaver.shareMultipleImages(context, uris, title)
                                        },
                                        onRetryFailedPhotos = { viewModel.startDownload() }
                                    )
                                }

                                AppTab.HISTORY -> {
                                    HistoryScreen(
                                        videos = historyList,
                                        searchQuery = searchQuery,
                                        onSearchChanged = { viewModel.onSearchQueryChanged(it) },
                                        onPlayVideo = { uri, path, title, author ->
                                            viewModel.openInAppPlayer(title, author, uri, path)
                                        },
                                        onShareVideo = { uri, path, title ->
                                            MediaSaver.shareVideo(context, uri, path, title)
                                        },
                                        onViewPhotos = { uris, title, author ->
                                            viewModel.openPhotoGallery(title = title, author = author, photoUris = uris)
                                        },
                                        onSharePhotos = { uris, title ->
                                            MediaSaver.shareMultipleImages(context, uris, title)
                                        },
                                        onDeleteVideo = { viewModel.deleteHistoryItem(it) },
                                        onClearAll = { viewModel.clearAllHistory() }
                                    )
                                }

                                AppTab.SETTINGS -> {
                                    SettingsScreen(
                                        preferHd = preferHd,
                                        isDarkTheme = isDarkTheme,
                                        autoFetchClipboard = autoFetchClipboard,
                                        onTogglePreferHd = { viewModel.togglePreferHd() },
                                        onToggleTheme = { viewModel.toggleTheme() },
                                        onToggleAutoFetchClipboard = { viewModel.toggleAutoFetchClipboard() },
                                        onClearCache = { viewModel.clearAppCache() }
                                    )
                                }
                            }
                        }
                    }

                    // Floating Share-Intent Overlay Dialog
                    shareModalState?.let { modalState ->
                        ShareOverlayDialog(
                            state = modalState,
                            onDismiss = { viewModel.dismissShareModal() },
                            onPlayClick = { uri, path ->
                                val info = (modalState as? ShareModalState.Success)?.info
                                val title = info?.title ?: "TikTok Video"
                                val author = info?.authorUsername ?: "creator"
                                viewModel.dismissShareModal()
                                viewModel.openInAppPlayer(title, author, uri, path)
                            },
                            onShareClick = { uri, path, title ->
                                MediaSaver.shareVideo(context, uri, path, title)
                            },
                            onToggleImageSelect = { viewModel.toggleImageSelection(it) },
                            onSelectAllImages = { viewModel.selectAllImages() },
                            onDeselectAllImages = { viewModel.deselectAllImages() },
                            onStartDownload = { viewModel.startDownload() },
                            onViewPhotosClick = { uris, title, author ->
                                viewModel.dismissShareModal()
                                viewModel.openPhotoGallery(title = title, author = author, photoUris = uris)
                            },
                            onShareAllPhotosClick = { uris, title ->
                                MediaSaver.shareMultipleImages(context, uris, title)
                            }
                        )
                    }

                    // Dedicated Full-Screen In-App Video Player Screen
                    AnimatedVisibility(
                        visible = playingVideo != null,
                        enter = fadeIn(animationSpec = tween(250)) + slideInVertically(
                            animationSpec = tween(250),
                            initialOffsetY = { it / 6 }
                        ),
                        exit = fadeOut(animationSpec = tween(200)) + slideOutVertically(
                            animationSpec = tween(200),
                            targetOffsetY = { it / 6 }
                        )
                    ) {
                        playingVideo?.let { videoInfo ->
                            VideoPlayerScreen(
                                video = videoInfo,
                                onClose = { viewModel.closeInAppPlayer() }
                            )
                        }
                    }

                    // Dedicated Full-Screen Photo Gallery Viewer Dialog
                    viewingPhotos?.let { galleryInfo ->
                        PhotoGalleryViewerDialog(
                            title = galleryInfo.title,
                            author = galleryInfo.author,
                            photoUris = galleryInfo.photoUris,
                            initialIndex = galleryInfo.initialIndex,
                            onDismiss = { viewModel.closePhotoGallery() }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkClipboard()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val shareOverlayIntent = Intent(this, ShareOverlayActivity::class.java).apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtras(intent)
            }
            startActivity(shareOverlayIntent)
            finish()
        }
    }
}
