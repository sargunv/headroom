package dev.sebastiano.headroom.desktop.platform

import java.nio.file.Files
import java.nio.file.Path

/** Persistent desktop data, with an explicit override for isolated tests and development. */
public object DesktopPaths {
    public fun resolve(
        override: Path? = null,
        home: Path = Path.of(System.getProperty("user.home")),
    ): Path {
        val directory =
            (override ?: home.resolve("Library/Application Support/Headroom"))
                .toAbsolutePath()
                .normalize()
        return Files.createDirectories(directory)
    }
}
