package com.example.util

import com.example.data.model.UserProfile
import java.util.Date

object UserLevelManager {

    data class LevelInfo(
        val level: Int,
        val title: String,
        val badgeName: String,
        val requiredAccountDays: Int,
        val requiredActiveDays: Int,
        val voiceCallUnlocked: Boolean,
        val videoCallUnlocked: Boolean,
        val description: String,
        val perks: List<String>
    )

    val LEVELS = listOf(
        LevelInfo(
            level = 1,
            title = "New Member",
            badgeName = "Level 1 • Bronze",
            requiredAccountDays = 0,
            requiredActiveDays = 0,
            voiceCallUnlocked = false,
            videoCallUnlocked = false,
            description = "Welcome to Bondly! Start by exploring and chatting with your matches.",
            perks = listOf("Discover Profiles", "Send & Receive Likes", "Real-Time Text Chat")
        ),
        LevelInfo(
            level = 2,
            title = "Active Explorer",
            badgeName = "Level 2 • Silver",
            requiredAccountDays = 7,
            requiredActiveDays = 5,
            voiceCallUnlocked = true,
            videoCallUnlocked = false,
            description = "Unlocked Voice Calling with mutual matches.",
            perks = listOf("All Level 1 Perks", "HD Voice Calling Unlocked", "Verified Activity Badge")
        ),
        LevelInfo(
            level = 3,
            title = "Trusted Connector",
            badgeName = "Level 3 • Gold",
            requiredAccountDays = 30,
            requiredActiveDays = 15,
            voiceCallUnlocked = true,
            videoCallUnlocked = true,
            description = "Unlocked 1-on-1 HD Video Calling with mutual matches.",
            perks = listOf("All Level 2 Perks", "HD Video Calling Unlocked", "Gold Profile Highlight")
        ),
        LevelInfo(
            level = 4,
            title = "Elite Member",
            badgeName = "Level 4 • Platinum",
            requiredAccountDays = 90,
            requiredActiveDays = 45,
            voiceCallUnlocked = true,
            videoCallUnlocked = true,
            description = "Priority placement in Discover feed and enhanced social reputation.",
            perks = listOf("All Level 3 Perks", "Priority Discovery Feed", "Unlimited Daily Likes", "Platinum Badge")
        ),
        LevelInfo(
            level = 5,
            title = "Bondly Ambassador",
            badgeName = "Level 5 • Diamond VIP",
            requiredAccountDays = 365,
            requiredActiveDays = 120,
            voiceCallUnlocked = true,
            videoCallUnlocked = true,
            description = "Top reputation milestone for veteran trusted members of the Bondly community.",
            perks = listOf("All Application Features", "Exclusive Diamond VIP Status", "Community Ambassador Perks")
        )
    )

    data class ProgressionStatus(
        val currentLevel: LevelInfo,
        val nextLevel: LevelInfo?,
        val accountAgeDays: Int,
        val activeDays: Int,
        val daysToNextLevel: Int,
        val activeDaysToNextLevel: Int,
        val progressPercentage: Float,
        val isVoiceCallAllowed: Boolean,
        val isVideoCallAllowed: Boolean,
        val isAdmin: Boolean
    )

    fun calculateProgression(profile: UserProfile?): ProgressionStatus {
        val isAdmin = profile?.role.equals("admin", ignoreCase = true)

        val createdAt = profile?.createdAt?.toDate() ?: Date()
        val now = Date()
        val diffMs = now.time - createdAt.time
        val accountAgeDays = (diffMs / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(0)
        val activeDays = (profile?.activeDaysCount ?: 1).coerceAtLeast(1)

        // Determine level
        val calculatedLevel = if (isAdmin) {
            5
        } else when {
            accountAgeDays >= 365 && activeDays >= 120 -> 5
            accountAgeDays >= 90 && activeDays >= 45 -> 4
            accountAgeDays >= 30 && activeDays >= 15 -> 3
            accountAgeDays >= 7 && activeDays >= 5 -> 2
            else -> 1
        }

        val currentLevelInfo = LEVELS.firstOrNull { it.level == calculatedLevel } ?: LEVELS[0]
        val nextLevelInfo = LEVELS.firstOrNull { it.level == calculatedLevel + 1 }

        val (daysRemaining, activeDaysRemaining, progress) = if (nextLevelInfo != null) {
            val reqDays = nextLevelInfo.requiredAccountDays
            val reqActive = nextLevelInfo.requiredActiveDays

            val remDays = (reqDays - accountAgeDays).coerceAtLeast(0)
            val remActive = (reqActive - activeDays).coerceAtLeast(0)

            val dayRatio = if (reqDays > 0) (accountAgeDays.toFloat() / reqDays).coerceIn(0f, 1f) else 1f
            val activeRatio = if (reqActive > 0) (activeDays.toFloat() / reqActive).coerceIn(0f, 1f) else 1f
            val combinedRatio = (dayRatio * 0.5f) + (activeRatio * 0.5f)

            Triple(remDays, remActive, combinedRatio)
        } else {
            Triple(0, 0, 1.0f)
        }

        return ProgressionStatus(
            currentLevel = currentLevelInfo,
            nextLevel = nextLevelInfo,
            accountAgeDays = accountAgeDays,
            activeDays = activeDays,
            daysToNextLevel = daysRemaining,
            activeDaysToNextLevel = activeDaysRemaining,
            progressPercentage = progress,
            isVoiceCallAllowed = isAdmin || currentLevelInfo.voiceCallUnlocked,
            isVideoCallAllowed = isAdmin || currentLevelInfo.videoCallUnlocked,
            isAdmin = isAdmin
        )
    }
}
