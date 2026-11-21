package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MutigandaDarkColorScheme = darkColorScheme(
    primary = ShieldEmerald,
    onPrimary = ObsidianNight,
    primaryContainer = DeepTealContainer,
    onPrimaryContainer = ShieldEmerald,
    secondary = TunnelCyan,
    onSecondary = ObsidianNight,
    secondaryContainer = SlateCardElevated,
    onSecondaryContainer = TunnelCyan,
    tertiary = AmberPulse,
    onTertiary = ObsidianNight,
    background = ObsidianNight,
    onBackground = IceWhite,
    surface = ObsidianSurface,
    onSurface = IceWhite,
    surfaceVariant = SlateCard,
    onSurfaceVariant = SlateMuted,
    outline = BorderHairline,
    outlineVariant = Color(0xFF172A42),
    error = CoralAlert,
    onError = Color.White,
)

private val MutigandaLightColorScheme = lightColorScheme(
    primary = EmeraldPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = Color(0xFF064E3B),
    secondary = CyanSecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFFAFE),
    onSecondaryContainer = Color(0xFF164E63),
    tertiary = AmberPulse,
    onTertiary = Color.White,
    background = LightCanvas,
    onBackground = DeepNavyText,
    surface = LightSurface,
    onSurface = DeepNavyText,
    surfaceVariant = LightCard,
    onSurfaceVariant = SlateDim,
    outline = Color(0xFFCBD5E1),
    error = CoralAlert,
    onError = Color.White,
)

@Composable
fun MutigandaVpnTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) MutigandaDarkColorScheme else MutigandaLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MutigandaTypography,
        content = content,
    )
}
