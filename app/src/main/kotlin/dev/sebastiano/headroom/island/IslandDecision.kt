package dev.sebastiano.headroom.island

/** Everything that decides whether a reset shows on the island. Each field is one fact. */
internal data class IslandConditions(
    /** The user turned the reset island on in the settings. */
    val enabled: Boolean,
    /** Which window can draw the island, if any. */
    val mode: IslandMode,
    val screenOn: Boolean,
    val landscape: Boolean,
    /** Headroom itself is on screen. The in-app confetti covers that case. */
    val appInForeground: Boolean,
    val doNotDisturb: Boolean,
    /** The device is locked. Only the [IslandMode.Overlay] mode cares. */
    val keyguardLocked: Boolean,
)

/** Why the island stays hidden. */
internal enum class IslandBlock {
    SettingOff,
    NoWindow,
    ScreenOff,

    /** In [IslandMode.Overlay] mode only: that window is never shown on the lock screen. */
    DeviceLocked,
    Landscape,
    AppInForeground,
    DoNotDisturb,
}

/** The first reason the island must stay hidden, or null when it may show. */
internal fun IslandConditions.blockedBy(): IslandBlock? =
    when {
        !enabled -> IslandBlock.SettingOff
        mode == IslandMode.None -> IslandBlock.NoWindow
        !screenOn -> IslandBlock.ScreenOff
        mode == IslandMode.Overlay && keyguardLocked -> IslandBlock.DeviceLocked
        landscape -> IslandBlock.Landscape
        appInForeground -> IslandBlock.AppInForeground
        doNotDisturb -> IslandBlock.DoNotDisturb
        else -> null
    }

internal fun IslandConditions.allowsIsland(): Boolean = blockedBy() == null
