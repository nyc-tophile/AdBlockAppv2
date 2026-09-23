package com.example.adblock

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class AdBlockTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val snapshot = AdBlockStatusStore.snapshot(this)
        if (snapshot.status == AdBlockStatus.ON || snapshot.status == AdBlockStatus.STARTING) {
            VpnControl.stop(this)
            updateTile()
            return
        }

        if (!VpnControl.hasPermission(this)) {
            // A Quick Settings tile cannot reliably present the VPN consent dialog itself. Open
            // the activity, where the user can grant the official Android VPN permission.
            AdBlockStatusStore.set(this, AdBlockStatus.PERMISSION_REQUIRED)
            openAppForAuthorization()
            updateTile()
            return
        }

        VpnControl.start(this)
        updateTile()
    }

    private fun openAppForAuthorization() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        unlockAndRun {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val snapshot = AdBlockStatusStore.snapshot(this)
        val enabled = snapshot.status == AdBlockStatus.ON || snapshot.status == AdBlockStatus.STARTING
        val permissionMissing = !VpnControl.hasPermission(this)

        tile.label = getString(R.string.app_name)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_adblock)
        when {
            enabled -> {
                tile.subtitle = if (snapshot.status == AdBlockStatus.STARTING) "Starting" else "ON"
                tile.state = Tile.STATE_ACTIVE
            }
            permissionMissing -> {
                tile.subtitle = "Open app to allow VPN"
                tile.state = Tile.STATE_INACTIVE
            }
            snapshot.status == AdBlockStatus.ERROR -> {
                tile.subtitle = "VPN unavailable"
                tile.state = Tile.STATE_UNAVAILABLE
            }
            else -> {
                tile.subtitle = "OFF"
                tile.state = Tile.STATE_INACTIVE
            }
        }
        tile.updateTile()
    }
}
