package com.david.mailapp.ui.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.DialogNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.navigation.toRoute
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import com.david.mailapp.R
import com.david.mailapp.feature.compose.ComposeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainNavigationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun mainScreen_fabVisibility_basedOnRoute() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        lateinit var navController: TestNavHostController

        composeTestRule.setContent {
            navController = TestNavHostController(context).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
                navigatorProvider.addNavigator(DialogNavigator())
            }
            MainScreen(
                navController = navController
            )
        }

        composeTestRule.waitForIdle()

        // 1. Inbox -> FAB visible
        composeTestRule.onNodeWithTag("fab_compose").assertIsDisplayed()

        // 2. Navigate to Settings -> FAB hidden
        composeTestRule.runOnUiThread {
            navController.navigate(MainRoute.Settings)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("fab_compose").assertDoesNotExist()

        // 3. Navigate to Trash -> FAB visible
        composeTestRule.runOnUiThread {
            navController.navigate(MainRoute.Trash)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("fab_compose").assertIsDisplayed()

        // 4. Navigate to Search -> FAB hidden
        composeTestRule.runOnUiThread {
            navController.navigate(MainRoute.Search)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("fab_compose").assertDoesNotExist()

        // 5. Navigate to Detail -> FAB hidden
        composeTestRule.runOnUiThread {
            navController.navigate(MainRoute.EmailDetail("123"))
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("fab_compose").assertDoesNotExist()

        // 6. Navigate to Compose -> FAB hidden
        composeTestRule.runOnUiThread {
            navController.navigate(MainRoute.Compose(ComposeMode.WRITE))
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("fab_compose").assertDoesNotExist()
    }

    @Test
    fun testNavigationFlowsAndReconstruction() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        lateinit var navController: TestNavHostController

        composeTestRule.setContent {
            navController = TestNavHostController(context).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
                navigatorProvider.addNavigator(DialogNavigator())
            }
            MainScreen(
                navController = navController
            )
        }

        composeTestRule.waitForIdle()

        // 1. Navigate to EmailDetail and verify exact reconstruction (including sensitive chars)
        val sensitiveId = "id/with/slashes?and=queries&spaces= "
        composeTestRule.runOnUiThread {
            navController.navigate(MainRoute.EmailDetail(sensitiveId))
        }
        composeTestRule.waitForIdle()
        assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.EmailDetail>() == true)
        val detailRoute = navController.currentBackStackEntry?.toRoute<MainRoute.EmailDetail>()
        assertEquals(sensitiveId, detailRoute?.emailId)

        // 2. Navigate to Compose (WRITE) and reconstruct
        composeTestRule.runOnUiThread {
            navController.navigate(MainRoute.Compose(ComposeMode.WRITE))
        }
        composeTestRule.waitForIdle()
        assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Compose>() == true)
        var composeRoute = navController.currentBackStackEntry?.toRoute<MainRoute.Compose>()
        assertEquals(ComposeMode.WRITE, composeRoute?.mode)
        assertEquals(null, composeRoute?.originalEmailId)

        // 3. Navigate to Compose (REPLY) and reconstruct
        composeTestRule.runOnUiThread {
            navController.navigate(MainRoute.Compose(ComposeMode.REPLY, "orig123"))
        }
        composeTestRule.waitForIdle()
        composeRoute = navController.currentBackStackEntry?.toRoute<MainRoute.Compose>()
        assertEquals(ComposeMode.REPLY, composeRoute?.mode)
        assertEquals("orig123", composeRoute?.originalEmailId)

        // 4. Navigate to Compose (FORWARD) and reconstruct
        composeTestRule.runOnUiThread {
            navController.navigate(MainRoute.Compose(ComposeMode.FORWARD, "orig456"))
        }
        composeTestRule.waitForIdle()
        composeRoute = navController.currentBackStackEntry?.toRoute<MainRoute.Compose>()
        assertEquals(ComposeMode.FORWARD, composeRoute?.mode)
        assertEquals("orig456", composeRoute?.originalEmailId)
    }

    @Test
    fun testDetailToComposeAndBackPreservesDetail() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        lateinit var navController: TestNavHostController

        composeTestRule.setContent {
            navController = TestNavHostController(context).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
                navigatorProvider.addNavigator(DialogNavigator())
            }
            MainScreen(
                navController = navController
            )
        }

        composeTestRule.waitForIdle()

        // 1. Navigate to Detail
        composeTestRule.runOnUiThread {
            navController.navigate(MainRoute.EmailDetail("detail_id"))
        }
        composeTestRule.waitForIdle()
        val detailEntry = navController.currentBackStackEntry
        assertTrue(detailEntry?.destination?.hasRoute<MainRoute.EmailDetail>() == true)

        // 2. Detail -> Reply
        composeTestRule.runOnUiThread {
            navController.navigate(MainRoute.Compose(ComposeMode.REPLY, "detail_id"))
        }
        composeTestRule.waitForIdle()
        assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Compose>() == true)
        val replyRoute = navController.currentBackStackEntry?.toRoute<MainRoute.Compose>()
        assertEquals(ComposeMode.REPLY, replyRoute?.mode)
        assertEquals("detail_id", replyRoute?.originalEmailId)

        // Reply -> Back (popBackStack) -> should preserve the same Detail entry in back stack
        composeTestRule.runOnUiThread {
            navController.popBackStack()
        }
        composeTestRule.waitForIdle()
        var postBackEntry = navController.currentBackStackEntry
        assertTrue(postBackEntry?.destination?.hasRoute<MainRoute.EmailDetail>() == true)
        assertEquals(detailEntry, postBackEntry)

        // 3. Detail -> Forward
        composeTestRule.runOnUiThread {
            navController.navigate(MainRoute.Compose(ComposeMode.FORWARD, "detail_id"))
        }
        composeTestRule.waitForIdle()
        assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Compose>() == true)
        val forwardRoute = navController.currentBackStackEntry?.toRoute<MainRoute.Compose>()
        assertEquals(ComposeMode.FORWARD, forwardRoute?.mode)
        assertEquals("detail_id", forwardRoute?.originalEmailId)

        // Forward -> Back (popBackStack) -> should preserve the same Detail entry in back stack
        composeTestRule.runOnUiThread {
            navController.popBackStack()
        }
        composeTestRule.waitForIdle()
        postBackEntry = navController.currentBackStackEntry
        assertTrue(postBackEntry?.destination?.hasRoute<MainRoute.EmailDetail>() == true)
        assertEquals(detailEntry, postBackEntry)
    }

    @Test
    fun search_detail_and_back_return_to_search() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        lateinit var navController: TestNavHostController

        composeTestRule.setContent {
            navController = TestNavHostController(context).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
                navigatorProvider.addNavigator(DialogNavigator())
            }
            MainScreen(navController = navController)
        }
        composeTestRule.waitForIdle()

        composeTestRule.runOnUiThread {
            navController.navigate(MainRoute.Search)
        }
        composeTestRule.waitForIdle()
        assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Search>() == true)

        composeTestRule.runOnUiThread {
            navController.navigate(MainRoute.EmailDetail("123"))
        }
        composeTestRule.waitForIdle()
        assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.EmailDetail>() == true)
        val detailRoute = navController.currentBackStackEntry?.toRoute<MainRoute.EmailDetail>()
        assertEquals("123", detailRoute?.emailId)

        composeTestRule.runOnUiThread {
            navController.popBackStack()
        }
        composeTestRule.waitForIdle()
        assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Search>() == true)
    }

    @Test
    fun drawer_selects_top_level_destination_and_is_disabled_for_overlays() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        lateinit var navController: TestNavHostController

        composeTestRule.setContent {
            navController = TestNavHostController(context).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
                navigatorProvider.addNavigator(DialogNavigator())
            }
            MainScreen(navController = navController)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription(context.getString(R.string.action_menu)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("drawer_item_inbox").assertIsDisplayed().assertIsSelected()
        composeTestRule.onNodeWithTag("drawer_item_trash").performClick()
        composeTestRule.waitForIdle()
        assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Trash>() == true)

        composeTestRule.onNodeWithContentDescription(context.getString(R.string.action_menu)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("drawer_item_trash").assertIsDisplayed().assertIsSelected()
        composeTestRule.onNodeWithTag("drawer_item_trash").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("drawer_item_trash").assertIsNotDisplayed()

        composeTestRule.runOnUiThread {
            navController.navigate(MainRoute.EmailDetail("gesture-detail"))
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().performTouchInput { swipeRight() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("drawer_item_trash").assertIsNotDisplayed()

        composeTestRule.runOnUiThread {
            navController.navigate(MainRoute.Compose(ComposeMode.WRITE))
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().performTouchInput { swipeRight() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("drawer_item_trash").assertIsNotDisplayed()
    }

    @Test
    fun trash_detail_compose_and_back_return_to_real_origin() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        lateinit var navController: TestNavHostController

        composeTestRule.setContent {
            navController = TestNavHostController(context).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
                navigatorProvider.addNavigator(DialogNavigator())
            }
            MainScreen(navController = navController)
        }
        composeTestRule.waitForIdle()

        composeTestRule.runOnUiThread {
            navController.navigate(MainRoute.Trash)
            navController.navigate(MainRoute.EmailDetail("origin-message"))
            navController.navigate(MainRoute.Compose(ComposeMode.REPLY, "origin-message"))
        }
        composeTestRule.waitForIdle()
        assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Compose>() == true)

        composeTestRule.runOnUiThread { navController.popBackStack() }
        composeTestRule.waitForIdle()
        assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.EmailDetail>() == true)

        composeTestRule.onNodeWithContentDescription(context.getString(R.string.detail_back)).performClick()
        composeTestRule.waitForIdle()
        assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Trash>() == true)
    }

    @Test
    fun settings_internal_back_has_priority_before_outer_nav_host() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        lateinit var navController: TestNavHostController

        composeTestRule.setContent {
            navController = TestNavHostController(context).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
                navigatorProvider.addNavigator(DialogNavigator())
            }
            MainScreen(navController = navController)
        }
        composeTestRule.waitForIdle()

        composeTestRule.runOnUiThread { navController.navigate(MainRoute.Settings) }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.settings_appearance)).performClick()
        composeTestRule.waitForIdle()

        Espresso.pressBack()
        composeTestRule.waitForIdle()
        assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Settings>() == true)
        composeTestRule.onNodeWithText(context.getString(R.string.settings_appearance)).assertIsDisplayed()

        Espresso.pressBack()
        composeTestRule.waitForIdle()
        assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Inbox>() == true)
    }

    @Test
    fun testTopLevelNavigationAndStateRestoration() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        lateinit var navController: TestNavHostController

        composeTestRule.setContent {
            navController = TestNavHostController(context).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
                navigatorProvider.addNavigator(DialogNavigator())
            }
            MainScreen(navController = navController)
        }
        composeTestRule.waitForIdle()

        // 1. Initial active destination must be Inbox
        assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Inbox>() == true)

        // 2. Cycle: Inbox -> Trash -> Settings -> Inbox
        composeTestRule.runOnUiThread {
            navController.navigateToTopLevel(MainRoute.Trash)
        }
        composeTestRule.waitForIdle()
        assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Trash>() == true)

        composeTestRule.runOnUiThread {
            navController.navigateToTopLevel(MainRoute.Settings)
        }
        composeTestRule.waitForIdle()
        assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Settings>() == true)

        composeTestRule.runOnUiThread {
            navController.navigateToTopLevel(MainRoute.Inbox)
        }
        composeTestRule.waitForIdle()
        assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Inbox>() == true)

        // Repeat cycle several times to verify back stack size does not grow indefinitely
        val initialBackStackSize = navController.currentBackStack.value.size
        for (i in 1..3) {
            composeTestRule.runOnUiThread {
                navController.navigateToTopLevel(MainRoute.Trash)
                navController.navigateToTopLevel(MainRoute.Settings)
                navController.navigateToTopLevel(MainRoute.Inbox)
            }
            composeTestRule.waitForIdle()
        }
        val finalBackStackSize = navController.currentBackStack.value.size
        assertEquals(initialBackStackSize, finalBackStackSize)

        // 3. Reselecting the same top-level route (Trash) does not replace the entry
        // Open drawer, select Trash
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.action_menu)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("drawer_item_trash").performClick()
        composeTestRule.waitForIdle()
        assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Trash>() == true)
        val trashEntry1 = navController.currentBackStackEntry

        // Open drawer, select Trash again
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.action_menu)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("drawer_item_trash").performClick()
        composeTestRule.waitForIdle()
        val trashEntry2 = navController.currentBackStackEntry
        assertEquals(trashEntry1, trashEntry2)

        // 4. Save a marker in Trash SavedStateHandle, change destination, return, and verify it's restored
        val trashEntry = navController.getBackStackEntry<MainRoute.Trash>()
        composeTestRule.runOnUiThread {
            trashEntry.savedStateHandle["test_marker"] = "restored_value"
        }
        composeTestRule.waitForIdle()

        composeTestRule.runOnUiThread {
            navController.navigateToTopLevel(MainRoute.Inbox)
        }
        composeTestRule.waitForIdle()

        composeTestRule.runOnUiThread {
            navController.navigateToTopLevel(MainRoute.Trash)
        }
        composeTestRule.waitForIdle()
        val restoredTrashEntry = navController.currentBackStackEntry
        assertTrue(restoredTrashEntry?.destination?.hasRoute<MainRoute.Trash>() == true)
        assertEquals("restored_value", restoredTrashEntry?.savedStateHandle?.get<String>("test_marker"))

        // 5. SavedStateHandle marker for Settings
        composeTestRule.runOnUiThread {
            navController.navigateToTopLevel(MainRoute.Settings)
        }
        composeTestRule.waitForIdle()
        val settingsEntry = navController.getBackStackEntry<MainRoute.Settings>()
        composeTestRule.runOnUiThread {
            settingsEntry.savedStateHandle["settings_marker"] = "settings_value"
        }
        composeTestRule.waitForIdle()

        composeTestRule.runOnUiThread {
            navController.navigateToTopLevel(MainRoute.Inbox)
        }
        composeTestRule.waitForIdle()

        composeTestRule.runOnUiThread {
            navController.navigateToTopLevel(MainRoute.Settings)
        }
        composeTestRule.waitForIdle()
        val restoredSettingsEntry = navController.currentBackStackEntry
        assertTrue(restoredSettingsEntry?.destination?.hasRoute<MainRoute.Settings>() == true)
        assertEquals("settings_value", restoredSettingsEntry?.savedStateHandle?.get<String>("settings_marker"))

        // 6. Enter Settings -> Appearance Settings
        composeTestRule.onNodeWithText(context.getString(R.string.settings_appearance)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.settings_appearance)).assertIsDisplayed()

        // Switch to Trash via drawer swipe gesture
        composeTestRule.onRoot().performTouchInput { swipeRight() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("drawer_item_trash").performClick()
        composeTestRule.waitForIdle()
        assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Trash>() == true)

        // Go back to Settings via drawer
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.action_menu)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("drawer_item_settings").performClick()
        composeTestRule.waitForIdle()

        // Verify we are restored directly to Appearance Screen!
        assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Settings>() == true)
        composeTestRule.onNodeWithText(context.getString(R.string.settings_appearance)).assertIsDisplayed()

        // 7. Back navigation: from Appearance, back goes to Settings Hub, next back goes to Inbox
        Espresso.pressBack()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.settings_account)).assertIsDisplayed()

        Espresso.pressBack()
        composeTestRule.waitForIdle()
        assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Inbox>() == true)

        // Press back from Trash -> should go back to Inbox
        composeTestRule.runOnUiThread {
            navController.navigateToTopLevel(MainRoute.Trash)
        }
        composeTestRule.waitForIdle()
        assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Trash>() == true)

        Espresso.pressBack()
        composeTestRule.waitForIdle()
        assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Inbox>() == true)

        // 8. Search, Detail, and Compose do not allow opening drawer via gesture
        // Go to Search
        composeTestRule.runOnUiThread {
            navController.navigate(MainRoute.Search)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().performTouchInput { swipeRight() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("drawer_item_inbox").assertIsNotDisplayed()

        // Go to Detail
        composeTestRule.runOnUiThread {
            navController.navigate(MainRoute.EmailDetail("123"))
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().performTouchInput { swipeRight() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("drawer_item_inbox").assertIsNotDisplayed()

        // Go to Compose
        composeTestRule.runOnUiThread {
            navController.navigate(MainRoute.Compose(ComposeMode.WRITE))
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().performTouchInput { swipeRight() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("drawer_item_inbox").assertIsNotDisplayed()
    }

    @Test
    fun testOverlayNavigationAndLocalHighlights() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        lateinit var navController: TestNavHostController

        composeTestRule.setContent {
            navController = TestNavHostController(context).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
                navigatorProvider.addNavigator(DialogNavigator())
            }
            MainScreen(navController = navController)
        }
        composeTestRule.waitForIdle()

        // 1. Repeated calls to navigateToOverlay do not create consecutive duplicates
        composeTestRule.runOnUiThread {
            navController.navigateToOverlay(MainRoute.Search)
            navController.navigateToOverlay(MainRoute.Search)
        }
        composeTestRule.waitForIdle()
        // Verify only 1 Search entry on stack
        val searchCount = navController.currentBackStack.value.count { it.destination.hasRoute<MainRoute.Search>() }
        assertEquals(1, searchCount)

        // Close Search
        composeTestRule.runOnUiThread {
            navController.popBackStack()
        }
        composeTestRule.waitForIdle()

        // Repeated Detail navigation keeps a single Detail entry.
        composeTestRule.runOnUiThread {
            navController.navigateToOverlay(MainRoute.EmailDetail("duplicate_detail"))
            navController.navigateToOverlay(MainRoute.EmailDetail("duplicate_detail"))
        }
        composeTestRule.waitForIdle()
        val detailCount = navController.currentBackStack.value.count {
            it.destination.hasRoute<MainRoute.EmailDetail>()
        }
        assertEquals(1, detailCount)
        composeTestRule.runOnUiThread { navController.popBackStack() }
        composeTestRule.waitForIdle()

        // Repeated Compose navigation keeps a single Compose entry.
        composeTestRule.runOnUiThread {
            navController.navigateToOverlay(MainRoute.Compose(ComposeMode.WRITE))
            navController.navigateToOverlay(MainRoute.Compose(ComposeMode.WRITE))
        }
        composeTestRule.waitForIdle()
        val composeCount = navController.currentBackStack.value.count {
            it.destination.hasRoute<MainRoute.Compose>()
        }
        assertEquals(1, composeCount)
        composeTestRule.runOnUiThread { navController.popBackStack() }
        composeTestRule.waitForIdle()

        // 2. Detail close via visual button and system back delivers highlight ONLY to origin and consumes it
        // A. From Inbox
        composeTestRule.runOnUiThread {
            navController.navigateToOverlay(MainRoute.EmailDetail("inbox_email"))
        }
        composeTestRule.waitForIdle()
        assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.EmailDetail>() == true)

        // Close via closeEmailDetail (visual back button action)
        composeTestRule.runOnUiThread {
            navController.closeEmailDetail("inbox_email")
        }
        composeTestRule.waitForIdle()
        // Returns to Inbox
        assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Inbox>() == true)
        // Verify Inbox entry has the highlight ID
        val inboxEntry = navController.currentBackStackEntry
        assertEquals("inbox_email", inboxEntry?.savedStateHandle?.get<String>(KEY_CLOSED_EMAIL_ID))

        // Clear highlight and verify it becomes null
        composeTestRule.runOnUiThread {
            inboxEntry?.savedStateHandle?.set(KEY_CLOSED_EMAIL_ID, null)
        }
        composeTestRule.waitForIdle()
        assertEquals(null, inboxEntry?.savedStateHandle?.get<String>(KEY_CLOSED_EMAIL_ID))

        // B. From Trash
        composeTestRule.runOnUiThread {
            navController.navigateToTopLevel(MainRoute.Trash)
        }
        composeTestRule.waitForIdle()
        composeTestRule.runOnUiThread {
            navController.navigateToOverlay(MainRoute.EmailDetail("trash_email"))
        }
        composeTestRule.waitForIdle()

        // Close via system Back
        Espresso.pressBack()
        composeTestRule.waitForIdle()
        // Returns to Trash
        assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Trash>() == true)
        val trashEntry = navController.currentBackStackEntry
        assertEquals("trash_email", trashEntry?.savedStateHandle?.get<String>(KEY_CLOSED_EMAIL_ID))
        // Clear
        composeTestRule.runOnUiThread {
            trashEntry?.savedStateHandle?.set(KEY_CLOSED_EMAIL_ID, null)
        }
        composeTestRule.waitForIdle()

        // C. From Search
        composeTestRule.runOnUiThread {
            navController.navigateToOverlay(MainRoute.Search)
        }
        composeTestRule.waitForIdle()
        composeTestRule.runOnUiThread {
            navController.navigateToOverlay(MainRoute.EmailDetail("search_email"))
        }
        composeTestRule.waitForIdle()

        // Close Detail
        composeTestRule.runOnUiThread {
            navController.closeEmailDetail("search_email")
        }
        composeTestRule.waitForIdle()
        // Returns to Search
        assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Search>() == true)
        val searchEntry = navController.currentBackStackEntry
        assertEquals("search_email", searchEntry?.savedStateHandle?.get<String>(KEY_CLOSED_EMAIL_ID))

        // 3. Confirm that a highlight produced for Search does not leak/appear in Inbox or Trash
        val inboxEntryCheck = navController.getBackStackEntry<MainRoute.Inbox>()
        val trashEntryCheck = navController.getBackStackEntry<MainRoute.Trash>()
        assertEquals(null, inboxEntryCheck.savedStateHandle.get<String>(KEY_CLOSED_EMAIL_ID))
        assertEquals(null, trashEntryCheck.savedStateHandle.get<String>(KEY_CLOSED_EMAIL_ID))

        // Clear Search highlight
        composeTestRule.runOnUiThread {
            searchEntry?.savedStateHandle?.set(KEY_CLOSED_EMAIL_ID, null)
        }
        composeTestRule.waitForIdle()

        // Go back from Search to Trash
        composeTestRule.runOnUiThread {
            navController.popBackStack()
        }
        composeTestRule.waitForIdle()

        // Go to Inbox
        composeTestRule.runOnUiThread {
            navController.navigateToTopLevel(MainRoute.Inbox)
        }
        composeTestRule.waitForIdle()

        // 4. Confirm that closing Compose WRITE does not publish highlight
        composeTestRule.runOnUiThread {
            navController.navigateToOverlay(MainRoute.Compose(ComposeMode.WRITE))
        }
        composeTestRule.waitForIdle()
        assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Compose>() == true)

        composeTestRule.runOnUiThread {
            navController.popBackStack()
        }
        composeTestRule.waitForIdle()
        // Returns to Inbox
        assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Inbox>() == true)
        assertEquals(null, navController.currentBackStackEntry?.savedStateHandle?.get<String>(KEY_CLOSED_EMAIL_ID))

        // 5. Confirm that closing Compose REPLY/FORWARD returns to Detail without premature highlight,
        // and closing Detail subsequently delivers highlight to origin
        composeTestRule.runOnUiThread {
            navController.navigateToOverlay(MainRoute.EmailDetail("orig_msg"))
        }
        composeTestRule.waitForIdle()

        // Open Compose REPLY
        composeTestRule.runOnUiThread {
            navController.navigateToOverlay(MainRoute.Compose(ComposeMode.REPLY, "orig_msg"))
        }
        composeTestRule.waitForIdle()
        assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Compose>() == true)

        // Close Compose
        composeTestRule.runOnUiThread {
            navController.popBackStack()
        }
        composeTestRule.waitForIdle()
        // Should be back in Detail
        assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.EmailDetail>() == true)
        val currentDetailEntry = navController.currentBackStackEntry
        assertEquals(null, currentDetailEntry?.savedStateHandle?.get<String>(KEY_CLOSED_EMAIL_ID))

        // Now close Detail
        composeTestRule.runOnUiThread {
            navController.closeEmailDetail("orig_msg")
        }
        composeTestRule.waitForIdle()
        // Should be back in Inbox
        assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Inbox>() == true)
        assertEquals("orig_msg", navController.currentBackStackEntry?.savedStateHandle?.get<String>(KEY_CLOSED_EMAIL_ID))

        // Clear
        composeTestRule.runOnUiThread {
            navController.currentBackStackEntry?.savedStateHandle?.set(KEY_CLOSED_EMAIL_ID, null)
        }
        composeTestRule.waitForIdle()

        // Repeat the complete flow for FORWARD.
        composeTestRule.runOnUiThread {
            navController.navigateToOverlay(MainRoute.EmailDetail("forward_msg"))
            navController.navigateToOverlay(MainRoute.Compose(ComposeMode.FORWARD, "forward_msg"))
        }
        composeTestRule.waitForIdle()
        assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Compose>() == true)

        composeTestRule.runOnUiThread { navController.popBackStack() }
        composeTestRule.waitForIdle()
        assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.EmailDetail>() == true)
        val forwardDetailEntry = navController.currentBackStackEntry
        assertEquals(null, forwardDetailEntry?.savedStateHandle?.get<String>(KEY_CLOSED_EMAIL_ID))

        composeTestRule.runOnUiThread {
            navController.closeEmailDetail("forward_msg")
        }
        composeTestRule.waitForIdle()
        assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Inbox>() == true)
        assertEquals(
            "forward_msg",
            navController.currentBackStackEntry?.savedStateHandle?.get<String>(KEY_CLOSED_EMAIL_ID)
        )

        composeTestRule.runOnUiThread {
            navController.currentBackStackEntry?.savedStateHandle?.set(KEY_CLOSED_EMAIL_ID, null)
        }
        composeTestRule.waitForIdle()
    }
}
