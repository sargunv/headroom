package dev.sebastiano.headroom.desktop.shell

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.sebastiano.headroom.model.QuotaDisplay
import dev.sebastiano.headroom.shared.SecureValueStore
import dev.sebastiano.headroom.shared.openHeadroom
import dev.sebastiano.headroom.ui.home.HomeViewModel
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopPresentationTest {
    @TempDir lateinit var directory: Path
    private val dispatchers = PresentationTestDispatchers()

    @BeforeEach
    fun installMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun restoreMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `window compositions and tray use the original view model instance`() = runTest {
        val headroom = openHeadroom(directory.toString(), FakeSecrets, dispatchers.storage)
        val presentation =
            DesktopPresentation(headroom, backgroundScope, simulatedLatency = Duration.ZERO)
        try {
            val restored =
                ViewModelProvider.create(presentation, presentation.graph.homeViewModelFactory)[
                        HomeViewModel::class]
            assertSame(presentation.homeViewModel, restored)
            assertSame(headroom.data.settings, presentation.graph.settings)
            withContext(dispatchers.waiting) {
                withTimeout(10.seconds) {
                    presentation.graph.accountsLoaded.first { it }
                    presentation.homeViewModel.state.first {
                        it.accountsLoaded && it.accounts.isNotEmpty()
                    }
                }
            }
        } finally {
            presentation.close()
            headroom.closeAndJoin()
        }
    }

    @Test
    fun `closing presentation cancels models without opening or closing another data session`() =
        runTest {
            val headroom = openHeadroom(directory.toString(), FakeSecrets, dispatchers.storage)
            val presentation =
                DesktopPresentation(headroom, backgroundScope, simulatedLatency = Duration.ZERO)
            val modelJob =
                checkNotNull(presentation.homeViewModel.viewModelScope.coroutineContext[Job])
            try {
                presentation.close()
                assertTrue(modelJob.isCompleted)
                headroom.data.settings.setQuotaDisplay(QuotaDisplay.Left)
                assertTrue(
                    headroom.data.settings.settings.first().quotaDisplay == QuotaDisplay.Left
                )
            } finally {
                presentation.close()
                headroom.closeAndJoin()
            }
        }

    private object FakeSecrets : SecureValueStore {
        override fun read(key: String): String? = null

        override fun write(key: String, value: String): Boolean =
            error("Presentation tests must not save credentials")

        override fun delete(key: String): Boolean = true
    }
}

private class PresentationTestDispatchers(
    val storage: CoroutineDispatcher = Dispatchers.Default,
    val waiting: CoroutineDispatcher = Dispatchers.IO,
)
