package com.example.util

import com.example.data.model.UserProfile
import java.util.regex.Pattern

enum class OperationType {
    READ, WRITE, DELETE, CALL
}

object FirestoreErrorHandler {
    fun getReadableError(error: Exception?, operation: OperationType = OperationType.READ): String {
        val msg = error?.message ?: ""
        return when {
            msg.contains("PERMISSION_DENIED", ignoreCase = true) ->
                "Action not permitted. Please complete your profile or check your permissions."
            msg.contains("UNAVAILABLE", ignoreCase = true) ->
                "Network connection lost. Please check your internet connection."
            else -> error?.localizedMessage ?: "Something went wrong. Please try again."
        }
    }
}

object ContactSharingFilter {
    private val PHONE_PATTERN = Pattern.compile("(\\+?[0-9]{1,3}[- .]?)?([0-9]{10}|[0-9]{3}[- .][0-9]{3}[- .][0-9]{4})")
    private val INSTA_PATTERN = Pattern.compile("(?i)(insta|ig|snap|telegram|wa\\.me|whatsapp|t\\.me)[\\s:@]+[a-zA-Z0-9_.]+")

    data class CheckResult(
        val isRestricted: Boolean,
        val reason: String = ""
    )

    fun checkMessage(text: String, userLevel: Int): CheckResult {
        if (userLevel < 2) {
            if (PHONE_PATTERN.matcher(text).find()) {
                return CheckResult(true, "Sharing mobile numbers is unlocked at Level 2. Keep conversations on Bondly for your safety!")
            }
            if (INSTA_PATTERN.matcher(text).find()) {
                return CheckResult(true, "Sharing external handles requires Level 2. Bondly protects your privacy until trust is built.")
            }
        }
        return CheckResult(false)
    }
}

object UserLevelManager {
    data class LevelInfo(
        val level: Int,
        val title: String,
        val perk: String,
        val minDays: Int
    )

    val levels = listOf(
        LevelInfo(1, "Newcomer", "Browse profiles & message matches", 0),
        LevelInfo(2, "Verified Bond", "Unlock voice/video calling & contact sharing", 3),
        LevelInfo(3, "Active Connector", "Priority in discovery & daily match boost", 7),
        LevelInfo(4, "Trusted Companion", "Unlimited daily likes & profile badge", 14),
        LevelInfo(5, "Bondly Star", "VIP badge, premium discovery & all perks", 30)
    )

    fun calculateLevel(profile: UserProfile): Int {
        val days = profile.activeDaysCount
        return when {
            days >= 30 && profile.profileCompleted -> 5
            days >= 14 && profile.profileCompleted -> 4
            days >= 7 && profile.profileCompleted -> 3
            days >= 3 -> 2
            else -> 1
        }
    }
}
