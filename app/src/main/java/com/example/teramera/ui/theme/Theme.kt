package com.example.teramera.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = AccentTeal,
    onPrimary = Color.White,
    primaryContainer = AccentTealSoft,
    onPrimaryContainer = Ink1,
    secondary = AccentViolet,
    onSecondary = Color.White,
    secondaryContainer = AccentVioletSoft,
    onSecondaryContainer = Ink1,
    tertiary = PosGreen,
    onTertiary = Color.White,
    tertiaryContainer = PosSoft,
    onTertiaryContainer = Ink1,
    error = NegRed,
    onError = Color.White,
    errorContainer = NegSoft,
    onErrorContainer = Ink1,
    background = Bg1,
    onBackground = Ink1,
    surface = Bg2,
    onSurface = Ink1,
    surfaceVariant = Bg3,
    onSurfaceVariant = Ink2,
    outline = BorderStrong,
    outlineVariant = BorderSubtle,
)

private val DarkColors = darkColorScheme(
    primary = DAccentTeal,
    onPrimary = DBg1,
    primaryContainer = DAccentTealSoft,
    onPrimaryContainer = DInk1,
    secondary = DAccentViolet,
    onSecondary = DBg1,
    secondaryContainer = DAccentVioletSoft,
    onSecondaryContainer = DInk1,
    tertiary = DPosGreen,
    onTertiary = DBg1,
    tertiaryContainer = DPosSoft,
    onTertiaryContainer = DInk1,
    error = DNegRed,
    onError = DBg1,
    errorContainer = DNegSoft,
    onErrorContainer = DInk1,
    background = DBg1,
    onBackground = DInk1,
    surface = DBg2,
    onSurface = DInk1,
    surfaceVariant = DBg3,
    onSurfaceVariant = DInk2,
    outline = DInk3,
    outlineVariant = DBorderSubtle,
)

@Composable
fun TerameraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}
