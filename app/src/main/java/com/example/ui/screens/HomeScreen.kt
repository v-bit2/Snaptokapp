package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.DownloadErrorCard
import com.example.ui.components.DownloadSuccessCard
import com.example.ui.components.DownloadingProgressCard
import com.example.ui.components.VideoInfoCard
import com.example.ui.theme.CoralPrimary
import com.example.ui.theme.CoralVariant
import com.example.ui.theme.TealAccent
import com.example.ui.viewmodel.DownloadUiState

@Composable
fun HomeScreen(
    urlInput: String,
    clipboardUrl: String?,
    preferHd: Boolean,
    downloadState: DownloadUiState,
    onUrlChanged: (String) -> Unit,
    onPasteClick: () -> Unit,
    onUseClipboardUrl: () -> Unit,
    onDismissClipboard: () -> Unit,
    onTogglePreferHd: () -> Unit,
    onFetchClick: () -> Unit,
    onStartDownloadClick: () -> Unit,
    onResetClick: () -> Unit,
    onPlaySavedVideo: (uri: String, filePath: String, title: String, author: String) -> Unit,
    onShareSavedVideo: (uri: String, filePath: String, title: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // App Header / Hero Branding
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(CoralPrimary, CoralVariant)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = "SnapTok",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "TikTok No-Watermark Downloader",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Quality Toggle Badge
            FilterChip(
                selected = preferHd,
                onClick = onTogglePreferHd,
                label = { Text("HD", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Hd,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (preferHd) CoralPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CoralPrimary.copy(alpha = 0.15f),
                    selectedLabelColor = CoralPrimary
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .height(32.dp)
                    .testTag("hd_quality_toggle")
            )
        }

        // Clipboard auto-detect banner
        AnimatedVisibility(
            visible = clipboardUrl != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            clipboardUrl?.let { clipUrl ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, TealAccent.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = null,
                            tint = TealAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Link in clipboard",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TealAccent
                            )
                            Text(
                                text = clipUrl.take(30) + "…",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = onUseClipboardUrl,
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TealAccent),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Paste & Fetch", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Main Input Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(14.dp)
                ),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Paste TikTok Link",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )

                OutlinedTextField(
                    value = urlInput,
                    onValueChange = onUrlChanged,
                    placeholder = { Text("https://www.tiktok.com/@user/video/…", fontSize = 12.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("url_input_field"),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            keyboardController?.hide()
                            onFetchClick()
                        }
                    ),
                    trailingIcon = {
                        if (urlInput.isNotBlank()) {
                            IconButton(
                                onClick = { onUrlChanged("") },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear URL", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CoralPrimary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Paste Button
                    Button(
                        onClick = onPasteClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .testTag("paste_button"),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Paste Link", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    // Fetch & Download Button
                    Button(
                        onClick = {
                            keyboardController?.hide()
                            onFetchClick()
                        },
                        modifier = Modifier
                            .weight(1.2f)
                            .height(38.dp)
                            .testTag("fetch_button"),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Fetch Video", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Active State Views (Loading / Video Info / Downloading / Success / Error)
        when (downloadState) {
            is DownloadUiState.Idle -> {
                // Info / Instructions Card
                HowToUseCard()
            }

            is DownloadUiState.FetchingInfo -> {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = CoralPrimary, strokeWidth = 3.dp)
                        Text(
                            text = "Analyzing TikTok link…",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Extracting clean no-watermark stream",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            is DownloadUiState.InfoLoaded -> {
                VideoInfoCard(
                    info = downloadState.info,
                    preferHd = preferHd,
                    onDownloadClick = onStartDownloadClick
                )
            }

            is DownloadUiState.Downloading -> {
                DownloadingProgressCard(
                    percent = downloadState.percent,
                    downloadedBytes = downloadState.downloadedBytes,
                    totalBytes = downloadState.totalBytes,
                    title = downloadState.info.title
                )
            }

            is DownloadUiState.Success -> {
                DownloadSuccessCard(
                    outcome = downloadState.outcome,
                    onPlayClick = {
                        onPlaySavedVideo(
                            downloadState.outcome.uriString,
                            downloadState.outcome.filePath,
                            downloadState.outcome.videoInfo.title,
                            downloadState.outcome.videoInfo.authorUsername
                        )
                    },
                    onShareClick = {
                        onShareSavedVideo(
                            downloadState.outcome.uriString,
                            downloadState.outcome.filePath,
                            downloadState.outcome.videoInfo.title
                        )
                    },
                    onResetClick = onResetClick
                )
            }

            is DownloadUiState.Error -> {
                DownloadErrorCard(
                    message = downloadState.message,
                    onRetryClick = onFetchClick
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun HowToUseCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                shape = RoundedCornerShape(14.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = CoralPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "How to save videos without watermark",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Step 1
            StepItem(
                number = "1",
                title = "Copy Link in TikTok",
                desc = "Open TikTok, tap Share on any video, and tap 'Copy Link' (or share directly to SnapTok)."
            )

            // Step 2
            StepItem(
                number = "2",
                title = "Paste into SnapTok",
                desc = "Tap 'Paste Link' above. SnapTok automatically reads and validates the video URL."
            )

            // Step 3
            StepItem(
                number = "3",
                title = "Saved to Gallery",
                desc = "Tap Download! The video is saved directly to your phone's Gallery under Movies/SnapTok."
            )
        }
    }
}

@Composable
private fun StepItem(
    number: String,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(CoralPrimary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                color = CoralPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = desc,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
