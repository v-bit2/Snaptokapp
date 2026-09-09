package com.example.ui.screens

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.storage.MediaSaver
import com.example.ui.theme.CoralPrimary
import com.example.ui.theme.TealAccent
import com.example.ui.viewmodel.VideoPlaybackInfo
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

enum class VideoScaleMode {
    FIT_CENTER,
    CROP_FILL
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    video: VideoPlaybackInfo,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Intercept back button to dismiss screen
    BackHandler(onBack = onClose)

    // Player States
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }

    var isPlaying by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }

    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderDragPositionMs by remember { mutableFloatStateOf(0f) }

    var isMuted by remember { mutableStateOf(false) }
    var isLooping by remember { mutableStateOf(true) }
    var scaleMode by remember { mutableStateOf(VideoScaleMode.FIT_CENTER) }

    var areControlsVisible by remember { mutableStateOf(true) }
    var lastInteractionTimestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Periodically update progress while playing
    LaunchedEffect(isPlaying, isDraggingSlider) {
        while (isPlaying && !isDraggingSlider) {
            videoViewRef?.let { vv ->
                try {
                    val pos = vv.currentPosition.toLong().coerceAtLeast(0L)
                    val dur = vv.duration.toLong().coerceAtLeast(0L)
                    currentPositionMs = pos
                    if (dur > 0L) {
                        durationMs = dur
                    }
                } catch (ignored: Exception) {}
            }
            delay(200)
        }
    }

    // Auto-hide controls after 4 seconds of inactivity when playing
    LaunchedEffect(areControlsVisible, isPlaying, lastInteractionTimestamp, isDraggingSlider) {
        if (areControlsVisible && isPlaying && !isDraggingSlider) {
            delay(4000)
            areControlsVisible = false
        }
    }

    fun resetControlsTimer() {
        areControlsVisible = true
        lastInteractionTimestamp = System.currentTimeMillis()
    }

    fun togglePlayPause() {
        resetControlsTimer()
        videoViewRef?.let { vv ->
            if (isPlaying) {
                vv.pause()
                isPlaying = false
            } else {
                vv.start()
                isPlaying = true
            }
        }
    }

    fun seekRelative(offsetMs: Long) {
        resetControlsTimer()
        videoViewRef?.let { vv ->
            val target = (vv.currentPosition + offsetMs).coerceIn(0L, durationMs.coerceAtLeast(1L))
            vv.seekTo(target.toInt())
            currentPositionMs = target
        }
    }

    fun toggleMute() {
        resetControlsTimer()
        val newMuted = !isMuted
        isMuted = newMuted
        mediaPlayerRef?.let { mp ->
            try {
                val vol = if (newMuted) 0f else 1f
                mp.setVolume(vol, vol)
            } catch (ignored: Exception) {}
        }
    }

    fun toggleLoop() {
        resetControlsTimer()
        val newLoop = !isLooping
        isLooping = newLoop
        mediaPlayerRef?.let { mp ->
            try {
                mp.isLooping = newLoop
            } catch (ignored: Exception) {}
        }
    }

    // Release player on dispose
    DisposableEffect(video.uriString) {
        onDispose {
            try {
                videoViewRef?.stopPlayback()
            } catch (ignored: Exception) {}
            videoViewRef = null
            mediaPlayerRef = null
        }
    }

    // Full Screen Container
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF08080C))
            .testTag("video_player_screen")
    ) {
        // --- Video View Surface ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    areControlsVisible = !areControlsVisible
                    lastInteractionTimestamp = System.currentTimeMillis()
                },
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            Gravity.CENTER
                        )

                        val videoUri = try {
                            val parsed = Uri.parse(video.uriString)
                            if (parsed.scheme == "content") parsed else Uri.fromFile(File(video.filePath))
                        } catch (e: Exception) {
                            Uri.fromFile(File(video.filePath))
                        }

                        setVideoURI(videoUri)

                        setOnPreparedListener { mp ->
                            mediaPlayerRef = mp
                            isLoading = false
                            errorMessage = null
                            mp.isLooping = isLooping
                            val vol = if (isMuted) 0f else 1f
                            mp.setVolume(vol, vol)
                            durationMs = mp.duration.toLong().coerceAtLeast(0L)
                            start()
                            isPlaying = true
                        }

                        setOnCompletionListener {
                            if (!isLooping) {
                                isPlaying = false
                                areControlsVisible = true
                            }
                        }

                        setOnErrorListener { _, what, extra ->
                            isLoading = false
                            errorMessage = "Unable to play video format (error $what:$extra)."
                            true
                        }

                        videoViewRef = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // --- Loading Spinner ---
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        color = CoralPrimary,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "Loading video…",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // --- Error Banner ---
        errorMessage?.let { error ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF1E1E28),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Playback Error",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = error,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.White.copy(alpha = 0.1f),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onClose() }
                                    .padding(vertical = 12.dp)
                            ) {
                                Text(
                                    text = "Close",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = CoralPrimary,
                                modifier = Modifier
                                    .weight(1.3f)
                                    .clickable {
                                        MediaSaver.playVideo(context, video.uriString, video.filePath)
                                    }
                                    .padding(vertical = 12.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.OpenInNew,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "External Player",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Controls Overlay ---
        AnimatedVisibility(
            visible = areControlsVisible && errorMessage == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top Gradient Vignette
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.85f),
                                    Color.Black.copy(alpha = 0.4f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Bottom Gradient Vignette
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.5f),
                                    Color.Black.copy(alpha = 0.92f)
                                )
                            )
                        )
                )

                // === TOP BAR ===
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Back Button
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.55f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("player_back_button")
                            .clickable { onClose() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Close player",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Video Info Header
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                    ) {
                        Text(
                            text = video.title.ifBlank { "TikTok Video" },
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "@${video.author.ifBlank { "creator" }}",
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = CoralPrimary.copy(alpha = 0.25f),
                                border = androidx.compose.foundation.BorderStroke(0.5.dp, CoralPrimary.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "No Watermark",
                                    color = CoralPrimary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    // Top Action Icons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Loop toggle button
                        Surface(
                            shape = CircleShape,
                            color = if (isLooping) CoralPrimary.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.55f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isLooping) CoralPrimary else Color.White.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("player_loop_button")
                                .clickable { toggleLoop() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isLooping) Icons.Default.RepeatOne else Icons.Default.Repeat,
                                    contentDescription = "Toggle looping",
                                    tint = if (isLooping) CoralPrimary else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Mute/Unmute button
                        Surface(
                            shape = CircleShape,
                            color = if (isMuted) Color.Red.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.55f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isMuted) Color.Red.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("player_mute_button")
                                .clickable { toggleMute() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isMuted) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                                    contentDescription = "Toggle audio",
                                    tint = if (isMuted) Color(0xFFFF6B6B) else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Share button
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.55f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("player_share_button")
                                .clickable {
                                    MediaSaver.shareVideo(context, video.uriString, video.filePath, video.title)
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share video",
                                    tint = Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }
                }

                // === CENTER PLAYBACK CONTROLS ===
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    // -10s Rewind Button
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.6f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .size(54.dp)
                            .testTag("player_rewind_button")
                            .clickable { seekRelative(-10_000L) }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Replay10,
                                    contentDescription = "Rewind 10 seconds",
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }

                    // Play/Pause Master Button
                    Surface(
                        shape = CircleShape,
                        color = CoralPrimary,
                        shadowElevation = 12.dp,
                        modifier = Modifier
                            .size(72.dp)
                            .testTag("player_play_pause_button")
                            .clickable { togglePlayPause() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    // +10s Fast Forward Button
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.6f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .size(54.dp)
                            .testTag("player_forward_button")
                            .clickable { seekRelative(10_000L) }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Forward10,
                                    contentDescription = "Forward 10 seconds",
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }
                }

                // === BOTTOM CONTROLS & TIMELINE ===
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Timeline Slider & Timestamps
                    val displayPositionMs = if (isDraggingSlider) sliderDragPositionMs.toLong() else currentPositionMs
                    val sliderValue = if (durationMs > 0L) {
                        (displayPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTimeMs(displayPositionMs),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace
                        )

                        Text(
                            text = formatTimeMs(durationMs),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Scrubbing Slider
                    Slider(
                        value = sliderValue,
                        onValueChange = { fraction ->
                            isDraggingSlider = true
                            sliderDragPositionMs = fraction * durationMs.toFloat()
                        },
                        onValueChangeFinished = {
                            videoViewRef?.let { vv ->
                                val target = sliderDragPositionMs.toInt()
                                vv.seekTo(target)
                                currentPositionMs = target.toLong()
                            }
                            isDraggingSlider = false
                            resetControlsTimer()
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = CoralPrimary,
                            activeTrackColor = CoralPrimary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .testTag("player_timeline_slider")
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Bottom Utility Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Restart from beginning
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.1f),
                            modifier = Modifier
                                .testTag("player_replay_button")
                                .clickable {
                                    videoViewRef?.let { vv ->
                                        vv.seekTo(0)
                                        currentPositionMs = 0L
                                        if (!isPlaying) {
                                            vv.start()
                                            isPlaying = true
                                        }
                                        resetControlsTimer()
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Replay,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Replay",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // External Player Button
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                            modifier = Modifier
                                .testTag("player_external_button")
                                .clickable {
                                    MediaSaver.playVideo(context, video.uriString, video.filePath)
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInNew,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Open in External App",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTimeMs(timeMs: Long): String {
    val totalSeconds = (timeMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
