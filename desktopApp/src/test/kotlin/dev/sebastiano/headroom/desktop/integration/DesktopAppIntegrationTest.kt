package dev.sebastiano.headroom.desktop.integration

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEditable
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.sebastiano.headroom.desktop.shell.DesktopPresentation
import dev.sebastiano.headroom.desktop.shell.MacSettingsContent
import dev.sebastiano.headroom.desktop.shell.SharedHeadroomContent
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaDisplay
import dev.sebastiano.headroom.shared.SecureValueStore
import dev.sebastiano.headroom.shared.openHeadroom
import dev.sebastiano.headroom.ui.SharedUiDestination
import dev.sebastiano.headroom.ui.SharedUiNavigationRequest
import dev.sebastiano.headroom.ui.TOOLBAR_TAG
import dev.sebastiano.headroom.ui.accounts.ACCOUNTS_TAG
import dev.sebastiano.headroom.ui.accounts.SIGN_IN_KEY_FIELD_TAG
import dev.sebastiano.headroom.ui.accounts.providerOptionTag
import dev.sebastiano.headroom.ui.detail.DETAIL_TAG
import dev.sebastiano.headroom.ui.overview.DEMO_BANNER_TAG
import dev.sebastiano.headroom.ui.overview.OVERVIEW_LIST_TAG
import dev.sebastiano.headroom.ui.overview.accountCardTag
import dev.sebastiano.headroom.ui.platform.DesktopUiPlatform
import dev.sebastiano.headroom.ui.resets.RESETS_TAG
import dev.sebastiano.headroom.ui.settings.LICENCES_LIST_TAG
import dev.sebastiano.headroom.ui.settings.LICENCES_TAG
import dev.sebastiano.headroom.ui.settings.SETTINGS_ACCOUNTS_TAG
import dev.sebastiano.headroom.ui.settings.SETTINGS_LIST_TAG
import dev.sebastiano.headroom.ui.settings.SETTINGS_TAG
import dev.sebastiano.headroom.ui.stats.STATS_TAG
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class DesktopAppIntegrationTest {
    @BeforeEach
    fun installMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun restoreMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `wide layout navigates through original shared demo screens`() =
        navigationTest(Size(1100f, 800f))

    @Test
    fun `compact layout keeps original shared destinations reachable`() =
        navigationTest(Size(500f, 800f))

    @Test
    fun `the same window changes between mobile card grid and split panes when resized`() =
        desktopTest { fixture ->
            val size = mutableStateOf(DpSize(1100.dp, 800.dp))
            showApp(fixture, size)
            awaitDemo()
            onNodeWithTag(DETAIL_TAG).assertIsDisplayed()
            listOf(500, 839, 600, 599, 1100, 480, 1100).forEach { width ->
                runOnIdle { size.value = DpSize(width.dp, 800.dp) }
                waitForIdle()
                onNodeWithTag(OVERVIEW_LIST_TAG).assertIsDisplayed()
                if (width < 840) onNodeWithTag(DETAIL_TAG).assertDoesNotExist()
                else onNodeWithTag(DETAIL_TAG).assertIsDisplayed()
                if (width < 600) onNodeWithTag(TOOLBAR_TAG).assertIsDisplayed()
                else onNodeWithTag(TOOLBAR_TAG).assertDoesNotExist()
            }
            runOnIdle { size.value = DpSize(500.dp, 800.dp) }
            waitForIdle()
            val account = fixture.presentation.homeViewModel.state.value.accounts.first()
            onNodeWithTag(OVERVIEW_LIST_TAG)
                .performScrollToNode(hasTestTag(accountCardTag(account.id)))
            onNodeWithTag(accountCardTag(account.id)).performClick()
            awaitTag(DETAIL_TAG)
            listOf(1100, 500).forEach { width ->
                runOnIdle { size.value = DpSize(width.dp, 800.dp) }
                waitForIdle()
                onNodeWithTag(DETAIL_TAG).assertIsDisplayed()
                assertEquals(
                    account.id,
                    fixture.presentation.homeViewModel.detail.value?.account?.id,
                )
            }
            onNodeWithContentDescription("Back").performClick()
            awaitTag(OVERVIEW_LIST_TAG)
            fixture.assertNoExternalEffects()
        }

    @Test
    fun `settings exposes packaged notices and delegates the selected licence link`() =
        desktopTest { fixture ->
            showApp(fixture)
            awaitDemo()
            openSettings()
            onNodeWithTag(SETTINGS_LIST_TAG).performScrollToNode(hasText("Open-source licences"))
            onNodeWithText("Open-source licences").performClick()
            awaitTag(LICENCES_TAG)
            onNodeWithTag(LICENCES_LIST_TAG).performScrollToNode(hasText("Annotation"))
            onNodeWithText("Annotation").assertIsDisplayed().performClick()
            onNodeWithText("Annotation").assert(hasText("Apache License 2.0"))
            assertTrue(fixture.platform.loadLicences().contains("TERMS AND CONDITIONS"))
            onNodeWithText("View license").performClick()
            onNodeWithContentDescription("Back").performClick()
            awaitTag(SETTINGS_TAG)
            fixture.assertNoExternalEffects(
                expectedUriRequests = listOf("https://spdx.org/licenses/Apache-2.0.html")
            )
        }

    @Test
    fun `used and left choices update original quota semantics and survive reopening`() =
        desktopTest { fixture ->
            showApp(fixture)
            awaitDemo()
            awaitDescription("% used")
            onAllNodesWithText("%%", substring = true).assertCountEquals(0)
            openSettings()
            onNodeWithText("Left").performScrollTo().performClick()
            waitUntil(timeoutMillis = 10_000) {
                onAllNodes(hasText("Left") and isSelected()).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithText("Left").assertIsSelected()
            closeSettings()
            awaitDescription("% left")
            onAllNodesWithText("%%", substring = true).assertCountEquals(0)
            onAllNodes(hasContentDescription("% used", substring = true)).assertCountEquals(0)

            // Dispose the old scene before closing its ViewModels and single data session.
            runOnIdle { fixture.render = false }
            waitForIdle()
            fixture.reopen()
            runOnIdle { fixture.render = true }
            awaitDemo()
            awaitDescription("% left")
            assertEquals(
                QuotaDisplay.Left,
                fixture.headroom.data.settings.settings.first().quotaDisplay,
            )
            openSettings()
            onNodeWithText("Left").assertIsSelected()
            onNodeWithText("Used").performClick()
            closeSettings()
            awaitDescription("% used")
            onAllNodes(hasContentDescription("% left", substring = true)).assertCountEquals(0)
            fixture.assertNoExternalEffects()
        }

    @Test
    fun `API key requires explicit add stays masked and cancel never submits`() =
        desktopTest { fixture ->
            showApp(fixture)
            awaitDemo()
            onNodeWithText("Add account").performClick()
            awaitTag(ACCOUNTS_TAG)
            onAllNodes(isEditable()).assertCountEquals(0)
            onAllNodesWithText("Choose a provider").assertCountEquals(0)
            onNodeWithText("Add account").performScrollTo().performClick()
            awaitText("Choose a provider")
            // This provider requests input; no browser, OAuth polling or HTTP starts here.
            onNodeWithTag(providerOptionTag(Provider.ZAi)).performScrollTo().performClick()
            awaitTag(SIGN_IN_KEY_FIELD_TAG)
            val password = SemanticsMatcher.keyIsDefined(SemanticsProperties.Password)
            onNode(isEditable() and password).assertIsDisplayed()
            onNodeWithTag(SIGN_IN_KEY_FIELD_TAG).performTextInput(FAKE_KEY)
            if (System.getProperty("os.name").startsWith("Mac")) {
                onNodeWithTag(SIGN_IN_KEY_FIELD_TAG).performKeyInput {
                    keyDown(Key.MetaLeft)
                    pressKey(Key.DirectionLeft)
                    keyUp(Key.MetaLeft)
                }
                onNodeWithTag(SIGN_IN_KEY_FIELD_TAG)
                    .assert(
                        SemanticsMatcher.expectValue(
                            SemanticsProperties.TextSelectionRange,
                            TextRange(0),
                        )
                    )
            }
            assertNotEquals(FAKE_KEY, renderedSecret())
            onNode(isEditable() and password).assertExists()
            onNodeWithText("Save key").assertIsDisplayed()
            onNodeWithContentDescription("Cancel sign-in").performClick()
            awaitTag(ACCOUNTS_TAG)
            onAllNodes(isEditable()).assertCountEquals(0)
            onNodeWithContentDescription("Back").performClick()
            awaitText("You are looking at demo accounts. Add your own to replace them.")

            onNodeWithText("Add account").performScrollTo().performClick()
            onNodeWithTag(providerOptionTag(Provider.ZAi)).performScrollTo().performClick()
            awaitTag(SIGN_IN_KEY_FIELD_TAG)
            assertEquals("", renderedSecret())
            onNodeWithContentDescription("Cancel sign-in").performClick()
            onNodeWithContentDescription("Back").performClick()
            awaitText("You are looking at demo accounts. Add your own to replace them.")
            assertTrue(fixture.headroom.data.repository.accounts.value.isEmpty())
            fixture.assertNoExternalEffects()
        }

    @Test
    fun `original overview never claims demo accounts before storage has loaded`() {
        val dispatcher = GatedDispatcher()
        desktopTest(dispatcher = dispatcher) { fixture ->
            showApp(fixture)
            onAllNodesWithText("Demo data").assertCountEquals(0)
            onAllNodes(hasContentDescription("% used", substring = true)).assertCountEquals(0)
            assertTrue(!fixture.presentation.graph.accountsLoaded.value)
            dispatcher.release()
            awaitDemo()
            awaitDescription("% used")
            fixture.assertNoExternalEffects()
        }
    }

    @Test
    fun `host navigation requests use original pages and Escape returns through their back stack`() =
        desktopTest { fixture ->
            showApp(fixture)
            awaitDemo()
            listOf(
                    SharedUiDestination.Resets to RESETS_TAG,
                    SharedUiDestination.Stats to STATS_TAG,
                    SharedUiDestination.Overview to OVERVIEW_LIST_TAG,
                    SharedUiDestination.Accounts to ACCOUNTS_TAG,
                    SharedUiDestination.Settings to SETTINGS_TAG,
                )
                .forEachIndexed { index, (destination, tag) ->
                    runOnIdle {
                        fixture.navigationRequest =
                            SharedUiNavigationRequest(
                                serial = index.toLong(),
                                destination = destination,
                            )
                    }
                    awaitTag(tag)
                    runOnIdle { assertEquals(null, fixture.navigationRequest) }
                }
            onNodeWithTag(SETTINGS_ACCOUNTS_TAG).performClick()
            awaitTag(ACCOUNTS_TAG)
            onNodeWithContentDescription("Back")
                .performSemanticsAction(SemanticsActions.RequestFocus)
            onRoot().performKeyInput { pressKey(Key.Escape) }
            awaitTag(SETTINGS_TAG)
            onNodeWithContentDescription("Close settings")
                .performSemanticsAction(SemanticsActions.RequestFocus)
            onRoot().performKeyInput { pressKey(Key.Escape) }
            awaitTag(OVERVIEW_LIST_TAG)
            fixture.assertNoExternalEffects()
        }

    private fun navigationTest(size: Size) =
        desktopTest(size) { fixture ->
            showApp(fixture)
            awaitDemo()
            onNodeWithTag(OVERVIEW_LIST_TAG).assertIsDisplayed()
            val overview = homeNavigation("Overview", size).fetchSemanticsNode().boundsInRoot
            val resets = homeNavigation("Resets", size).fetchSemanticsNode().boundsInRoot
            if (size.width < 600f) {
                onNodeWithTag(TOOLBAR_TAG).assertIsDisplayed()
                assertEquals(overview.top, resets.top)
                assertTrue(resets.left > overview.left)
            } else {
                onNodeWithTag(TOOLBAR_TAG).assertDoesNotExist()
                assertEquals(overview.left, resets.left)
                assertTrue(resets.top > overview.top)
            }
            val account = fixture.presentation.homeViewModel.state.value.accounts.first()
            onNodeWithTag(OVERVIEW_LIST_TAG)
                .performScrollToNode(hasTestTag(accountCardTag(account.id)))
            if (size.width < 600f) {
                onNodeWithTag(accountCardTag(account.id)).assert(hasText("weekly used"))
            } else {
                awaitDescription("% used")
            }
            recordDesktopScreenshot("original-overview-${size.width.toInt()}")
            onNodeWithTag(accountCardTag(account.id)).performScrollTo().performClick()
            awaitTag(DETAIL_TAG)
            awaitDescription("Usage so far:")
            recordDesktopScreenshot("original-detail-${size.width.toInt()}")
            if (size.width < 600f) {
                onNodeWithContentDescription("Back").performClick()
                awaitTag(OVERVIEW_LIST_TAG)
            }

            homeNavigation("Resets", size).performClick()
            awaitTag(RESETS_TAG)
            onNodeWithTag(RESETS_TAG).performScrollToNode(hasText("Upcoming"))
            onNodeWithText("Upcoming").assertIsDisplayed()
            onNodeWithTag(RESETS_TAG).performScrollToNode(hasText("Used when each window reset"))
            onNodeWithText("Used when each window reset").assertIsDisplayed()
            recordDesktopScreenshot("original-resets-${size.width.toInt()}")

            homeNavigation("Stats", size).performClick()
            awaitTag(STATS_TAG)
            awaitText("Clean resets")
            onNodeWithText("Example stats from demo data").assertIsDisplayed()
            onNodeWithTag(STATS_TAG).performScrollToNode(hasText("Who works hardest"))
            onNodeWithText("Who works hardest").assertIsDisplayed()
            onNodeWithTag(STATS_TAG).performScrollToNode(hasText("When you burn quota"))
            awaitDescription("Quota use by day and hour")
            recordDesktopScreenshot("original-stats-${size.width.toInt()}")

            homeNavigation("Overview", size).performClick()
            awaitTag(OVERVIEW_LIST_TAG)
            openSettings()
            onNodeWithText("Show quotas as").assertIsDisplayed()
            onNodeWithTag(SETTINGS_LIST_TAG).performScrollToNode(hasText("Launch at login"))
            onNode(isToggleable() and hasAnyAncestor(hasTestTag("mac-settings"))).performClick()
            assertEquals(listOf(true), fixture.loginChanges.toList())
            recordDesktopScreenshot("original-settings-${size.width.toInt()}")
            onAllNodesWithText("Home screen widgets").assertCountEquals(0)
            onAllNodesWithText("Reset island (experimental)").assertCountEquals(0)
            onNodeWithTag(SETTINGS_LIST_TAG).performScrollToNode(hasText("Your accounts"))
            onNodeWithTag(SETTINGS_ACCOUNTS_TAG).performClick()
            awaitTag(ACCOUNTS_TAG)
            onNodeWithText("You are looking at demo accounts. Add your own to replace them.")
                .assertIsDisplayed()
            recordDesktopScreenshot("original-accounts-${size.width.toInt()}")
            onNodeWithText("Add account").performScrollTo().performClick()
            awaitText("Choose a provider")
            Provider.entries.forEach { provider ->
                onNodeWithTag(providerOptionTag(provider)).performScrollTo().assertIsDisplayed()
            }
            onNodeWithContentDescription("Back").performClick()
            awaitTag(ACCOUNTS_TAG)
            onNodeWithContentDescription("Back").performClick()
            awaitTag(SETTINGS_TAG)
            closeSettings()
            awaitTag(OVERVIEW_LIST_TAG)
            fixture.assertNoExternalEffects()
        }

    private fun ComposeUiTest.homeNavigation(label: String, size: Size) =
        if (size.width < 600f) onNodeWithContentDescription(label)
        else onNodeWithTag("nav-item-${label.lowercase()}")

    private fun ComposeUiTest.showApp(fixture: DesktopFixture, windowSize: State<DpSize>? = null) {
        setContent {
            TestWindowSize(windowSize?.value) {
                if (fixture.render) {
                    var login by remember { mutableStateOf(false) }
                    // AboutLibraries uses Compose's URI handler rather than UiPlatform.
                    CompositionLocalProvider(LocalUriHandler provides fixture.uriHandler) {
                        SharedHeadroomContent(
                            presentation = fixture.presentation,
                            platform = fixture.platform,
                            navigationRequest = fixture.navigationRequest,
                            onConsumeNavigation = { fixture.navigationRequest = null },
                            desktopSettings = { modifier ->
                                MacSettingsContent(
                                    launchAtLogin = login,
                                    onLaunchAtLoginChange = {
                                        login = it
                                        fixture.loginChanges.add(it)
                                    },
                                    modifier = modifier.testTag("mac-settings"),
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    private fun ComposeUiTest.openSettings() {
        onNodeWithTag(OVERVIEW_LIST_TAG).performScrollToNode(hasContentDescription("Settings"))
        onNodeWithContentDescription("Settings").performClick()
        awaitTag(SETTINGS_TAG)
    }

    private fun ComposeUiTest.closeSettings() {
        onNodeWithTag(SETTINGS_LIST_TAG)
            .performScrollToNode(hasContentDescription("Close settings"))
        onNodeWithContentDescription("Close settings").performClick()
        awaitTag(OVERVIEW_LIST_TAG)
    }

    private fun ComposeUiTest.renderedSecret(): String {
        val layouts = mutableListOf<TextLayoutResult>()
        onNodeWithTag(SIGN_IN_KEY_FIELD_TAG).performSemanticsAction(
            SemanticsActions.GetTextLayoutResult
        ) {
            it(layouts)
        }
        return layouts.single().layoutInput.text.text
    }

    private fun ComposeUiTest.awaitDemo() {
        awaitTag(DEMO_BANNER_TAG)
        onNodeWithText("These numbers are examples. Add an account to see your own limits.")
            .assertIsDisplayed()
    }

    private fun ComposeUiTest.awaitTag(tag: String) {
        waitUntil(timeoutMillis = 10_000) {
            onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.TestTag, tag))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        waitForIdle()
    }

    private fun ComposeUiTest.awaitText(text: String, substring: Boolean = false) {
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun ComposeUiTest.awaitDescription(text: String) {
        waitUntil(timeoutMillis = 10_000) {
            onAllNodes(hasContentDescription(text, substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun desktopTest(
        size: Size = Size(1100f, 800f),
        dispatcher: CoroutineDispatcher = Dispatchers.Default,
        block: suspend ComposeUiTest.(DesktopFixture) -> Unit,
    ) {
        val fixture = DesktopFixture(dispatcher)
        try {
            runSkikoComposeUiTest(size = size, testTimeout = 60.seconds) { block(fixture) }
        } finally {
            (dispatcher as? GatedDispatcher)?.release()
            // Compose is disposed before models and their database are closed.
            runBlocking { fixture.close() }
        }
    }

    private companion object {
        const val FAKE_KEY = "demo-key-not-a-real-credential"
    }
}

private class DesktopFixture(private val dispatcher: CoroutineDispatcher) {
    private val directory = Files.createTempDirectory("headroom-shared-compose-test").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    var headroom = openHeadroom(directory.path, FakeSecrets, dispatcher)
        private set

    var presentation = newPresentation()
        private set

    var render by mutableStateOf(true)
    var navigationRequest by mutableStateOf<SharedUiNavigationRequest?>(null)
    val openedUrls = CopyOnWriteArrayList<String>()
    val uriRequests = CopyOnWriteArrayList<String>()
    val uriHandler =
        object : UriHandler {
            override fun openUri(uri: String) {
                uriRequests.add(uri)
            }
        }
    val copiedTexts = CopyOnWriteArrayList<String>()
    val loginChanges = CopyOnWriteArrayList<Boolean>()
    val platform = DesktopUiPlatform(openedUrls::add, copiedTexts::add, is24Hour = true)

    private fun newPresentation() =
        DesktopPresentation(headroom, scope, simulatedLatency = Duration.ZERO)

    suspend fun reopen() {
        presentation.close()
        headroom.closeAndJoin()
        headroom = openHeadroom(directory.path, FakeSecrets, dispatcher)
        presentation = newPresentation()
    }

    fun assertNoExternalEffects(expectedUriRequests: List<String> = emptyList()) {
        assertTrue(openedUrls.isEmpty(), "Tests must not launch a browser")
        assertEquals(
            expectedUriRequests,
            uriRequests.toList(),
            "Only explicit licence links may be requested",
        )
        assertTrue(copiedTexts.isEmpty(), "Tests must not write the clipboard")
        assertTrue(headroom.data.repository.accounts.value.isEmpty(), "No sign-in may be submitted")
    }

    suspend fun close() {
        try {
            presentation.close()
            headroom.closeAndJoin()
        } finally {
            scope.cancel()
            directory.deleteRecursively()
        }
    }

    private object FakeSecrets : SecureValueStore {
        override fun read(key: String): String? = null

        override fun write(key: String, value: String): Boolean =
            error("Tests must not save sign-ins")

        override fun delete(key: String): Boolean = true
    }
}

private class GatedDispatcher(private val delegate: CoroutineDispatcher = Dispatchers.Default) :
    CoroutineDispatcher() {
    private val lock = Any()
    private var released = false
    private val queued = mutableListOf<Pair<CoroutineContext, Runnable>>()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        synchronized(lock) {
            if (!released) {
                queued.add(context to block)
                return
            }
        }
        delegate.dispatch(context, block)
    }

    fun release() {
        val pending =
            synchronized(lock) {
                released = true
                queued.toList().also { queued.clear() }
            }
        pending.forEach { (context, block) -> delegate.dispatch(context, block) }
    }
}
