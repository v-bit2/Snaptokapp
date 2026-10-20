package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.storage.MediaSaver
import com.example.service.VideoDownloader
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.NeoCoral
import com.example.ui.theme.NeoCyan
import com.example.ui.theme.NeoMint
import com.example.ui.theme.NeoYellow

@Composable
fun DownloadingProgressCard(
    percent: Int,
    downloadedBytes: Long,
    totalBytes: Long,
    title: String,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = (percent / 100f).coerceIn(0f, 1f),
        label = "DownloadProgress"
    )

    NeoCard(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = MaterialTheme.colorScheme.surface,
        borderWidth = 2.dp,
        shadowOffset = 3.dp,
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        color = NeoCoral,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "DOWNLOADING VIDEO…",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = 0.4.sp
                    )
                }

                NeoBadge(
                    text = "$percent%",
                    backgroundColor = NeoYellow,
                    fontSize = 11,
                    shadowOffset = 1.5.dp
                )
            }

            // Paper Neo Progress Bar (Solid border with filled chunk)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(5.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(NeoCoral)
                )
            }

            Text(
                text = if (totalBytes > 0) {
                    "${MediaSaver.formatBytes(downloadedBytes)} / ${MediaSaver.formatBytes(totalBytes)}"
                } else {
                    MediaSaver.formatBytes(downloadedBytes)
                },
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ProcessingProgressCard(
    percent: Int,
    statusMessage: String,
    title: String,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = (percent / 100f).coerceIn(0f, 1f),
        label = "ProcessingProgress"
    )

    NeoCard(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = MaterialTheme.colorScheme.surface,
        borderWidth = 2.dp,
        shadowOffset = 3.dp,
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        color = NeoMint,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = statusMessage.ifBlank { "SAVING VIDEO…" }.uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = 0.4.sp
                    )
                }

                NeoBadge(
                    text = "$percent%",
                    backgroundColor = NeoMint,
                    fontSize = 11,
                    shadowOffset = 1.5.dp
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(5.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(NeoMint)
                )
            }
        }
    }
}

@Composable
fun PhotoDownloadingProgressCard(
    completedCount: Int,
    totalCount: Int,
    percent: Int,
    title: String,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = (percent / 100f).coerceIn(0f, 1f),
        label = "PhotoDownloadProgress"
    )

    NeoCard(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = MaterialTheme.colorScheme.surface,
        borderWidth = 2.dp,
        shadowOffset = 3.dp,
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "SAVING PHOTOS ($completedCount/$totalCount)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 0.4.sp
                )

                NeoBadge(
                    text = "$percent%",
                    backgroundColor = NeoCyan,
                    fontSize = 11,
                    shadowOffset = 1.5.dp
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(5.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(NeoCyan)
                )
            }
        }
    }
}

