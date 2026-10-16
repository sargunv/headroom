package dev.sebastiano.headroom.desktop.platform

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MacBrowserTest {
    @Test
    fun `opens HTTPS as a single argument without invoking a shell`() {
        var command: List<String>? = null
        val browser = MacBrowser { arguments ->
            command = arguments
            true
        }
        val url = "https://example.invalid/authorize?code=fake&next=\u0024(test)"
        assertTrue(browser.open(url))
        assertEquals(listOf("/usr/bin/open", "--", url), command)
        assertTrue(browser.open("HTTP://example.invalid/path"))
    }

    @Test
    fun `rejects non-web and malformed URLs without starting a process`() {
        var calls = 0
        val browser = MacBrowser {
            calls++
            true
        }
        for (url in
            listOf(
                "file:///etc/passwd",
                "headroom://signed-in",
                "javascript:alert(1)",
                "-aCalculator",
                "https:",
                "https:///path",
                "https://",
                "https://example.invalid/\n",
            )) {
            assertFalse(browser.open(url), url)
        }
        assertEquals(0, calls)
    }

    @Test
    fun `reports process failures without exposing the URL`() {
        assertFalse(MacBrowser { false }.open("https://example.invalid"))
        assertFalse(MacBrowser { throw IOException("fake-code") }.open("https://example.invalid"))
    }
}
