package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

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
    background = CharcoalDark,
    onBackground = TextWhite,
    surface = CharcoalSurface,
    onSurface = TextWhite,
    surfaceVariant = CharcoalCard,
    onSurfaceVariant = TextLightGrey,
    error = ErrorRed,
    onError = TextWhite,
    outline = CharcoalBorder
)

@Composable
fun BondlyTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = CharcoalDark.toArgb()
                window.navigationBarColor = CharcoalDark.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = BondlyColorScheme,
        typography = Typography,
        content = content
    )
}
