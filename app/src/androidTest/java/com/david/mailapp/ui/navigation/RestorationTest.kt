package com.david.mailapp.ui.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso
import com.david.mailapp.R
import com.david.mailapp.ui.test.RecreateMode
import com.david.mailapp.ui.test.RestorationProbe
import com.david.mailapp.ui.test.RestorationTestActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Fase 4.5B — seis contratos ejecutados con Activity y proceso equivalente. */
class RestorationTest {

    @get:Rule
    val rule = createAndroidComposeRule<RestorationTestActivity>()

    private val ctx get() = rule.activity.applicationContext

    @Before
    fun resetProbe() {
        RestorationProbe.reset()
        RestorationTestActivity.mode = RecreateMode.ACTIVITY
    }

    @After
    fun releasePendingSend() {
        RestorationTestActivity.mode = RecreateMode.ACTIVITY
    }

    private fun recreate(mode: RecreateMode) {
        val oldActivityId = System.identityHashCode(rule.activity)
        RestorationTestActivity.mode = mode
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
        assertNotEquals("ActivityScenario debe crear otra Activity", oldActivityId, System.identityHashCode(rule.activity))
    }

    private fun waitForText(text: String) {
        rule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                rule.onNodeWithText(text).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        rule.onNodeWithText(text).assertIsDisplayed()
    }

    @Test fun detail_activeAfterRecreate_activity() = detail(RecreateMode.ACTIVITY)
    @Test fun detail_activeAfterRecreate_process() = detail(RecreateMode.PROCESS_EQUIVALENT)

    private fun detail(mode: RecreateMode) {
        rule.onNodeWithTag("nav_detail").performClick()
        rule.onNodeWithText("Detail d1").assertIsDisplayed()
        recreate(mode)
        rule.onNodeWithText("Detail d1").assertIsDisplayed()
        Espresso.pressBack()
        rule.waitForIdle()
        rule.onNodeWithTag("inbox_list").assertIsDisplayed()
    }

    @Test fun search_restoresExactState_activity() = search(RecreateMode.ACTIVITY)
    @Test fun search_restoresExactState_process() = search(RecreateMode.PROCESS_EQUIVALENT)

    private fun search(mode: RecreateMode) {
        rule.onNodeWithTag("nav_search").performClick()
        rule.onNodeWithTag("search_input").performTextInput("hello")
        waitForText("R:10")
        rule.onNodeWithText("Q:hello").assertIsDisplayed()
        rule.onNodeWithText("T:next_page_1").assertIsDisplayed()

        rule.onNodeWithTag("search_load_next").performClick()
        waitForText("R:20")
        rule.onNodeWithText("T:next_page_2").assertIsDisplayed()
        rule.onNodeWithTag("search_list").performScrollToIndex(6)
        rule.onNodeWithText("SP:6:0").assertIsDisplayed()

        assertEquals(listOf(null, "next_page_1"), RestorationProbe.searchCalls.map { it.pageToken })
        assertEquals(1, RestorationProbe.searchViewModelIds.size)
        val firstVmId = RestorationProbe.searchViewModelIds.single()

        recreate(mode)

        rule.onNodeWithText("Q:hello").assertIsDisplayed()
        rule.onNodeWithText("SP:6:0").assertIsDisplayed()
        if (mode == RecreateMode.ACTIVITY) {
            rule.onNodeWithText("R:20").assertIsDisplayed()
            rule.onNodeWithText("T:next_page_2").assertIsDisplayed()
            assertEquals(1, RestorationProbe.searchViewModelIds.size)
            assertEquals(2, RestorationProbe.searchCalls.size)
        } else {
            waitForText("R:10")
            rule.onNodeWithText("T:next_page_1").assertIsDisplayed()
            assertEquals(2, RestorationProbe.searchViewModelIds.size)
            assertNotEquals(firstVmId, RestorationProbe.searchViewModelIds.last())
            assertEquals(null, RestorationProbe.searchCalls.last().pageToken)
        }
    }

    @Test fun compose_restoresExactFields_activity() = compose(RecreateMode.ACTIVITY)
    @Test fun compose_restoresExactFields_process() = compose(RecreateMode.PROCESS_EQUIVALENT)

