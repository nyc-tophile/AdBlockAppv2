package com.example.adblock

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class AdBlockTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()

        if (checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            setUnavailable("ADB permission required")
            return
        }

        val success = try {
            PrivateDnsController.toggle(this)
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }

        if (success) updateTile() else setUnavailable("Unable to change DNS")
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val enabled = try {
            PrivateDnsController.isEnabled(this)
        } catch (_: Exception) {
            false
        }

        tile.label = "AdBlock"
        tile.subtitle = if (enabled) "ON" else "OFF"
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.icon = Icon.createWithResource(this, R.drawable.ic_adblock)
        tile.updateTile()
    }

    private fun setUnavailable(message: String) {
        val tile = qsTile ?: return
        tile.label = "AdBlock"
        tile.subtitle = message
        tile.state = Tile.STATE_UNAVAILABLE
        tile.icon = Icon.createWithResource(this, R.drawable.ic_adblock)
        tile.updateTile()
    }
}
