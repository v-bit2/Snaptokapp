package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.entity.DownloadedVideoEntity
import com.example.data.storage.MediaSaver
import com.example.ui.components.NeoBadge
import com.example.ui.components.NeoButton
import com.example.ui.components.NeoCard
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.NeoCoral
import com.example.ui.theme.NeoCyan
import com.example.ui.theme.NeoYellow

/**
 * Clean Paper Neobrutalism History Screen
 */
@Composable
fun HistoryScreen(
    videos: List<DownloadedVideoEntity>,
    searchQuery: String,
    onSearchChanged: (String) -> Unit,
    onPlayVideo: (uri: String, filePath: String, title: String, author: String) -> Unit,
    onShareVideo: (uri: String, filePath: String, title: String) -> Unit,
    onViewPhotos: (photoUris: List<String>, title: String, author: String) -> Unit = { _, _, _ -> },
    onSharePhotos: (photoUris: List<String>, title: String) -> Unit = { _, _ -> },
    onDeleteVideo: (DownloadedVideoEntity) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<DownloadedVideoEntity?>(null) }

    val totalBytes = remember(videos) { videos.sumOf { it.fileSizeBytes } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Library",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = (-0.3).sp
                    )
                    NeoBadge(
                        text = "${videos.size} SAVED",
                        backgroundColor = NeoYellow,
                        fontSize = 9
                    )
                }
                Text(
                    text = "${MediaSaver.formatBytes(totalBytes)} in Gallery",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (videos.isNotEmpty()) {
                Box(modifier = Modifier.padding(end = 2.dp, bottom = 2.dp)) {
                    Box(
                        modifier = Modifier
                            .offset(x = 1.5.dp, y = 1.5.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.outline)
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                            .clickable { showClearAllConfirm = true }
                            .testTag("clear_all_button")
                            .padding(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear All",
                            tint = ErrorRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Search Bar with Paper Neobrutalism Border
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChanged,
            placeholder = { Text("Search by caption or @creator…", fontSize = 12.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(
                        onClick = { onSearchChanged("") },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "Clear search",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("history_search_field"),
            shape = RoundedCornerShape(8.dp),
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.outline,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        // List or Empty
        if (videos.isEmpty()) {
            EmptyHistoryView(isSearching = searchQuery.isNotBlank())
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("history_list"),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(videos, key = { it.id }) { video ->
                    HistoryItemCard(
                        video = video,
                        onPlayClick = {
                            if (video.isPhotoPost) {
                                onViewPhotos(video.getPhotoUris(), video.title, video.authorHandle)
                            } else {
                                onPlayVideo(video.videoUri, video.filePath, video.title, video.authorHandle)
                            }
                        },
                        onShareClick = {
                            if (video.isPhotoPost) {
                                onSharePhotos(video.getPhotoUris(), video.title)
                            } else {
                                onShareVideo(video.videoUri, video.filePath, video.title)
                            }
                        },
                        onDeleteClick = { itemToDelete = video }
                    )
                }
            }
        }
    }

    // Delete single confirmation
    itemToDelete?.let { video ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = {
                Text(
                    "DELETE DOWNLOAD?",
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp
                )
            },
            text = {
                Text(
                    "Are you sure you want to remove \"${video.title.ifBlank { "this item" }}\" from your history?",
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                NeoButton(
                    text = "DELETE",
                    onClick = {
                        onDeleteVideo(video)
                        itemToDelete = null
                    },
                    containerColor = ErrorRed,
                    contentColor = Color.White,
                    height = 32.dp,
                    fontSize = 11,
                    shadowOffset = 2.dp,
                    modifier = Modifier.width(90.dp)
                )
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancel", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        )
    }

    // Clear All confirmation
    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = {
                Text(
                    "CLEAR ALL HISTORY?",
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp
                )
            },
            text = {
                Text(
                    "This will remove all downloaded items from your history.",
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                NeoButton(
                    text = "CLEAR ALL",
                    onClick = {
                        onClearAll()
                        showClearAllConfirm = false
                    },
                    containerColor = ErrorRed,
                    contentColor = Color.White,
                    height = 32.dp,
                    fontSize = 11,
                    shadowOffset = 2.dp,
                    modifier = Modifier.width(100.dp)
                )
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) {
                    Text("Cancel", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        )
    }
}

@Composable
fun HistoryItemCard(
    video: DownloadedVideoEntity,
    onPlayClick: () -> Unit,
    onShareClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    NeoCard(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = MaterialTheme.colorScheme.surface,
        borderWidth = 1.5.dp,
        shadowOffset = 2.5.dp,
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(8.dp),
        onClick = onPlayClick,
        testTag = "history_item_${video.id}"
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail with solid border
            Box(modifier = Modifier.padding(end = 1.dp, bottom = 1.dp)) {
                Box(
                    modifier = Modifier
                        .size(width = 54.dp, height = 72.dp)
                        .offset(x = 1.5.dp, y = 1.5.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.outline)
                )
                Box(
                    modifier = Modifier
                        .size(width = 54.dp, height = 72.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = video.coverUrl,
                        contentDescription = "Cover",
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop
                    )

                    // Play / Photos icon badge
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF141416).copy(alpha = 0.75f))
                            .border(1.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (video.isPhotoPost) Icons.Default.Collections else Icons.Default.PlayArrow,
                            contentDescription = if (video.isPhotoPost) "View photos" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    if (video.isPhotoPost) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(2.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(NeoCyan)
                                .padding(horizontal = 3.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "${video.photoCount}P",
                                color = Color(0xFF141416),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    } else if (video.durationSeconds > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(2.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFF141416))
                                .padding(horizontal = 3.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = MediaSaver.formatDuration(video.durationSeconds),
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Details
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = video.title.ifBlank { "TikTok Video" },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "@${video.authorHandle}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeoCoral,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NeoBadge(
                        text = if (video.isPhotoPost) "${video.photoCount} PHOTOS" else "NO WATERMARK",
                        backgroundColor = if (video.isPhotoPost) NeoCyan else NeoYellow,
                        fontSize = 8
                    )
                    Text(
                        text = MediaSaver.formatBytes(video.fileSizeBytes),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Quick Actions: Share & Delete
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconButton(onClick = onShareClick, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(15.dp)
                    )
                }

                IconButton(onClick = onDeleteClick, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = ErrorRed,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyHistoryView(isSearching: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(modifier = Modifier.padding(end = 2.dp, bottom = 2.dp)) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .offset(x = 2.5.dp, y = 2.5.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.outline)
            )
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(NeoYellow)
                    .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSearching) Icons.Default.Search else Icons.Default.VideoLibrary,
                    contentDescription = null,
                    tint = Color(0xFF141416),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = if (isSearching) "NO MATCHING DOWNLOADS" else "YOUR LIBRARY IS EMPTY",
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = if (isSearching) "Try searching with a different caption or username"
            else "Paste any TikTok video or photo link on the Downloader tab to save it here!",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
