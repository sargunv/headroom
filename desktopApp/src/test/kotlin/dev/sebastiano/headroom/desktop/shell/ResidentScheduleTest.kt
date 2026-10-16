package dev.sebastiano.headroom.desktop.shell

import dev.sebastiano.headroom.shared.ResetCheckUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResidentScheduleTest {
    @Test
    fun `refresh runs initially and at the configured interval`() {
        val schedule = ResidentSchedule()
        assertTrue(schedule.poll(0, 15))
        for (now in 15_000L until 900_000L step 15_000L) {
            assertFalse(schedule.poll(now, 15))
        }
        assertTrue(schedule.poll(900_000, 15))
    }

    @Test
    fun `on open avoids periodic refresh but catches up after sleep`() {
        val schedule = ResidentSchedule()
        assertTrue(schedule.poll(0, null))
        assertFalse(schedule.poll(15_000, null))
        assertTrue(schedule.poll(300_000, null))
        assertFalse(schedule.poll(315_000, null))
    }

    @Test
    fun `clock changes cannot postpone refresh indefinitely`() {
        val schedule = ResidentSchedule()
        schedule.poll(300_000, 15)
        assertTrue(schedule.poll(0, 15))
    }

    @Test
    fun `replanning preserves due reset baselines for confirmation and retries`() {
        val queue = ResetCheckQueue()
        val original = ResetCheckUi("account", "weekly", 100, 100, 95.0)
        queue.update(listOf(original), 0)
        queue.update(emptyList(), 200)
        assertEquals(listOf(original), queue.due(200))
        queue.complete(original)
        assertEquals(emptyList(), queue.due(200))
    }

    @Test
    fun `new future plans replace old future checks and stale checks expire`() {
        val queue = ResetCheckQueue()
        queue.update(listOf(ResetCheckUi("a", "weekly", 100, 100, 95.0)), 0)
        queue.update(emptyList(), 50)
        assertEquals(emptyList(), queue.due(200))
        queue.update(listOf(ResetCheckUi("a", "weekly", 100, 100, 95.0)), 0)
        queue.update(emptyList(), 100_000_000)
        assertEquals(emptyList(), queue.due(100_000_000))
    }

    @Test
    fun `a replanned due occurrence cannot overwrite its original usage baseline`() {
        val queue = ResetCheckQueue()
        val original = ResetCheckUi("a", "weekly", 100, 100, 95.0)
        queue.update(listOf(original), 0)
        queue.update(listOf(original.copy(usedBefore = 0.0)), 200)
        assertEquals(listOf(original), queue.due(200))
    }

    @Test
    fun `failed reset checks back off without losing their baseline`() {
        val queue = ResetCheckQueue()
        val check = ResetCheckUi("a", "weekly", 100, 100, 95.0)
        queue.update(listOf(check), 0)
        queue.attempted(check, 100)
        queue.update(emptyList(), 200)
        assertEquals(emptyList(), queue.due(200))
        assertEquals(listOf(check), queue.due(60_100))
        queue.attempted(check, 60_100)
        assertEquals(emptyList(), queue.due(120_100))
        assertEquals(listOf(check), queue.due(180_100))
    }

    @Test
    fun `sync during reset grace preserves original baseline before trigger`() {
        val queue = ResetCheckQueue()
        val original = ResetCheckUi("a", "weekly", 43_210_000, 43_200_000, 95.0)
        queue.update(listOf(original), 43_199_000)
        queue.update(emptyList(), 43_205_000)
        assertEquals(emptyList(), queue.due(43_205_000))
        assertEquals(listOf(original), queue.due(43_210_000))
    }

    @Test
    fun `completed occurrences cannot be revived by a stale plan`() {
        val queue = ResetCheckQueue()
        val check = ResetCheckUi("a", "weekly", 100, 100, 95.0)
        queue.update(listOf(check), 100)
        queue.complete(check)
        queue.update(listOf(check), 200)
        assertEquals(emptyList(), queue.due(200))
    }

    @Test
    fun `network failure has at most eight attempts even if the plan repeats`() {
        val queue = ResetCheckQueue()
        val check = ResetCheckUi("a", "weekly", 100, 100, 95.0)
        var attempts = 0
        for (minute in 0..180) {
            val now = 100 + minute * 60_000L
            queue.update(listOf(check), now)
            queue.due(now).forEach {
                attempts++
                queue.attempted(it, now)
            }
        }
        assertEquals(8, attempts)
    }
}
