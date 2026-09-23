package com.example.adblock

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.quicksettings.TileService

enum class AdBlockStatus {
    OFF,
    STARTING,
    ON,
    PERMISSION_REQUIRED,
    ERROR
}

data class AdBlockSnapshot(
    val status: AdBlockStatus,
    val detail: String?
)

/** Stores the last state reported by the VPN service for the activity and tile. */
object AdBlockStatusStore {
    const val ACTION_STATUS_CHANGED = "com.example.adblock.STATUS_CHANGED"

    private const val PREFERENCES = "adblock_status"
    private const val STATUS = "status"
    private const val DETAIL = "detail"

    fun snapshot(context: Context): AdBlockSnapshot {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val status = preferences.getString(STATUS, AdBlockStatus.OFF.name)
            ?.let { runCatching { AdBlockStatus.valueOf(it) }.getOrNull() }
            ?: AdBlockStatus.OFF
        return AdBlockSnapshot(status, preferences.getString(DETAIL, null))
    }

    fun set(context: Context, status: AdBlockStatus, detail: String? = null) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(STATUS, status.name)
            .putString(DETAIL, detail)
            .apply()

        context.sendBroadcast(Intent(ACTION_STATUS_CHANGED).setPackage(context.packageName))
        TileService.requestListeningState(
            context,
            ComponentName(context, AdBlockTileService::class.java)
        )
    }
}

object VpnControl {
    const val ACTION_START = "com.example.adblock.action.START"
    const val ACTION_STOP = "com.example.adblock.action.STOP"

    fun hasPermission(context: Context): Boolean = android.net.VpnService.prepare(context) == null

    fun start(context: Context) {
        AdBlockStatusStore.set(context, AdBlockStatus.STARTING)
        val intent = Intent(context, AdBlockVpnService::class.java).setAction(ACTION_START)
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (exception: RuntimeException) {
            AdBlockStatusStore.set(
                context,
                AdBlockStatus.ERROR,
                "Unable to start the VPN: ${exception.message ?: "system rejected the request"}"
            )
        }
    }

    fun stop(context: Context) {
        AdBlockStatusStore.set(context, AdBlockStatus.OFF)
        context.stopService(Intent(context, AdBlockVpnService::class.java).setAction(ACTION_STOP))
    }
}
