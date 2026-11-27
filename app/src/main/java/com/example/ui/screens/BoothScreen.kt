package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.audio.DeckState
import com.example.audio.DjMixerEngine
import com.example.audio.MixerUiState
import com.example.data.model.CrossfadeCurve
import com.example.data.model.DeckId
import com.example.data.model.DjTrack
import com.example.data.model.TrackSourceType
import com.example.ui.components.LiveWaveformBar
import com.example.ui.components.SourceBadge
import com.example.ui.components.TurntableDisc
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
import com.example.ui.theme.WarmGold
import kotlin.math.roundToInt

@Composable
fun BoothScreen(
    mixerState: MixerUiState,
    engine: DjMixerEngine,
    onUpdatePlaylistMixSettings: (Int, CrossfadeCurve) -> Unit,
    onImportLocalAudio: (Uri) -> Unit,
    onNavigateToYouTube: () -> Unit,
    onNavigateToVibeStudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAdvancedMix by remember { mutableStateOf(false) }

    val localAudioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportLocalAudio(uri)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0E1320), ObsidianBg)
                )
            ),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header bar with active playlist & status
            item {
                BoothHeader(
                    playlistName = mixerState.activePlaylistName,
                    isCrossfading = mixerState.isCrossfading,
                    statusMessage = mixerState.statusMessage,
                    onDismissStatus = { engine.clearStatusMessage() }
                )
            }

            // Dual Decks (Deck A & Deck B)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DeckConsoleCard(
                        deck = mixerState.deckA,
                        isMasterActive = mixerState.activeDeck == DeckId.A,
                        accentColor = DeckACyan,
                        onPlayPause = { engine.togglePlayPause(DeckId.A) },
                        onCueRestart = { engine.cueRestart(DeckId.A) },
                        onSeek = { sec -> engine.seekDeck(DeckId.A, sec) },
                        onPitchChange = { rate -> engine.setDeckPitch(DeckId.A, rate) },
                        testTagPrefix = "deck_a"
                    )

                    DeckConsoleCard(
                        deck = mixerState.deckB,
                        isMasterActive = mixerState.activeDeck == DeckId.B,
                        accentColor = DeckBCoral,
                        onPlayPause = { engine.togglePlayPause(DeckId.B) },
                        onCueRestart = { engine.cueRestart(DeckId.B) },
                        onSeek = { sec -> engine.seekDeck(DeckId.B, sec) },
                        onPitchChange = { rate -> engine.setDeckPitch(DeckId.B, rate) },
                        testTagPrefix = "deck_b"
                    )
                }
            }

            // Central Crossfader + Live Waveform + Auto-Mix
            item {
                CrossfaderConsoleCard(
                    mixerState = mixerState,
                    showAdvanced = showAdvancedMix,
                    onToggleAdvanced = { showAdvancedMix = !showAdvancedMix },
                    onCrossfaderChange = { engine.setCrossfader(it) },
                    onMixNow = { engine.mixNow() },
                    onSyncBpm = { engine.syncIncomingBpmToActive() },
                    onToggleAutoMix = { engine.setAutoMixEnabled(it) },
                    onDurationChange = { sec -> onUpdatePlaylistMixSettings(sec, mixerState.crossfadeCurve) },
                    onCurveChange = { curve -> onUpdatePlaylistMixSettings(mixerState.crossfadeDurationSec, curve) },
                    onMasterVolumeChange = { engine.setMasterVolume(it) }
                )
            }

            // Queue Header & Quick Actions
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Playback Queue",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                        Text(
                            text = "${mixerState.queue.size} tracks · Auto transition",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { localAudioPicker.launch(arrayOf("audio/*")) },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, ConsoleCardBorder),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("import_local_audio_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "Add from device",
                                modifier = Modifier.size(16.dp),
                                tint = AcidMint
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("File", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                        }
                        OutlinedButton(
                            onClick = onNavigateToYouTube,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, DeckBCoral.copy(alpha = 0.6f)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("quick_youtube_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Search YouTube",
                                modifier = Modifier.size(16.dp),
                                tint = DeckBCoral
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("YouTube", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                        }
                    }
                }
            }

            if (mixerState.queue.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ConsoleSurface),
                        border = BorderStroke(1.dp, ConsoleCardBorder),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = DeckACyan,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "The DJ Booth queue is empty",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Button(
                                onClick = onNavigateToVibeStudio,
                                colors = ButtonDefaults.buttonColors(containerColor = DeckACyan, contentColor = ObsidianBg)
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Create Playlist by Vibe")
                            }
                        }
                    }
                }
            } else {
                itemsIndexed(
                    items = mixerState.queue,
                    key = { index, track -> "${track.id}_${track.sourceUriOrId}_$index" }
                ) { index, track ->
                    QueueTrackRow(
                        index = index,
                        track = track,
                        isCurrent = index == mixerState.currentQueueIndex,
                        onPlayNow = { engine.jumpToQueueTrack(index) },
                        onLoadDeckA = { engine.loadSingleTrackToDeck(DeckId.A, track, autoPlay = true) },
                        onLoadDeckB = { engine.loadSingleTrackToDeck(DeckId.B, track, autoPlay = true) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun BoothHeader(
    playlistName: String,
    isCrossfading: Boolean,
    statusMessage: String?,
    onDismissStatus: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "MIXPULSE LIVE BOOTH",
                    style = MaterialTheme.typography.labelMedium,
                    color = DeckACyan
                )
                Text(
                    text = playlistName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isCrossfading) AcidMint.copy(alpha = 0.18f) else ConsoleSurfaceElevated,
                border = BorderStroke(
                    1.dp,
                    if (isCrossfading) AcidMint else ConsoleCardBorder
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isCrossfading) AcidMint else DeckACyan)
                    )
                    Text(
                        text = if (isCrossfading) "CROSSFADING" else "GAPLESS ON",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isCrossfading) AcidMint else TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        AnimatedVisibility(visible = !statusMessage.isNullOrBlank()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDismissStatus() },
                shape = RoundedCornerShape(12.dp),
                color = DeckACyan.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, DeckACyan.copy(alpha = 0.4f))
            ) {
                Text(
                    text = statusMessage.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun DeckConsoleCard(
    deck: DeckState,
    isMasterActive: Boolean,
    accentColor: Color,
    onPlayPause: () -> Unit,
    onCueRestart: () -> Unit,
    onSeek: (Int) -> Unit,
    onPitchChange: (Float) -> Unit,
    testTagPrefix: String
) {
    val track = deck.track
    val remaining = (deck.durationSec - deck.positionSec).coerceAtLeast(0)
    val isEndingSoon = deck.isPlaying && deck.durationSec > 10 && remaining in 1..12

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isMasterActive) ConsoleSurfaceElevated else ConsoleSurface
        ),
        border = BorderStroke(
            width = if (isMasterActive) 1.5.dp else 1.dp,
            color = if (isMasterActive) accentColor.copy(alpha = 0.85f) else ConsoleCardBorder
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TurntableDisc(
                    thumbnailUrl = track?.thumbnailUrl,
                    sourceType = track?.sourceType,
                    isPlaying = deck.isPlaying,
                    accentColor = accentColor,
                    size = 60.dp
                )

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = accentColor
                        ) {
                            Text(
                                text = "DECK ${deck.deckId.name}",
                                style = MaterialTheme.typography.labelSmall,
                                color = ObsidianBg,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        if (track != null) {
                            SourceBadge(track.sourceType)
                            Text(
                                text = "${deck.effectiveBpm} BPM",
                                style = MaterialTheme.typography.labelMedium,
                                color = accentColor
                            )
                        }
                    }

                    Text(
                        text = track?.title ?: "Empty Deck — Select a track",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (track != null) TextPrimary else TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track?.artist ?: "Waiting for Auto-Cue",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Cue & Play/Pause controls (min 48dp touch target)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onCueRestart,
                        enabled = track != null,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("${testTagPrefix}_cue_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay,
                            contentDescription = "Reset Cue Deck ${deck.deckId.name}",
                            tint = if (track != null) TextSecondary else TextMuted
                        )
                    }

                    Surface(
                        onClick = onPlayPause,
                        enabled = track != null,
                        shape = CircleShape,
                        color = if (track != null) accentColor else ConsoleCardBorder,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("${testTagPrefix}_play_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (deck.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (deck.isPlaying) "Pause" else "Play",
                                tint = ObsidianBg,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }

            if (track != null) {
                // Seek bar + timecode
                val maxDur = deck.durationSec.coerceAtLeast(1)
                var isDraggingSeek by remember(track.sourceUriOrId) { mutableStateOf(false) }
                var dragPosition by remember(track.sourceUriOrId) { mutableStateOf(0f) }
                val displayedSec = if (isDraggingSeek) dragPosition.roundToInt() else deck.positionSec
                val displayedRemaining = (deck.durationSec - displayedSec).coerceAtLeast(0)

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Slider(
                        value = if (isDraggingSeek) dragPosition else deck.positionSec.toFloat().coerceIn(0f, maxDur.toFloat()),
                        onValueChange = {
                            isDraggingSeek = true
                            dragPosition = it
                        },
                        onValueChangeFinished = {
                            isDraggingSeek = false
                            onSeek(dragPosition.roundToInt())
                        },
                        valueRange = 0f..maxDur.toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = accentColor,
                            activeTrackColor = accentColor,
                            inactiveTrackColor = ConsoleCardBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTimecode(displayedSec),
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                        Text(
                            text = if (deck.isBuffering) "Buffering..."
                            else if (isEndingSoon) "MIX IN -${displayedRemaining}s"
                            else "-${formatTimecode(displayedRemaining)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isEndingSoon) WarmGold else TextSecondary,
                            fontWeight = if (isEndingSoon) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                // Tempo / Pitch Nudge bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "TEMPO ${((deck.pitchRate - 1f) * 100f).roundToInt().let { if (it >= 0) "+$it%" else "$it%" }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        modifier = Modifier.width(82.dp)
                    )
                    Slider(
                        value = deck.pitchRate,
                        onValueChange = onPitchChange,
                        valueRange = 0.85f..1.15f,
                        colors = SliderDefaults.colors(
                            thumbColor = TextPrimary,
                            activeTrackColor = accentColor.copy(alpha = 0.65f),
                            inactiveTrackColor = ConsoleCardBorder
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(22.dp)
                    )
                }

                if (!deck.errorMessage.isNullOrBlank()) {
                    val ctx = LocalContext.current
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = deck.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        if (track.sourceType == TrackSourceType.YOUTUBE) {
                            OutlinedButton(
                                onClick = {
                                    val ytIntent = Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://www.youtube.com/watch?v=${track.sourceUriOrId}")
                                    )
                                    runCatching { ctx.startActivity(ytIntent) }
                                },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, DeckBCoral.copy(alpha = 0.6f)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Open YT", style = MaterialTheme.typography.labelSmall, color = DeckBCoral)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CrossfaderConsoleCard(
    mixerState: MixerUiState,
    showAdvanced: Boolean,
    onToggleAdvanced: () -> Unit,
    onCrossfaderChange: (Float) -> Unit,
    onMixNow: () -> Unit,
    onSyncBpm: () -> Unit,
    onToggleAutoMix: (Boolean) -> Unit,
    onDurationChange: (Int) -> Unit,
    onCurveChange: (CrossfadeCurve) -> Unit,
    onMasterVolumeChange: (Float) -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = ConsoleSurfaceElevated),
        border = BorderStroke(1.dp, ConsoleCardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Live Waveform
            LiveWaveformBar(
                isPlayingA = mixerState.deckA.isPlaying,
                isPlayingB = mixerState.deckB.isPlaying,
                isCrossfading = mixerState.isCrossfading,
                crossfader = mixerState.crossfader,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
            )

            // Crossfade progress bar when actively mixing
            if (mixerState.isCrossfading) {
                LinearProgressIndicator(
                    progress = { mixerState.crossfadeProgress },
                    color = AcidMint,
                    trackColor = ConsoleCardBorder,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                )
            }

            // Crossfader labels + slider
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "A (${((1f - mixerState.crossfader) * 100).roundToInt()}%)",
                        style = MaterialTheme.typography.labelMedium,
                        color = DeckACyan
                    )
                    Text(
                        text = "CROSSFADER",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    Text(
                        text = "B (${(mixerState.crossfader * 100).roundToInt()}%)",
                        style = MaterialTheme.typography.labelMedium,
                        color = DeckBCoral
                    )
                }

                Slider(
                    value = mixerState.crossfader,
                    onValueChange = onCrossfaderChange,
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = TextPrimary,
                        activeTrackColor = DeckBCoral,
                        inactiveTrackColor = DeckACyan
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("crossfader_slider")
                )
            }

            // Action row: Mix Now + BPM Sync + Auto-Mix toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onMixNow,
                    enabled = !mixerState.isCrossfading && mixerState.queue.isNotEmpty(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AcidMint,
                        contentColor = ObsidianBg
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("mix_now_btn")
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (mixerState.isCrossfading) "Mixing..." else "Mix Now",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = onSyncBpm,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, DeckACyan.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("sync_bpm_btn")
                ) {
                    Icon(Icons.Default.Sync, contentDescription = "Sync BPM", tint = DeckACyan, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sync", style = MaterialTheme.typography.labelLarge, color = DeckACyan)
                }

                IconButton(
                    onClick = onToggleAdvanced,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("mix_settings_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Crossfade Settings",
                        tint = if (showAdvanced) DeckACyan else TextSecondary
                    )
                }
            }

            // Auto-Mix Switch row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Gapless Auto-Mix (${mixerState.crossfadeDurationSec}s)",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                    Text(
                        text = "Pre-cues the next track & fades automatically",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Switch(
                    checked = mixerState.autoMixEnabled,
                    onCheckedChange = onToggleAutoMix,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ObsidianBg,
                        checkedTrackColor = AcidMint
                    ),
                    modifier = Modifier.testTag("automix_switch")
                )
            }

            AnimatedVisibility(visible = showAdvanced) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Crossfade Duration: ${mixerState.crossfadeDurationSec} seconds",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary
                    )
                    Slider(
                        value = mixerState.crossfadeDurationSec.toFloat(),
                        onValueChange = { onDurationChange(it.roundToInt()) },
                        valueRange = 4f..16f,
                        steps = 11,
                        colors = SliderDefaults.colors(
                            thumbColor = AcidMint,
                            activeTrackColor = AcidMint,
                            inactiveTrackColor = ConsoleCardBorder
                        )
                    )

                    Text(
                        text = "Crossfade Curve",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CrossfadeCurve.entries.forEach { curve ->
                            FilterChip(
                                selected = mixerState.crossfadeCurve == curve,
                                onClick = { onCurveChange(curve) },
                                label = { Text(curve.label, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = DeckACyan.copy(alpha = 0.2f),
                                    selectedLabelColor = DeckACyan
                                )
                            )
                        }
                    }

                    Text(
                        text = "Master Volume: ${(mixerState.masterVolume * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary
                    )
                    Slider(
                        value = mixerState.masterVolume,
                        onValueChange = onMasterVolumeChange,
                        valueRange = 0.2f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = TextPrimary,
                            activeTrackColor = DeckACyan,
                            inactiveTrackColor = ConsoleCardBorder
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueTrackRow(
    index: Int,
    track: DjTrack,
    isCurrent: Boolean,
    onPlayNow: () -> Unit,
    onLoadDeckA: () -> Unit,
    onLoadDeckB: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isCurrent) DeckACyan.copy(alpha = 0.12f) else ConsoleSurface,
        border = BorderStroke(
            1.dp,
            if (isCurrent) DeckACyan.copy(alpha = 0.6f) else ConsoleCardBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlayNow() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "%02d".format(index + 1),
                style = MaterialTheme.typography.labelMedium,
                color = if (isCurrent) DeckACyan else TextMuted
            )

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SourceBadge(track.sourceType)
                    Text(
                        text = "${track.bpm} BPM · ${formatTimecode(track.durationSeconds)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
                Text(
                    text = track.transitionNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Surface(
                    onClick = onLoadDeckA,
                    shape = RoundedCornerShape(8.dp),
                    color = DeckACyan.copy(alpha = 0.16f),
                    modifier = Modifier.size(width = 36.dp, height = 36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("A", style = MaterialTheme.typography.labelMedium, color = DeckACyan, fontWeight = FontWeight.Bold)
                    }
                }
                Surface(
                    onClick = onLoadDeckB,
                    shape = RoundedCornerShape(8.dp),
                    color = DeckBCoral.copy(alpha = 0.16f),
                    modifier = Modifier.size(width = 36.dp, height = 36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("B", style = MaterialTheme.typography.labelMedium, color = DeckBCoral, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
