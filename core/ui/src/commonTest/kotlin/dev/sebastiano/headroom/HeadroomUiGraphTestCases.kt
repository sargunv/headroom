package dev.sebastiano.headroom

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SAVED_STATE_REGISTRY_OWNER_KEY
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.enableSavedStateHandles
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.savedstate.SavedState
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.savedState
import dev.sebastiano.headroom.appdata.DemoResetHistory
import dev.sebastiano.headroom.appdata.InMemoryAlertPreferences
import dev.sebastiano.headroom.model.FakeQuotaRepository
import dev.sebastiano.headroom.signin.FakeSignInController
import dev.sebastiano.headroom.ui.accounts.AccountsViewModel
import dev.sebastiano.headroom.ui.home.HomeViewModel
import dev.sebastiano.headroom.ui.settings.SettingsViewModel
import dev.sebastiano.headroom.ui.stats.StatsViewModel
import dev.sebastiano.headroom.widgets.WidgetPinner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress(
    "AbstractClassCanBeConcreteClass"
) // Only platform subclasses discover these cases, once per runner.
abstract class HeadroomUiGraphTestCases : PresentationTest() {
    @Test
    fun `the original factories work with a plain desktop ViewModel owner`() =
        runTest(dispatcher) {
            val graph = graph()
            val owner = PlainOwner()
            try {
                val home =
                    ViewModelProvider.create(owner, graph.homeViewModelFactory)[
                            HomeViewModel::class]
                backgroundScope.launch { home.detail.collect {} }
                runCurrent()
                home.select("demo-codex")
                runCurrent()
                assertEquals("demo-codex", home.detail.value?.account?.id)
                assertSame(
                    home,
                    ViewModelProvider.create(owner, graph.homeViewModelFactory)[
                            HomeViewModel::class],
                )

                val accounts =
                    ViewModelProvider.create(owner, graph.accountsViewModelFactory)[
                            AccountsViewModel::class]
                val settings =
                    ViewModelProvider.create(owner, graph.settingsViewModelFactory)[
                            SettingsViewModel::class]
                val stats =
                    ViewModelProvider.create(owner, graph.statsViewModelFactory)[
                            StatsViewModel::class]
                assertEquals(4, accounts.state.value.accounts.size)
                assertEquals("test-version", settings.state.value.appVersion)
                assertEquals(true, stats.state.value.loading)
            } finally {
                owner.viewModelStore.clear()
            }
        }

    @Test
    fun `a saved state owner restores the selected account through the shared factory`() =
        runTest(dispatcher) {
            val graph = graph()
            val first = SavedOwner()
            val restored: SavedOwner
            try {
                val home = first.home(graph)
                home.select("demo-codex")
                restored = SavedOwner(first.save())
            } finally {
                first.viewModelStore.clear()
            }
            try {
                val home = restored.home(graph)
                backgroundScope.launch { home.detail.collect {} }
                runCurrent()
                assertEquals("demo-codex", home.detail.value?.account?.id)
            } finally {
                restored.viewModelStore.clear()
            }
        }

    private fun TestScope.graph(): HeadroomUiGraph {
        val now = Instant.parse("2026-09-27T12:32:00Z")
        return HeadroomUiGraph(
            quotaRepository = FakeQuotaRepository({ now }),
            isDemo = MutableStateFlow(true),
            accountsLoaded = MutableStateFlow(true),
            alertPreferences = InMemoryAlertPreferences(),
            clock = { now },
            zone = TimeZone.UTC,
            signInController = FakeSignInController(),
            widgetPinner = WidgetPinner { false },
            resetHistory = DemoResetHistory,
            tickInterval = null,
            appVersion = "test-version",
            statsDispatcher = dispatcher,
            resetScope = backgroundScope,
        )
    }

    private class PlainOwner : ViewModelStoreOwner {
        override val viewModelStore: ViewModelStore = ViewModelStore()
    }

    private class SavedOwner(restored: SavedState? = null) :
        ViewModelStoreOwner, SavedStateRegistryOwner {
        override val viewModelStore: ViewModelStore = ViewModelStore()
        override val lifecycle: LifecycleRegistry = LifecycleRegistry.createUnsafe(this)
        private val controller = SavedStateRegistryController.create(this)
        override val savedStateRegistry: SavedStateRegistry = controller.savedStateRegistry

        init {
            controller.performAttach()
            enableSavedStateHandles()
            controller.performRestore(restored)
            lifecycle.currentState = Lifecycle.State.CREATED
        }

        fun home(graph: HeadroomUiGraph): HomeViewModel {
            val extras =
                MutableCreationExtras().apply {
                    this[SAVED_STATE_REGISTRY_OWNER_KEY] = this@SavedOwner
                    this[VIEW_MODEL_STORE_OWNER_KEY] = this@SavedOwner
                }
            return ViewModelProvider.create(this, graph.homeViewModelFactory, extras)[
                    HomeViewModel::class]
        }

        fun save(): SavedState = savedState().also(controller::performSave)
    }
}
