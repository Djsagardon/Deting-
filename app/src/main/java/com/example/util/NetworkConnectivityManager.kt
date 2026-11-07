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
                Log.d(TAG, "Network became available. Probing live internet...")
                scope.launch {
                    val hasLiveInternet = probeLiveInternet()
                    _isOnline.value = hasLiveInternet
                    Log.d(TAG, "Live internet probe result onAvailable: $hasLiveInternet")
                }
            }

            override fun onLost(network: Network) {
                Log.w(TAG, "Network connection lost.")
                _isOnline.value = false
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                val isOnlineNow = hasInternet && isValidated
                Log.d(TAG, "Capabilities changed: hasInternet=$hasInternet, isValidated=$isValidated")

                if (isOnlineNow != _isOnline.value) {
                    if (hasInternet && !isValidated) {
                        // Might be waiting for validation or captive portal, do active probe
                        scope.launch {
                            _isOnline.value = probeLiveInternet()
                        }
                    } else {
                        _isOnline.value = isOnlineNow
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
                _isOnline.value = live
            }
        } else {
            _isOnline.value = true
        }
        return _isOnline.value
    }

    /**
     * Actively tests if HTTP packets reach external servers within 2.5 seconds.
     * Prevents captive portal or disconnected router from tricking the app into thinking it is online.
     */
    suspend fun probeLiveInternet(): Boolean = withContext(Dispatchers.IO) {
        val probeUrls = listOf(
            "https://clients3.google.com/generate_204",
            "https://www.google.com/generate_204"
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
                if (code == 204 || code == 200) {
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
