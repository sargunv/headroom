package dev.sebastiano.headroom.desktop.shell

import dev.sebastiano.headroom.shared.SecureValueStore
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator

/** Owns only a newly-created temporary directory, never the production Application Support path. */
internal class SmokeResources private constructor(val directory: Path) : AutoCloseable {
    private val values = mutableMapOf<String, String>()
    val secrets: SecureValueStore =
        object : SecureValueStore {
            override fun read(key: String): String? = synchronized(values) { values[key] }

            override fun write(key: String, value: String): Boolean =
                synchronized(values) {
                    values[key] = value
                    true
                }

            override fun delete(key: String): Boolean =
                synchronized(values) {
                    values.remove(key)
                    true
                }
        }

    override fun close() {
        synchronized(values) { values.clear() }
        if (Files.exists(directory)) {
            Files.walk(directory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }

    companion object {
        fun create(parent: Path? = null): SmokeResources =
            SmokeResources(
                if (parent == null) Files.createTempDirectory("headroom-smoke-")
                else Files.createTempDirectory(parent, "headroom-smoke-")
            )
    }
}
