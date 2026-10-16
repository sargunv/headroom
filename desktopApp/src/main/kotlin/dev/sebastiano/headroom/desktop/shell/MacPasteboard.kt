package dev.sebastiano.headroom.desktop.shell

import com.sun.jna.Memory
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer

/** NSPasteboard via AppKit, always called on Tao's UI thread; no AWT Toolkit or shell process. */
internal class MacPasteboard {
    private val appKit by lazy {
        NativeLibrary.getInstance("/System/Library/Frameworks/AppKit.framework/AppKit")
    }
    private val objc by lazy { NativeLibrary.getInstance("objc") }
    private val message by lazy { objc.getFunction("objc_msgSend") }

    fun write(text: String): Boolean =
        safely(false) {
            inPool {
                val board = send(classNamed("NSPasteboard"), "generalPasteboard")
                message.invokeLong(arrayOf(board, selector("clearContents")))
                message.invokeInt(
                    arrayOf(board, selector("setString:forType:"), string(text), string(TEXT_TYPE))
                ) != 0
            }
        }

    fun read(): String? =
        safely(null) {
            inPool {
                val board = send(classNamed("NSPasteboard"), "generalPasteboard")
                val value = send(board, "stringForType:", string(TEXT_TYPE))
                value?.let { send(it, "UTF8String")?.getString(0, "UTF-8") }
            }
        }

    private fun classNamed(name: String): Pointer? {
        appKit // Keep AppKit loaded while its Objective-C classes are in use.
        return objc.getFunction("objc_getClass").invokePointer(arrayOf(name))
    }

    private fun selector(name: String): Pointer? =
        objc.getFunction("sel_registerName").invokePointer(arrayOf(name))

    private fun send(receiver: Pointer?, name: String, vararg arguments: Any?): Pointer? =
        message.invokePointer(arrayOf(receiver, selector(name), *arguments))

    private fun string(text: String): Pointer? {
        val bytes = text.toByteArray(Charsets.UTF_8)
        return Memory(bytes.size.toLong() + 1).use { memory ->
            memory.write(0, bytes, 0, bytes.size)
            memory.setByte(bytes.size.toLong(), 0)
            try {
                send(classNamed("NSString"), "stringWithUTF8String:", memory)
            } finally {
                bytes.fill(0)
            }
        }
    }

    private inline fun <T> inPool(operation: () -> T): T {
        val pool = send(classNamed("NSAutoreleasePool"), "new")
        return try {
            operation()
        } finally {
            send(pool, "drain")
        }
    }

    private inline fun <T> safely(fallback: T, operation: () -> T): T =
        try {
            operation()
        } catch (_: RuntimeException) {
            fallback
        } catch (_: LinkageError) {
            fallback
        }

    private companion object {
        const val TEXT_TYPE = "public.utf8-plain-text"
    }
}
