package dev.sebastiano.headroom.island

/** How the island is drawn right now. */
enum class IslandMode {
    /**
     * The accessibility service is connected. The best mode: the pill grows around the camera, over
     * the status bar and the lock screen.
     */
    Accessibility,

    /**
     * The service is not connected, but the user allowed Display over other apps. The pill hangs
     * below the status bar, and never shows on the lock screen.
     */
    Overlay,

    /** Neither is available. A reset uses the heads-up notification. */
    None,
}

/** The best mode that works now: the service first, then display over other apps. */
fun resolveIslandMode(serviceConnected: Boolean, canDrawOverlays: Boolean): IslandMode =
    when {
        serviceConnected -> IslandMode.Accessibility
        canDrawOverlays -> IslandMode.Overlay
        else -> IslandMode.None
    }
