package com.hermes.mobile.domain

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings

object VpnGuard {
    fun isVpnTransportActive(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        return manager.allNetworks.any { network ->
            manager.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }
    }

    fun requestTailscaleConnect(context: Context): Boolean {
        return runCatching {
            context.packageManager.getApplicationInfo(TAILSCALE_PACKAGE, 0)
            context.sendBroadcast(
                Intent(TAILSCALE_CONNECT_ACTION)
                    .setPackage(TAILSCALE_PACKAGE)
                    .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            )
            true
        }.getOrDefault(false)
    }

    fun openTailscaleOrVpnSettings(context: Context) {
        context.packageManager.getLaunchIntentForPackage(TAILSCALE_PACKAGE)?.let { launchIntent ->
            runCatching { context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            return
        }
        runCatching { context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    const val TAILSCALE_PACKAGE = "com.tailscale.ipn"
    const val TAILSCALE_CONNECT_ACTION = "com.tailscale.ipn.CONNECT_VPN"
}
