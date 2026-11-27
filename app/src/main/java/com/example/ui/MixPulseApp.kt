package com.example.ui

import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.audio.MixerUiState
import com.example.data.model.DeckId
import com.example.data.model.TrackSourceType
import com.example.ui.screens.BoothScreen
import com.example.ui.screens.PlaylistsScreen
import com.example.ui.screens.VibeStudioScreen
import com.example.ui.screens.YouTubeSearchScreen
import com.example.ui.theme.AcidMint
import com.example.ui.theme.ConsoleCardBorder
import com.example.ui.theme.ConsoleSurface
import com.example.ui.theme.ConsoleSurfaceElevated
import com.example.ui.theme.DeckACyan
import com.example.ui.theme.DeckBCoral
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

const val ROUTE_BOOTH = "booth"
const val ROUTE_VIBE = "vibe"
const val ROUTE_YOUTUBE = "youtube"
const val ROUTE_PLAYLISTS = "playlists"

private data class NavDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val TopDestinations = listOf(
    NavDestination(ROUTE_BOOTH, R.string.nav_booth, Icons.Filled.GraphicEq, Icons.Outlined.GraphicEq, "nav_booth"),
    NavDestination(ROUTE_VIBE, R.string.nav_vibe, Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome, "nav_vibe"),
    NavDestination(ROUTE_YOUTUBE, R.string.nav_youtube, Icons.Filled.Search, Icons.Outlined.Search, "nav_youtube"),
    NavDestination(ROUTE_PLAYLISTS, R.string.nav_playlists, Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic, "nav_playlists")
)

@Composable
fun MixPulseApp(
    viewModel: MixPulseViewModel = viewModel()
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_BOOTH

    val mixerState by viewModel.mixerState.collectAsStateWithLifecycle()
    val vibeState by viewModel.vibeState.collectAsStateWithLifecycle()
    val youtubeState by viewModel.youtubeState.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()

    val navigateTo: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        val useRail = maxWidth >= 700.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (useRail) {
                NavigationRail(
                    containerColor = ConsoleSurface,
                    modifier = Modifier
                        .fillMaxHeight()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                ) {
                    Spacer(modifier = Modifier.height(12.dp))
                    TopDestinations.forEach { dest ->
                        val selected = currentRoute == dest.route
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navigateTo(dest.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                    contentDescription = stringResource(dest.labelRes)
                                )
                            },
                            label = { Text(stringResource(dest.labelRes)) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = DeckACyan,
                                selectedTextColor = DeckACyan,
                                indicatorColor = DeckACyan.copy(alpha = 0.16f),
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary
                            ),
                            modifier = Modifier.testTag(dest.testTag)
                        )
                    }
                }
            }

            Scaffold(
                containerColor = ObsidianBg,
                contentWindowInsets = WindowInsets.safeDrawing,
                bottomBar = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Persistent Live YouTube Deck Host + Mini Player Bar so audio never drops across tabs
                        PersistentDeckMonitorDock(
                            mixerState = mixerState,
                            provideWebView = { deckId ->
                                viewModel.mixerEngine.getOrCreateYouTubeBridge(deckId).webView
                            },
                            showMiniTransport = currentRoute != ROUTE_BOOTH,
                            onToggleActivePlay = {
                                viewModel.mixerEngine.togglePlayPause(mixerState.activeDeck)
                            },
                            onMixNow = { viewModel.mixerEngine.mixNow() },
                            onOpenBooth = { navigateTo(ROUTE_BOOTH) }
                        )

                        if (!useRail) {
                            NavigationBar(
                                containerColor = ConsoleSurface,
                                tonalElevation = 0.dp
                            ) {
                                TopDestinations.forEach { dest ->
                                    val selected = currentRoute == dest.route
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { navigateTo(dest.route) },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                                contentDescription = stringResource(dest.labelRes)
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = stringResource(dest.labelRes),
                                                maxLines = 1
                                            )
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = DeckACyan,
                                            selectedTextColor = DeckACyan,
                                            indicatorColor = DeckACyan.copy(alpha = 0.16f),
                                            unselectedIconColor = TextMuted,
                                            unselectedTextColor = TextMuted
                                        ),
                                        modifier = Modifier.testTag(dest.testTag)
                                    )
                                }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = ROUTE_BOOTH,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    composable(ROUTE_BOOTH) {
                        BoothScreen(
                            mixerState = mixerState,
                            engine = viewModel.mixerEngine,
                            onUpdatePlaylistMixSettings = { sec, curve ->
                                viewModel.updateActivePlaylistMixSettings(sec, curve)
                            },
                            onImportLocalAudio = { uri -> viewModel.importLocalAudio(uri) },
                            onNavigateToYouTube = { navigateTo(ROUTE_YOUTUBE) },
                            onNavigateToVibeStudio = { navigateTo(ROUTE_VIBE) }
                        )
                    }

                    composable(ROUTE_VIBE) {
                        VibeStudioScreen(
                            state = vibeState,
                            onSelectPreset = viewModel::selectVibePreset,
                            onPromptChange = viewModel::updateVibePrompt,
                            onEnergyChange = viewModel::updateVibeEnergy,
                            onTrackCountChange = viewModel::updateVibeTrackCount,
                            onCrossfadeChange = viewModel::updateVibeCrossfade,
                            onCurveChange = viewModel::updateVibeCurve,
                            onGenerateClick = {
                                viewModel.generateVibePlaylist()
                            },
                            onOpenInBooth = { navigateTo(ROUTE_BOOTH) }
                        )
                    }

                    composable(ROUTE_YOUTUBE) {
                        YouTubeSearchScreen(
                            state = youtubeState,
                            playlists = playlists,
                            onQueryChange = viewModel::updateYouTubeQuery,
                            onSearch = { query -> viewModel.searchYouTube(query) },
                            onSelectTargetPlaylist = viewModel::setTargetPlaylistForYouTube,
                            onAddToPlaylist = { result, playlistId ->
                                viewModel.addYouTubeTrackToPlaylist(result, playlistId)
                            },
                            onLoadToDeck = { result, deckId ->
                                viewModel.loadYouTubeResultToDeck(result, deckId)
                                navigateTo(ROUTE_BOOTH)
                            },
                            onImportLocalAudio = { uri, pid ->
                                viewModel.importLocalAudio(uri, pid)
                            },
                            onDismissFeedback = viewModel::clearYouTubeFeedback
                        )
                    }

                    composable(ROUTE_PLAYLISTS) {
                        PlaylistsScreen(
                            playlists = playlists,
                            activePlaylistId = mixerState.activePlaylistId,
                            onLoadInBooth = { playlist ->
                                viewModel.loadPlaylistInBooth(playlist, autoStart = true)
                                navigateTo(ROUTE_BOOTH)
                            },
                            onCreatePlaylist = viewModel::createEmptyPlaylist,
                            onDeletePlaylist = viewModel::deletePlaylist,
                            onRemoveTrack = viewModel::removeTrack
                        )
                    }
                }
            }
        }
    }
}

