package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.DeckId
import com.example.data.model.DjPlaylist
import com.example.data.model.YouTubeSearchResult
import com.example.ui.YouTubeSearchUiState
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

private val QuickYouTubeGenres = listOf(
    "Afro House" to "Afro House music 2025",
    "Club Remixes" to "Club house remix 2025",
    "Deep House" to "Deep House sunset mix",
    "Melodic Techno" to "Melodic Techno club",
    "90s Dance" to "90s dance club hits",
    "Lo-Fi Chill" to "Lo-fi chill beats"
)

@Composable
fun YouTubeSearchScreen(
    state: YouTubeSearchUiState,
    playlists: List<DjPlaylist>,
    onQueryChange: (String) -> Unit,
    onSearch: (String?) -> Unit,
    onSelectTargetPlaylist: (Long) -> Unit,
    onAddToPlaylist: (YouTubeSearchResult, Long) -> Unit,
    onLoadToDeck: (YouTubeSearchResult, DeckId) -> Unit,
    onImportLocalAudio: (Uri, Long) -> Unit,
    onDismissFeedback: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val localPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportLocalAudio(uri, state.targetPlaylistId)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "YOUTUBE DISCOVERY & FILES",
                        style = MaterialTheme.typography.labelMedium,
                        color = DeckBCoral
                    )
                    Text(
                        text = "Find tracks for your DJ set",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary
                    )
                }
            }

            // Search bar + Local audio import button
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = state.query,
                            onValueChange = onQueryChange,
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.search_youtube_hint),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    focusManager.clearFocus()
                                    onSearch(null)
                                }
                            ),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DeckBCoral,
                                unfocusedBorderColor = ConsoleCardBorder,
                                focusedContainerColor = ConsoleSurface,
                                unfocusedContainerColor = ConsoleSurface,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("youtube_search_input")
                        )

                        Surface(
                            onClick = {
                                focusManager.clearFocus()
                                onSearch(null)
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = DeckBCoral,
                            modifier = Modifier
                                .size(54.dp)
                                .testTag("youtube_search_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = ObsidianBg
                                )
                            }
                        }
                    }

                    // Quick genre chips
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(QuickYouTubeGenres) { (label, query) ->
                            FilterChip(
                                selected = state.query.equals(query, ignoreCase = true),
                                onClick = {
                                    focusManager.clearFocus()
                                    onSearch(query)
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = DeckBCoral.copy(alpha = 0.2f),
                                    selectedLabelColor = DeckBCoral
                                )
                            )
                        }
                    }
                }
            }

            // Target Playlist Selector + Local File Import
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ConsoleSurface),
                    border = BorderStroke(1.dp, ConsoleCardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Add to playlist:",
                                style = MaterialTheme.typography.labelLarge,
                                color = TextSecondary
                            )
                            OutlinedButton(
                                onClick = { localPicker.launch(arrayOf("audio/*")) },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, AcidMint.copy(alpha = 0.5f)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = AcidMint, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("From device", style = MaterialTheme.typography.labelSmall, color = AcidMint)
                            }
                        }

                        if (playlists.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(playlists, key = { it.id }) { pl ->
                                    val isSelected = pl.id == state.targetPlaylistId
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { onSelectTargetPlaylist(pl.id) },
                                        label = {
                                            Text(
                                                text = pl.name,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = DeckACyan.copy(alpha = 0.2f),
                                            selectedLabelColor = DeckACyan
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Feedback banner
            item {
                AnimatedVisibility(visible = !state.feedbackMessage.isNullOrBlank()) {
                    Surface(
                        onClick = onDismissFeedback,
                        shape = RoundedCornerShape(12.dp),
                        color = AcidMint.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, AcidMint.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = state.feedbackMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = AcidMint,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            if (state.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = DeckBCoral)
                    }
                }
            } else if (!state.errorMessage.isNullOrBlank()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = ConsoleSurface,
                        border = BorderStroke(1.dp, ConsoleCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = state.errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(state.results, key = { it.videoId }) { result ->
                    YouTubeResultCard(
                        result = result,
                        onAddToPlaylist = { onAddToPlaylist(result, state.targetPlaylistId) },
                        onLoadDeckA = { onLoadToDeck(result, DeckId.A) },
                        onLoadDeckB = { onLoadToDeck(result, DeckId.B) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun YouTubeResultCard(
    result: YouTubeSearchResult,
    onAddToPlaylist: () -> Unit,
    onLoadDeckA: () -> Unit,
    onLoadDeckB: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ConsoleSurfaceElevated),
        border = BorderStroke(1.dp, ConsoleCardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AsyncImage(
                    model = result.thumbnailUrl,
                    contentDescription = result.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 92.dp, height = 60.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ConsoleSurface)
                )

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = result.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${result.channelName} · ${result.durationText} · ~${result.estimatedBpm} BPM",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = AcidMint.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = "Ready for DJ Mix",
                            style = MaterialTheme.typography.labelSmall,
                            color = AcidMint,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onAddToPlaylist,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AcidMint,
                        contentColor = ObsidianBg
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add to Playlist", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onLoadDeckA,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, DeckACyan.copy(alpha = 0.7f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(42.dp)
                ) {
                    Text("Deck A", style = MaterialTheme.typography.labelMedium, color = DeckACyan)
                }

                OutlinedButton(
                    onClick = onLoadDeckB,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, DeckBCoral.copy(alpha = 0.7f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(42.dp)
                ) {
                    Text("Deck B", style = MaterialTheme.typography.labelMedium, color = DeckBCoral)
                }
            }
        }
    }
}
