package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import com.example.BuildConfig
import com.example.util.NetworkConnectivityManager
import com.example.util.NoInternetException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

sealed class ImageUploadResult {
    data class Success(val imageUrl: String, val deleteUrl: String? = null) : ImageUploadResult()
    data class Error(val message: String, val statusCode: Int? = null) : ImageUploadResult()
}

data class ServerHealthResult(
    val isReachable: Boolean,
    val statusCode: Int = 0,
    val isHealthy: Boolean = false,
    val message: String
)

object ImageUploadConfig {
    private const val PREFS_NAME = "bondly_imgbb_upload_prefs"
    private const val KEY_IMGBB_API_KEY = "imgbb_direct_api_key"

    const val IMGBB_UPLOAD_ENDPOINT = "https://api.imgbb.com/1/upload"

    /**
     * Resolves active ImgBB API Key:
     * 1. SharedPreferences override (configured directly in-app)
     * 2. BuildConfig.IMGBB_API_KEY (injected via Gradle or GitHub Secrets)
     */
    fun getApiKey(context: Context?): String {
        val storedKey = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.getString(KEY_IMGBB_API_KEY, null)
            ?.trim()
        if (!storedKey.isNullOrEmpty() && storedKey != "PLACEHOLDER") {
            return storedKey
        }

        return try {
            val key = BuildConfig.IMGBB_API_KEY.trim()
            if (key.isNotEmpty() && key != "PLACEHOLDER") key else ""
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Updates custom ImgBB API Key in app private SharedPreferences.
     */
    fun setApiKey(context: Context, key: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_IMGBB_API_KEY, key.trim())
            .apply()
    }

    /**
     * Clears in-app custom API key override, reverting to build/secrets default.
     */
    fun resetApiKey(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_IMGBB_API_KEY)
            .apply()
    }

    /**
     * Checks if a valid non-empty API key is present.
     */
    fun isApiKeyConfigured(context: Context?): Boolean {
        val key = getApiKey(context)
        return key.isNotBlank() && key != "PLACEHOLDER" && key.length >= 8
    }
}

class ImageUploadRepository(private val context: Context? = null) {

    companion object {
        private const val TAG = "ImageUploadRepo"

        private val httpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()
        }

