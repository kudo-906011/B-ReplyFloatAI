package com.replyfloat.ai.service

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.N)
class QuickSettingsTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        val isRunning = FloatingOverlayService.isRunning
        tile.state = if (isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivityAndCollapse(intent)
            return
        }

        if (FloatingOverlayService.isRunning) {
            FloatingOverlayService.stopService(this)
            qsTile?.state = Tile.STATE_INACTIVE
        } else {
            FloatingOverlayService.startService(this)
            qsTile?.state = Tile.STATE_ACTIVE
        }
        qsTile?.updateTile()
    }
}