/**
 * Keeps both YouTube IFrame WebViews permanently attached to the window hierarchy so
 * dual-deck YouTube playback & crossfading never stops when navigating between screens.
 */
@Composable
private fun PersistentDeckMonitorDock(
    mixerState: MixerUiState,
    provideWebView: (DeckId) -> WebView,
    showMiniTransport: Boolean,
    onToggleActivePlay: () -> Unit,
    onMixNow: () -> Unit,
    onOpenBooth: () -> Unit
) {
    val deckAHasYouTube = mixerState.deckA.track?.sourceType == TrackSourceType.YOUTUBE
    val deckBHasYouTube = mixerState.deckB.track?.sourceType == TrackSourceType.YOUTUBE
    val hasYouTube = deckAHasYouTube || deckBHasYouTube
    val activeDeck = if (mixerState.activeDeck == DeckId.A) mixerState.deckA else mixerState.deckB
    val showDock = showMiniTransport && activeDeck.track != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ConsoleSurface)
    ) {
        if (hasYouTube) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (deckAHasYouTube) {
                    DeckMonitorTile(
                        label = "LIVE A",
                        accent = DeckACyan,
                        webView = provideWebView(DeckId.A),
                        modifier = Modifier.weight(1f)
                    )
                }
                if (deckBHasYouTube) {
                    DeckMonitorTile(
                        label = "LIVE B",
                        accent = DeckBCoral,
                        webView = provideWebView(DeckId.B),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        AnimatedVisibility(visible = showDock) {
            val track = activeDeck.track
            if (track != null) {
                Surface(
                    color = ConsoleSurfaceElevated,
                    border = BorderStroke(1.dp, ConsoleCardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenBooth() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (mixerState.activeDeck == DeckId.A) DeckACyan else DeckBCoral,
                            modifier = Modifier.size(10.dp)
                        ) {}

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (mixerState.isCrossfading) "Crossfading..."
                                else "Deck ${mixerState.activeDeck.name} · ${activeDeck.effectiveBpm} BPM",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (mixerState.isCrossfading) AcidMint else TextSecondary
                            )
                        }

                        IconButton(
                            onClick = onMixNow,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Mix Now",
                                tint = AcidMint
                            )
                        }

                        Surface(
                            onClick = onToggleActivePlay,
                            shape = CircleShape,
                            color = DeckACyan,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (activeDeck.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (activeDeck.isPlaying) "Pause" else "Play",
                                    tint = ObsidianBg,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeckMonitorTile(
    label: String,
    accent: Color,
    webView: WebView,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                fontWeight = FontWeight.Bold
            )
        }
        Box(
            modifier = Modifier
                .width(64.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(6.dp))
        ) {
            AndroidView(
                factory = {
                    (webView.parent as? ViewGroup)?.removeView(webView)
                    webView
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
