package dev.sebastiano.headroom.desktop.shell

import java.util.concurrent.TimeUnit

/** Wall-clock polling also catches a suspended laptop and backwards clock adjustments. */
internal class ResidentSchedule {
    private var lastPoll: Long? = null
    private var lastRefresh: Long? = null

    fun poll(now: Long, intervalMinutes: Int?): Boolean {
        val previousPoll = lastPoll
        val previousRefresh = lastRefresh
        lastPoll = now
        val resumed = previousPoll != null && now - previousPoll >= RESUME_GAP_MILLIS
        val clockChanged = previousPoll != null && now < previousPoll
        val intervalDue =
            intervalMinutes != null &&
                previousRefresh != null &&
                now - previousRefresh >= TimeUnit.MINUTES.toMillis(intervalMinutes.toLong())
        val due = previousRefresh == null || resumed || clockChanged || intervalDue
        if (due) lastRefresh = now
        return due
    }

    private companion object {
        val RESUME_GAP_MILLIS = TimeUnit.MINUTES.toMillis(2)
    }
}
