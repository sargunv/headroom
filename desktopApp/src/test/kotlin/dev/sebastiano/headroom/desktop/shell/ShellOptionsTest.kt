package dev.sebastiano.headroom.desktop.shell

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShellOptionsTest {
    @Test
    fun `production starts normally and preserves background and data directory options`() {
        val defaults = ShellOptions.parse(emptyArray())
        assertFalse(defaults.smokeTest)
        assertFalse(defaults.background)
        assertEquals(null, defaults.dataDirectory)
        val options = ShellOptions.parse(arrayOf("--background", "--data-dir", "/isolated/data"))
        assertTrue(options.background)
        assertEquals(Path.of("/isolated/data"), options.dataDirectory)
    }

    @Test
    fun `smoke mode cannot reuse a caller supplied data directory or hide its window`() {
        val options =
            ShellOptions.parse(arrayOf("--smoke-test", "--background", "--data-dir=/real/data"))
        assertTrue(options.smokeTest)
        assertFalse(options.background)
        assertEquals(null, options.dataDirectory)
    }
}
