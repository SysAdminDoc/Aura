package com.freevibe.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Whether a rotation may call a wallpaper source or download a picture right now. */
enum class RotationNetworkAccess {
    ALLOWED,

    /** No usable connection. A wallpaper saved ahead of time still applies. */
    OFFLINE,

    /** Wi-Fi only is on and this connection is metered, so nothing is fetched or downloaded. */
    WAITING_FOR_WIFI,
}

internal data class RotationNetworkState(val connected: Boolean, val metered: Boolean)

internal fun rotationNetworkAccess(state: RotationNetworkState, wifiOnly: Boolean): RotationNetworkAccess = when {
    !state.connected -> RotationNetworkAccess.OFFLINE
    wifiOnly && state.metered -> RotationNetworkAccess.WAITING_FOR_WIFI
    else -> RotationNetworkAccess.ALLOWED
}

/**
 * Rotation work carries no WorkManager network constraint, so that a wallpaper saved ahead of time
 * can apply with the phone offline. The connection is checked here instead, by the worker, before
 * every source call and every download. That is also where the Wi-Fi only setting is enforced.
 */
@Singleton
class RotationNetworkGate internal constructor(
    private val state: () -> RotationNetworkState,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(state = { readRotationNetworkState(context) })

    fun access(wifiOnly: Boolean): RotationNetworkAccess = rotationNetworkAccess(state(), wifiOnly)
}

/** Nothing is known about the network: let the rotation try, and keep Wi-Fi only on the safe side. */
private val UNKNOWN_NETWORK = RotationNetworkState(connected = true, metered = true)

/**
 * Connected means what the WorkManager constraint used to mean: an active network that has internet
 * access and has been validated. A captive portal or a network Android has blocked for this app
 * reads as offline.
 */
private fun readRotationNetworkState(context: Context): RotationNetworkState = runCatching {
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
        ?: return@runCatching UNKNOWN_NETWORK
    val capabilities = connectivity.activeNetwork?.let(connectivity::getNetworkCapabilities)
    RotationNetworkState(
        connected = capabilities != null &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
        metered = connectivity.isActiveNetworkMetered,
    )
}.getOrDefault(UNKNOWN_NETWORK)
