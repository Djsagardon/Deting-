package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.example.BuildConfig
import com.example.util.BondlyErrorClassifier
import com.example.util.NoInternetException
import com.example.util.NetworkConnectivityManager
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
import java.net.URI
import java.util.concurrent.TimeUnit

sealed class ImageUploadResult {
    data class Success(val imageUrl: String, val deleteUrl: String? = null) : ImageUploadResult()
    data class Error(val message: String, val statusCode: Int? = null) : ImageUploadResult()
}

data class ServerHealthResult(
    val isReachable: Boolean,
    val statusCode: Int = 0,
    val isHealthy: Boolean = false,
    val hasImgbbKey: Boolean? = null,
    val message: String
)

object ImageUploadConfig {
    private const val PREFS_NAME = "bondly_image_upload_prefs"
    private const val KEY_WORKER_URL = "cloudflare_worker_endpoint_url"

    // Default placeholder
    const val DEFAULT_PLACEHOLDER_URL = "https://bondly-image-uploader.dating-buddy.workers.dev/upload"

    /**
     * Resolves the active worker URL:
     * 1. Dynamic override in SharedPreferences (set via in-app server settings)
     * 2. BuildConfig.CLOUDFLARE_WORKER_URL (injected via Gradle or GitHub Secrets)
     * 3. Fallback placeholder URL
     */
    fun getWorkerUrl(context: Context?): String {
        val stored = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.getString(KEY_WORKER_URL, null)
            ?.trim()
        if (!stored.isNullOrEmpty()) {
            return normalizeEndpointUrl(stored)
        }

        val buildConfigUrl = try {
            BuildConfig.CLOUDFLARE_WORKER_URL.trim()
        } catch (_: Exception) {
            ""
        }
        if (buildConfigUrl.isNotEmpty()) {
            return normalizeEndpointUrl(buildConfigUrl)
        }

        return DEFAULT_PLACEHOLDER_URL
    }

