package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.signin.FakeSignInController
import dev.sebastiano.headroom.ui.accounts.ACCOUNTS_TAG
import dev.sebastiano.headroom.ui.accounts.SIGN_IN_KEY_FIELD_TAG
import dev.sebastiano.headroom.ui.detail.DETAIL_TAG
import dev.sebastiano.headroom.ui.overview.OVERVIEW_LIST_TAG
import dev.sebastiano.headroom.ui.settings.SETTINGS_TAG
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class SharedNavigationTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun explicitHostNavigationWinsOverAnEarlierAccountRequest() {
        val graph = testGraph(rule.activity)
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                HeadroomApp(
                    graph = graph,
                    openAccountRequest = OpenAccountRequest("demo-claude", serial = 1),
                    navigationRequest = SharedUiNavigationRequest(2, SharedUiDestination.Settings),
                )
            }
        }
        rule.onNodeWithTag(SETTINGS_TAG).assertIsDisplayed()
    }

    @Test
    fun overviewHostCommandReturnsFromTheAccountDetailToTheList() {
        val graph = testGraph(rule.activity)
        var navigation by mutableStateOf<SharedUiNavigationRequest?>(null)
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                HeadroomApp(
                    graph = graph,
                    openAccountRequest = OpenAccountRequest("demo-claude", serial = 1),
                    navigationRequest = navigation,
                )
            }
        }
        rule.onNodeWithTag(DETAIL_TAG).assertIsDisplayed()
        rule.runOnIdle { navigation = SharedUiNavigationRequest(2, SharedUiDestination.Overview) }
        rule.onNodeWithTag(OVERVIEW_LIST_TAG).assertIsDisplayed()
    }

    @Test
    fun newerAccountRequestWinsOverOlderHostNavigation() {
        val graph = testGraph(rule.activity)
        var consumedNavigation = 0
        var consumedAccount = 0
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                HeadroomApp(
                    graph = graph,
                    openAccountRequest = OpenAccountRequest("demo-claude", serial = 2),
                    onConsumeOpenAccount = { consumedAccount++ },
                    navigationRequest = SharedUiNavigationRequest(1, SharedUiDestination.Settings),
                    onConsumeNavigation = { consumedNavigation++ },
                )
            }
        }
        rule.onNodeWithTag(DETAIL_TAG).assertIsDisplayed()
        rule.runOnIdle {
            assertEquals(1, consumedNavigation, "Discard the stale command instead of replaying it")
            assertEquals(1, consumedAccount)
        }
    }

    @Test
    fun queuedSettingsOpensAfterReauthenticationIsCancelled() {
        queuedDestinationOpensAfterReauthenticationIsCancelled(
            SharedUiDestination.Settings,
            SETTINGS_TAG,
        )
    }

    @Test
    fun queuedAccountsOpensAfterReauthenticationIsCancelled() {
        queuedDestinationOpensAfterReauthenticationIsCancelled(
            SharedUiDestination.Accounts,
            ACCOUNTS_TAG,
        )
    }

    private fun queuedDestinationOpensAfterReauthenticationIsCancelled(
        destination: SharedUiDestination,
        expectedTag: String,
    ) {
        val graph =
            testGraph(
                rule.activity,
                realAccounts =
                    DemoData.manyAccounts(FIXED_NOW).filter { it.account.id == "demo-zai" },
                signInController = FakeSignInController(),
            )
        var navigation by mutableStateOf<SharedUiNavigationRequest?>(null)
        var consumedNavigation = 0
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                HeadroomApp(
                    graph = graph,
                    openAccountRequest =
                        OpenAccountRequest("demo-zai", serial = 1, signInAgain = true),
                    navigationRequest = navigation,
                    onConsumeNavigation = { consumedNavigation++ },
                )
            }
        }
        rule.onNodeWithTag(SIGN_IN_KEY_FIELD_TAG).assertIsDisplayed()
        rule.runOnIdle { navigation = SharedUiNavigationRequest(2, destination) }
        rule.onNodeWithTag(SIGN_IN_KEY_FIELD_TAG).assertIsDisplayed()
        rule.runOnIdle { assertEquals(0, consumedNavigation, "Wait until sign-in ends") }

        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }

        rule.onNodeWithTag(expectedTag).assertIsDisplayed()
        rule.onNodeWithTag(SIGN_IN_KEY_FIELD_TAG).assertDoesNotExist()
        rule.runOnIdle { assertEquals(1, consumedNavigation) }
    }
}
