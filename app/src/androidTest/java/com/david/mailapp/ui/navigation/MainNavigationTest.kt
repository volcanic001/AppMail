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
    fun testNavigationFlows() {
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

        // Inbox -> Search
        composeTestRule.runOnUiThread {
            navController.navigate(MainRoute.Search)
        }
        composeTestRule.waitForIdle()
        assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Search>() == true)

        // Search -> Detail
        composeTestRule.runOnUiThread {
            navController.navigate(MainRoute.EmailDetail("123"))
        }
        composeTestRule.waitForIdle()
        assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.EmailDetail>() == true)
        val detailRoute = navController.currentBackStackEntry?.toRoute<MainRoute.EmailDetail>()
        assertEquals("123", detailRoute?.emailId)

        // Detail -> Back -> Search
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
