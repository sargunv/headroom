package dev.sebastiano.headroom.desktop.shell

import dev.sebastiano.headroom.shared.ResetCheckUi
import java.util.concurrent.TimeUnit

/** Preserve the pre-reset baseline when replanning no longer includes a check that is now due. */
internal class ResetCheckQueue {
    private val pending = linkedMapOf<String, ResetCheckUi>()
    private val retries = mutableMapOf<String, Retry>()
    private val finished = mutableMapOf<String, Long>()

    fun update(plan: List<ResetCheckUi>, now: Long) {
        pending.entries.removeAll { (_, check) ->
            check.expectedResetAtEpochMillis > now ||
                now - check.expectedResetAtEpochMillis > RETENTION_MILLIS
        }
        retries.keys.retainAll(pending.keys)
        finished.entries.removeAll { (_, expected) -> now - expected > RETENTION_MILLIS }
        plan
            .filter {
                it.key() !in finished && now - it.expectedResetAtEpochMillis <= RETENTION_MILLIS
            }
            .forEach { check -> pending.putIfAbsent(check.key(), check) }
    }

    fun due(now: Long): List<ResetCheckUi> =
        pending.values
            .filter {
                it.triggerAtEpochMillis <= now && (retries[it.key()]?.at ?: Long.MIN_VALUE) <= now
            }
            .toList()

    fun attempted(check: ResetCheckUi, now: Long) {
        val attempts = (retries[check.key()]?.attempts ?: 0) + 1
        if (attempts >= MAX_ATTEMPTS) {
            complete(check)
            return
        }
        val previousDelay = retries[check.key()]?.delay ?: 0L
        val nextDelay =
            (previousDelay * BACKOFF_MULTIPLIER).coerceIn(MIN_RETRY_MILLIS, MAX_RETRY_MILLIS)
        retries[check.key()] = Retry(now + nextDelay, nextDelay, attempts)
    }

    fun complete(check: ResetCheckUi) {
        pending.remove(check.key())
        retries.remove(check.key())
        finished[check.key()] = check.expectedResetAtEpochMillis
    }

    private fun ResetCheckUi.key(): String = "$accountId/$windowId/$expectedResetAtEpochMillis"

    private companion object {
        val RETENTION_MILLIS = TimeUnit.DAYS.toMillis(1)
        val MIN_RETRY_MILLIS = TimeUnit.MINUTES.toMillis(1)
        val MAX_RETRY_MILLIS = TimeUnit.MINUTES.toMillis(15)
        const val BACKOFF_MULTIPLIER = 2
        const val MAX_ATTEMPTS = 8
    }
}

private data class Retry(val at: Long, val delay: Long, val attempts: Int)
