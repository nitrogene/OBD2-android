package com.nitrogene.obd2.transport.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.net.SocketFactory

/**
 * Resolves the active Android Wi-Fi [Network] and binds process/socket traffic to it so
 * `192.168.42.1` is never routed over cellular data when `OBD2-Scanner` has no internet access.
 */
class WifiNetworkBinder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val connectivityManager: ConnectivityManager
        get() = context.getSystemService(ConnectivityManager::class.java)

    /**
     * Returns a [SocketFactory] bound to the current Wi-Fi [Network], or awaits one up to [timeoutMs].
     */
    suspend fun acquireWifiSocketFactory(timeoutMs: Long = 5_000L): Pair<Network, SocketFactory> {
        val cm = connectivityManager
        findActiveWifiNetwork(cm)?.let { network ->
            cm.bindProcessToNetwork(network)
            return network to network.socketFactory
        }

        val deferred = CompletableDeferred<Network>()
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                deferred.complete(network)
            }
        }

        cm.requestNetwork(request, callback)
        return try {
            val network = withTimeout(timeoutMs) { deferred.await() }
            cm.bindProcessToNetwork(network)
            network to network.socketFactory
        } finally {
            runCatching { cm.unregisterNetworkCallback(callback) }
        }
    }

    /**
     * Clears any process-wide Wi-Fi network binding when the transport disconnects.
     */
    fun releaseBinding() {
        runCatching { connectivityManager.bindProcessToNetwork(null) }
    }

    @Suppress("DEPRECATION")
    private fun findActiveWifiNetwork(cm: ConnectivityManager): Network? {
        return cm.allNetworks.firstOrNull { network ->
            val caps = cm.getNetworkCapabilities(network) ?: return@firstOrNull false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        }
    }
}
