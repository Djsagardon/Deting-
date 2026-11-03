package com.example.data.repository

import android.content.Context
import android.net.Uri

sealed class ImageUploadResult {
    data class Success(val imageUrl: String, val deleteUrl: String? = null) : ImageUploadResult()
    data class Error(val message: String) : ImageUploadResult()
}

class ImageUploadRepository(private val context: Context? = null) {
    suspend fun uploadProfilePhoto(uri: Uri, userId: String, slotIndex: Int): Result<String> {
        return Result.success(uri.toString())
    }

    suspend fun uploadImage(
        uri: Uri,
        apiKey: String? = null,
        onProgress: (Int) -> Unit = {}
    ): ImageUploadResult {
        return ImageUploadResult.Success(uri.toString())
    }
}
