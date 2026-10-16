package dev.sebastiano.headroom.desktop

import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Contains notification identifiers only, never credentials or provider responses. */
internal class DesktopNotificationLedger(private val path: Path) {
    private var state = load()

    val signInNotified: List<String>
        get() = state.signInNotified

    val reminded: List<String>
        get() = state.reminded

    fun hasReset(key: String): Boolean = key in state.resets

    fun recordReset(key: String) {
        if (hasReset(key)) return
        save(state.copy(resets = (state.resets + key).takeLast(MAX_RESETS)))
    }

    fun update(signInNotified: List<String>, reminded: List<String>) {
        val next = state.copy(signInNotified = signInNotified, reminded = reminded)
        if (next != state) save(next)
    }

    private fun load(): NotificationState =
        try {
            if (Files.exists(path)) json.decodeFromString(Files.readString(path))
            else NotificationState()
        } catch (_: SerializationException) {
            NotificationState()
        } catch (_: IOException) {
            NotificationState()
        } catch (_: IllegalArgumentException) {
            NotificationState()
        }

    private fun save(next: NotificationState) {
        val parent = path.toAbsolutePath().parent
        Files.createDirectories(parent)
        val temporary = Files.createTempFile(parent, "notifications-", ".json.tmp")
        try {
            Files.writeString(temporary, json.encodeToString(next))
            try {
                Files.move(
                    temporary,
                    path,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
            }
            state = next
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private companion object {
        const val MAX_RESETS = 1_000
        val json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
private data class NotificationState(
    val resets: List<String> = emptyList(),
    val signInNotified: List<String> = emptyList(),
    val reminded: List<String> = emptyList(),
)
