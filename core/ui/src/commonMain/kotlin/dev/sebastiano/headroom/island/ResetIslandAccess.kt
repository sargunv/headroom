package dev.sebastiano.headroom.island

import dev.sebastiano.headroom.model.Provider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What the settings screen needs from the reset island. */
interface ResetIslandAccess {
    /** True while the accessibility service that draws the island is connected. */
    val ready: StateFlow<Boolean>

    /** True when the user allowed Display over other apps, the fallback when [ready] is false. */
    val overlayAllowed: StateFlow<Boolean>

    /**
     * True when accessibility settings list the service as turned on. It can be true a moment
     * before [ready], while Android connects the service.
     */
    val enabledInSettings: StateFlow<Boolean>

    /** Reads the accessibility and overlay settings again. Call it when the user comes back. */
    fun refresh()

    /**
     * Shows the island with [provider]'s logo and [message] at once, for the Try button, in the
     * best mode available. It does not ask whether the screen is free. It returns false when
     * neither the service nor the overlay is available. Call it on the main thread.
     */
    fun showDemo(provider: Provider, message: String): Boolean

    companion object {
        /** No island at all, for previews and builds without the service. */
        val Unavailable: ResetIslandAccess =
            object : ResetIslandAccess {
                override val ready: StateFlow<Boolean> = MutableStateFlow(false)
                override val overlayAllowed: StateFlow<Boolean> = MutableStateFlow(false)
                override val enabledInSettings: StateFlow<Boolean> = MutableStateFlow(false)

                override fun refresh() = Unit

                override fun showDemo(provider: Provider, message: String) = false
            }
    }
}
