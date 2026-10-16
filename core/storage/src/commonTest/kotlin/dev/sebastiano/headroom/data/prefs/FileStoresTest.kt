package dev.sebastiano.headroom.data.prefs

import dev.sebastiano.headroom.data.db.TestDirectory
import dev.sebastiano.headroom.model.ResetAttempt
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.time.Instant
import kotlinx.serialization.builtins.serializer
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.Path
import okio.Source

class FileStoresTest {
    private val folder = TestDirectory()

    @AfterTest
    fun tearDown() {
        folder.close()
    }

    @Test
    fun `storage filesystem is the platform system filesystem`() {
        assertSame(FileSystem.SYSTEM, storageFileSystem())
    }

    @Test
    fun `json files use the injected filesystem`() {
        var reads = 0
        val fileSystem =
            object : ForwardingFileSystem(FileSystem.SYSTEM) {
                override fun source(file: Path): Source {
                    reads++
                    return super.source(file)
                }
            }
        val file = JsonFile(folder.path / "injected.json", String.serializer(), "", fileSystem)

        file.write("stored value")

        assertEquals("stored value", file.read())
        assertEquals(1, reads)
    }

    @Test
    fun `attempt targets survive a new store on the same file`() {
        val file = folder.path / "targets.json"
        FileAttemptTargetStore(file).save(mapOf("key-1" to "credit-a", "key-2" to "token-b"))

        assertEquals(
            mapOf("key-1" to "credit-a", "key-2" to "token-b"),
            FileAttemptTargetStore(file).load(),
        )
    }

    @Test
    fun `reset attempts survive a new store on the same file`() {
        val file = folder.path / "attempts.json"
        val attempt = ResetAttempt("acc", "pool", "key", Instant.parse("2026-10-10T12:00:00Z"))
        FileResetAttemptStore(file).save(listOf(attempt))

        assertEquals(listOf(attempt), FileResetAttemptStore(file).load())
    }

    @Test
    fun `a missing or unreadable file reads as empty`() {
        val file = folder.path / "broken.json"
        assertEquals(emptyMap(), FileAttemptTargetStore(file).load())

        FileSystem.SYSTEM.write(file) { writeUtf8("not json") }

        assertEquals(emptyMap(), FileAttemptTargetStore(file).load())
        assertEquals(emptyList(), FileResetAttemptStore(file).load())
    }
}
