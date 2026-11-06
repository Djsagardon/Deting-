package com.example.util

import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.firestore.FirebaseFirestoreException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException

class NoInternetException(
    message: String = "No internet connection. Please connect to the internet and try again."
) : Exception(message)

class ServerUnavailableException(
    message: String = "Unable to reach server. Please check your connection and try again."
) : Exception(message)

class AuthExpiredException(
    message: String = "Your session has expired. Please sign in again."
) : Exception(message)

class StorageServiceException(
    message: String = "Photo storage service is temporarily unavailable. Please try again."
) : Exception(message)

class DatabasePermissionException(
    message: String = "You do not have permission to perform this action."
) : Exception(message)

class ServerTimeoutException(
    message: String = "Server request timed out. Please try again."
) : Exception(message)

object BondlyErrorClassifier {

    const val MSG_NO_INTERNET = "No internet connection. Please connect to the internet and try again."
    const val MSG_SERVER_UNAVAILABLE = "Unable to connect to live server. Please check your connection and try again."
    const val MSG_AUTH_EXPIRED = "Your session has expired. Please sign in again."
    const val MSG_STORAGE_UNAVAILABLE = "Upload server is temporarily unreachable. Please try again later."
    const val MSG_STORAGE_FAILED = "Photo storage service could not complete the upload. Please try again."
    const val MSG_PERMISSION_DENIED = "Access denied. Please ensure your profile is complete or verify permissions."
    const val MSG_TIMEOUT = "Server request timed out. Please check your connection and try again."
    const val MSG_GENERIC = "Something went wrong. Please check your connection and try again."

    fun getUserFriendlyMessage(throwable: Throwable?): String {
        if (throwable == null) return MSG_GENERIC
        val rawMsg = throwable.message ?: ""

        return when {
            throwable is NoInternetException -> MSG_NO_INTERNET
            throwable is ServerUnavailableException -> MSG_SERVER_UNAVAILABLE
            throwable is AuthExpiredException -> MSG_AUTH_EXPIRED
            throwable is StorageServiceException -> MSG_STORAGE_FAILED
            throwable is DatabasePermissionException -> MSG_PERMISSION_DENIED
            throwable is ServerTimeoutException -> MSG_TIMEOUT

            throwable is UnknownHostException -> MSG_NO_INTERNET
            throwable is ConnectException -> MSG_SERVER_UNAVAILABLE
            throwable is SocketTimeoutException || throwable is TimeoutException -> MSG_TIMEOUT

            throwable is FirebaseAuthInvalidUserException || throwable is FirebaseAuthRecentLoginRequiredException ->
                MSG_AUTH_EXPIRED

            throwable is FirebaseFirestoreException -> {
                when (throwable.code) {
                    FirebaseFirestoreException.Code.UNAVAILABLE -> MSG_SERVER_UNAVAILABLE
                    FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> MSG_TIMEOUT
                    FirebaseFirestoreException.Code.PERMISSION_DENIED -> MSG_PERMISSION_DENIED
                    FirebaseFirestoreException.Code.UNAUTHENTICATED -> MSG_AUTH_EXPIRED
                    else -> rawMsg.ifBlank { MSG_SERVER_UNAVAILABLE }
                }
            }

            rawMsg.contains("No internet", ignoreCase = true) ||
            rawMsg.contains("network connection", ignoreCase = true) -> MSG_NO_INTERNET

            rawMsg.contains("PERMISSION_DENIED", ignoreCase = true) -> MSG_PERMISSION_DENIED
            rawMsg.contains("UNAVAILABLE", ignoreCase = true) -> MSG_SERVER_UNAVAILABLE
            rawMsg.contains("timeout", ignoreCase = true) -> MSG_TIMEOUT
            rawMsg.contains("session", ignoreCase = true) || rawMsg.contains("auth", ignoreCase = true) -> MSG_AUTH_EXPIRED

            else -> rawMsg.ifBlank { MSG_GENERIC }
        }
    }
}
