package dev.sebastiano.headroom.island

/** What the settings row says about the island. */
enum class ResetIslandStatus {
    Off,
    NeedsPermission,
    ReadyAccessibility,
    ReadyOverlay,
}

/**
 * The status line of the settings row. With the switch off it is [Off][ResetIslandStatus.Off]. With
 * it on, it says which way the island is drawn, or that it needs a permission.
 */
fun resetIslandStatus(enabled: Boolean, mode: IslandMode): ResetIslandStatus =
    when {
        !enabled -> ResetIslandStatus.Off
        mode == IslandMode.Accessibility -> ResetIslandStatus.ReadyAccessibility
        mode == IslandMode.Overlay -> ResetIslandStatus.ReadyOverlay
        else -> ResetIslandStatus.NeedsPermission
    }