    private fun compose(mode: RecreateMode) {
        rule.onNodeWithTag("nav_compose_reply").performClick()
        waitForComposeOriginal()

        rule.onNodeWithTag("compose_input_to").performTextReplacement("a@b.com")
        rule.onNodeWithTag("compose_input_cc").performTextReplacement("c@d.com")
        rule.onNodeWithTag("compose_input_bcc").performTextReplacement("e@f.com")
        rule.onNodeWithTag("compose_input_subject").performTextReplacement("Hi")
        rule.onNodeWithTag("compose_input_body").performTextReplacement("Hello world")
        rule.onNodeWithTag("compose_toggle_ccbcc").performClick()

        assertEquals(1, RestorationProbe.composeViewModelIds.size)
        val firstVmId = RestorationProbe.composeViewModelIds.single()
        recreate(mode)

        rule.onNodeWithText("mode:REPLY").assertIsDisplayed()
        rule.onNodeWithText("original:reply-42").assertIsDisplayed()
        rule.onNodeWithText("to:a@b.com").assertIsDisplayed()
        rule.onNodeWithText("cc:c@d.com").assertIsDisplayed()
        rule.onNodeWithText("bcc:e@f.com").assertIsDisplayed()
        rule.onNodeWithText("subj:Hi").assertIsDisplayed()
        rule.onNodeWithText("body:Hello world").assertIsDisplayed()
        rule.onNodeWithText("ccBcc:true").assertIsDisplayed()

        if (mode == RecreateMode.ACTIVITY) {
            assertEquals(1, RestorationProbe.composeViewModelIds.size)
        } else {
            assertEquals(2, RestorationProbe.composeViewModelIds.size)
            assertNotEquals(firstVmId, RestorationProbe.composeViewModelIds.last())
        }

        // A running send is memory-only: retained by a configuration recreation,
        // but absent after the fresh-ViewModel process-equivalent path.
        RestorationProbe.composeSources.last().holdSend = true
        rule.runOnUiThread {
            RestorationProbe.composeViewModels.last().onSend()
            val sendingState = RestorationProbe.composeViewModels.last().uiState.value
            assertTrue(sendingState.toString(), sendingState.isSending)
        }
        recreate(mode)
        if (mode == RecreateMode.ACTIVITY) {
            rule.onNodeWithText("sending:true").assertIsDisplayed()
        } else {
            rule.onNodeWithText("sending:false").assertIsDisplayed()
            rule.onNodeWithText("resultNull:true").assertIsDisplayed()
        }
    }

    private fun waitForComposeOriginal() = waitForComposeText("original:reply-42")

    private fun waitForComposeText(text: String) {
        rule.waitUntil(timeoutMillis = 5_000) {
            runCatching { rule.onNodeWithText(text).fetchSemanticsNode(); true }.getOrDefault(false)
        }
        rule.onNodeWithText(text).assertIsDisplayed()
    }

    @Test fun settings_appearanceSurvives_activity() = settings(RecreateMode.ACTIVITY)
    @Test fun settings_appearanceSurvives_process() = settings(RecreateMode.PROCESS_EQUIVALENT)

    private fun settings(mode: RecreateMode) {
        rule.onNodeWithTag("nav_settings").performClick()
        rule.onNodeWithText(ctx.getString(R.string.settings_appearance)).performClick()
        rule.onNodeWithText(ctx.getString(R.string.theme_title)).assertIsDisplayed()

        recreate(mode)

        rule.onNodeWithText(ctx.getString(R.string.theme_title)).assertIsDisplayed()
        Espresso.pressBack()
        rule.waitForIdle()
        rule.onNodeWithText(ctx.getString(R.string.settings_account)).assertIsDisplayed()
        rule.onNodeWithText(ctx.getString(R.string.theme_title)).assertDoesNotExist()
    }

    @Test fun scroll_preservesIndependentPositions_activity() = scroll(RecreateMode.ACTIVITY)
    @Test fun scroll_preservesIndependentPositions_process() = scroll(RecreateMode.PROCESS_EQUIVALENT)

    private fun scroll(mode: RecreateMode) {
        rule.onNodeWithTag("inbox_list").performScrollToIndex(30)
        rule.onNodeWithText("IP:30:0").assertIsDisplayed()

        rule.onNodeWithTag("nav_trash").performClick()
        rule.onNodeWithTag("trash_list").performScrollToIndex(15)
        rule.onNodeWithText("TP:15:0").assertIsDisplayed()

        rule.onNodeWithTag("nav_inbox").performClick()
        rule.onNodeWithText("IP:30:0").assertIsDisplayed()
        rule.onNodeWithTag("nav_search").performClick()
        rule.onNodeWithTag("search_input").performTextInput("scrolltest")
        waitForText("R:10")
        rule.onNodeWithTag("search_list").performScrollToIndex(6)
        rule.onNodeWithText("SP:6:0").assertIsDisplayed()

        recreate(mode)

        waitForText("SP:6:0")
        Espresso.pressBack()
        rule.waitForIdle()
        waitForText("IP:30:0")
        rule.onNodeWithTag("nav_trash").performClick()
        waitForText("TP:15:0")
    }

    @Test fun searchCycle_preservedUnderDetail_resetOnClose_activity() = searchCycle(RecreateMode.ACTIVITY)
    @Test fun searchCycle_preservedUnderDetail_resetOnClose_process() = searchCycle(RecreateMode.PROCESS_EQUIVALENT)

    private fun searchCycle(mode: RecreateMode) {
        rule.onNodeWithTag("nav_search").performClick()
        rule.onNodeWithTag("search_input").performTextInput("cycle")
        waitForText("R:10")
        rule.onNodeWithTag("search_list").performScrollToIndex(6)
        rule.onNodeWithTag("nav_detail").performClick()
        rule.onNodeWithText("Detail d1").assertIsDisplayed()

        recreate(mode)

        rule.onNodeWithText("Detail d1").assertIsDisplayed()
        Espresso.pressBack()
        rule.waitForIdle()
        rule.onNodeWithText("Q:cycle").assertIsDisplayed()
        rule.onNodeWithText("SP:6:0").assertIsDisplayed()
        Espresso.pressBack()
        rule.waitForIdle()
        rule.onNodeWithTag("inbox_list").assertIsDisplayed()
        rule.onNodeWithTag("nav_search").performClick()
        rule.onNodeWithText("Q:").assertIsDisplayed()
        rule.onNodeWithText("SP:0:0").assertIsDisplayed()
        assertTrue(RestorationProbe.searchViewModelIds.isNotEmpty())
    }
}
