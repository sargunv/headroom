package dev.sebastiano.headroom.desktop.shell

import com.sun.jna.NativeLibrary
import java.nio.file.Path

/** Geometry may be unavailable when macOS hides menu extras. Verify the actual native instance. */
internal object NativeTrayProbe {
    fun initialized(): Boolean =
        try {
            val system = NativeLibrary.getInstance("/usr/lib/libSystem.B.dylib")
            val count = system.getFunction("_dyld_image_count").invokeInt(emptyArray())
            val imageName = system.getFunction("_dyld_get_image_name")
            (0 until count).any { index ->
                val name = imageName.invokePointer(arrayOf<Any?>(index))?.getString(0, "UTF-8")
                if (name == null || Path.of(name).fileName.toString() != "libMacTray.dylib") false
                else {
                    // Public C export in ComposeNativeTray 2.1.6's tray.h / tray.swift.
                    NativeLibrary.getInstance(name)
                        .getFunction("tray_get_instance")
                        .invokePointer(emptyArray()) != null
                }
            }
        } catch (_: RuntimeException) {
            false
        } catch (_: LinkageError) {
            false
        }
}
