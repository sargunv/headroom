package dev.sebastiano.headroom.tile

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import dev.sebastiano.headroom.R

/** Shows the system's "Add tile" prompt through [StatusBarManager.requestAddTileService]. */
class StatusBarTileAdder(private val context: Context) : TileAdder {
    override fun request(onResult: (TileAddResult) -> Unit) {
        val statusBar = context.getSystemService(StatusBarManager::class.java)
        if (statusBar == null) {
            onResult(TileAddResult.Failed)
            return
        }
        statusBar.requestAddTileService(
            ComponentName(context, HeadroomTileService::class.java),
            context.getString(R.string.tile_label),
            Icon.createWithResource(context, R.drawable.ic_tile_headroom),
            context.mainExecutor,
        ) { code ->
            onResult(
                when (code) {
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> TileAddResult.Added
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED ->
                        TileAddResult.AlreadyAdded
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED ->
                        TileAddResult.NotAdded
                    else -> TileAddResult.Failed
                }
            )
        }
    }
}
