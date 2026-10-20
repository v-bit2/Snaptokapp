package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.TikTokVideoInfo
import com.example.data.storage.MediaSaver
import com.example.ui.theme.NeoCoral
import com.example.ui.theme.NeoCyan
import com.example.ui.theme.NeoYellow

/**
 * Paper Neobrutalism Video Info Card
 */
@Composable
fun VideoInfoCard(
    info: TikTokVideoInfo,
    preferHd: Boolean,
    onDownloadClick: () -> Unit,
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
            // Upper Section: Thumbnail + Metadata
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Video Thumbnail with solid Paper Border & Shadow
                Box(modifier = Modifier.padding(end = 2.dp, bottom = 2.dp)) {
                    Box(
                        modifier = Modifier
                            .size(width = 68.dp, height = 88.dp)
                            .offset(x = 2.dp, y = 2.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.outline)
                    )
                    Box(
                        modifier = Modifier
                            .size(width = 68.dp, height = 88.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                    ) {
                        AsyncImage(
                            model = info.coverUrl,
                            contentDescription = "Video Thumbnail",
                            modifier = Modifier.matchParentSize(),
                            contentScale = ContentScale.Crop
                        )

                        // Center Play Icon Indicator
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF141416).copy(alpha = 0.7f))
                                .border(1.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        // Sticker Duration Badge
                        if (info.durationSeconds > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(3.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color(0xFF141416))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = MediaSaver.formatDuration(info.durationSeconds),
                                    color = Color.White,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }

                // Author & Video Details
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Author Profile Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (info.authorAvatarUrl.isNotBlank()) {
                            AsyncImage(
                                model = info.authorAvatarUrl,
                                contentDescription = "Author Avatar",
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(NeoYellow)
                                    .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = Color(0xFF141416),
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }

                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = info.authorNickname.ifBlank { info.authorUsername },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Verified",
                                    tint = NeoCyan,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                            Text(
                                text = "@${info.authorUsername}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Caption / Title
                    Text(
                        text = info.title.ifBlank { "TikTok Video" },
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Format Sticker Badges
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NeoBadge(
                            text = "NO WATERMARK",
                            backgroundColor = NeoYellow,
                            fontSize = 8
                        )

                        if (info.estimatedSizeBytes > 0) {
                            NeoBadge(
                                text = MediaSaver.formatBytes(info.estimatedSizeBytes),
                                backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                                textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 8
                            )
                        }
                    }
                }
            }

            // Tactile Action Button
            NeoButton(
                text = if (preferHd && !info.hdPlayUrl.isNullOrBlank()) "DOWNLOAD HD VIDEO" else "DOWNLOAD VIDEO",
                onClick = onDownloadClick,
                icon = Icons.Default.Download,
                containerColor = NeoCoral,
                contentColor = Color.White,
                height = 40.dp,
                fontSize = 12,
                shadowOffset = 2.5.dp,
                testTag = "download_now_button"
            )
        }
    }
}
