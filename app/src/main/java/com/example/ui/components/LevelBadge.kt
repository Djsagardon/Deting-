package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.UserLevelManager

@Composable
fun LevelBadge(
    level: Int,
    isAdmin: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (isAdmin) {
        // Special Verified Official Bondly Tag
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFF6200EA), Color(0xFFFF2D75))))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Verified,
                    contentDescription = "Official",
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Official Bondly",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp
                )
            }
        }
        return
    }

    val (bgColor, borderColor, textColor, badgeTitle) = when (level) {
        5 -> listOf(Color(0xFF2C194D), Color(0xFFE040FB), Color(0xFFFF80AB), "Level 5 • Diamond VIP")
        4 -> listOf(Color(0xFF152238), Color(0xFF00E5FF), Color(0xFF80D8FF), "Level 4 • Platinum")
        3 -> listOf(Color(0xFF332A15), Color(0xFFFFD700), Color(0xFFFFE082), "Level 3 • Gold")
        2 -> listOf(Color(0xFF242B35), Color(0xFFB0BEC5), Color(0xFFECEFF1), "Level 2 • Silver")
        else -> listOf(Color(0xFF2A1F1D), Color(0xFFCD7F32), Color(0xFFFFCC80), "Level 1 • Bronze")
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor as Color)
            .border(1.dp, borderColor as Color, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (level >= 3) Icons.Default.Star else Icons.Default.Shield,
                contentDescription = null,
                tint = borderColor,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = badgeTitle as String,
                color = textColor as Color,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }
    }
}
