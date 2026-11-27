package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.DjPlaylist
import com.example.ui.components.SourceBadge
import com.example.ui.components.formatTimecode
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

@Composable
fun PlaylistsScreen(
    playlists: List<DjPlaylist>,
    activePlaylistId: Long,
    onLoadInBooth: (DjPlaylist) -> Unit,
    onCreatePlaylist: (String, String) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onRemoveTrack: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedId by remember { mutableStateOf<Long?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "SAVED DJ SETS",
                        style = MaterialTheme.typography.labelMedium,
                        color = AcidMint
                    )
                    Text(
                        text = "Your Playlists",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary
                    )
                }
            }

            if (playlists.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ConsoleSurface),
                        border = BorderStroke(1.dp, ConsoleCardBorder),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.LibraryMusic, contentDescription = null, tint = DeckACyan, modifier = Modifier.size(36.dp))
                            Text("No saved playlists", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text("Create a new set in Vibe Studio or tap +", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }
            } else {
                items(playlists, key = { it.id }) { playlist ->
                    val isActive = playlist.id == activePlaylistId
                    val isExpanded = expandedId == playlist.id

                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isActive) ConsoleSurfaceElevated else ConsoleSurface
                        ),
                        border = BorderStroke(
                            width = if (isActive) 1.5.dp else 1.dp,
                            color = if (isActive) DeckACyan else ConsoleCardBorder
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expandedId = if (isExpanded) null else playlist.id
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = DeckACyan.copy(alpha = 0.16f)
                                        ) {
                                            Text(
                                                text = playlist.vibeTag,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = DeckACyan,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Text(
                                            text = "${playlist.tracks.size} tracks · ${playlist.crossfadeSeconds}s mix",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextMuted
                                        )
                                    }

                                    Text(
                                        text = playlist.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = playlist.moodDescription,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                IconButton(
                                    onClick = { expandedId = if (isExpanded) null else playlist.id },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = "Show tracks",
                                        tint = TextSecondary
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { onLoadInBooth(playlist) },
                                    enabled = playlist.tracks.isNotEmpty(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = DeckACyan,
                                        contentColor = ObsidianBg
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isActive) "Playing in Booth" else "Load in DJ Booth",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                IconButton(
                                    onClick = { onDeletePlaylist(playlist.id) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete playlist",
                                        tint = DeckBCoral
                                    )
                                }
                            }

                            AnimatedVisibility(visible = isExpanded) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (playlist.tracks.isEmpty()) {
                                        Text(
                                            text = "Empty playlist — add tracks from the YouTube tab.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextMuted
                                        )
                                    } else {
                                        playlist.tracks.forEachIndexed { idx, track ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = "%02d".format(idx + 1),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = TextMuted
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = track.title,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = TextPrimary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        SourceBadge(track.sourceType)
                                                        Text(
                                                            text = "${track.bpm} BPM · ${formatTimecode(track.durationSeconds)}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = TextSecondary
                                                        )
                                                    }
                                                }
                                                IconButton(
                                                    onClick = { onRemoveTrack(track.id) },
                                                    modifier = Modifier.size(40.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.DeleteOutline,
                                                        contentDescription = "Remove track",
                                                        tint = TextMuted,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showCreateDialog = true },
            containerColor = DeckACyan,
            contentColor = ObsidianBg,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("create_playlist_fab")
        ) {
            Icon(Icons.Default.Add, contentDescription = "New playlist")
        }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, vibe ->
                onCreatePlaylist(name, vibe)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var vibe by remember { mutableStateOf("Club Mix") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ConsoleSurfaceElevated,
        title = { Text("New DJ Playlist", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Playlist name") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = vibe,
                    onValueChange = { vibe = it },
                    label = { Text("Vibe / Genre") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, vibe) }) {
                Text("Create", color = DeckACyan)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