    /**
     * Updates the custom Cloudflare Worker URL in device storage.
     */
    fun setWorkerUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_WORKER_URL, normalizeEndpointUrl(url.trim()))
            .apply()
    }

    /**
     * Clears the custom URL override, reverting to build defaults.
     */
    fun resetWorkerUrl(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_WORKER_URL)
            .apply()
    }

    /**
     * Normalizes the endpoint URL to ensure proper protocol and trailing /upload path.
     */
    fun normalizeEndpointUrl(rawUrl: String): String {
        var url = rawUrl.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        url = url.trimEnd('/')
        if (!url.endsWith("/upload")) {
            url = "$url/upload"
        }
        return url
    }

    /**
     * Checks if a URL is still the unconfigured default placeholder.
     */
    fun isPlaceholderUrl(url: String): Boolean {
        val clean = url.lowercase().trim()
        return clean.contains("dating-buddy.workers.dev") ||
               clean.contains("<your-subdomain>") ||
               clean.contains("example.com") ||
               clean.isEmpty()
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
     * Gets the current effective worker URL.
     */
    fun getEffectiveWorkerUrl(appContext: Context? = context): String {
        return ImageUploadConfig.getWorkerUrl(appContext)
    }

    /**
     * Tests connectivity to the Cloudflare Worker `/health` endpoint.
     */
    suspend fun testHealth(appContext: Context): ServerHealthResult = withContext(Dispatchers.IO) {
        if (!NetworkConnectivityManager.isConnected()) {
            return@withContext ServerHealthResult(
                isReachable = false,
                message = "No internet connection. Please connect to the internet and try again."
            )
        }

        val endpointUrl = getEffectiveWorkerUrl(appContext)

        if (ImageUploadConfig.isPlaceholderUrl(endpointUrl)) {
            return@withContext ServerHealthResult(
                isReachable = false,
                message = "The upload server URL is currently set to a default placeholder ('$endpointUrl'). Please configure your actual deployed Cloudflare Worker URL."
            )
        }

        val healthUrl = try {
            val uri = URI(endpointUrl)
            val scheme = uri.scheme ?: "https"
            val host = uri.host ?: return@withContext ServerHealthResult(
                isReachable = false,
                message = "Invalid server URL: missing host"
            )
            val port = if (uri.port != -1) ":${uri.port}" else ""
            "$scheme://$host$port/health"
        } catch (e: Exception) {
            return@withContext ServerHealthResult(
                isReachable = false,
                message = "Invalid URL syntax: ${e.localizedMessage}"
            )
        }

        try {
            val request = Request.Builder()
                .url(healthUrl)
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val json = try { JSONObject(responseBody) } catch (_: Exception) { null }
                val hasImgbbKey = json?.optBoolean("hasImgbbKey", false)
                val status = json?.optString("status", "healthy") ?: "healthy"

                if (hasImgbbKey == false) {
                    ServerHealthResult(
                        isReachable = true,
                        statusCode = response.code,
                        isHealthy = false,
                        hasImgbbKey = false,
                        message = "Connected to Worker, but IMGBB_API_KEY secret is MISSING in Cloudflare Worker. Run 'wrangler secret put IMGBB_API_KEY'."
                    )
                } else {
                    ServerHealthResult(
                        isReachable = true,
                        statusCode = response.code,
                        isHealthy = true,
                        hasImgbbKey = true,
                        message = "Connected successfully! Storage service is online and ready ($status)."
                    )
                }
            } else {
                ServerHealthResult(
                    isReachable = true,
                    statusCode = response.code,
                    isHealthy = false,
                    message = "Server reached at /health, but returned HTTP ${response.code}."
                )
            }
        } catch (e: Exception) {
            val detail = when (e) {
                is java.net.UnknownHostException -> {
                    "DNS resolution failed: Host '${e.message}' not found. Make sure your Cloudflare Worker is published to this subdomain."
                }
                is java.net.ConnectException -> "Connection refused: Server is not responding on this address."
                is java.net.SocketTimeoutException -> "Connection timed out after 30 seconds."
                is javax.net.ssl.SSLException -> "SSL/TLS handshake failed for ${healthUrl}."
                else -> e.localizedMessage ?: "Network connection failed"
            }
            ServerHealthResult(
                isReachable = false,
                message = detail
            )
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
        if (!NetworkConnectivityManager.isConnected()) {
            return@withContext Result.failure(NoInternetException("No internet connection. Please connect to the internet and try again."))
        }

        val workerUrl = getEffectiveWorkerUrl(appContext)

        // 1. Check if the worker URL is unconfigured
        if (ImageUploadConfig.isPlaceholderUrl(workerUrl)) {
            val msg = "Upload server is not configured. Tap the Settings icon above to enter your deployed Cloudflare Worker URL."
            Log.e(TAG, "Worker URL placeholder check failed: $workerUrl")
            return@withContext Result.failure(Exception(msg))
        }

        // 2. Authenticate user with Firebase
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser
        if (currentUser == null) {
            val msg = "Authentication required: Please sign in to your Bondly account before uploading photos."
            Log.e(TAG, msg)
            return@withContext Result.failure(Exception(msg))
        }

        // 3. Retrieve fresh Firebase ID Token (forces live network verification)
        val idToken = try {
            currentUser.getIdToken(true).await().token ?: ""
        } catch (e: Exception) {
            Log.w(TAG, "Failed to retrieve live Firebase ID token: ${e.message}")
            if (e is com.google.firebase.FirebaseNetworkException) {
                return@withContext Result.failure(NoInternetException("No internet connection. Please connect to the internet and try again."))
            }
            ""
        }
        if (idToken.isEmpty()) {
            val msg = "Could not obtain secure authentication token. Please check your internet connection."
            Log.e(TAG, msg)
            return@withContext Result.failure(Exception(msg))
        }

        // 4. Compress image to optimized JPEG bytes under 1.5MB
        val compressedBytes = compressImageUri(appContext, uri)
            ?: return@withContext Result.failure(Exception("Could not read or process image file."))

        try {
            // 5. Build multipart request body
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

            val request = Request.Builder()
                .url(workerUrl)
                .header("Authorization", "Bearer $idToken")
                .header("Accept", "application/json")
                .post(multipartBody)
                .build()

            Log.d(TAG, "Sending photo upload to: $workerUrl (size: ${compressedBytes.size} bytes)")

            // 6. Execute network call
            val response = httpClient.newCall(request).execute()
            val responseCode = response.code
            val responseBody = response.body?.string() ?: ""

            Log.d(TAG, "Worker responded with HTTP $responseCode")

            if (!response.isSuccessful) {
                val serverErrorText = try {
                    JSONObject(responseBody).optString("error", "")
                } catch (_: Exception) {
                    ""
                }

                val diagnosticMsg = when (responseCode) {
                    400 -> if (serverErrorText.isNotBlank()) serverErrorText else "Bad Request (HTTP 400): Image format invalid or file corrupted."
                    401 -> if (serverErrorText.isNotBlank()) serverErrorText else "Unauthorized (HTTP 401): Authentication session was rejected by the server."
                    403 -> "Forbidden (HTTP 403): You do not have permission to upload photos."
                    404 -> "Endpoint Not Found (HTTP 404): Check that your Worker URL ends with /upload."
                    413 -> "File Too Large (HTTP 413): The image exceeds the maximum allowed size (10 MB)."
                    429 -> "Rate Limited (HTTP 429): Too many uploads. Please wait a minute before trying again."
                    500 -> if (serverErrorText.isNotBlank()) serverErrorText else "Server Error (HTTP 500): Storage server configuration issue. Please check server settings."
                    502 -> if (serverErrorText.isNotBlank()) serverErrorText else "Server Error (HTTP 502): Storage service rejected the upload. Please verify server settings."
                    503 -> "Service Unavailable (HTTP 503): Upload server temporarily unavailable."
                    504 -> "Gateway Timeout (HTTP 504): The upstream storage service timed out."
                    else -> if (serverErrorText.isNotBlank()) serverErrorText else "Upload failed with HTTP $responseCode."
                }

                Log.e(TAG, "Upload failed [HTTP $responseCode]: $diagnosticMsg | Body: $responseBody")
                return@withContext Result.failure(Exception(diagnosticMsg))
            }

            // 7. Parse response JSON
            val json = try {
                JSONObject(responseBody)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse JSON response: $responseBody", e)
                return@withContext Result.failure(Exception("Invalid server response: Expected JSON format."))
            }

            if (json.optBoolean("success", false)) {
                val imageUrl = json.optString("url")
                if (imageUrl.isNotBlank() && (imageUrl.startsWith("http://") || imageUrl.startsWith("https://"))) {
                    Log.d(TAG, "Photo slot $slotIndex uploaded successfully: $imageUrl")
                    return@withContext Result.success(imageUrl)
                }
            }

            val failReason = json.optString("error", "Missing image URL in server response.")
            Log.e(TAG, "Server returned success=false: $failReason")
            Result.failure(Exception(failReason))

        } catch (e: Exception) {
            Log.e(TAG, "Exception uploading image via worker: ${e.message}", e)
            val friendlyMsg = when (e) {
                is java.net.UnknownHostException -> {
                    "DNS Error: Server host '${e.message}' could not be resolved. Please verify your Cloudflare Worker URL in Server Settings."
                }
                is java.net.ConnectException -> {
                    "Connection Error: Could not connect to '$workerUrl'. Check your internet connection or verify the server is running."
                }
                is java.net.SocketTimeoutException -> {
                    "Timeout Error: Upload timed out. Check your internet connection and try again."
                }
                is javax.net.ssl.SSLException -> {
                    "SSL/Security Error: Failed to establish secure connection to '$workerUrl'."
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
