package com.david.mailapp.ui.navigation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.DialogNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import com.david.mailapp.feature.compose.ComposeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * BackIdempotencyContractTest — Subfase 2: Contrato Back idempotente.
 *
 * Verifica que popBackStackFrom y closeEmailDetail cumplan el contrato
 * de idempotencia con TestNavHostController.
 */
class BackIdempotencyContractTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setup(): TestNavHostController {
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
        return navController
    }

    // ═══════════════════════════════════════════════════════════════
    //  popBackStackFrom contract
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun popBackStackFrom_currentEntry_returnsTrue() {
        val navController = setup()
        composeTestRule.runOnUiThread {
            navController.navigateToOverlay(MainRoute.EmailDetail("e-current"))
        }
        composeTestRule.waitForIdle()

        val entry = navController.currentBackStackEntry
        assertNotNull(entry)
        assertEquals(Lifecycle.State.RESUMED, entry!!.lifecycle.currentState)

        composeTestRule.runOnUiThread {
            assertTrue(navController.popBackStackFrom(entry))
        }
        composeTestRule.waitForIdle()
        assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Inbox>() == true)
    }

    @Test
    fun popBackStackFrom_sameEntryTwice_secondReturnsFalse() {
        val navController = setup()
        composeTestRule.runOnUiThread {
            navController.navigateToOverlay(MainRoute.EmailDetail("e-twice"))
        }
        composeTestRule.waitForIdle()

        val entry = navController.currentBackStackEntry
        assertNotNull(entry)

        composeTestRule.runOnUiThread {
            assertTrue(navController.popBackStackFrom(entry!!))
        }
        composeTestRule.waitForIdle()

        // Second call with same (now removed) entry returns false
        composeTestRule.runOnUiThread {
            assertFalse(navController.popBackStackFrom(entry!!))
        }
        composeTestRule.waitForIdle()

        // Destination must still be Inbox — no extra pop consumed
        assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Inbox>() == true)
    }

    @Test
    fun popBackStackFrom_staleEntry_doesNotModifyStack() {
        val navController = setup()
        composeTestRule.runOnUiThread {
            navController.navigateToOverlay(MainRoute.EmailDetail("e-stale"))
        }
        composeTestRule.waitForIdle()

        val staleEntry = navController.currentBackStackEntry
        assertNotNull(staleEntry)

        // Navigate forward to a different overlay
        composeTestRule.runOnUiThread {
            navController.navigateToOverlay(MainRoute.Compose(ComposeMode.WRITE))
        }
        composeTestRule.waitForIdle()

        // staleEntry is no longer current — Compose screen is active
        assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Compose>() == true)

        composeTestRule.runOnUiThread {
            assertFalse(navController.popBackStackFrom(staleEntry!!))
        }
        composeTestRule.waitForIdle()

        // Stack must be unchanged — still on Compose
        assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Compose>() == true)
    }

    @Test
    fun popBackStackFrom_rootEntry_returnsFalse() {
        val navController = setup()
        val rootEntry = navController.currentBackStackEntry
        assertNotNull(rootEntry)
        assertTrue(rootEntry!!.destination.hasRoute<MainRoute.Inbox>())

        composeTestRule.runOnUiThread {
            assertFalse(navController.popBackStackFrom(rootEntry))
        }
        composeTestRule.waitForIdle()

        // Still on Inbox
        assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Inbox>() == true)
    }

    // ═══════════════════════════════════════════════════════════════
    //  closeEmailDetail contract
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun closeEmailDetail_fromDetail_toInbox() {
        val navController = setup()
        composeTestRule.runOnUiThread {
            navController.navigateToOverlay(MainRoute.EmailDetail("ce-inbox"))
        }
        composeTestRule.waitForIdle()

        val entry = navController.currentBackStackEntry!!
        assertTrue(entry.destination.hasRoute<MainRoute.EmailDetail>())

        composeTestRule.runOnUiThread {
            assertTrue(navController.closeEmailDetail(entry, "ce-inbox"))
        }
        composeTestRule.waitForIdle()

        assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Inbox>() == true)
        assertEquals(
            "ce-inbox",
            navController.currentBackStackEntry?.savedStateHandle?.get<String>(KEY_CLOSED_EMAIL_ID)
        )
    }

    @Test
    fun closeEmailDetail_fromDetail_toTrash() {
        val navController = setup()
        composeTestRule.runOnUiThread {
            navController.navigateToTopLevel(MainRoute.Trash)
            navController.navigateToOverlay(MainRoute.EmailDetail("ce-trash"))
        }
        composeTestRule.waitForIdle()

        val entry = navController.currentBackStackEntry!!
        composeTestRule.runOnUiThread {
            assertTrue(navController.closeEmailDetail(entry, "ce-trash"))
        }
        composeTestRule.waitForIdle()

        assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Trash>() == true)
        assertEquals(
            "ce-trash",
            navController.currentBackStackEntry?.savedStateHandle?.get<String>(KEY_CLOSED_EMAIL_ID)
        )
    }

    @Test
    fun closeEmailDetail_fromDetail_toSearch() {
        val navController = setup()
        composeTestRule.runOnUiThread {
            navController.navigateToOverlay(MainRoute.Search)
            navController.navigateToOverlay(MainRoute.EmailDetail("ce-search"))
        }
        composeTestRule.waitForIdle()

        val entry = navController.currentBackStackEntry!!
        composeTestRule.runOnUiThread {
            assertTrue(navController.closeEmailDetail(entry, "ce-search"))
        }
        composeTestRule.waitForIdle()

        assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Search>() == true)
        assertEquals(
            "ce-search",
            navController.currentBackStackEntry?.savedStateHandle?.get<String>(KEY_CLOSED_EMAIL_ID)
        )
    }

    @Test
    fun closeEmailDetail_repeatDoesNotOverwriteHighlight() {
        val navController = setup()
        composeTestRule.runOnUiThread {
            navController.navigateToOverlay(MainRoute.EmailDetail("ce-repeat"))
        }
        composeTestRule.waitForIdle()

        val entry = navController.currentBackStackEntry!!

        // First close — sets highlight
        composeTestRule.runOnUiThread {
            assertTrue(navController.closeEmailDetail(entry, "ce-repeat"))
        }
        composeTestRule.waitForIdle()
        assertEquals("ce-repeat", navController.currentBackStackEntry?.savedStateHandle?.get<String>(KEY_CLOSED_EMAIL_ID))

        // Navigate to Detail again, then try to close with a stale entry
        composeTestRule.runOnUiThread {
            navController.navigateToOverlay(MainRoute.EmailDetail("ce-other"))
        }
        composeTestRule.waitForIdle()

        // Repeat with stale entry — must return false, NOT overwrite highlight
        composeTestRule.runOnUiThread {
            assertFalse(navController.closeEmailDetail(entry, "ce-repeat"))
        }
        composeTestRule.waitForIdle()

        // Current destination is still the other detail — stack unchanged
        assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.EmailDetail>() == true)
    }
}
