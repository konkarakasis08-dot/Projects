package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.DeckId
import com.example.data.model.TrackSourceType
import com.example.ui.theme.AcidMint
import com.example.ui.theme.ConsoleCardBorder
import com.example.ui.theme.ConsoleSurface
import com.example.ui.theme.ConsoleSurfaceElevated
import com.example.ui.theme.DeckACyan
import com.example.ui.theme.DeckBCoral
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.sin

fun formatTimecode(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val mins = safe / 60
    val secs = safe % 60
    return "%02d:%02d".format(mins, secs)
}

@Composable
fun TurntableDisc(
    thumbnailUrl: String?,
    sourceType: TrackSourceType?,
    isPlaying: Boolean,
    accentColor: Color,
    size: Dp = 64.dp,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "vinylSpin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "vinylAngle"
    )
    val effectiveRotation = if (isPlaying) rotation else 0f

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(ConsoleSurface)
            .border(
                width = if (isPlaying) 2.dp else 1.dp,
                color = if (isPlaying) accentColor else ConsoleCardBorder,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
                .clip(CircleShape)
                .rotate(effectiveRotation),
            contentAlignment = Alignment.Center
        ) {
            if (!thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = "Εξώφυλλο τραγουδιού",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.img_vinyl_cover),
                    contentDescription = "Βινύλιο MixPulse",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        // Center spindle hole
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(Color(0xFF090B10))
                .border(1.5.dp, accentColor.copy(alpha = 0.85f), CircleShape)
        )
    }
}

@Composable
fun LiveWaveformBar(
    isPlayingA: Boolean,
    isPlayingB: Boolean,
    isCrossfading: Boolean,
    crossfader: Float,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "waveform")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    val anyPlaying = isPlayingA || isPlayingB

    Canvas(modifier = modifier) {
        val barCount = 36
        val totalWidth = size.width
        val gap = 4.dp.toPx()
        val barWidth = ((totalWidth - gap * (barCount - 1)) / barCount).coerceAtLeast(2f)
        val centerY = size.height / 2f

        for (i in 0 until barCount) {
            val normX = i.toFloat() / (barCount - 1).coerceAtLeast(1)
            val baseHeight = if (anyPlaying) {
                val wave1 = sin(phase + i * 0.45f)
                val wave2 = sin(phase * 1.7f - i * 0.25f)
                val combined = (0.35f + 0.35f * ((wave1 + wave2) * 0.5f + 1f) * 0.5f)
                (size.height * combined).coerceIn(6f, size.height * 0.92f)
            } else {
                size.height * (0.16f + 0.08f * sin(i * 0.6f))
            }

            val barColor = when {
                isCrossfading -> {
                    val blend = normX
                    if (blend < 0.5f) DeckACyan else DeckBCoral
                }
                normX < (1f - crossfader) -> DeckACyan
                else -> DeckBCoral
            }.copy(alpha = if (anyPlaying) 0.9f else 0.35f)

            val x = i * (barWidth + gap)
            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, centerY - baseHeight / 2f),
                size = Size(barWidth, baseHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

@Composable
fun SourceBadge(sourceType: TrackSourceType) {
    val (label, color) = when (sourceType) {
        TrackSourceType.YOUTUBE -> "YOUTUBE" to DeckBCoral
        TrackSourceType.LOCAL_AUDIO -> "DEVICE" to AcidMint
        TrackSourceType.BUILT_IN_GROOVE -> "STUDIO STEM" to DeckACyan
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun AiPlaceholderNoticeBanner(
    message: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = ConsoleSurfaceElevated,
        border = BorderStroke(1.dp, Color(0xFFFFC857).copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.GraphicEq,
                contentDescription = null,
                tint = Color(0xFFFFC857),
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}
