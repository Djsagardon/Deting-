package com.example.ui

sealed class Screen {
    data object Main : Screen()
    data object SearchById : Screen()
    data class PublicProfile(val userId: String) : Screen()
    data object EditProfile : Screen()
    data object PhotoManagement : Screen()
    data object CallHistory : Screen()
    data object Settings : Screen()
    data object PrivacySafetySettings : Screen()
    data object BlockedUsers : Screen()
    data class ReportUser(val targetUid: String, val targetName: String) : Screen()
    data object AccountDeletion : Screen()
    data object HelpSupport : Screen()
    data object Admin : Screen()
    data class Call(
        val callId: String,
        val targetUid: String,
        val targetName: String,
        val isVideo: Boolean,
        val isIncoming: Boolean
    ) : Screen()
}
