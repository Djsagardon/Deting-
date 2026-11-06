package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

sealed class ImageUploadResult {
    data class Success(val imageUrl: String, val deleteUrl: String? = null) : ImageUploadResult()
    data class Error(val message: String) : ImageUploadResult()
}

class ImageUploadRepository(private val context: Context? = null) {

    companion object {
        private const val TAG = "ImageUploadRepo"

        // Configurable Cloudflare Worker endpoint
        // Users can point this to their deployed worker:
        // https://bondly-image-uploader.<subdomain>.workers.dev/upload
        var workerEndpointUrl: String = "https://bondly-image-uploader.dating-buddy.workers.dev/upload"

        private val httpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()
        }
    }

    /**
     * Uploads a single image to the secure Cloudflare Worker proxy.
     * The Cloudflare Worker verifies the Firebase ID Token and forwards to ImgBB using the secure IMGBB_API_KEY.
     */
    suspend fun uploadProfilePhoto(
        appContext: Context,
        uri: Uri,
        userId: String = "",
        slotIndex: Int = 0
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 1. Compress image to optimized JPEG bytes
            val compressedBytes = compressImageUri(appContext, uri)
                ?: return@withContext Result.failure(Exception("Could not read or process image file."))

            // 2. Fetch fresh Firebase Auth ID Token for authentication
            val auth = FirebaseAuth.getInstance()
            val currentUser = auth.currentUser
            val idToken = try {
                currentUser?.getIdToken(false)?.await()?.token ?: ""
            } catch (e: Exception) {
                Log.w(TAG, "Failed to retrieve Firebase ID token: ${e.message}")
                ""
            }

            // 3. Prepare multipart request body
            val mediaType = "image/jpeg".toMediaTypeOrNull()
            val requestFile = compressedBytes.toRequestBody(mediaType)

            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "image",
                    "bondly_photo_${slotIndex}_${System.currentTimeMillis()}.jpg",
                    requestFile
                )
                .build()

            val requestBuilder = Request.Builder()
                .url(workerEndpointUrl)
                .post(multipartBody)

            if (idToken.isNotEmpty()) {
                requestBuilder.header("Authorization", "Bearer $idToken")
            }

            val request = requestBuilder.build()

            // 4. Execute HTTP request
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = try {
                    JSONObject(responseBody).optString("error", "HTTP ${response.code}: Upload failed")
                } catch (_: Exception) {
                    "Upload failed (HTTP ${response.code}). Ensure your Cloudflare Worker is deployed."
                }
                Log.e(TAG, "Worker upload error: $errorMsg")
                return@withContext Result.failure(Exception(errorMsg))
            }

            // 5. Parse response JSON
            val json = JSONObject(responseBody)
            if (json.optBoolean("success", false)) {
                val imageUrl = json.optString("url")
                if (imageUrl.isNotEmpty()) {
                    Log.d(TAG, "Photo uploaded successfully: $imageUrl")
                    return@withContext Result.success(imageUrl)
                }
            }

            val failReason = json.optString("error", "Missing image URL in server response.")
            Result.failure(Exception(failReason))
        } catch (e: Exception) {
            Log.e(TAG, "Exception uploading image via worker: ${e.message}", e)
            val friendlyMsg = if (e is java.net.UnknownHostException || e is java.net.ConnectException) {
                "Unable to connect to upload server. Please verify your Cloudflare Worker URL or internet connection."
            } else {
                e.localizedMessage ?: "Upload failed unexpectedly"
            }
            Result.failure(Exception(friendlyMsg))
        }
    }

    /**
     * Compresses the bitmap at URI to a standard, high-quality JPEG under 1.5MB.
     */
    private fun compressImageUri(context: Context, uri: Uri): ByteArray? {
        var inputStream: InputStream? = null
        return try {
            inputStream = context.contentResolver.openInputStream(uri)
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, boundsOptions)
            inputStream?.close()

            // Calculate inSampleSize to prevent OutOfMemory
            var sampleSize = 1
            val maxDimension = 1800
            val maxSide = maxOf(boundsOptions.outWidth, boundsOptions.outHeight)
            while (maxSide / (sampleSize * 2) >= maxDimension) {
                sampleSize *= 2
            }

            inputStream = context.contentResolver.openInputStream(uri)
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeStream(inputStream, null, decodeOptions) ?: return null

            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            bitmap.recycle()
            outputStream.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "Error compressing image: ${e.message}")
            null
        } finally {
            try {
                inputStream?.close()
            } catch (_: Exception) {}
        }
    }
}
