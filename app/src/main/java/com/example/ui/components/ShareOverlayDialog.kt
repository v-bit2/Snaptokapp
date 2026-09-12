package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.ui.theme.CoralPrimary
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TealAccent
import com.example.ui.viewmodel.ShareModalState
import kotlinx.coroutines.delay

@Composable
fun ShareOverlayDialog(
    state: ShareModalState,
    onDismiss: () -> Unit,
    onPlayClick: (uri: String, filePath: String) -> Unit,
    onShareClick: (uri: String, filePath: String, title: String) -> Unit,
    onToggleImageSelect: (String) -> Unit = {},
    onSelectAllImages: () -> Unit = {},
    onDeselectAllImages: () -> Unit = {},
    onStartDownload: () -> Unit = {},
    onViewPhotosClick: (photoUris: List<String>, title: String, author: String) -> Unit = { _, _, _ -> },
    onShareAllPhotosClick: (photoUris: List<String>, title: String) -> Unit = { _, _ -> }
) {
    // Auto-dismiss after 4 seconds on success
    if (state is ShareModalState.Success) {
        LaunchedEffect(state) {
            delay(4000)
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(24.dp))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(24.dp)
                )
                .testTag("share_overlay_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Row with App Identity and Close
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
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CoralPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "ST",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp
                            )
                        }
                        Text(
                            text = "SnapTok Quick Download",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Dynamic Body depending on State
                AnimatedContent(
                    targetState = state,
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                    label = "ShareModalState"
                ) { modalState ->
                    when (modalState) {
                        is ShareModalState.Fetching -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = CoralPrimary,
                                    modifier = Modifier.size(36.dp),
                                    strokeWidth = 3.dp
                                )
                                Text(
                                    text = "Fetching TikTok details…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Analyzing link from share intent",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        is ShareModalState.Preview -> {
                            val info = modalState.info
                            if (info.isPhotoPost) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "@${info.authorUsername}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = "${modalState.selectedImages.size} of ${info.images.size} selected",
                                                fontSize = 11.sp,
                                                color = CoralPrimary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }

                                        TextButton(
                                            onClick = {
                                                if (modalState.selectedImages.size == info.images.size) {
                                                    onDeselectAllImages()
                                                } else {
                                                    onSelectAllImages()
                                                }
                                            }
                                        ) {
                                            Text(
                                                text = if (modalState.selectedImages.size == info.images.size) "Deselect All" else "Select All",
                                                fontSize = 11.sp,
                                                color = TealAccent,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    // Horizontal preview strip with selection
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        itemsIndexed(info.images) { index, imgUrl ->
                                            val isSelected = modalState.selectedImages.contains(imgUrl)
                                            Box(
                                                modifier = Modifier
                                                    .size(72.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .border(
                                                        width = if (isSelected) 2.dp else 1.dp,
                                                        color = if (isSelected) CoralPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                    .clickable { onToggleImageSelect(imgUrl) }
                                            ) {
                                                AsyncImage(
                                                    model = imgUrl,
                                                    contentDescription = "Photo ${index + 1}",
                                                    modifier = Modifier.fillMaxWidth(),
                                                    contentScale = ContentScale.Crop
                                                )

                                                // Checkbox badge
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(3.dp)
                                                        .size(20.dp)
                                                        .clip(CircleShape)
                                                        .background(if (isSelected) CoralPrimary else Color.Black.copy(alpha = 0.5f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (isSelected) {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = null,
                                                            tint = Color.White,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Button(
                                        onClick = onStartDownload,
                                        enabled = modalState.selectedImages.isNotEmpty(),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary)
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Download Selected (${modalState.selectedImages.size})",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(
                                            model = info.coverUrl,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(width = 44.dp, height = 58.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = info.title.ifBlank { "TikTok Video" },
                                                style = MaterialTheme.typography.titleSmall,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "@${info.authorUsername}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = onStartDownload,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary)
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Download No-Watermark Video", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        is ShareModalState.Downloading -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = modalState.info.coverUrl,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(width = 44.dp, height = 58.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )

                                    Column(modifier = Modifier.weight(1f)) {
                                        val downloadTitle = if (modalState.info.isPhotoPost) {
                                            "Downloading ${modalState.completedCount} of ${modalState.totalCount} photos… (${modalState.percent}%)"
                                        } else {
                                            "Downloading… ${modalState.percent}%"
                                        }
                                        Text(
                                            text = downloadTitle,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = CoralPrimary
                                        )
                                        Text(
                                            text = "@${modalState.info.authorUsername}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                LinearProgressIndicator(
                                    progress = { (modalState.percent / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = TealAccent,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                        }

                        is ShareModalState.Success -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = SuccessGreen,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = if (modalState.info.isPhotoPost) "${modalState.savedUris.size} Photos Saved to Gallery!" else "Saved to Gallery!",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = SuccessGreen
                                    )
                                }

                                Text(
                                    text = modalState.info.title.ifBlank { "Downloaded without watermark." },
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (modalState.info.isPhotoPost) {
                                        Button(
                                            onClick = {
                                                onViewPhotosClick(
                                                    modalState.savedUris,
                                                    modalState.info.title,
                                                    modalState.info.authorUsername
                                                )
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary)
                                        ) {
                                            Icon(Icons.Default.Collections, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("View")
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                onShareAllPhotosClick(modalState.savedUris, modalState.info.title)
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Share All")
                                        }
                                    } else {
                                        Button(
                                            onClick = { onPlayClick(modalState.uriString, modalState.filePath) },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Play")
                                        }

                                        OutlinedButton(
                                            onClick = { onShareClick(modalState.uriString, modalState.filePath, modalState.info.title) },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Share")
                                        }
                                    }
                                }
                            }
                        }

                        is ShareModalState.Error -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = null,
                                    tint = ErrorRed,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = modalState.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                                Button(
                                    onClick = onDismiss,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary)
                                ) {
                                    Text("Dismiss")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
