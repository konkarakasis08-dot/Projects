package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.CrossfadeCurve
import com.example.data.model.VibePreset
import com.example.data.model.VibePresets
import com.example.ui.VibeStudioUiState
import com.example.ui.components.AiPlaceholderNoticeBanner
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
import kotlin.math.roundToInt

@Composable
fun VibeStudioScreen(
    state: VibeStudioUiState,
    onSelectPreset: (VibePreset) -> Unit,
    onPromptChange: (String) -> Unit,
    onEnergyChange: (Int) -> Unit,
    onTrackCountChange: (Int) -> Unit,
    onCrossfadeChange: (Int) -> Unit,
    onCurveChange: (CrossfadeCurve) -> Unit,
    onGenerateClick: () -> Unit,
    onOpenInBooth: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Banner with generated artwork
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(172.dp)
                        .clip(RoundedCornerShape(22.dp))
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_vibe_banner),
                        contentDescription = "DJ Vibe Studio",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0x33090B10),
                                        Color(0xE6090B10)
                                    )
                                )
                            )
                            .padding(18.dp),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "VIBE STUDIO",
                                style = MaterialTheme.typography.labelMedium,
                                color = DeckACyan
                            )
                            Text(
                                text = "Build a DJ Set by Vibe",
                                style = MaterialTheme.typography.headlineSmall,
                                color = TextPrimary
                            )
                            Text(
                                text = "Automatic YouTube track lookup & gapless crossfades",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            // Placeholder AI Notice Banner (since Firebase setup was declined)
            item {
                AiPlaceholderNoticeBanner(
                    message = stringResource(R.string.ai_placeholder_notice)
                )
            }

            // Vibe Presets Carousel
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Choose Mood / Vibe",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(VibePresets.all, key = { it.id }) { preset ->
                            val selected = state.selectedPreset.id == preset.id
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (selected) DeckACyan.copy(alpha = 0.16f) else ConsoleSurface,
                                border = BorderStroke(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) DeckACyan else ConsoleCardBorder
                                ),
                                modifier = Modifier
                                    .width(205.dp)
                                    .clickable { onSelectPreset(preset) }
                                    .testTag("vibe_preset_${preset.id}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "${preset.bpmRange.first}–${preset.bpmRange.last} BPM",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) DeckACyan else TextMuted
                                    )
                                    Text(
                                        text = preset.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = preset.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Custom Vibe Description
            item {
                OutlinedTextField(
                    value = state.customPrompt,
                    onValueChange = onPromptChange,
                    label = { Text("Describe the vibe (optional)") },
                    placeholder = { Text(stringResource(R.string.vibe_prompt_hint)) },
                    minLines = 2,
                    maxLines = 3,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DeckACyan,
                        unfocusedBorderColor = ConsoleCardBorder,
                        focusedContainerColor = ConsoleSurface,
                        unfocusedContainerColor = ConsoleSurface,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vibe_prompt_input")
                )
            }

            // Sliders for Energy, Track Count, Crossfade Duration & Curve
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = ConsoleSurface),
                    border = BorderStroke(1.dp, ConsoleCardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Energy & Tempo", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text("${state.energyLevel}%", style = MaterialTheme.typography.labelMedium, color = DeckBCoral)
                        }
                        Slider(
                            value = state.energyLevel.toFloat(),
                            onValueChange = { onEnergyChange(it.roundToInt()) },
                            valueRange = 25f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = DeckBCoral,
                                activeTrackColor = DeckBCoral,
                                inactiveTrackColor = ConsoleCardBorder
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Track Count", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text("${state.trackCount} tracks", style = MaterialTheme.typography.labelMedium, color = DeckACyan)
                        }
                        Slider(
                            value = state.trackCount.toFloat(),
                            onValueChange = { onTrackCountChange(it.roundToInt()) },
                            valueRange = 4f..10f,
                            steps = 5,
                            colors = SliderDefaults.colors(
                                thumbColor = DeckACyan,
                                activeTrackColor = DeckACyan,
                                inactiveTrackColor = ConsoleCardBorder
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Crossfade Duration (gapless)", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text("${state.crossfadeSeconds}s", style = MaterialTheme.typography.labelMedium, color = AcidMint)
                        }
                        Slider(
                            value = state.crossfadeSeconds.toFloat(),
                            onValueChange = { onCrossfadeChange(it.roundToInt()) },
                            valueRange = 4f..16f,
                            steps = 11,
                            colors = SliderDefaults.colors(
                                thumbColor = AcidMint,
                                activeTrackColor = AcidMint,
                                inactiveTrackColor = ConsoleCardBorder
                            )
                        )

                        Text("Crossfade Curve", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CrossfadeCurve.entries.forEach { curve ->
                                FilterChip(
                                    selected = state.curve == curve,
                                    onClick = { onCurveChange(curve) },
                                    label = { Text(curve.label, style = MaterialTheme.typography.labelSmall) },
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

            // Generate Button
            item {
                Button(
                    onClick = onGenerateClick,
                    enabled = !state.isGenerating,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DeckACyan,
                        contentColor = ObsidianBg
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("generate_vibe_btn")
                ) {
                    if (state.isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = ObsidianBg,
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Searching tracks & building mix...", style = MaterialTheme.typography.labelLarge)
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Generate DJ Playlist",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (!state.errorMessage.isNullOrBlank()) {
                item {
                    Text(
                        text = state.errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Result card when playlist was generated
            item {
                val outcome = state.lastOutcome
                AnimatedVisibility(visible = outcome != null) {
                    if (outcome != null) {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = ConsoleSurfaceElevated),
                            border = BorderStroke(1.dp, AcidMint.copy(alpha = 0.6f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "SET READY · ${outcome.playlist.tracks.size} TRACKS",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = AcidMint
                                )
                                Text(
                                    text = outcome.playlist.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Matched ${outcome.resolvedYouTubeCount} YouTube tracks" +
                                        if (outcome.resolvedYouTubeCount < outcome.playlist.tracks.size)
                                            " (remaining slots filled with studio groove stems)."
                                        else " with ${outcome.playlist.crossfadeSeconds}s transition cues ready.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                                Button(
                                    onClick = onOpenInBooth,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AcidMint,
                                        contentColor = ObsidianBg
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("open_generated_booth_btn")
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Open & DJ in Booth", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}
