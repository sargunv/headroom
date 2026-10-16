package dev.sebastiano.headroom.shared

import dev.sebastiano.headroom.data.openHeadroomStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Opens Headroom with its files in [directory], such as Application Support, and the sign-ins in
 * [secrets], the Keychain. Callbacks arrive on the main thread.
 */
public fun openHeadroom(directory: String, secrets: SecureValueStore): Headroom {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    return createHeadroom(directory, secrets, openHeadroomStorage(directory, scope), scope)
}
