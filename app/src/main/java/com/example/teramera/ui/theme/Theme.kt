package com.example.teramera.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Peach,
    onPrimary = OnAccent,
    primaryContainer = PeachSoft,
    onPrimaryContainer = Ink900,
    secondary = Mint,
    onSecondary = OnAccent,
    secondaryContainer = MintSoft,
    onSecondaryContainer = Ink900,
    tertiary = Pos,
    onTertiary = Paper100,
    tertiaryContainer = PosSoft,
    onTertiaryContainer = Ink900,
    error = Neg,
    onError = Paper100,
    errorContainer = NegSoft,
    onErrorContainer = Ink900,
    background = Paper100,
    onBackground = Ink900,
    surface = Paper000,
    onSurface = Ink900,
    surfaceVariant = Paper200,
    onSurfaceVariant = Ink500,
    surfaceTint = Peach,
    surfaceDim = Paper300,
    surfaceBright = Paper000,
    surfaceContainerLowest = Paper000,
    surfaceContainerLow = Paper100,
    surfaceContainer = Paper200,
    surfaceContainerHigh = Paper200,
    surfaceContainerHighest = Paper300,
    outline = BorderStrong,
    outlineVariant = BorderSubtle,
    // Ink surfaces (headers, nav, hero cards) in light mode.
    inverseSurface = Ink900,
    inverseOnSurface = Paper100,
    inversePrimary = Peach,
)

private val DarkColors = darkColorScheme(
    primary = Peach,
    onPrimary = OnAccent,
    primaryContainer = DPeachContainer,
    onPrimaryContainer = Paper100,
    secondary = Mint,
    onSecondary = OnAccent,
    secondaryContainer = DMintContainer,
    onSecondaryContainer = Paper100,
    tertiary = DPos,
    onTertiary = OnAccent,
    tertiaryContainer = DPosSoft,
    onTertiaryContainer = Paper100,
    error = DNeg,
    onError = OnAccent,
    errorContainer = DNegSoft,
    onErrorContainer = Paper100,
    background = Ink900,
    onBackground = Paper100,
    surface = Ink800,
    onSurface = Paper100,
    surfaceVariant = Ink700,
    onSurfaceVariant = DInk2,
    surfaceTint = Peach,
    surfaceDim = Ink900,
    surfaceBright = Ink700,
    surfaceContainerLowest = Ink900,
    surfaceContainerLow = Ink800,
    surfaceContainer = Ink800,
    surfaceContainerHigh = Ink700,
    surfaceContainerHighest = Ink700,
    outline = DBorderStrong,
    outlineVariant = DBorderSubtle,
    // Ink surfaces step up one level so they don't vanish on the ink page.
    inverseSurface = Ink800,
    inverseOnSurface = Paper100,
    inversePrimary = Peach,
)

@Composable
fun TerameraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        shapes = TerameraShapes,
        content = content,
    )
}
