package dev.sebastiano.headroom.desktop.shell

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class SmokeResourcesTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `smoke secrets stay in memory and cleanup removes only the owned temporary directory`() {
        val unrelated = Files.writeString(directory.resolve("keep.txt"), "keep")
        val smoke = SmokeResources.create(directory)
        assertEquals(null, smoke.secrets.read("fixture"))
        assertTrue(smoke.secrets.write("fixture", "fake-only"))
        assertEquals("fake-only", smoke.secrets.read("fixture"))
        Files.createDirectories(smoke.directory.resolve("nested"))
        Files.writeString(smoke.directory.resolve("nested/data"), "demo")
        smoke.close()
        assertFalse(Files.exists(smoke.directory))
        assertTrue(Files.exists(unrelated))
        assertEquals(null, smoke.secrets.read("fixture"))
    }
}
