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
}
