package dev.sebastiano.headroom.desktop.shell

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.asAwtTransferable
import androidx.compose.ui.text.AnnotatedString
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.IOException

@OptIn(ExperimentalComposeUiApi::class)
internal class MacClipboard(private val pasteboard: MacPasteboard) : Clipboard {
    // Compose's text codec expects a Transferable value; StringSelection never initializes Toolkit.
    override suspend fun getClipEntry(): ClipEntry? =
        pasteboard.read()?.let { ClipEntry(StringSelection(it)) }

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        pasteboard.write(clipEntry.text().orEmpty())
    }
}

// Legacy Compose text fields still read this local; both clipboard interfaces share NSPasteboard.
@Suppress("DEPRECATION")
@OptIn(ExperimentalComposeUiApi::class)
internal class MacClipboardManager(private val pasteboard: MacPasteboard) : ClipboardManager {
    override fun getText(): AnnotatedString? = pasteboard.read()?.let(::AnnotatedString)

    override fun setText(annotatedString: AnnotatedString) {
        pasteboard.write(annotatedString.text)
    }

    override fun getClip(): ClipEntry? = pasteboard.read()?.let { ClipEntry(StringSelection(it)) }

    override fun setClip(clipEntry: ClipEntry?) {
        pasteboard.write(clipEntry.text().orEmpty())
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun ClipEntry?.text(): String? =
    try {
        this?.asAwtTransferable?.getTransferData(DataFlavor.stringFlavor) as? String
    } catch (_: UnsupportedFlavorException) {
        null
    } catch (_: IOException) {
        null
    }
