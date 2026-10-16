package dev.sebastiano.headroom.desktop.shell

import java.nio.file.Path

internal data class ShellOptions(
    val smokeTest: Boolean,
    val background: Boolean,
    val dataDirectory: Path?,
) {
    companion object {
        fun parse(args: Array<String>): ShellOptions {
            if ("--smoke-test" in args) return ShellOptions(true, false, null)
            val directoryIndex = args.indexOf("--data-dir")
            val directory =
                args.firstOrNull { it.startsWith("--data-dir=") }?.substringAfter('=')
                    ?: args.getOrNull(directoryIndex + 1)?.takeIf {
                        directoryIndex >= 0 && !it.startsWith("--")
                    }
            return ShellOptions(
                false,
                "--background" in args,
                directory?.takeIf { it.isNotBlank() }?.let(Path::of),
            )
        }
    }
}
