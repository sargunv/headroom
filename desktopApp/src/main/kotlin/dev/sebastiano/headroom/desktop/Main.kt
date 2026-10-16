package dev.sebastiano.headroom.desktop

import dev.nucleusframework.application.nucleusApplication
import dev.sebastiano.headroom.desktop.shell.HeadroomShell
import dev.sebastiano.headroom.desktop.shell.runDesktopSmokeTest
import java.io.IOException
import kotlin.system.exitProcess

public fun main(args: Array<String>) {
    if ("--smoke-test" in args) {
        exitProcess(smokeExitCode(args))
    } else {
        nucleusApplication(
            args = args,
            enableSingleInstance = true,
            dockIconFollowsWindows = true,
        ) {
            HeadroomShell(args)
        }
    }
}

private fun smokeExitCode(args: Array<String>): Int =
    try {
        runDesktopSmokeTest(args)
        0
    } catch (_: IOException) {
        smokeFailed()
    } catch (_: RuntimeException) {
        smokeFailed()
    } catch (_: LinkageError) {
        smokeFailed()
    }

private fun smokeFailed(): Int {
    System.err.println("HEADROOM_SMOKE_TEST_FAILED: native demo window/tray did not become ready")
    return 1
}
