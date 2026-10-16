package dev.sebastiano.headroom.desktop.platform

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class DesktopPathsTest {
    @TempDir lateinit var temporary: Path

    @Test
    fun `resolves application support under the supplied home`() {
        val directory = DesktopPaths.resolve(home = temporary)
        assertEquals(temporary.resolve("Library/Application Support/Headroom"), directory)
        assertTrue(Files.isDirectory(directory))
        assertEquals(directory, DesktopPaths.resolve(home = temporary))
    }

    @Test
    fun `explicit override avoids touching the home directory`() {
        val home = temporary.resolve("unused-home")
        val override = temporary.resolve("isolated/data")
        assertEquals(override, DesktopPaths.resolve(override = override, home = home))
        assertTrue(Files.isDirectory(override))
        assertTrue(Files.notExists(home))
    }

    @Test
    fun `file at the destination is an error`() {
        val file = Files.createFile(temporary.resolve("file"))
        assertFailsWith<java.io.IOException> { DesktopPaths.resolve(override = file) }
    }
}
