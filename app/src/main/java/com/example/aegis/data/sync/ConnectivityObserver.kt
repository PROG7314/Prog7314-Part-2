package com.example.aegis.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/**
 * "Do we have internet right now?" as a StateFlow, plus a callback for the moment it comes back.
 * Needs the ACCESS_NETWORK_STATE permission in the manifest.
 *
 * Online means the network can reach the internet AND Android has validated it, so a Wi-Fi network
 * with no internet behind it counts as offline.
 */
class ConnectivityObserver(context: Context) {

    private val manager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _online = MutableStateFlow(readOnlineNow())
    val online: StateFlow<Boolean> = _online.asStateFlow()

    val isOnline: Boolean get() = _online.value

    private var started = false

    /** Call once (Application.onCreate). [onReconnect] runs each time we go from offline to online. */
    fun start(onReconnect: () -> Unit) {
        if (started) return
        started = true

        manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                setOnline(caps.isUsable(), onReconnect)
            }

            override fun onLost(network: Network) {
                setOnline(false, onReconnect)
            }
        })
    }

    private fun setOnline(now: Boolean, onReconnect: () -> Unit) {
        val was = _online.getAndUpdate { now }
        if (now && !was) onReconnect()
    }

    private fun readOnlineNow(): Boolean {
        val network = manager.activeNetwork ?: return false
        return manager.getNetworkCapabilities(network)?.isUsable() == true
    }

    private fun NetworkCapabilities.isUsable() =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}