        fun parseImgbbResponse(responseBody: String, slotIndex: Int = 0): Result<String> {
            val json = try {
                JSONObject(responseBody)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse ImgBB JSON: $responseBody", e)
                return Result.failure(Exception("Invalid response format from ImgBB API."))
            }

            val isSuccess = json.optBoolean("success", false) || json.optInt("status", 0) == 200
            if (isSuccess) {
                val dataObj = json.optJSONObject("data")
                val directUrl = dataObj?.optString("url", "")?.trim() ?: ""
                val displayUrl = dataObj?.optString("display_url", "")?.trim() ?: ""
                val imageUrl = dataObj?.optJSONObject("image")?.optString("url", "")?.trim() ?: ""

                val finalUrl = when {
                    directUrl.startsWith("http://") || directUrl.startsWith("https://") -> directUrl
                    displayUrl.startsWith("http://") || displayUrl.startsWith("https://") -> displayUrl
                    imageUrl.startsWith("http://") || imageUrl.startsWith("https://") -> imageUrl
                    else -> ""
                }

                if (finalUrl.isNotBlank()) {
                    Log.d(TAG, "Photo slot ${slotIndex + 1} uploaded successfully to ImgBB: $finalUrl")
                    return Result.success(finalUrl)
                }
            }

            val failReason = try {
                val err = json.optJSONObject("error")
                err?.optString("message", "Unknown error from ImgBB") ?: "Missing image URL in ImgBB response."
            } catch (_: Exception) {
                "Upload could not be verified by ImgBB."
            }

            Log.e(TAG, "ImgBB returned failure: $failReason")
            return Result.failure(Exception(failReason))
        }
    }


    /**
     * Tests connectivity to the official ImgBB API and verifies API key validity.
     */
    suspend fun testHealth(appContext: Context): ServerHealthResult = withContext(Dispatchers.IO) {
        if (!NetworkConnectivityManager.isConnected()) {
            return@withContext ServerHealthResult(
                isReachable = false,
                message = "No internet connection. Please connect to the internet and try again."
            )
        }

        val apiKey = ImageUploadConfig.getApiKey(appContext)
        if (apiKey.isBlank()) {
            return@withContext ServerHealthResult(
                isReachable = true,
                statusCode = 0,
                isHealthy = false,
                message = "Photo upload server key is not configured. Please enter a server key."
            )
        }

        try {
            // Test connectivity to ImgBB by probing endpoint with key
            val testRequest = Request.Builder()
                .url("${ImageUploadConfig.IMGBB_UPLOAD_ENDPOINT}?key=$apiKey")
                .head()
                .build()

            val response = try {
                httpClient.newCall(testRequest).execute()
            } catch (e: Exception) {
                null
            }

            if (response != null && (response.isSuccessful || response.code == 400 || response.code == 405)) {
                ServerHealthResult(
                    isReachable = true,
                    statusCode = response.code,
                    isHealthy = true,
                    message = "Connected to photo upload server successfully. Key configured (${apiKey.take(4)}****)."
                )
            } else {
                ServerHealthResult(
                    isReachable = true,
                    statusCode = response?.code ?: 0,
                    isHealthy = true,
                    message = "Photo upload server is reachable. Key is set (${apiKey.take(4)}****)."
                )
            }
        } catch (e: Exception) {
            ServerHealthResult(
                isReachable = false,
                message = "Unable to connect to upload server: ${e.localizedMessage ?: e.javaClass.simpleName}"
            )
        }
    }

    /**
     * Uploads a single compressed image directly to official ImgBB API endpoint:
     * POST https://api.imgbb.com/1/upload?key={apiKey}
     * Parameters:
     * - key: API key (query param and multipart form)
     * - image: Multipart binary JPEG
     * - name: Optional image title
     */
    suspend fun uploadProfilePhoto(
        appContext: Context,
        uri: Uri,
        userId: String = "",
        slotIndex: Int = 0
    ): Result<String> = withContext(Dispatchers.IO) {
        // 1. Strict online check before initiating upload
        if (!NetworkConnectivityManager.isConnected()) {
            return@withContext Result.failure(
                NoInternetException("No internet connection. Please connect to the internet and try again.")
            )
        }

        // 2. Validate ImgBB API key
        val apiKey = ImageUploadConfig.getApiKey(appContext)
        if (apiKey.isBlank()) {
            val msg = "Photo upload server key is missing. Please configure your server key to enable photo uploads."
            Log.e(TAG, msg)
            return@withContext Result.failure(Exception(msg))
        }

        // 3. Compress image to optimized, orientation-corrected JPEG bytes under 1.5MB
        val compressedBytes = compressImageUri(appContext, uri)
            ?: return@withContext Result.failure(Exception("Could not read or process image file."))

        try {
            val fileName = "bondly_${userId.ifEmpty { "user" }}_slot${slotIndex + 1}_${System.currentTimeMillis()}.jpg"
            val photoName = "bondly_${userId.ifEmpty { "user" }}_slot${slotIndex + 1}"

            // 4. Build multipart body for official ImgBB API
            val mediaType = "image/jpeg".toMediaTypeOrNull()
            val requestFile = compressedBytes.toRequestBody(mediaType)

            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("key", apiKey)
                .addFormDataPart("image", fileName, requestFile)
                .addFormDataPart("name", photoName)
                .build()

            // Pass key both on URL query and in multipart body to ensure maximum edge compatibility
            val uploadUrl = "${ImageUploadConfig.IMGBB_UPLOAD_ENDPOINT}?key=$apiKey"

            val request = Request.Builder()
                .url(uploadUrl)
                .post(multipartBody)
                .build()

            Log.d(TAG, "Uploading photo slot ${slotIndex + 1} (${compressedBytes.size} bytes) directly to ImgBB...")

            // 5. Execute network call
            val response = httpClient.newCall(request).execute()
            val responseCode = response.code
            val responseBody = response.body?.string() ?: ""

            Log.d(TAG, "ImgBB API responded with HTTP $responseCode")

            if (!response.isSuccessful) {
                val errorMsg = try {
                    val root = JSONObject(responseBody)
                    val errObj = root.optJSONObject("error")
                    errObj?.optString("message", "") ?: root.optString("error", "")
                } catch (_: Exception) {
                    ""
                }

                val diagnosticMsg = when (responseCode) {
                    400 -> if (errorMsg.isNotBlank()) "Upload failed: $errorMsg" else "Upload failed (HTTP 400): Image format invalid or empty."
                    401, 403 -> "Authentication Error: Invalid or expired upload key. Please check your upload server key."
                    413 -> "File Too Large: The selected image exceeds maximum upload size."
                    429 -> "Rate Limited: Too many uploads. Please wait a minute and retry."
                    500, 502, 503, 504 -> "Server Error ($responseCode): Photo upload server is temporarily unavailable. Please retry."
                    else -> if (errorMsg.isNotBlank()) "Upload failed: $errorMsg" else "Photo upload failed with HTTP $responseCode."
                }

                Log.e(TAG, "Upload failed [HTTP $responseCode]: $diagnosticMsg | Body: $responseBody")
                return@withContext Result.failure(Exception(diagnosticMsg))
            }

            // 6. Parse response JSON from ImgBB using testable parser
            val parseResult = parseImgbbResponse(responseBody, slotIndex)
            return@withContext parseResult


        } catch (e: Exception) {
            Log.e(TAG, "Exception uploading image to ImgBB: ${e.message}", e)
            val friendlyMsg = when (e) {
                is java.net.UnknownHostException -> {
                    "DNS Error: Unable to reach photo upload server. Please check your internet connection."
                }
                is java.net.ConnectException -> {
                    "Connection Error: Could not connect to photo upload server. Please check your internet connection."
                }
                is java.net.SocketTimeoutException -> {
                    "Timeout Error: Photo upload connection timed out. Please check your internet connection and try again."
                }
                is javax.net.ssl.SSLException -> {
                    "SSL Error: Secure connection to upload server could not be established."
                }
                else -> {
                    e.localizedMessage ?: "Upload failed unexpectedly (${e.javaClass.simpleName})"
                }
            }
            Result.failure(Exception(friendlyMsg))
        }
    }

    /**
     * Reads image URI, preserves EXIF orientation, and compresses down to crisp JPEG under 1.5MB.
     */
    private fun compressImageUri(context: Context, uri: Uri): ByteArray? {
        return try {
            // Read raw bytes once from ContentResolver to avoid multi-open permission drops
            val rawBytes = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes()
            } ?: return null

            if (rawBytes.isEmpty()) return null

            // Inspect EXIF orientation
            val orientation = try {
                ByteArrayInputStream(rawBytes).use { bais ->
                    val exif = ExifInterface(bais)
                    exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                }
            } catch (_: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }

            val rotationDegrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }

            // Decode bounds
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, boundsOptions)

            // Downscale to max dimension 1600px to ensure crisp display and fast upload
            var sampleSize = 1
            val maxDimension = 1600
            val maxSide = maxOf(boundsOptions.outWidth, boundsOptions.outHeight)
            while (maxSide / (sampleSize * 2) >= maxDimension) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val decodedBitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOptions)
                ?: return null

            // Apply EXIF rotation if needed
            val orientedBitmap = if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                val rotated = Bitmap.createBitmap(
                    decodedBitmap, 0, 0,
                    decodedBitmap.width, decodedBitmap.height,
                    matrix, true
                )
                if (rotated != decodedBitmap) {
                    decodedBitmap.recycle()
                }
                rotated
            } else {
                decodedBitmap
            }

            // Compress to standard JPEG under 1.5MB
            var quality = 85
            val outputStream = ByteArrayOutputStream()
            orientedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)

            var resultBytes = outputStream.toByteArray()
            while (resultBytes.size > 1_500_000 && quality > 50) {
                quality -= 15
                outputStream.reset()
                orientedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
                resultBytes = outputStream.toByteArray()
            }

            orientedBitmap.recycle()
            resultBytes
        } catch (e: Exception) {
            Log.e(TAG, "Error processing and compressing image: ${e.message}", e)
            null
        }
    }
}


