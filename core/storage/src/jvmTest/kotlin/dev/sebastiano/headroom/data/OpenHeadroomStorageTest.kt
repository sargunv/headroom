package dev.sebastiano.headroom.data

import dev.sebastiano.headroom.data.db.TestDirectory
import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaDisplay
import dev.sebastiano.headroom.model.QuotaResult
import dev.sebastiano.headroom.model.QuotaSnapshot
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.WindowKind
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okio.FileSystem

class OpenHeadroomStorageTest {
    @Test
    fun `a new opener restores accounts snapshots settings and alerts`() = runBlocking {
        val folder = TestDirectory()
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val directory = (folder.path / "nested" / "storage").toString()
        val now = Instant.parse("2026-10-15T12:00:00Z")
        val window = QuotaWindow("weekly", "Weekly", WindowKind.Weekly, 64.0, now + 7.days, 7.days)
        val snapshot = QuotaSnapshot(Provider.Claude, "a", null, listOf(window), now)
        val fetch: suspend (Account) -> QuotaResult = { QuotaResult.Success(snapshot) }
        try {
            val firstJob = SupervisorJob()
            val firstScope = CoroutineScope(firstJob + dispatcher)
            val first = openHeadroomStorage(directory, firstScope)
            try {
                val accounts = first.accounts(fetch, { now }, firstScope)
                accounts.addAccount(Account("a", Provider.Claude, "fixture@example.com"))
                accounts.refresh("a")
                first.settings.setQuotaDisplay(QuotaDisplay.Left)
                first.alertPreferences.setEnabled("a", window.id, false)
            } finally {
                firstJob.cancelAndJoin()
                first.close()
            }

            val secondJob = SupervisorJob()
            val secondScope = CoroutineScope(secondJob + dispatcher)
            val second = openHeadroomStorage(directory, secondScope)
            try {
                val state = second.accounts(fetch, { now }, secondScope).current().single()
                assertEquals("fixture@example.com", state.account.label)
                assertEquals(snapshot, state.snapshot)
                assertEquals(QuotaDisplay.Left, second.settings.settings.first().quotaDisplay)
                assertFalse(second.alertPreferences.isEnabled("a", window).first())
                assertEquals(
                    true,
                    FileSystem.SYSTEM.exists(folder.path / "nested" / "storage" / "headroom.db"),
                )
            } finally {
                secondJob.cancelAndJoin()
                second.close()
            }
        } finally {
            dispatcher.close()
            folder.close()
        }
    }
}
