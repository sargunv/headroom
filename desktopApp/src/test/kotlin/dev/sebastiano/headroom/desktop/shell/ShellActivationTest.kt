package dev.sebastiano.headroom.desktop.shell

import dev.sebastiano.headroom.shared.SignInUi
import dev.sebastiano.headroom.shared.ZCodeUi
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals

class ShellActivationTest {
    @Test
    fun `successful account and ZCode authorization restore the dashboard`() {
        var restores = 0
        val activation = ShellActivation { restores++ }
        activation.signInChanged(SignInUi.Success("claude", "Claude", "demo@example.com"))
        activation.zCodeChanged(ZCodeUi.Done)

        assertEquals(2, restores)
    }

    @Test
    fun `waiting and failed authorization do not steal focus`() {
        var restores = 0
        val activation = ShellActivation { restores++ }
        activation.signInChanged(SignInUi.Idle)
        activation.signInChanged(SignInUi.Starting("claude", "Claude"))
        activation.signInChanged(SignInUi.Failed("claude", "Claude", "network"))
        activation.zCodeChanged(ZCodeUi.Idle)

        assertEquals(0, restores)
    }

    @Test
    fun `only the registered sign in return link restores the dashboard`() {
        var restores = 0
        val activation = ShellActivation { restores++ }
        activation.openUri(URI("https://example.com/signed-in"))
        activation.openUri(URI("headroom://unrecognized"))
        activation.openUri(URI("headroom://signed-in"))

        assertEquals(1, restores)
    }
}
