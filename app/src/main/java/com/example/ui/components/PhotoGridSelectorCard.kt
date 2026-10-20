package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.example.data.model.TikTokVideoInfo
import com.example.ui.theme.NeoCoral
import com.example.ui.theme.NeoCyan
import com.example.ui.theme.NeoYellow

/**
 * Clean Paper Neobrutalism Photo Grid Selector Card
 */
@Composable
fun PhotoGridSelectorCard(
    info: TikTokVideoInfo,
    selectedImages: Set<String>,
    onToggleSelect: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var previewIndex by remember { mutableStateOf<Int?>(null) }
    val totalCount = info.images.size
    val selectedCount = selectedImages.size
    val allSelected = selectedCount == totalCount && totalCount > 0

    NeoCard(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = MaterialTheme.colorScheme.surface,
        borderWidth = 2.dp,
        shadowOffset = 3.dp,
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(12.dp),
        testTag = "photo_grid_selector_card"
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Author & Type Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (info.authorAvatarUrl.isNotBlank()) {
                    AsyncImage(
                        model = info.authorAvatarUrl,
                        contentDescription = "Avatar",
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(NeoYellow)
                            .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color(0xFF141416),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = info.authorNickname.ifBlank { info.authorUsername },
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "@${info.authorUsername}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                NeoBadge(
                    text = "$totalCount PHOTOS",
                    backgroundColor = NeoCyan,
                    textColor = Color(0xFF141416),
                    fontSize = 9
                )
            }

            // Caption
            if (info.title.isNotBlank()) {
                Text(
                    text = info.title,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Selection Controls Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$selectedCount of $totalCount selected",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selectedCount > 0) NeoCoral else MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Neo Button to Toggle Select All
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (allSelected) NeoCoral else NeoYellow)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                        .clickable { if (allSelected) onDeselectAll() else onSelectAll() }
                        .testTag("select_all_toggle_button")
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (allSelected) "Deselect All" else "Select All",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = if (allSelected) Color.White else Color(0xFF141416)
                    )
                }
            }

            // Grid of Thumbnails
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(info.images) { index, imageUrl ->
                        val isSelected = selectedImages.contains(imageUrl)

                        Box(modifier = Modifier.padding(end = 1.dp, bottom = 1.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .offset(x = 1.5.dp, y = 1.5.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.outline)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(
                                        width = if (isSelected) 2.dp else 1.5.dp,
                                        color = if (isSelected) NeoCoral else MaterialTheme.colorScheme.outline,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable { previewIndex = index }
                                    .testTag("photo_thumbnail_$index")
                            ) {
                                AsyncImage(
                                    model = imageUrl,
                                    contentDescription = "Photo ${index + 1}",
                                    modifier = Modifier.matchParentSize(),
                                    contentScale = ContentScale.Crop
                                )

                                // Index badge
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(3.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(Color(0xFF141416))
                                        .padding(horizontal = 3.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "#${index + 1}",
                                        color = Color.White,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }

                                // Selection Stamp Badge
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) NeoCoral else Color(0xFF141416).copy(alpha = 0.6f)
                                        )
                                        .border(
                                            width = 1.5.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.outline else Color.White,
                                            shape = CircleShape
                                        )
                                        .clickable { onToggleSelect(imageUrl) }
                                        .testTag("checkbox_photo_$index"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Download Action Button
            NeoButton(
                text = if (selectedCount > 0) "DOWNLOAD SELECTED ($selectedCount)" else "SELECT PHOTOS TO DOWNLOAD",
                onClick = onDownloadClick,
                icon = Icons.Default.Download,
                containerColor = if (selectedCount > 0) NeoCoral else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (selectedCount > 0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                height = 42.dp,
                fontSize = 12,
                shadowOffset = 2.5.dp,
                testTag = "download_selected_photos_button"
            )
        }
    }

    // Lightbox Dialog if an image was tapped
    previewIndex?.let { idx ->
        PhotoLightboxDialog(
            images = info.images,
            initialIndex = idx,
            selectedImages = selectedImages,
            onToggleSelect = onToggleSelect,
            onDismiss = { previewIndex = null }
        )
    }
}
