package dev.sebastiano.headroom.shared

import dev.sebastiano.headroom.data.HeadroomStorage
import dev.sebastiano.headroom.data.openHeadroomStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job

/**
 * Opens the desktop app using bundled SQLite and platform-protected [secrets]. Observations arrive
 * on [dispatcher]: the UI's Main dispatcher by default, or an explicit dispatcher for headless use.
 * Call [Headroom.closeAndJoin] before opening the same directory again.
 */
public fun openHeadroom(
    directory: String,
    secrets: SecureValueStore,
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
): Headroom {
    val scope = CoroutineScope(SupervisorJob() + dispatcher)
    var storage: HeadroomStorage? = null
    var opened = false
    try {
        val openedStorage = openHeadroomStorage(directory, scope)
        storage = openedStorage
        return createHeadroom(directory, secrets, openedStorage, scope).also { opened = true }
    } finally {
        if (!opened) {
            scope.cancel()
            val abandonedStorage = storage
            scope.coroutineContext.job.invokeOnCompletion { abandonedStorage?.close() }
        }
    }
}
