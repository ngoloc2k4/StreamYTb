package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.example.R
import coil.compose.AsyncImage
import com.example.data.local.SubscriptionEntity
import com.example.data.model.StreamChannel
import com.example.data.model.StreamVideo
import com.example.ui.components.ChannelCard
import com.example.ui.components.VideoCard

data class ChannelGroupItem(@StringRes val titleRes: Int, val id: String)

@Composable
fun SubscriptionsScreen(
    subscriptions: List<SubscriptionEntity>,
    allChannels: List<StreamChannel>,
    videos: List<StreamVideo>,
    onVideoClick: (StreamVideo) -> Unit,
    onToggleSubscribe: (StreamChannel) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val groups = listOf(
        ChannelGroupItem(R.string.group_all, "All"),
        ChannelGroupItem(R.string.group_music, "Âm nhạc"),
        ChannelGroupItem(R.string.group_coding, "Lập trình"),
        ChannelGroupItem(R.string.group_relax, "Thư giãn"),
        ChannelGroupItem(R.string.group_news, "Tin tức")
    )
    var selectedGroupId by remember { mutableStateOf("All") }
    var showImportDialog by remember { mutableStateOf(false) }
    var importJsonText by remember { mutableStateOf("") }
    var showAllChannelsDialog by remember { mutableStateOf(false) }

    val subscribedIds = subscriptions.map { it.channelId }.toSet()

    val filteredSubscriptions = remember(subscriptions, selectedGroupId) {
        if (selectedGroupId == "All") subscriptions
        else subscriptions.filter { it.customGroup == selectedGroupId }
    }

    val channelVideos = remember(videos, subscribedIds) {
        videos.filter { it.channelId in subscribedIds }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("subscriptions_screen"),
        contentPadding = PaddingValues(bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top bar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.subscriptions_title, subscriptions.size),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Row {
                    IconButton(
                        onClick = onExportBackup,
                        modifier = Modifier.testTag("btn_export_backup")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = stringResource(R.string.subscriptions_export_desc),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { showImportDialog = true },
                        modifier = Modifier.testTag("btn_import_backup")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = stringResource(R.string.subscriptions_import_desc),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Horizontal avatars row
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Add / Discover more channels item
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { showAllChannelsDialog = true }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.subscriptions_discover_channels),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(stringResource(R.string.subscriptions_discover), style = MaterialTheme.typography.labelSmall)
                    }
                }

                items(subscriptions) { sub ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(60.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF202534))
                        ) {
                            AsyncImage(
                                model = sub.thumbnailUrl,
                                contentDescription = sub.title,
                                modifier = Modifier.matchParentSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = sub.title,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // Custom Group Filters (custom_group in Room schema)
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(groups) { grp ->
                    FilterChip(
                        selected = selectedGroupId == grp.id,
                        onClick = { selectedGroupId = grp.id },
                        label = { Text(stringResource(grp.titleRes)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }

        // Subscribed Feed Videos
        if (channelVideos.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Subscriptions,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.subscriptions_empty_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(channelVideos) { video ->
                VideoCard(
                    video = video,
                    onClick = { onVideoClick(video) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }

    // Import Dialog
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text(stringResource(R.string.subscriptions_import_dialog_title)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.subscriptions_import_dialog_desc),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importJsonText,
                        onValueChange = { importJsonText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        placeholder = { Text(stringResource(R.string.subscriptions_import_dialog_placeholder)) }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (importJsonText.isNotEmpty()) {
                            onImportBackup(importJsonText)
                            showImportDialog = false
                            importJsonText = ""
                        }
                    }
                ) {
                    Text(stringResource(R.string.subscriptions_import_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // All Channels / Discovery Dialog
    if (showAllChannelsDialog) {
        AlertDialog(
            onDismissRequest = { showAllChannelsDialog = false },
            title = { Text(stringResource(R.string.subscriptions_discover_dialog_title)) },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(allChannels) { ch ->
                        ChannelCard(
                            channel = ch,
                            isSubscribed = ch.id in subscribedIds,
                            onToggleSubscribe = { onToggleSubscribe(ch) }
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showAllChannelsDialog = false }) {
                    Text(stringResource(R.string.action_done))
                }
            }
        )
    }
}
