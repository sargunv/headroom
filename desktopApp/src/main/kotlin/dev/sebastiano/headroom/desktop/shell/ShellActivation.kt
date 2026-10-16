package dev.sebastiano.headroom.desktop.shell

import dev.sebastiano.headroom.shared.SignInUi
import dev.sebastiano.headroom.shared.ZCodeUi
import java.net.URI

/** Provider authorization must also restore a window hidden while the browser was open. */
internal class ShellActivation(private val restore: () -> Unit) {
    fun signInChanged(state: SignInUi) {
        if (state is SignInUi.Success) restore()
    }

    fun zCodeChanged(state: ZCodeUi) {
        if (state == ZCodeUi.Done) restore()
    }

    fun openUri(uri: URI) {
        if (uri.scheme.equals("headroom", ignoreCase = true) && uri.host == "signed-in") restore()
    }
}