@Composable
fun DownloadSuccessCard(
    outcome: VideoDownloader.DownloadOutcome,
    onPlayClick: () -> Unit,
    onShareClick: () -> Unit,
    onResetClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    NeoCard(
        modifier = modifier.fillMaxWidth(),
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
            // Header Ticket Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                NeoBadge(
                    text = "SAVED TO GALLERY ✓",
                    backgroundColor = NeoMint,
                    textColor = Color(0xFF141416),
                    fontSize = 10,
                    shadowOffset = 1.5.dp
                )

                Text(
                    text = MediaSaver.formatBytes(outcome.fileSize),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Preview Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.padding(end = 1.dp, bottom = 1.dp)) {
                    Box(
                        modifier = Modifier
                            .size(width = 38.dp, height = 50.dp)
                            .offset(x = 1.5.dp, y = 1.5.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.outline)
                    )
                    Box(
                        modifier = Modifier
                            .size(width = 38.dp, height = 50.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                    ) {
                        AsyncImage(
                            model = outcome.videoInfo.coverUrl,
                            contentDescription = "Saved Thumbnail",
                            modifier = Modifier.matchParentSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = outcome.videoInfo.title.ifBlank { "TikTok Video" },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "@${outcome.videoInfo.authorUsername}",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Tactile Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                NeoButton(
                    text = "PLAY",
                    onClick = onPlayClick,
                    icon = Icons.Default.PlayArrow,
                    containerColor = NeoMint,
                    contentColor = Color(0xFF141416),
                    height = 36.dp,
                    fontSize = 11,
                    shadowOffset = 2.dp,
                    modifier = Modifier.weight(1f),
                    testTag = "action_play_button"
                )

                NeoButton(
                    text = "SHARE",
                    onClick = onShareClick,
                    icon = Icons.Default.Share,
                    containerColor = NeoCyan,
                    contentColor = Color(0xFF141416),
                    height = 36.dp,
                    fontSize = 11,
                    shadowOffset = 2.dp,
                    modifier = Modifier.weight(1f),
                    testTag = "action_share_button"
                )

                NeoButton(
                    text = "NEW",
                    onClick = onResetClick,
                    icon = Icons.Default.Refresh,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    height = 36.dp,
                    fontSize = 11,
                    shadowOffset = 2.dp,
                    modifier = Modifier.width(76.dp),
                    testTag = "action_new_button"
                )
            }
        }
    }
}

@Composable
fun PhotoSuccessCard(
    outcome: VideoDownloader.PhotoDownloadOutcome,
    onViewPhotosClick: (photoUris: List<String>, title: String, author: String) -> Unit,
    onShareAllClick: (photoUris: List<String>, title: String) -> Unit,
    onRetryFailed: (failedUrls: List<String>) -> Unit,
    onResetClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    NeoCard(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = MaterialTheme.colorScheme.surface,
        borderWidth = 2.dp,
        shadowOffset = 3.dp,
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(12.dp),
        testTag = "photo_success_card"
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                NeoBadge(
                    text = "${outcome.successfulCount} PHOTOS SAVED ✓",
                    backgroundColor = NeoMint,
                    textColor = Color(0xFF141416),
                    fontSize = 10,
                    shadowOffset = 1.5.dp
                )

                Text(
                    text = MediaSaver.formatBytes(outcome.totalSize),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Thumbnail strip with paper borders
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(outcome.savedUris) { index, uri ->
                    Box(modifier = Modifier.padding(end = 1.dp, bottom = 1.dp)) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .offset(x = 1.5.dp, y = 1.5.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.outline)
                        )
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                                .clickable {
                                    onViewPhotosClick(
                                        outcome.savedUris,
                                        outcome.postInfo.title,
                                        outcome.postInfo.authorUsername
                                    )
                                }
                        ) {
                            AsyncImage(
                                model = uri,
                                contentDescription = "Saved Photo ${index + 1}",
                                modifier = Modifier.matchParentSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }

            // Partial failure warning if any failed
            if (outcome.failedUrls.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(ErrorRed.copy(alpha = 0.15f))
                        .border(1.5.dp, ErrorRed, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(14.dp))
                            Text(
                                text = "${outcome.failedUrls.size} failed",
                                fontSize = 10.sp,
                                color = ErrorRed,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        NeoButton(
                            text = "RETRY",
                            onClick = { onRetryFailed(outcome.failedUrls) },
                            containerColor = ErrorRed,
                            contentColor = Color.White,
                            height = 26.dp,
                            fontSize = 9,
                            shadowOffset = 1.dp,
                            modifier = Modifier.width(60.dp)
                        )
                    }
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                NeoButton(
                    text = "VIEW",
                    onClick = {
                        onViewPhotosClick(
                            outcome.savedUris,
                            outcome.postInfo.title,
                            outcome.postInfo.authorUsername
                        )
                    },
                    icon = Icons.Default.Collections,
                    containerColor = NeoCoral,
                    contentColor = Color.White,
                    height = 36.dp,
                    fontSize = 11,
                    shadowOffset = 2.dp,
                    modifier = Modifier.weight(1.1f),
                    testTag = "action_view_photos_button"
                )

                NeoButton(
                    text = "SHARE",
                    onClick = {
                        onShareAllClick(outcome.savedUris, outcome.postInfo.title)
                    },
                    icon = Icons.Default.Share,
                    containerColor = NeoCyan,
                    contentColor = Color(0xFF141416),
                    height = 36.dp,
                    fontSize = 11,
                    shadowOffset = 2.dp,
                    modifier = Modifier.weight(1f),
                    testTag = "action_share_all_photos_button"
                )

                NeoButton(
                    text = "NEW",
                    onClick = onResetClick,
                    icon = Icons.Default.Refresh,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    height = 36.dp,
                    fontSize = 11,
                    shadowOffset = 2.dp,
                    modifier = Modifier.width(76.dp),
                    testTag = "action_photo_new_button"
                )
            }
        }
    }
}

@Composable
fun DownloadErrorCard(
    message: String,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    NeoCard(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = MaterialTheme.colorScheme.surface,
        borderColor = ErrorRed,
        shadowColor = MaterialTheme.colorScheme.outline,
        borderWidth = 2.dp,
        shadowOffset = 3.dp,
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(ErrorRed)
                    .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            NeoBadge(
                text = "DOWNLOAD FAILED",
                backgroundColor = ErrorRed,
                textColor = Color.White,
                fontSize = 10,
                shadowOffset = 1.dp
            )

            Text(
                text = message,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            NeoButton(
                text = "TRY AGAIN",
                onClick = onRetryClick,
                icon = Icons.Default.Refresh,
                containerColor = NeoCoral,
                contentColor = Color.White,
                height = 34.dp,
                fontSize = 11,
                shadowOffset = 2.dp,
                modifier = Modifier.width(140.dp),
                testTag = "error_retry_button"
            )
        }
    }
}
