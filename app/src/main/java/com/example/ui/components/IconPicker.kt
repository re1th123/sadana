package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

val wellnessEmojis = listOf(
    "🧘", "🧘‍♂️", "🧘‍♀️", "💆", "💆‍♂️", "💆‍♀️", "✨", "☁️", "🌿", "🌸", "💪", "🏃", "🏃‍♂️", "🏃‍♀️", "🤸", "🤸‍♂️", "🤸‍♀️",
    "❤️", "🔥", "☀️", "🌙", "🌊", "🍃", "💧", "🔋", "🌱", "🧭", "☯️", "👁️", "🙌", "🛌", "👣", "🧉", "🥑", "📦"
)

// Predefined calming visual preset URLs for quick click simulation of image/gif upload
val presetCalmingImages = listOf(
    "https://images.unsplash.com/photo-1544367567-0f2fcb009e0b?q=80&w=200&auto=format&fit=crop" to "Yoga Stretch (Photo)",
    "https://images.unsplash.com/photo-1506126613408-eca07ce68773?q=80&w=200&auto=format&fit=crop" to "Lotus Peace (Photo)",
    "https://i.giphy.com/media/v1.Y2lkPTc5MGI3NjExM3ZpdGlkY3E1MmJ6MzE3M3A2dGhhMXAxdG8xOWZocWJ3ZGx6cmY0cCZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/3o7TKoVqOq787NqBHi/giphy.gif" to "River Flow (Calming GIF)",
    "https://i.giphy.com/media/v1.Y2lkPTc5MGI3NjExN3NscHN6aXhpMWxmeHpmczBtZTlyZTBzOWh6ZXZnczV1ODVzdjJtdyZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/H7Sc66As99YF2l5zW0/giphy.gif" to "Breathing Orb (GIF)"
)

@Composable
fun IconPicker(
    isEmoji: Boolean,
    onModeChange: (Boolean) -> Unit,
    selectedValue: String,
    onValueSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredEmojis = remember(searchQuery) {
        if (searchQuery.isBlank()) wellnessEmojis else {
            wellnessEmojis.filter { emoji ->
                // Basic check or just include any wellness query
                searchQuery.length == 1 || emoji.contains(searchQuery)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(16.dp)
    ) {
        Text(
            text = "Select Icon",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Mode Toggles (Tabs)
        TabRow(
            selectedTabIndex = if (isEmoji) 0 else 1,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Tab(
                selected = isEmoji,
                onClick = { onModeChange(true) },
                text = { Text("Choose Emoji", fontWeight = FontWeight.SemiBold) }
            )
            Tab(
                selected = !isEmoji,
                onClick = { onModeChange(false) },
                text = { Text("Upload Image / GIF", fontWeight = FontWeight.SemiBold) }
            )
        }

        if (isEmoji) {
            // EMOJI PICKER
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search emojis...", fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 44.dp),
                    modifier = Modifier.height(160.dp),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredEmojis) { emoji ->
                        val isSelected = isEmoji && selectedValue == emoji
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                    else Color.Transparent
                                )
                                .border(
                                    BorderStroke(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    onValueSelected(emoji)
                                }
                        ) {
                            Text(text = emoji, fontSize = 22.sp)
                        }
                    }
                }
            }
        } else {
            // IMAGE / GIF PICKER / SIMULATED UPLOAD
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Text Field for direct URL Input
                OutlinedTextField(
                    value = if (!isEmoji) selectedValue else "",
                    onValueChange = { onValueSelected(it) },
                    label = { Text("Paste Image / GIF URL", fontSize = 12.sp) },
                    placeholder = { Text("https://example.com/asset.gif", fontSize = 14.sp) },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Simulated Instant Preset Uplinks
                Text(
                    text = "Tap to upload preset calming GIF/Photo:",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    ),
                    modifier = Modifier.padding(top = 4.dp)
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                // Scrollable row of premium predefined images/gifs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presetCalmingImages.forEach { (url, label) ->
                        val isCurrent = !isEmoji && selectedValue == url
                        Card(
                            onClick = { onValueSelected(url) },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                width = if (isCurrent) 2.dp else 1.dp,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Transparent
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(60.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                AsyncImage(
                                    model = url,
                                    contentDescription = label,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.4f))
                                )
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color.White,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(4.dp)
                                )
                            }
                        }
                    }
                }

                // Simulated Manual File Selector button
                Button(
                    onClick = {
                        // Simulate selecting a random GIF
                        val mockGif = "https://i.giphy.com/media/v1.Y2lkPTc5MGI3NjExM3ZpdGlkY3E1MmJ6MzE3M3A2dGhhMXAxdG8xOWZocWJ3ZGx6cmY0cCZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/3o7TKoVqOq787NqBHi/giphy.gif"
                        onValueSelected(mockGif)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Simulate Photo Library Upload", fontSize = 12.sp)
                }

                // Real-time Preview of the image / gif URL
                if (!isEmoji && selectedValue.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.05f)),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = selectedValue,
                            contentDescription = "Uploaded Preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxHeight()
                        )
                    }
                }
            }
        }
    }
}
