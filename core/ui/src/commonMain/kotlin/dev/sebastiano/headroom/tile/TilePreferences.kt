package dev.sebastiano.headroom.tile

import dev.sebastiano.headroom.model.TileSubtitleMode
import kotlinx.coroutines.flow.StateFlow

/** The tile subtitle preference, stored by the platform that owns the tile service. */
interface TilePreferences {
    val subtitle: StateFlow<TileSubtitleMode>

    fun setSubtitle(mode: TileSubtitleMode)
}
