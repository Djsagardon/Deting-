package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val BondlyColorScheme = darkColorScheme(
    primary = BondlyPink,
    onPrimary = TextWhite,
    primaryContainer = BondlyMagenta,
    onPrimaryContainer = TextWhite,
    secondary = BondlyPurple,
    onSecondary = TextWhite,
    secondaryContainer = CharcoalCardElevated,
    onSecondaryContainer = TextLightGrey,
    tertiary = BondlyGold,
    onTertiary = CharcoalDark,
    background = CharcoalDark,
    onBackground = TextWhite,
    surface = CharcoalSurface,
    onSurface = TextWhite,
    surfaceVariant = CharcoalCard,
    onSurfaceVariant = TextLightGrey,
    outline = CharcoalBorder,
    error = ErrorRed,
    onError = TextWhite
)

@Composable
fun BondlyTheme(
    darkTheme: Boolean = true, // Luxury dark by default for premium dating aesthetic
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = BondlyColorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    BondlyTheme(darkTheme = true, content = content)
}
