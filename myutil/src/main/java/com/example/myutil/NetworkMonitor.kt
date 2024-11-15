package com.example.myutil

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

class NetworkMonitor private constructor(context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val networkCallback: ConnectivityManager.NetworkCallback

    private var mReconnect: ((Boolean) -> Unit)? = null
    fun reconnectCallback(init: (Boolean) -> Unit) = apply { this.mReconnect = init }

    companion object {

        @Volatile
        private var instance: NetworkMonitor? = null

        fun getInstance(context: Context? = null): NetworkMonitor {
            if (instance == null) {
                synchronized(NetworkMonitor::class) {
                    if (instance == null) {
                        instance = NetworkMonitor(context!!)
                    }
                }
            }
            return instance!!
        }
    }


    init {
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                mReconnect?.invoke(true)
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                mReconnect?.invoke(false)
            }
        }
    }

    fun register() {
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
    }

    fun unregister() {
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }

    fun release() {
        mReconnect = null
    }
}
