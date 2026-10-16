package dev.sebastiano.headroom

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/** Installs Main before the original presentation tests create their ViewModels. */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress(
    "AbstractClassCanBeConcreteClass"
) // Fixture base, not a standalone suite; subclasses own test discovery.
abstract class PresentationTest {
    val dispatcher: TestDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun installMainDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun restoreMainDispatcher() {
        Dispatchers.resetMain()
    }
}
