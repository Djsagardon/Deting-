package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Primary Branding & Accent Colors
val BondlyPink = Color(0xFFFF2D75)
val BondlyMagenta = Color(0xFFE02885)
val BondlyPurple = Color(0xFF8E2DE2)
val BondlyViolet = Color(0xFF4A00E0)
val BondlyRose = Color(0xFFFF6584)
val BondlyGold = Color(0xFFFFB800)

// Luxury Dark Palette
val CharcoalDark = Color(0xFF0F0E17)
val CharcoalSurface = Color(0xFF161522)
val CharcoalCard = Color(0xFF1F1D30)
val CharcoalCardElevated = Color(0xFF29263E)
val CharcoalBorder = Color(0xFF33304E)

// Text and Functional Colors
val TextWhite = Color(0xFFFFFFFF)
val TextLightGrey = Color(0xFFD1CFDC)
val TextMuted = Color(0xFF8D8A9F)
val OnlineGreen = Color(0xFF00E676)
val VerifiedBlue = Color(0xFF2979FF)
val ErrorRed = Color(0xFFFF334B)
val SuperLikeBlue = Color(0xFF00B0FF)

// Gradients
val BondlyGradient = Brush.horizontalGradient(
    colors = listOf(BondlyPink, BondlyMagenta, BondlyPurple)
)

val BondlyVerticalGradient = Brush.verticalGradient(
    colors = listOf(BondlyPink, BondlyPurple)
)

val CardOverlayGradient = Brush.verticalGradient(
    colors = listOf(Color.Transparent, Color(0x66000000), Color(0xF00F0E17))
)

val GoldGradient = Brush.horizontalGradient(
    colors = listOf(Color(0xFFFFD700), Color(0xFFFFA500))
)
