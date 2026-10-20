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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.ui.components.NeoBadge
import com.example.ui.components.NeoButton
import com.example.ui.components.NeoCard
import com.example.ui.components.NeoPillToggle
import com.example.ui.components.PhotoDownloadingProgressCard
import com.example.ui.components.PhotoGridSelectorCard
import com.example.ui.components.PhotoSuccessCard
import com.example.ui.components.ProcessingProgressCard
import com.example.ui.components.VideoInfoCard
import com.example.ui.theme.NeoCoral
import com.example.ui.theme.NeoCyan
import com.example.ui.theme.NeoYellow
import com.example.ui.viewmodel.DownloadUiState

/**
 * Clean Paper Neobrutalism Home Screen
 */
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
    onToggleImageSelect: (String) -> Unit = {},
    onSelectAllImages: () -> Unit = {},
    onDeselectAllImages: () -> Unit = {},
    onViewSavedPhotos: (photoUris: List<String>, title: String, author: String) -> Unit = { _, _, _ -> },
    onShareSavedPhotos: (photoUris: List<String>, title: String) -> Unit = { _, _ -> },
    onRetryFailedPhotos: (failedUrls: List<String>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Paper Neobrutalist Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Tactile ST Stamp
                Box(
                    modifier = Modifier.padding(end = 2.dp, bottom = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .offset(x = 2.dp, y = 2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.outline)
                    )
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(NeoYellow)
                            .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "ST",
                            color = Color(0xFF141416),
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "SnapTok",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = (-0.3).sp
                    )
                    NeoBadge(
                        text = "DIRECT",
                        backgroundColor = NeoCyan,
                        textColor = Color(0xFF141416),
                        fontSize = 9
                    )
                }
            }

            // Quality Toggle Pill
            NeoPillToggle(
                selected = preferHd,
                onClick = onTogglePreferHd,
                label = "HD",
                icon = Icons.Default.Hd,
                activeColor = NeoCoral,
                testTag = "hd_quality_toggle"
            )
        }

        // Clipboard auto-detect banner
        AnimatedVisibility(
            visible = clipboardUrl != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            clipboardUrl?.let { clipUrl ->
                NeoCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = NeoYellow.copy(alpha = 0.25f),
                    borderWidth = 1.5.dp,
                    shadowOffset = 2.5.dp,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(NeoYellow)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                tint = Color(0xFF141416),
                                modifier = Modifier.size(13.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "LINK DETECTED",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface,
                                letterSpacing = 0.4.sp
                            )
                            Text(
                                text = clipUrl.take(28) + "…",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }

                        NeoButton(
                            text = "PASTE",
                            onClick = onUseClipboardUrl,
                            containerColor = NeoYellow,
                            contentColor = Color(0xFF141416),
                            height = 28.dp,
                            fontSize = 10,
                            shadowOffset = 1.5.dp,
                            modifier = Modifier.width(68.dp)
                        )
                    }
                }
            }
        }

        // Clean Paper Input Card
        NeoCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = MaterialTheme.colorScheme.surface,
            borderWidth = 2.dp,
            shadowOffset = 3.dp,
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Outlined Input with Paper Aesthetics
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = onUrlChanged,
                    placeholder = {
                        Text(
                            "Paste TikTok link here…",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("url_input_field"),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            if (urlInput.isNotBlank()) {
                                IconButton(
                                    onClick = { onUrlChanged("") },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "Clear URL",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                            // Sticker-style Paste Button
                            Box(
                                modifier = Modifier.padding(end = 2.dp, bottom = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .offset(x = 1.dp, y = 1.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.outline)
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(NeoCyan)
                                        .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                                        .clickable(onClick = onPasteClick)
                                        .testTag("paste_button")
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentPaste,
                                            contentDescription = "Paste",
                                            tint = Color(0xFF141416),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = "Paste",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFF141416)
                                        )
                                    }
                                }
                            }
                        }
                    },
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
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.outline,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    )
                )

                // High-Contrast Tactile Download Button
                NeoButton(
                    text = "DOWNLOAD VIDEO",
                    onClick = {
                        keyboardController?.hide()
                        onFetchClick()
                    },
                    icon = Icons.Default.Download,
                    containerColor = NeoCoral,
                    contentColor = Color.White,
                    height = 42.dp,
                    fontSize = 12,
                    shadowOffset = 2.5.dp,
                    testTag = "fetch_button"
                )
            }
        }

        // Active State Views (Loading / Video Info / Photo Grid / Downloading / Success / Error)
        when (downloadState) {
            is DownloadUiState.Idle -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    NeoBadge(
                        text = "NO WATERMARK • HD VIDEO • PHOTO ALBUMS",
                        backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                        textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10,
                        shadowOffset = 1.dp
                    )
                }
            }

            is DownloadUiState.FetchingInfo -> {
                NeoCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    borderWidth = 1.5.dp,
                    shadowOffset = 2.5.dp,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = NeoCoral,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "FETCHING MEDIA INFO…",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            is DownloadUiState.InfoLoaded -> {
                if (downloadState.info.isPhotoPost) {
                    PhotoGridSelectorCard(
                        info = downloadState.info,
                        selectedImages = downloadState.selectedImages,
                        onToggleSelect = onToggleImageSelect,
                        onSelectAll = onSelectAllImages,
                        onDeselectAll = onDeselectAllImages,
                        onDownloadClick = onStartDownloadClick
                    )
                } else {
                    VideoInfoCard(
                        info = downloadState.info,
                        preferHd = preferHd,
                        onDownloadClick = onStartDownloadClick
                    )
                }
            }

            is DownloadUiState.Downloading -> {
                DownloadingProgressCard(
                    percent = downloadState.percent,
                    downloadedBytes = downloadState.downloadedBytes,
                    totalBytes = downloadState.totalBytes,
                    title = downloadState.info.title
                )
            }

            is DownloadUiState.Processing -> {
                ProcessingProgressCard(
                    percent = downloadState.percent,
                    statusMessage = downloadState.statusMessage,
                    title = downloadState.info.title
                )
            }

            is DownloadUiState.PhotoDownloading -> {
                PhotoDownloadingProgressCard(
                    completedCount = downloadState.completedCount,
                    totalCount = downloadState.totalCount,
                    percent = downloadState.percent,
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

            is DownloadUiState.PhotoSuccess -> {
                PhotoSuccessCard(
                    outcome = downloadState.outcome,
                    onViewPhotosClick = { uris, title, author ->
                        onViewSavedPhotos(uris, title, author)
                    },
                    onShareAllClick = { uris, title ->
                        onShareSavedPhotos(uris, title)
                    },
                    onRetryFailed = onRetryFailedPhotos,
                    onResetClick = onResetClick
                )
            }

            is DownloadUiState.Error -> {
                DownloadErrorCard(
                    message = downloadState.message,
                    onRetryClick = {
                        keyboardController?.hide()
                        onFetchClick()
                    }
                )
            }
        }
    }
}
