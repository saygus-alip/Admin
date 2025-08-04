package com.alip.admin.Internet

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper

class NetworkConnectivityObserver(context: Context) {

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var networkStateChangeListener: ((Boolean) -> Unit)? = null

    fun register(listener: (Boolean) -> Unit) {
        this.networkStateChangeListener = listener
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Handler(Looper.getMainLooper()).post {
                    listener(true)
                }
            }

            override fun onLost(network: Network) {
                Handler(Looper.getMainLooper()).post {
                    listener(false)
                }
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback!!)
    }

    fun unregister() {
        networkCallback?.let {
            connectivityManager.unregisterNetworkCallback(it)
        }
    }
}