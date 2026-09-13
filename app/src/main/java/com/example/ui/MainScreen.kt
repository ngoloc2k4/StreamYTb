package com.example.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.ui.components.FullPlayer
import com.example.ui.components.MiniPlayer
import com.example.ui.components.RecSysDialog
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.MusicScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SubscriptionsScreen
import kotlinx.coroutines.flow.collectLatest

data class NavTabItem(
    @StringRes val titleRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val feedItems by viewModel.feedItems.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val subscriptions by viewModel.subscriptions.collectAsState()
    val watchHistory by viewModel.watchHistory.collectAsState()
    val preferences by viewModel.preferences.collectAsState()
    val playerState by viewModel.playerState.collectAsState()
    val isCurrentChannelSubscribed by viewModel.isCurrentChannelSubscribed.collectAsState()
    val isSearchOpen by viewModel.isSearchOpen.collectAsState()
    val isRecSysDialogOpen by viewModel.isRecSysDialogOpen.collectAsState()
    val isFullPlayerExpanded by viewModel.isFullPlayerExpanded.collectAsState()

    var currentTabIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.toastMessage.collectLatest { message ->
            Toast.makeText(context, message.asString(context), Toast.LENGTH_SHORT).show()
        }
    }

    // Back handling for FullPlayer or Search Screen
    BackHandler(enabled = isFullPlayerExpanded || isSearchOpen) {
        if (isFullPlayerExpanded) {
            viewModel.setFullPlayerExpanded(false)
        } else if (isSearchOpen) {
            viewModel.setSearchOpen(false)
        }
    }

    val tabs = listOf(
        NavTabItem(R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home, "nav_tab_home"),
        NavTabItem(R.string.nav_music, Icons.Filled.Headphones, Icons.Outlined.Headphones, "nav_tab_music"),
        NavTabItem(R.string.nav_subscriptions, Icons.Filled.Subscriptions, Icons.Outlined.Subscriptions, "nav_tab_subscriptions"),
        NavTabItem(R.string.nav_library, Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic, "nav_tab_library")
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpandedScreen = maxWidth >= 600.dp

        if (isExpandedScreen) {
            // TABLET / EXPANDED SCREEN: Split Layout with NavigationRail
            Row(modifier = Modifier.fillMaxSize()) {
                // Navigation Rail
                NavigationRail(
                    modifier = Modifier.fillMaxHeight(),
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    tabs.forEachIndexed { index, tab ->
                        val title = stringResource(tab.titleRes)
                        NavigationRailItem(
                            selected = currentTabIndex == index,
                            onClick = {
                                currentTabIndex = index
                                viewModel.setSearchOpen(false)
                            },
                            icon = {
                                Icon(
                                    imageVector = if (currentTabIndex == index) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = title
                                )
                            },
                            label = { Text(title) },
                            modifier = Modifier.testTag(tab.testTag)
                        )
                    }
                }

                // 2-Column Split: 45% Player pane, 55% Content pane
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left Column: Player Pane (or placeholder if empty)
                    Box(
                        modifier = Modifier
                            .weight(0.45f)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        if (playerState.currentMedia != null) {
                            FullPlayer(
                                playerState = playerState,
                                exoPlayer = viewModel.playerManager.getExoPlayer(),
                                onCollapse = { /* On tablet player is permanently visible in split */ },
                                onPlayPause = { viewModel.togglePlayPause() },
                                onSeek = { viewModel.seekTo(it) },
                                onNext = { viewModel.playNext() },
                                onPrevious = { viewModel.playPrevious() },
                                onToggleAudioMode = { viewModel.toggleAudioOnlyMode() },
                                onSelectSpeed = { viewModel.setSpeed(it) },
                                onSelectClient = { viewModel.setClientSpoof(it) },
                                onSelectQueueItem = { viewModel.playVideo(it) },
                                isSubscribed = isCurrentChannelSubscribed,
                                onToggleSubscribe = { viewModel.toggleSubscribeCurrent() }
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.player_select_media_hint),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Right Column: Feed and navigation content
                    Box(
                        modifier = Modifier
                            .weight(0.55f)
                            .fillMaxHeight()
                    ) {
                        ScreenContent(
                            currentTabIndex = currentTabIndex,
                            isSearchOpen = isSearchOpen,
                            viewModel = viewModel,
                            feedItems = feedItems,
                            isRefreshing = isRefreshing,
                            subscriptions = subscriptions,
                            watchHistory = watchHistory
                        )
                    }
                }
            }
        } else {
            // PHONE / COMPACT SCREEN: Standard Bottom Bar + MiniPlayer + Expandable FullPlayer
            Scaffold(
                bottomBar = {
                    Column(modifier = Modifier.navigationBarsPadding()) {
                        // Floating MiniPlayer
                        if (playerState.currentMedia != null && !isFullPlayerExpanded) {
                            MiniPlayer(
                                playerState = playerState,
                                onExpand = { viewModel.setFullPlayerExpanded(true) },
                                onPlayPause = { viewModel.togglePlayPause() },
                                onNext = { viewModel.playNext() },
                                onClose = { viewModel.dismissPlayer() },
                                onToggleAudioMode = { viewModel.toggleAudioOnlyMode() }
                            )
                        }

                        // Bottom Navigation Bar
                        if (!isFullPlayerExpanded && !isSearchOpen) {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                tonalElevation = 8.dp
                            ) {
                                tabs.forEachIndexed { index, tab ->
                                    val title = stringResource(tab.titleRes)
                                    NavigationBarItem(
                                        selected = currentTabIndex == index,
                                        onClick = { currentTabIndex = index },
                                        icon = {
                                            Icon(
                                                imageVector = if (currentTabIndex == index) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = title
                                            )
                                        },
                                        label = { Text(title) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = MaterialTheme.colorScheme.primary,
                                            selectedTextColor = MaterialTheme.colorScheme.primary,
                                            indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                        ),
                                        modifier = Modifier.testTag(tab.testTag)
                                    )
                                }
                            }
                        }
                    }
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    ScreenContent(
                        currentTabIndex = currentTabIndex,
                        isSearchOpen = isSearchOpen,
                        viewModel = viewModel,
                        feedItems = feedItems,
                        isRefreshing = isRefreshing,
                        subscriptions = subscriptions,
                        watchHistory = watchHistory
                    )

                    // Expandable FullPlayer overlay
                    AnimatedVisibility(
                        visible = isFullPlayerExpanded && playerState.currentMedia != null,
                        enter = slideInVertically(initialOffsetY = { it }),
                        exit = slideOutVertically(targetOffsetY = { it })
                    ) {
                        FullPlayer(
                            playerState = playerState,
                            exoPlayer = viewModel.playerManager.getExoPlayer(),
                            onCollapse = { viewModel.setFullPlayerExpanded(false) },
                            onPlayPause = { viewModel.togglePlayPause() },
                            onSeek = { viewModel.seekTo(it) },
                            onNext = { viewModel.playNext() },
                            onPrevious = { viewModel.playPrevious() },
                            onToggleAudioMode = { viewModel.toggleAudioOnlyMode() },
                            onSelectSpeed = { viewModel.setSpeed(it) },
                            onSelectClient = { viewModel.setClientSpoof(it) },
                            onSelectQueueItem = { viewModel.playVideo(it) },
                            isSubscribed = isCurrentChannelSubscribed,
                            onToggleSubscribe = { viewModel.toggleSubscribeCurrent() }
                        )
                    }
                }
            }
        }

        // RecSys Dialog
        if (isRecSysDialogOpen) {
            RecSysDialog(
                preferences = preferences,
                onDismiss = { viewModel.setRecSysDialogOpen(false) },
                onClearPreferences = { viewModel.clearPreferences() }
            )
        }
    }
}

