package dev.sebastiano.headroom.desktop.integration

import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import java.nio.file.Files
import java.nio.file.Path
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

/** Records the offscreen demo scene, never the user's screen or real accounts. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.recordDesktopScreenshot(name: String) {
    if (System.getenv("HEADROOM_RECORD_SCREENSHOTS") != "true") return
    val directory = Path.of("build", "reports", "desktop-screenshots")
    Files.createDirectories(directory)
    val bitmap = onRoot().captureToImage().asSkiaBitmap()
    try {
        Image.makeFromBitmap(bitmap).use { image ->
            image.encodeToData(EncodedImageFormat.PNG)?.use { data ->
                Files.write(directory.resolve("$name.png"), data.bytes)
            }
        }
    } finally {
        bitmap.close()
    }
}
