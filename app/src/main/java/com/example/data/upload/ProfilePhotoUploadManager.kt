package com.example.data.upload

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.repository.ImageUploadRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed class PhotoSlotState {
    data object Empty : PhotoSlotState()
    data class Selected(val uri: Uri) : PhotoSlotState()
    data class Uploading(val uri: Uri, val progressPercent: Int = 0) : PhotoSlotState()
    data class Success(val uri: Uri?, val remoteUrl: String) : PhotoSlotState()
    data class Failed(val uri: Uri, val errorMessage: String) : PhotoSlotState()

    val localUri: Uri?
        get() = when (this) {
            is Empty -> null
            is Selected -> uri
            is Uploading -> uri
            is Success -> uri
            is Failed -> uri
        }

    val displayUrl: String?
        get() = when (this) {
            is Success -> remoteUrl
            is Uploading -> uri.toString()
            is Selected -> uri.toString()
            is Failed -> uri.toString()
            is Empty -> null
        }
}

class ProfilePhotoUploadManager(
    private val imageRepo: ImageUploadRepository = ImageUploadRepository()
) {
    companion object {
        private const val TAG = "PhotoUploadManager"
        const val MAX_SLOTS = 5
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val uploadMutex = Mutex()
    private var activeUploadJob: Job? = null

    private val _slots = MutableStateFlow<List<PhotoSlotState>>(
        List(MAX_SLOTS) { PhotoSlotState.Empty }
    )
    val slots: StateFlow<List<PhotoSlotState>> = _slots.asStateFlow()

    private val _isSequentialUploading = MutableStateFlow(false)
    val isSequentialUploading: StateFlow<Boolean> = _isSequentialUploading.asStateFlow()

    private val _activeSlotIndex = MutableStateFlow<Int?>(-1)
    val activeSlotIndex: StateFlow<Int?> = _activeSlotIndex.asStateFlow()

    /**
     * Initializes slot states with existing profile URLs if present.
     */
    fun initializeFromExisting(urls: List<String>) {
        val current = _slots.value.toMutableList()
        var updated = false
        for (i in 0 until MAX_SLOTS) {
            val existingUrl = urls.getOrNull(i)
            if (!existingUrl.isNullOrEmpty() && current[i] is PhotoSlotState.Empty) {
                current[i] = PhotoSlotState.Success(null, existingUrl)
                updated = true
            }
        }
        if (updated) {
            _slots.value = current
        }
    }

    /**
     * Assigns multiple selected images from a single gallery operation into the 5 slots.
     * Fills empty or failed slots sequentially without overwriting already uploaded photos.
     */
    fun onMultiplePhotosSelected(context: Context, uris: List<Uri>, userId: String = "") {
        if (uris.isEmpty()) return

        val current = _slots.value.toMutableList()
        val distinctUris = uris.distinctBy { it.toString() }

        var uriIndex = 0

        // 1. If user selected 5 photos at once, assign them directly to slots 0..4
        if (distinctUris.size == MAX_SLOTS) {
            for (i in 0 until MAX_SLOTS) {
                // If slot is already successful with the exact same URI, keep it
                val existing = current[i]
                if (existing is PhotoSlotState.Success && existing.uri == distinctUris[i]) {
                    continue
                }
                current[i] = PhotoSlotState.Selected(distinctUris[i])
            }
        } else {
            // 2. Otherwise fill empty or failed slots first
            for (i in 0 until MAX_SLOTS) {
                if (uriIndex >= distinctUris.size) break
                val slot = current[i]
                if (slot is PhotoSlotState.Empty || slot is PhotoSlotState.Failed) {
                    current[i] = PhotoSlotState.Selected(distinctUris[uriIndex])
                    uriIndex++
                }
            }

            // 3. If there are still selected URIs, replace un-uploaded Selected slots
            for (i in 0 until MAX_SLOTS) {
                if (uriIndex >= distinctUris.size) break
                val slot = current[i]
                if (slot is PhotoSlotState.Selected) {
                    current[i] = PhotoSlotState.Selected(distinctUris[uriIndex])
                    uriIndex++
                }
            }
        }

        _slots.value = current
        triggerSequentialUpload(context.applicationContext, userId)
    }

    /**
     * Sets a single photo into a specific slot and triggers sequential upload.
     */
    fun onSinglePhotoSelected(context: Context, slotIndex: Int, uri: Uri, userId: String = "") {
        if (slotIndex !in 0 until MAX_SLOTS) return

        val current = _slots.value.toMutableList()
        current[slotIndex] = PhotoSlotState.Selected(uri)
        _slots.value = current

        triggerSequentialUpload(context.applicationContext, userId)
    }

    /**
     * Retries uploading a specific failed slot without restarting other successful slots.
     */
    fun retrySlot(context: Context, slotIndex: Int, userId: String = "") {
        if (slotIndex !in 0 until MAX_SLOTS) return
        val current = _slots.value.toMutableList()
        val slot = current[slotIndex]
        if (slot is PhotoSlotState.Failed) {
            current[slotIndex] = PhotoSlotState.Selected(slot.uri)
            _slots.value = current
            triggerSequentialUpload(context.applicationContext, userId)
        }
    }

    /**
     * Clears a specific slot back to Empty.
     */
    fun clearSlot(slotIndex: Int) {
        if (slotIndex !in 0 until MAX_SLOTS) return
        val current = _slots.value.toMutableList()
        current[slotIndex] = PhotoSlotState.Empty
        _slots.value = current
    }

    /**
     * Starts or continues the background queue to upload images ONE BY ONE sequentially.
     */
    private fun triggerSequentialUpload(appContext: Context, userId: String) {
        if (activeUploadJob?.isActive == true) {
            // Already running sequentially
            return
        }

        activeUploadJob = scope.launch {
            uploadMutex.withLock {
                _isSequentialUploading.value = true
                try {
                    while (isActive) {
                        // Find the next slot waiting for upload
                        val currentSlots = _slots.value
                        val nextIndex = currentSlots.indexOfFirst { it is PhotoSlotState.Selected }
                        if (nextIndex == -1) {
                            // No more selected photos to upload
                            break
                        }

                        val selectedSlot = currentSlots[nextIndex] as PhotoSlotState.Selected
                        val uriToUpload = selectedSlot.uri

                        _activeSlotIndex.value = nextIndex
                        updateSlotState(nextIndex, PhotoSlotState.Uploading(uriToUpload, progressPercent = 0))

                        Log.d(TAG, "Starting sequential upload for slot $nextIndex: $uriToUpload")

                        // Perform the actual network upload via ImageUploadRepository
                        val result = imageRepo.uploadProfilePhoto(
                            appContext = appContext,
                            uri = uriToUpload,
                            userId = userId,
                            slotIndex = nextIndex
                        )

                        result.onSuccess { remoteUrl ->
                            Log.d(TAG, "Slot $nextIndex upload succeeded: $remoteUrl")
                            updateSlotState(nextIndex, PhotoSlotState.Success(uriToUpload, remoteUrl))
                        }.onFailure { err ->
                            Log.e(TAG, "Slot $nextIndex upload failed: ${err.message}")
                            updateSlotState(
                                nextIndex,
                                PhotoSlotState.Failed(
                                    uriToUpload,
                                    err.localizedMessage ?: "Upload failed. Tap to retry."
                                )
                            )
                        }
                    }
                } finally {
                    _activeSlotIndex.value = -1
                    _isSequentialUploading.value = false
                }
            }
        }
    }

    private fun updateSlotState(index: Int, state: PhotoSlotState) {
        val current = _slots.value.toMutableList()
        if (index in 0 until MAX_SLOTS) {
            current[index] = state
            _slots.value = current
        }
    }

    /**
     * Checks if all 5 slots have uploaded successfully.
     */
    fun allUploaded(): Boolean {
        val current = _slots.value
        return current.size == MAX_SLOTS && current.all { it is PhotoSlotState.Success }
    }

    /**
     * Returns the 5 successfully uploaded image URLs.
     */
    fun getSuccessfulUrls(): List<String> {
        return _slots.value.mapNotNull {
            (it as? PhotoSlotState.Success)?.remoteUrl
        }
    }

    /**
     * Counts how many slots are uploaded.
     */
    fun countSuccessful(): Int {
        return _slots.value.count { it is PhotoSlotState.Success }
    }
}
