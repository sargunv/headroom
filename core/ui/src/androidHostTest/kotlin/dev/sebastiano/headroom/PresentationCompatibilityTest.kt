package dev.sebastiano.headroom

import dev.sebastiano.headroom.appdata.DemoResetHistory
import dev.sebastiano.headroom.appdata.DemoUsageHistory
import dev.sebastiano.headroom.model.FakeQuotaRepository
import dev.sebastiano.headroom.ui.stats.StatsViewModel
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone

/** Existing Android Java-time constructor calls remain valid after the common extraction. */
class PresentationCompatibilityTest : PresentationTest() {
    private val now = Instant.parse("2026-09-27T12:32:00Z")

    @Test
    fun demoUsageAcceptsTheOriginalJavaZoneArgument() =
        runTest(dispatcher) {
            val account = FakeQuotaRepository({ now }).accounts.value.first()
            val window = requireNotNull(account.primaryWindow)
            val legacy = DemoUsageHistory(DemoResetHistory, { now }, ZoneOffset.UTC)
            val common = DemoUsageHistory(DemoResetHistory, { now }, TimeZone.UTC)
            assertEquals(
                common.points(account.account.id, window).first(),
                legacy.points(account.account.id, window).first(),
            )
        }

    @Test
    fun statsAcceptsTheOriginalJavaZoneArgument() {
        val stats =
            StatsViewModel(
                repository = FakeQuotaRepository({ now }),
                history = DemoUsageHistory(DemoResetHistory, { now }, ZoneOffset.UTC),
                isDemo = MutableStateFlow(true),
                clock = { now },
                zone = ZoneOffset.UTC,
                computeDispatcher = dispatcher,
            )
        assertTrue(stats.state.value.loading)
    }
}
