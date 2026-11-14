package com.example.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.HttpURLConnection
import java.net.URL

object NetworkConnectivityManager {

    private const val TAG = "BondlyNetManager"

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private var appContext: Context? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Initializes the network monitor with the application context.
     * Safe to call multiple times; idempotently binds to the system ConnectivityManager.
     */
    fun initialize(context: Context) {
        if (appContext != null) {
            // Already initialized; update instantaneous status
            checkConnectivity(forceProbe = false)
            return
        }

        val appCtx = context.applicationContext
        appContext = appCtx

        val cm = appCtx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        connectivityManager = cm

        if (cm == null) {
            Log.w(TAG, "ConnectivityManager unavailable on this device.")
            return
        }

        // Initial snapshot check
        val initialStatus = evaluateActiveNetwork(cm)
        _isOnline.value = initialStatus

        // Register reactive network callback
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val cmRef = connectivityManager
                val immediateOnline = if (cmRef != null) evaluateActiveNetwork(cmRef) else true
                if (immediateOnline) {
                    _isOnline.value = true
                }
                Log.d(TAG, "Network became available (immediateOnline=$immediateOnline). Probing live internet...")
                scope.launch {
                    val hasLiveInternet = probeLiveInternet()
                    val latestCm = connectivityManager
                    val validatedBySystem = latestCm?.activeNetwork?.let { net ->
                        latestCm.getNetworkCapabilities(net)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
                    } ?: false
                    _isOnline.value = hasLiveInternet || validatedBySystem
                    Log.d(TAG, "Live internet probe result onAvailable: probe=$hasLiveInternet validated=$validatedBySystem")
                }
            }

            override fun onLost(network: Network) {
                val cmRef = connectivityManager
                val stillHasActive = cmRef != null && evaluateActiveNetwork(cmRef)
                Log.w(TAG, "Network connection lost (stillHasActive=$stillHasActive).")
                _isOnline.value = stillHasActive
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                Log.d(TAG, "Capabilities changed: hasInternet=$hasInternet, isValidated=$isValidated")

                if (hasInternet && isValidated) {
                    _isOnline.value = true
                } else if (!hasInternet) {
                    _isOnline.value = false
                } else {
                    // Has INTERNET capability, waiting for system validation
                    scope.launch {
                        val live = probeLiveInternet()
                        val latestCm = connectivityManager
                        val nowValidated = latestCm?.activeNetwork?.let { net ->
                            latestCm.getNetworkCapabilities(net)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
                        } ?: false
                        _isOnline.value = live || nowValidated
                    }
                }
            }
        }

        networkCallback = callback
        try {
            cm.registerNetworkCallback(request, callback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register NetworkCallback: ${e.message}")
        }
    }

    /**
     * Synchronous check of the current connectivity state.
     */
    fun isConnected(): Boolean {
        val cm = connectivityManager ?: appContext?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        return if (cm != null) evaluateActiveNetwork(cm) else _isOnline.value
    }

    /**
     * Evaluates active network capabilities from ConnectivityManager.
     */
    private fun evaluateActiveNetwork(cm: ConnectivityManager): Boolean {
        return try {
            val activeNetwork = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Actively checks connectivity and optionally executes a live HTTP probe.
     */
    fun checkConnectivity(forceProbe: Boolean = false): Boolean {
        val cm = connectivityManager ?: return _isOnline.value
        val hasActiveNet = evaluateActiveNetwork(cm)
        if (!hasActiveNet) {
            _isOnline.value = false
            return false
        }

        if (forceProbe) {
            scope.launch {
                val live = probeLiveInternet()
                val nowValidated = cm.activeNetwork?.let { net ->
                    cm.getNetworkCapabilities(net)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
                } ?: false
                _isOnline.value = live || nowValidated
            }
        } else {
            _isOnline.value = true
        }
        return _isOnline.value
    }

    /**
     * Actively tests if HTTP packets reach external servers within 2.5 seconds.
     * Includes Google 204 and Firestore endpoint checks so restricted networks still allow Firebase.
     */
    suspend fun probeLiveInternet(): Boolean = withContext(Dispatchers.IO) {
        val probeUrls = listOf(
            "https://clients3.google.com/generate_204",
            "https://www.google.com/generate_204",
            "https://firestore.googleapis.com"
        )
        for (urlString in probeUrls) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(urlString)
                connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 2500
                connection.readTimeout = 2500
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                connection.connect()

                val code = connection.responseCode
                if (code in 200..499) {
                    return@withContext true
                }
            } catch (_: Exception) {
                // Try fallback url
            } finally {
                connection?.disconnect()
            }
        }
        false
    }
}
