package dev.sebastiano.headroom.designsystem

import dev.sebastiano.headroom.designsystem.platform.formatResourceTemplate
import kotlin.test.Test
import kotlin.test.assertEquals

class ResourceFormattingTest {
    @Test
    fun `Android quote escapes are decoded before formatting`() {
        assertEquals(
            "This account's name is \"name\\'%%\": 34%",
            formatResourceTemplate(
                "This account\\'s name is \\\"%1\$s\\\": %2\$d%%",
                arrayOf<Any>("name\\'%%", 34),
                "en-US",
            ),
        )
        assertEquals("don't", formatResourceTemplate("don\\'t", emptyArray(), "en-US"))
    }

    @Test
    fun `Compose decoded whitespace and literal backslashes are not decoded again`() {
        val template = "first\nsecond\tthird \\n \\t \\u0041"
        assertEquals(template, formatResourceTemplate(template, emptyArray(), "en-US"))
    }

    @Test
    fun `original Android percent templates produce one literal percent`() {
        assertEquals("71%", formatResourceTemplate("%1\$d%%", arrayOf<Any>(71), "en-US"))
        assertEquals("0%", formatResourceTemplate("%1\$d%%", arrayOf<Any>(0), "en-US"))
    }

    @Test
    fun `escaped template percents never alter account or provider arguments`() {
        assertEquals(
            "name%%: 34%",
            formatResourceTemplate("%1\$s: %2\$d%%", arrayOf<Any>("name%%", 34), "en-US"),
        )
    }

    @Test
    fun `plain strings are not interpreted as format templates without arguments`() {
        assertEquals(
            "100% available",
            formatResourceTemplate("100% available", emptyArray(), "en-US"),
        )
    }
}