@Composable
private fun ScreenContent(
    currentTabIndex: Int,
    isSearchOpen: Boolean,
    viewModel: MainViewModel,
    feedItems: List<com.example.data.model.FeedItem>,
    isRefreshing: Boolean,
    subscriptions: List<com.example.data.local.SubscriptionEntity>,
    watchHistory: List<com.example.data.local.WatchHistoryEntity>
) {
    if (isSearchOpen) {
        SearchScreen(
            onBack = { viewModel.setSearchOpen(false) },
            onSearch = { q -> viewModel.repository.search(q) },
            onOnlineSearch = { q -> viewModel.repository.searchOnline(q) },
            onVideoClick = { video ->
                viewModel.playVideo(video)
                viewModel.setSearchOpen(false)
            }
        )
    } else {
        when (currentTabIndex) {
            0 -> HomeScreen(
                feedItems = feedItems,
                onVideoClick = { viewModel.playVideo(it) },
                onSearchClick = { viewModel.setSearchOpen(true) },
                onOpenRecSysStats = { viewModel.setRecSysDialogOpen(true) },
                onRefresh = { viewModel.refreshFeed() },
                isRefreshing = isRefreshing
            )
            1 -> MusicScreen(
                musicTracks = viewModel.repository.getMusicVideos(),
                onPlayTrack = { viewModel.playMusicTrack(it) }
            )
            2 -> SubscriptionsScreen(
                subscriptions = subscriptions,
                allChannels = viewModel.repository.getAllChannels(),
                videos = viewModel.repository.getAllVideos(),
                onVideoClick = { viewModel.playVideo(it) },
                onToggleSubscribe = { viewModel.toggleSubscribeChannel(it) },
                onExportBackup = { viewModel.exportBackup() },
                onImportBackup = { viewModel.importBackup(it) }
            )
            3 -> LibraryScreen(
                history = watchHistory,
                subscriptionsCount = subscriptions.size,
                allVideos = viewModel.repository.getAllVideos(),
                onVideoClick = { viewModel.playVideo(it) },
                onClearHistory = { viewModel.clearHistory() },
                onExportBackup = { viewModel.exportBackup() }
            )
        }
    }
}
