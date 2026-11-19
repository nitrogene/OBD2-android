package com.nitrogene.obd2.transport.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import com.nitrogene.obd2.diag.AppDiagLog
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

    private var activeCallback: ConnectivityManager.NetworkCallback? = null

    /**
     * Returns a [SocketFactory] bound to the current Wi-Fi [Network], or requests [ssid] via
     * [WifiNetworkSpecifier] (Android 10+) if no active non-VPN Wi-Fi network is present.
     */
    suspend fun acquireWifiSocketFactory(
        ssid: String = WifiConstants.DEFAULT_SSID,
        passphrase: String = WifiConstants.DEFAULT_PASSWORD,
        timeoutMs: Long = 15_000L,
    ): Pair<Network, SocketFactory> {
        val cm = connectivityManager
        val wifiEnabled = context.getSystemService(WifiManager::class.java)?.isWifiEnabled ?: true
        if (!wifiEnabled) {
            AppDiagLog.log("WIFI", "Wi-Fi adapter is OFF")
            throw IllegalStateException("Wi-Fi is turned OFF on the phone")
        }

        findActiveWifiNetwork(cm)?.let { network ->
            val lp = runCatching { cm.getLinkProperties(network) }.getOrNull()
            AppDiagLog.log("WIFI", "Bound active Wi-Fi network=$network iface=${lp?.interfaceName} addrs=${lp?.linkAddresses}")
            cm.bindProcessToNetwork(network)
            return network to network.socketFactory
        }

        val networksSummary = dumpAllNetworks(cm)
        AppDiagLog.log("WIFI", "No active non-VPN Wi-Fi in allNetworks: $networksSummary")

        val deferred = CompletableDeferred<Network>()
        val requestBuilder = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ssid.isNotBlank()) {
            val specifier = WifiNetworkSpecifier.Builder()
                .setSsid(ssid)
                .apply {
                    if (passphrase.isNotEmpty()) {
                        setWpa2Passphrase(passphrase)
                    }
                }
                .build()
            requestBuilder.setNetworkSpecifier(specifier)
            AppDiagLog.log("WIFI", "Requesting SoftAP via WifiNetworkSpecifier ssid=$ssid")
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val lp = runCatching { cm.getLinkProperties(network) }.getOrNull()
                AppDiagLog.log("WIFI", "requestNetwork onAvailable=$network iface=${lp?.interfaceName} addrs=${lp?.linkAddresses}")
                deferred.complete(network)
            }

            override fun onUnavailable() {
                AppDiagLog.log("WIFI", "requestNetwork onUnavailable()")
                deferred.completeExceptionally(
                    IllegalStateException("Wi-Fi network '$ssid' unavailable or cancelled"),
                )
            }
        }

        activeCallback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        activeCallback = callback
        cm.requestNetwork(requestBuilder.build(), callback)
        return try {
            val network = withTimeout(timeoutMs) { deferred.await() }
            cm.bindProcessToNetwork(network)
            network to network.socketFactory
        } catch (t: Throwable) {
            runCatching { cm.unregisterNetworkCallback(callback) }
            if (activeCallback === callback) activeCallback = null
            throw t
        }
    }

    /**
     * Clears any process-wide Wi-Fi network binding and specifier callback when disconnecting.
     */
    fun releaseBinding() {
        runCatching { connectivityManager.bindProcessToNetwork(null) }
        activeCallback?.let { runCatching { connectivityManager.unregisterNetworkCallback(it) } }
        activeCallback = null
    }

    @Suppress("DEPRECATION")
    private fun findActiveWifiNetwork(cm: ConnectivityManager): Network? {
        return cm.allNetworks.firstOrNull { network ->
            val caps = cm.getNetworkCapabilities(network) ?: return@firstOrNull false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        }
    }

    @Suppress("DEPRECATION")
    private fun dumpAllNetworks(cm: ConnectivityManager): String {
        return cm.allNetworks.joinToString("; ") { net ->
            val caps = cm.getNetworkCapabilities(net)
            val lp = runCatching { cm.getLinkProperties(net) }.getOrNull()
            val wifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            val cell = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
            val vpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            "$net(${lp?.interfaceName},wifi=$wifi,cell=$cell,vpn=$vpn,addrs=${lp?.linkAddresses})"
        }.ifEmpty { "none" }
    }
}
