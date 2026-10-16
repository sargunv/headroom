package dev.sebastiano.headroom.desktop

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopLifecycleTest {
    @Test
    fun `closing the window keeps the process running`() {
        var quits = 0
        val lifecycle = DesktopLifecycle(startHidden = false, onQuit = { quits++ })
        lifecycle.closeWindow()

        assertFalse(lifecycle.visible.value)
        assertEquals(0, quits)
        lifecycle.showWindow()
        assertTrue(lifecycle.visible.value)
    }

    @Test
    fun `login startup does not open a window`() {
        val lifecycle = DesktopLifecycle(startHidden = true, onQuit = {})

        assertFalse(lifecycle.visible.value)
    }

    @Test
    fun `explicit quit shuts down exactly once`() {
        var quits = 0
        val lifecycle = DesktopLifecycle(startHidden = false, onQuit = { quits++ })
        lifecycle.quit()
        lifecycle.quit()
        lifecycle.showWindow()

        assertEquals(1, quits)
        assertFalse(lifecycle.visible.value)
    }

    @Test
    fun `a concurrent restore cannot leave a quitting window visible`() {
        Executors.newFixedThreadPool(2).use { executor ->
            repeat(6_000) {
                val quits = AtomicInteger()
                val lifecycle =
                    DesktopLifecycle(startHidden = true, onQuit = { quits.incrementAndGet() })
                val start = CountDownLatch(1)
                val show = executor.submit {
                    start.await()
                    lifecycle.showWindow()
                }
                val quit = executor.submit {
                    start.await()
                    lifecycle.quit()
                }
                start.countDown()
                show.get()
                quit.get()

                assertFalse(lifecycle.visible.value)
                assertEquals(1, quits.get())
            }
        }
    }
}
