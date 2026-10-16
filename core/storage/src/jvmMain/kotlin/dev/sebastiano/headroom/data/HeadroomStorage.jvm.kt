package dev.sebastiano.headroom.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.sebastiano.headroom.data.db.HeadroomDatabase
import dev.sebastiano.headroom.data.db.addAllMigrations
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * Opens persistent desktop storage, creating [directory] when needed. Uses bundled SQLite rather
 * than a system installation. Cancel and join [scope] before closing the returned storage.
 */
public fun openHeadroomStorage(directory: String, scope: CoroutineScope): HeadroomStorage =
    openStorage(directory, scope)

private fun openStorage(
    directory: String,
    scope: CoroutineScope,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
): HeadroomStorage {
    val folder = File(directory).absoluteFile.normalize().path.toPath()
    FileSystem.SYSTEM.createDirectories(folder)
    val database =
        Room.databaseBuilder<HeadroomDatabase>(name = (folder / HeadroomDatabase.NAME).toString())
            .addAllMigrations()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(ioDispatcher)
            .build()
    val storeScope = CoroutineScope(scope.coroutineContext + ioDispatcher)
    return HeadroomStorage(
        database = database,
        settingsStore =
            PreferenceDataStoreFactory.createWithPath(scope = storeScope) {
                folder / "settings.preferences_pb"
            },
        alertStore =
            PreferenceDataStoreFactory.createWithPath(scope = storeScope) {
                folder / "alerts.preferences_pb"
            },
    )
}
