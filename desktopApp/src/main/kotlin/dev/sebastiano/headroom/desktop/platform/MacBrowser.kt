package dev.sebastiano.headroom.desktop.platform

import java.io.IOException
import java.net.URI
import java.net.URISyntaxException

/** Opens only web URLs, using a fixed executable and separate arguments, never a shell. */
public class MacBrowser internal constructor(private val launch: (List<String>) -> Boolean) {
    public constructor() : this(::launchBrowser)

    public fun open(url: String): Boolean {
        val uri =
            try {
                URI(url)
            } catch (_: URISyntaxException) {
                return false
            }
        if (
            !uri.scheme.equals("https", ignoreCase = true) &&
                !uri.scheme.equals("http", ignoreCase = true)
        )
            return false
        if (uri.host.isNullOrEmpty()) return false
        return try {
            launch(listOf("/usr/bin/open", "--", url))
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}

private fun launchBrowser(arguments: List<String>): Boolean {
    val process =
        ProcessBuilder(arguments)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
    process.outputStream.close()
    return try {
        process.waitFor() == 0
    } catch (_: InterruptedException) {
        process.destroy()
        Thread.currentThread().interrupt()
        false
    }
}
