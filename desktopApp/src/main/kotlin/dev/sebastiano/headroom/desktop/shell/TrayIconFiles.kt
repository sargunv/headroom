package dev.sebastiano.headroom.desktop.shell

import java.nio.file.Files
import java.nio.file.Path
import org.jetbrains.skia.BlendMode
import org.jetbrains.skia.Color
import org.jetbrains.skia.ColorFilter
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Surface

/** AppKit consumes paths. These process-owned assets never contain account or credential data. */
internal class TrayIconFiles : AutoCloseable {
    private val directory = Files.createTempDirectory("headroom-status-")
    val light: Path = directory.resolve("light.png")
    val dark: Path = directory.resolve("dark.png")

    init {
        val bytes =
            checkNotNull(javaClass.getResourceAsStream("/status-icon.png")).use { it.readBytes() }
        Files.write(light, bytes)
        Image.makeFromEncoded(bytes).use { source ->
            Surface.makeRasterN32Premul(source.width, source.height).use { surface ->
                Paint().use { paint ->
                    paint.colorFilter = ColorFilter.makeBlend(Color.WHITE, BlendMode.SRC_IN)
                    surface.canvas.drawImage(source, 0f, 0f, paint)
                }
                surface.makeImageSnapshot().use { image ->
                    checkNotNull(image.encodeToData()).use { Files.write(dark, it.bytes) }
                }
            }
        }
    }

    override fun close() {
        Files.deleteIfExists(light)
        Files.deleteIfExists(dark)
        Files.deleteIfExists(directory)
    }
}
