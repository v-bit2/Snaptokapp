package com.example.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.TikTokVideoInfo
import com.example.data.storage.MediaSaver
import com.example.service.DownloadProgressEvent
import com.example.service.TikwmApiService
import com.example.service.VideoDownloadService
import com.example.service.VideoDownloader
import com.example.ui.theme.CoralPrimary
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SnapTokTheme
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TealAccent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

sealed interface OverlayUiState {
    data class Fetching(val url: String) : OverlayUiState
    data class Preview(val info: TikTokVideoInfo) : OverlayUiState
    data class Downloading(
        val percent: Int,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val info: TikTokVideoInfo
    ) : OverlayUiState
    data class Success(val outcome: VideoDownloader.DownloadOutcome) : OverlayUiState
    data class Error(val message: String, val canRetry: Boolean, val targetUrl: String) : OverlayUiState
}

class ShareOverlayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setBackgroundDrawableResource(android.R.color.transparent)

        val rawText = intent?.getStringExtra(Intent.EXTRA_TEXT) ?: ""
        val parsedUrl = TikwmApiService.extractTikTokUrl(rawText) ?: rawText.trim()

        setContent {
            SnapTokTheme(darkTheme = true) {
                ShareOverlayScreen(
                    initialUrl = parsedUrl,
                    onDismiss = { finish() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val rawText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
        val parsedUrl = TikwmApiService.extractTikTokUrl(rawText) ?: rawText.trim()

        setContent {
            SnapTokTheme(darkTheme = true) {
                ShareOverlayScreen(
                    initialUrl = parsedUrl,
                    onDismiss = { finish() }
                )
            }
        }
    }
}

@Composable
fun ShareOverlayScreen(
    initialUrl: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var state by remember {
        mutableStateOf<OverlayUiState>(
            if (initialUrl.isNotBlank()) OverlayUiState.Fetching(initialUrl)
            else OverlayUiState.Error("No valid TikTok link detected in shared content.", canRetry = false, targetUrl = "")
        )
    }

    // Step 1: Automatically fetch preview metadata as soon as overlay appears
    fun fetchPreview(url: String) {
        if (url.isBlank()) {
            state = OverlayUiState.Error("No valid TikTok URL found.", canRetry = false, targetUrl = "")
            return
        }
        state = OverlayUiState.Fetching(url)
        coroutineScope.launch {
            val result = TikwmApiService.fetchVideoInfo(url)
            result.fold(
                onSuccess = { info ->
                    // Display preview card only — do NOT start download automatically
                    state = OverlayUiState.Preview(info)
                },
                onFailure = { error ->
                    state = OverlayUiState.Error(
                        message = error.localizedMessage ?: "Failed to fetch video preview. Check connection and retry.",
                        canRetry = true,
                        targetUrl = url
                    )
                }
            )
        }
    }

    // Trigger initial fetch when launched
    LaunchedEffect(initialUrl) {
        if (initialUrl.isNotBlank()) {
            fetchPreview(initialUrl)
        }
    }

    // Listen to foreground service progress events for the active download
    LaunchedEffect(Unit) {
        VideoDownloadService.progressEvents.collect { event ->
            when (event) {
                is DownloadProgressEvent.Progress -> {
                    val current = state
                    if (current is OverlayUiState.Downloading && current.info.originalTiktokUrl == event.url) {
                        state = OverlayUiState.Downloading(
                            percent = event.percent,
                            downloadedBytes = event.downloadedBytes,
                            totalBytes = event.totalBytes,
                            info = event.info
                        )
                    }
                }
                is DownloadProgressEvent.Success -> {
                    val current = state
                    if (current is OverlayUiState.Downloading && current.info.originalTiktokUrl == event.url) {
                        state = OverlayUiState.Success(event.outcome)
                    }
                }
                is DownloadProgressEvent.Error -> {
                    val current = state
                    if (current is OverlayUiState.Downloading && current.info.originalTiktokUrl == event.url) {
                        state = OverlayUiState.Error(
                            message = event.message,
                            canRetry = true,
                            targetUrl = event.url
                        )
                    }
                }
            }
        }
    }

    // Step 3: Auto-dismiss after 2 seconds on success
    if (state is OverlayUiState.Success) {
        LaunchedEffect(state) {
            delay(2000)
            onDismiss()
        }
    }

    // Transparent root container: tapping outside card dismisses overlay immediately
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onDismiss()
            }
            .padding(horizontal = 20.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        // Floating compact card with rounded corners and elevation shadow
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(20.dp)
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    // Consume click so tapping inside the card does not dismiss
                }
                .testTag("share_overlay_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header: Compact SnapTok Identity & Cancel / Close Icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(CoralPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "ST",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp
                            )
                        }
                        Text(
                            text = "SnapTok Quick Saver",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("share_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel and return to TikTok",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Dynamic Body Content based on Step State
                AnimatedContent(
                    targetState = state,
                    transitionSpec = {
                        (fadeIn(tween(180)) + scaleIn(initialScale = 0.96f, animationSpec = tween(180)))
                            .togetherWith(fadeOut(tween(180)) + scaleOut(targetScale = 0.96f, animationSpec = tween(180)))
                    },
                    label = "OverlayStateAnimation"
                ) { uiState ->
                    when (uiState) {
                        // Step 1: Loading spinner with "Fetching video…"
                        is OverlayUiState.Fetching -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = CoralPrimary,
                                    modifier = Modifier.size(38.dp),
                                    strokeWidth = 3.5.dp
                                )
                                Text(
                                    text = "Fetching video…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Reading TikTok link metadata…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        // Step 1 Preview Card: Thumbnail, author, caption, duration, and Download button
                        is OverlayUiState.Preview -> {
                            val info = uiState.info
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Metadata row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Video Thumbnail with Duration badge
                                    Box(
                                        modifier = Modifier
                                            .size(width = 56.dp, height = 74.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        AsyncImage(
                                            model = info.coverUrl,
                                            contentDescription = "Video thumbnail",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                        if (info.durationSeconds > 0) {
                                            Surface(
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .padding(3.dp),
                                                color = Color.Black.copy(alpha = 0.75f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "${info.durationSeconds}s",
                                                    color = Color.White,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Author & Caption info
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Text(
                                            text = "@${info.authorUsername}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = CoralPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = info.title.ifBlank { "TikTok Video" },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            lineHeight = 16.sp
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Timer,
                                                contentDescription = null,
                                                modifier = Modifier.size(11.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "No Watermark • HD Ready",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }

                                // Action Buttons Row: Cancel and Download
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = onDismiss,
                                        modifier = Modifier
                                            .weight(0.8f)
                                            .height(38.dp),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Cancel", fontSize = 13.sp)
                                    }

                                    Button(
                                        onClick = {
                                            // Step 2: Download begins ONLY on tap
                                            state = OverlayUiState.Downloading(
                                                percent = 0,
                                                downloadedBytes = 0L,
                                                totalBytes = info.estimatedSizeBytes,
                                                info = info
                                            )
                                            val started = try {
                                                VideoDownloadService.startDownload(
                                                    context = context,
                                                    videoUrl = info.originalTiktokUrl,
                                                    preferHd = true,
                                                    preloadedInfo = info
                                                )
                                            } catch (t: Throwable) {
                                                false
                                            }
                                            if (!started) {
                                                coroutineScope.launch {
                                                    val downloader = com.example.service.VideoDownloader(context)
                                                    val res = downloader.downloadVideo(info, true) { pct, bytes, total ->
                                                        state = OverlayUiState.Downloading(pct, bytes, total, info)
                                                    }
                                                    res.fold(
                                                        onSuccess = { outcome ->
                                                            state = OverlayUiState.Success(outcome)
                                                        },
                                                        onFailure = { err ->
                                                            state = OverlayUiState.Error(
                                                                err.localizedMessage ?: "Download failed",
                                                                true,
                                                                info.originalTiktokUrl
                                                            )
                                                        }
                                                    )
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1.2f)
                                            .height(38.dp)
                                            .testTag("share_download_button"),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Download", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }
                        }

                        // Step 2: Downloading Progress State
                        is OverlayUiState.Downloading -> {
                            val info = uiState.info
                            val animatedProgress by animateFloatAsState(
                                targetValue = (uiState.percent / 100f).coerceIn(0f, 1f),
                                label = "DownloadProgressAnimation"
                            )

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 44.dp, height = 58.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    ) {
                                        AsyncImage(
                                            model = info.coverUrl,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Downloading… ${uiState.percent}%",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = CoralPrimary
                                        )
                                        Text(
                                            text = "@${info.authorUsername}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (uiState.downloadedBytes > 0) {
                                            Text(
                                                text = MediaSaver.formatBytes(uiState.downloadedBytes),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TealAccent,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }

                                    CircularProgressIndicator(
                                        progress = { animatedProgress },
                                        color = CoralPrimary,
                                        modifier = Modifier.size(28.dp),
                                        strokeWidth = 3.dp,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                }

                                LinearProgressIndicator(
                                    progress = { animatedProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = TealAccent,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )

                                Text(
                                    text = "Download runs in background even if closed",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    fontSize = 10.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // Step 3: Success Checkmark Animation & Saved to Gallery
                        is OverlayUiState.Success -> {
                            val outcome = uiState.outcome
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Success",
                                        tint = SuccessGreen,
                                        modifier = Modifier.size(26.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Saved to Gallery!",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = SuccessGreen
                                        )
                                        Text(
                                            text = "${MediaSaver.formatBytes(outcome.fileSize)} • Ready to watch",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 10.sp
                                        )
                                    }
                                }

                                Text(
                                    text = outcome.videoInfo.title.ifBlank { "TikTok video downloaded without watermark." },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            MediaSaver.playVideo(context, outcome.uriString, outcome.filePath)
                                            onDismiss()
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(36.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Play", fontSize = 12.sp)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            MediaSaver.shareVideo(
                                                context,
                                                outcome.uriString,
                                                outcome.filePath,
                                                outcome.videoInfo.title
                                            )
                                            onDismiss()
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(36.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Share", fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        // Failure: Clear inline error message with Retry button
                        is OverlayUiState.Error -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = ErrorRed,
                                    modifier = Modifier.size(30.dp)
                                )
                                Text(
                                    text = uiState.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = onDismiss,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(36.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Close", fontSize = 12.sp)
                                    }

                                    if (uiState.canRetry && uiState.targetUrl.isNotBlank()) {
                                        Button(
                                            onClick = { fetchPreview(uiState.targetUrl) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(36.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = null,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Retry", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
