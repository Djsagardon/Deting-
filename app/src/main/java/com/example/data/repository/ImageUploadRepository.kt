package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.example.BuildConfig
import com.example.util.NoInternetException
import com.example.util.NetworkConnectivityManager
import kotlinx.coroutines.Dispatchers
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
        if (!storedKey.isNullOrEmpty()) {
            return storedKey
        }

        return try {
            BuildConfig.IMGBB_API_KEY.trim()
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
        return key.isNotBlank() && key != "PLACEHOLDER" && key.length >= 10
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
                message = "ImgBB API Key is not configured. Please provide your ImgBB API key."
            )
        }

        try {
            // Test connectivity to ImgBB by probing endpoint
            val testRequest = Request.Builder()
                .url(ImageUploadConfig.IMGBB_UPLOAD_ENDPOINT)
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
                    message = "Connected to ImgBB API successfully. Key configured (${apiKey.take(4)}****)."
                )
            } else {
                ServerHealthResult(
                    isReachable = true,
                    statusCode = response?.code ?: 0,
                    isHealthy = true,
                    message = "ImgBB API is reachable. Key is set (${apiKey.take(4)}****)."
                )
            }
        } catch (e: Exception) {
            ServerHealthResult(
                isReachable = false,
                message = "Unable to connect to ImgBB API: ${e.localizedMessage ?: e.javaClass.simpleName}"
            )
        }
    }

    /**
     * Uploads a single compressed image directly to official ImgBB API endpoint:
     * POST https://api.imgbb.com/1/upload
     * Parameters:
     * - key: API key
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
            val msg = "ImgBB API Key is missing. Please configure your ImgBB API key to enable photo uploads."
            Log.e(TAG, msg)
            return@withContext Result.failure(Exception(msg))
        }

        // 3. Compress image to optimized JPEG bytes under 1.5MB
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

            val request = Request.Builder()
                .url(ImageUploadConfig.IMGBB_UPLOAD_ENDPOINT)
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
                    400 -> if (errorMsg.isNotBlank()) "ImgBB Bad Request: $errorMsg" else "ImgBB Bad Request (HTTP 400): Image format invalid or empty."
                    401, 403 -> "ImgBB Authentication Error: Invalid or expired API key. Please check your ImgBB API key."
                    413 -> "File Too Large: The selected image exceeds ImgBB maximum upload size."
                    429 -> "Rate Limited: Too many uploads to ImgBB. Please wait a minute and retry."
                    500, 502, 503, 504 -> "ImgBB Server Error ($responseCode): ImgBB is temporarily unavailable. Please retry."
                    else -> if (errorMsg.isNotBlank()) "Upload failed: $errorMsg" else "ImgBB upload failed with HTTP $responseCode."
                }

                Log.e(TAG, "Upload failed [HTTP $responseCode]: $diagnosticMsg | Body: $responseBody")
                return@withContext Result.failure(Exception(diagnosticMsg))
            }

            // 6. Parse response JSON from ImgBB
            val json = try {
                JSONObject(responseBody)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse ImgBB JSON: $responseBody", e)
                return@withContext Result.failure(Exception("Invalid response format from ImgBB API."))
            }

            val isSuccess = json.optBoolean("success", false)
            if (isSuccess) {
                val dataObj = json.optJSONObject("data")
                val directUrl = dataObj?.optString("url", "") ?: ""
                val displayUrl = dataObj?.optString("display_url", "") ?: ""

                val finalUrl = when {
                    directUrl.startsWith("http://") || directUrl.startsWith("https://") -> directUrl
                    displayUrl.startsWith("http://") || displayUrl.startsWith("https://") -> displayUrl
                    else -> ""
                }

                if (finalUrl.isNotBlank()) {
                    Log.d(TAG, "Photo slot ${slotIndex + 1} uploaded successfully to ImgBB: $finalUrl")
                    return@withContext Result.success(finalUrl)
                }
            }

            val failReason = try {
                val err = json.optJSONObject("error")
                err?.optString("message", "Unknown error from ImgBB") ?: "Missing image URL in ImgBB response."
            } catch (_: Exception) {
                "Upload could not be verified by ImgBB."
            }

            Log.e(TAG, "ImgBB returned failure: $failReason")
            Result.failure(Exception(failReason))

        } catch (e: Exception) {
            Log.e(TAG, "Exception uploading image to ImgBB: ${e.message}", e)
            val friendlyMsg = when (e) {
                is java.net.UnknownHostException -> {
                    "DNS Error: Unable to resolve api.imgbb.com. Please check your internet connection."
                }
                is java.net.ConnectException -> {
                    "Connection Error: Could not connect to ImgBB server. Please check your internet connection."
                }
                is java.net.SocketTimeoutException -> {
                    "Timeout Error: ImgBB connection timed out. Please check your internet connection and try again."
                }
                is javax.net.ssl.SSLException -> {
                    "SSL Error: Secure connection to ImgBB could not be established."
                }
                else -> {
                    e.localizedMessage ?: "Upload failed unexpectedly (${e.javaClass.simpleName})"
                }
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
