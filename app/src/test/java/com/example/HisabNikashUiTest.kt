package com.example

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "w411dp-h891dp-xxhdpi")
class HisabNikashUiTest {

    @get:Rule(order = 0)
    val resetRule = object : ExternalResource() {
        override fun before() {
            runBlocking {
                val app = ApplicationProvider.getApplicationContext<HisabNikashApp>()
                app.container.prefsStore.resetAll()
                app.container.financeRepository.clearAllLocalData()
            }
        }
    }

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `onboarding to home to add expense updates live balance and Bangla switch preserves value on API 29`() {
        composeRule.waitForIdle()
        waitUntilTag("onboarding_skip_btn")

        // Skip onboarding with default Cash Wallet (0 balance)
        composeRule.onNodeWithTag("onboarding_skip_btn").performClick()
        composeRule.waitForIdle()

        waitUntilTag("home_total_balance", timeoutMs = 10_000)
        composeRule.onNodeWithTag("home_total_balance", useUnmergedTree = true).assertIsDisplayed()

        // Quick Add Expense (250.50 BDT)
        composeRule.onNodeWithTag("quick_expense_btn").performClick()
        waitUntilTag("entry_amount_input")

        composeRule.onNodeWithTag("entry_amount_input").performTextReplacement("250.50")
        composeRule.onNodeWithTag("entry_note_input").performTextInput("Lunch")
        composeRule.onNodeWithTag("entry_save_btn").performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("-৳250.50", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        // Appears on Total Balance, Available Balance, Net Cash Flow, and Recent Activity row
        composeRule.onAllNodesWithText("-৳250.50", useUnmergedTree = true).assertCountEquals(4)
        composeRule.onAllNodesWithText("-৳250.50", useUnmergedTree = true).onFirst().assertIsDisplayed()

        // Switch language to Bangla in Settings and verify Bangla formatted numeric value preserves exact digits
        composeRule.onNodeWithTag("nav_settings").performClick()
        waitUntilTag("lang_bn_chip")
        composeRule.onNodeWithTag("lang_bn_chip").performClick()

        composeRule.onNodeWithTag("nav_home").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("-৳২৫০.৫০", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("-৳২৫০.৫০", useUnmergedTree = true).onFirst().assertIsDisplayed()
    }

    private fun waitUntilTag(tag: String, timeoutMs: Long = 5_000) {
        try {
            composeRule.waitUntil(timeoutMillis = timeoutMs) {
                composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (t: Throwable) {
            val tree = runCatching { composeRule.onRoot(useUnmergedTree = true).printToString() }.getOrElse { it.toString() }
            throw AssertionError("Tag '$tag' not found within ${timeoutMs}ms. Tree:\n$tree", t)
        }
    }
}
