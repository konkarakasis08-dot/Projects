package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val MixPulseColorScheme = darkColorScheme(
    primary = DeckACyan,
    onPrimary = ObsidianBg,
    primaryContainer = DeckACyanDim,
    onPrimaryContainer = DeckACyan,
    secondary = DeckBCoral,
    onSecondary = ObsidianBg,
    secondaryContainer = DeckBCoralDim,
    onSecondaryContainer = DeckBCoral,
    tertiary = AcidMint,
    onTertiary = ObsidianBg,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = ConsoleSurface,
    onSurface = TextPrimary,
    surfaceVariant = ConsoleSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = ConsoleCardBorder,
    outlineVariant = TextMuted,
    error = ErrorCrimson,
    onError = ObsidianBg
)

val MixPulseShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MixPulseTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MixPulseColorScheme,
        typography = Typography,
        shapes = MixPulseShapes,
        content = content
    )
}
