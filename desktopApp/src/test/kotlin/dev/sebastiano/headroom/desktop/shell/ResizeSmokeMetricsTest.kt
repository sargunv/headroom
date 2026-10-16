package dev.sebastiano.headroom.desktop.shell

import androidx.compose.ui.unit.IntSize
import dev.sebastiano.headroom.ui.LayoutWidth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResizeSmokeMetricsTest {
    @Test
    fun `resize needs a new native callback and matching newly drawn root`() {
        val request = ResizeSmokeRequest(500, IntSize(1000, 1600), 10L, 2, 4)
        val draw = ResizeSmokeDraw(5, 30L, IntSize(1000, 1600), null)
        assertFalse(request.completed(2, IntSize(1000, 1600), draw))
        assertFalse(request.completed(3, IntSize(998, 1600), draw))
        assertFalse(request.completed(3, IntSize(1000, 1600), draw.copy(sequence = 4)))
        assertFalse(request.completed(3, IntSize(1000, 1600), draw.copy(nanos = 9L)))
        assertFalse(
            request.completed(3, IntSize(1000, 1600), draw.copy(measuredPx = IntSize(998, 1600)))
        )
        assertTrue(request.completed(3, IntSize(1000, 1600), draw))
    }

    @Test
    fun `expected layout uses actual native dp and exact width boundaries`() {
        assertEquals(LayoutWidth.Compact, resizeSmokeWidth(599.5f))
        assertEquals(LayoutWidth.Medium, resizeSmokeWidth(600f))
        assertEquals(LayoutWidth.Medium, resizeSmokeWidth(839.5f))
        assertEquals(LayoutWidth.Expanded, resizeSmokeWidth(840f))
    }

    @Test
    fun `diagnosis distinguishes stale info density mismatch and correct adaptive state`() {
        assertEquals(
            "stale-window-info",
            resizeSmokeDiagnosis(
                IntSize(1000, 1600),
                2f,
                IntSize(2080, 1600),
                2f,
                LayoutWidth.Expanded,
            ),
        )
        assertEquals(
            "density-mismatch",
            resizeSmokeDiagnosis(
                IntSize(1000, 1600),
                2f,
                IntSize(1000, 1600),
                1f,
                LayoutWidth.Expanded,
            ),
        )
        assertEquals(
            "adaptive-mismatch",
            resizeSmokeDiagnosis(
                IntSize(1000, 1600),
                2f,
                IntSize(1000, 1600),
                2f,
                LayoutWidth.Expanded,
            ),
        )
        assertEquals(
            "consistent",
            resizeSmokeDiagnosis(
                IntSize(1000, 1600),
                2f,
                IntSize(1000, 1600),
                2f,
                LayoutWidth.Compact,
            ),
        )
    }
}
