package com.david.mailapp.ui.navigation

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.AccessibilityAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.DialogNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import com.david.mailapp.R
import com.david.mailapp.feature.compose.ComposeMode
import com.david.mailapp.feature.emaildetail.EmailDetailPresentation
import com.david.mailapp.feature.emaildetail.EmailDetailUiState
import com.david.mailapp.testhelpers.testEmail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * BackIdempotencyCharacterizationTest — Subfase 1: Caracterización y baseline.
 *
 * Base histórica: commit 621df4d. HEAD efectivo de ejecución: 3a09abe.
 *
 * Caracteriza de forma determinista el efecto de dos solicitudes Back consecutivas
 * originadas en la misma entrada. Las pruebas usan:
 * - OnBackPressedDispatcherOwner de prueba con Lifecycle RESUMED (system back)
 * - SemanticsActions.OnClick capturado una sola vez (flecha visual)
 * - popBackStack() directo (evidencia programática)
 *
 * expected-red: fallan en aserción de destino/estado. Nunca por nodo desaparecido
 * ni por mecanismo de Espresso.
 */
class BackIdempotencyCharacterizationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ═══════════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════════

    private class TestBackPressedDispatcherOwner : OnBackPressedDispatcherOwner {
        private val lifecycleRegistry = LifecycleRegistry.createUnsafe(this).apply {
            currentState = Lifecycle.State.RESUMED
        }

        var fallbackCount: Int = 0
            private set

        override val lifecycle: Lifecycle
            get() = lifecycleRegistry

        override val onBackPressedDispatcher = OnBackPressedDispatcher {
            fallbackCount++
        }
    }

    private data class TestHarness(
        val navController: TestNavHostController,
        val backDispatcherOwner: TestBackPressedDispatcherOwner
    )

    private fun setupMainScreen(): TestHarness {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        lateinit var navController: TestNavHostController
        val dispatcherOwner = TestBackPressedDispatcherOwner()

        composeTestRule.setContent {
            CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides dispatcherOwner) {
                navController = remember {
                    TestNavHostController(context).apply {
                        navigatorProvider.addNavigator(ComposeNavigator())
                        navigatorProvider.addNavigator(DialogNavigator())
                    }
                }
                MainScreen(navController = navController)
            }
        }
        composeTestRule.waitForIdle()
        return TestHarness(navController, dispatcherOwner)
    }

    /** Dos popBackStackFrom() con la entrada capturada — el segundo es no-op idempotente. */
    private fun doublePopBackStackFrom(navController: TestNavHostController) {
        val entry = checkNotNull(navController.currentBackStackEntry)
        composeTestRule.runOnUiThread {
            navController.popBackStackFrom(entry)
            navController.popBackStackFrom(entry)
        }
        composeTestRule.waitForIdle()
    }

    /** Dos onBackPressed() en el mismo bloque UI — simula dos toques de system back. */
    private fun doubleSystemBack(owner: TestBackPressedDispatcherOwner) {
        composeTestRule.runOnUiThread {
            owner.onBackPressedDispatcher.onBackPressed()
            owner.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()
        assertEquals(
            "La segunda solicitud Back no debe escapar al fallback del owner",
            0,
            owner.fallbackCount
        )
    }

    private fun singleSystemBack(owner: TestBackPressedDispatcherOwner) {
        composeTestRule.runOnUiThread {
            owner.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()
    }

    private fun assertSettingsHubDisplayed() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        composeTestRule.onNodeWithText(ctx.getString(R.string.settings_appearance)).assertIsDisplayed()
        composeTestRule.onNodeWithText(ctx.getString(R.string.settings_account)).assertIsDisplayed()
    }

    private fun doubleClickAction(@StringRes contentDescriptionRes: Int) {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val semantics = composeTestRule
            .onNodeWithContentDescription(ctx.getString(contentDescriptionRes))
            .fetchSemanticsNode()
        @Suppress("UNCHECKED_CAST")
        val onClick =
            (semantics.config[SemanticsActions.OnClick] as AccessibilityAction<() -> Boolean>).action
        checkNotNull(onClick) { "La acción OnClick debe existir" }
        composeTestRule.runOnUiThread {
            onClick()
            onClick()
        }
        composeTestRule.waitForIdle()
    }

    // ═══════════════════════════════════════════════════════════════
    //  SECTION A — Evidencia programática (doublePop)
    //  Demuestra que dos popBackStack() crudos consumen dos destinos.
    //  Esto es evidencia del mecanismo, no prueba de solicitud obsoleta.
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun green_programmatic_popBackStackFrom_detail_toInbox() {
        val h = setupMainScreen()
        composeTestRule.runOnUiThread {
            h.navController.navigateToOverlay(MainRoute.EmailDetail("e-inbox"))
        }
        composeTestRule.waitForIdle()
        assertTrue(h.navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.EmailDetail>() == true)

        doublePopBackStackFrom(h.navController)

        assertTrue(h.navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Inbox>() == true)
    }

    @Test
    fun green_programmatic_popBackStackFrom_detail_toTrash() {
        val h = setupMainScreen()
        composeTestRule.runOnUiThread {
            h.navController.navigateToTopLevel(MainRoute.Trash)
            h.navController.navigateToOverlay(MainRoute.EmailDetail("e-trash"))
        }
        composeTestRule.waitForIdle()
        doublePopBackStackFrom(h.navController)
        assertTrue(h.navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Trash>() == true)
    }

    @Test
    fun green_programmatic_popBackStackFrom_detail_toSearch() {
        val h = setupMainScreen()
        composeTestRule.runOnUiThread {
            h.navController.navigateToOverlay(MainRoute.Search)
            h.navController.navigateToOverlay(MainRoute.EmailDetail("e-search"))
        }
        composeTestRule.waitForIdle()
        doublePopBackStackFrom(h.navController)
        assertTrue(h.navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Search>() == true)
    }

    @Test
    fun green_programmatic_popBackStackFrom_search() {
        val h = setupMainScreen()
        composeTestRule.runOnUiThread {
            h.navController.navigateToOverlay(MainRoute.Search)
        }
        composeTestRule.waitForIdle()
        doublePopBackStackFrom(h.navController)
        assertTrue(h.navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Inbox>() == true)
    }

    @Test
    fun green_programmatic_popBackStackFrom_composeWrite() {
        val h = setupMainScreen()
        composeTestRule.runOnUiThread {
            h.navController.navigateToOverlay(MainRoute.Compose(ComposeMode.WRITE))
        }
        composeTestRule.waitForIdle()
        doublePopBackStackFrom(h.navController)
        assertTrue(h.navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Inbox>() == true)
    }

    @Test
    fun green_programmatic_popBackStackFrom_composeReply() {
        val h = setupMainScreen()
        composeTestRule.runOnUiThread {
            h.navController.navigateToOverlay(MainRoute.EmailDetail("reply-src"))
            h.navController.navigateToOverlay(MainRoute.Compose(ComposeMode.REPLY, "reply-src"))
        }
        composeTestRule.waitForIdle()
        doublePopBackStackFrom(h.navController)
        assertTrue(h.navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.EmailDetail>() == true)
    }

    @Test
    fun green_programmatic_popBackStackFrom_composeForward() {
        val h = setupMainScreen()
        composeTestRule.runOnUiThread {
            h.navController.navigateToOverlay(MainRoute.EmailDetail("fwd-src"))
            h.navController.navigateToOverlay(MainRoute.Compose(ComposeMode.FORWARD, "fwd-src"))
        }
        composeTestRule.waitForIdle()
        doublePopBackStackFrom(h.navController)
        assertTrue(h.navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.EmailDetail>() == true)
    }

    @Test
    fun green_programmatic_popBackStackFrom_settings() {
        val h = setupMainScreen()
        composeTestRule.runOnUiThread {
            h.navController.navigateToTopLevel(MainRoute.Settings)
        }
        composeTestRule.waitForIdle()
        doublePopBackStackFrom(h.navController)
        assertTrue(h.navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Inbox>() == true)
    }

    // ═══════════════════════════════════════════════════════════════
    //  SECTION B — System back (OnBackPressedDispatcherOwner RESUMED)
    //  Dos onBackPressed() desde el mismo dispatcher, mismo bloque UI.
    //  Representa la repetición de la solicitud originaria.
    // ═══════════════════════════════════════════════════════════════

    // ── EmailDetail system back ──────────────────────────────────

    @Test
    fun alreadyGreen_systemBack_doubleFromDetail_toInbox() {
        val h = setupMainScreen()
        composeTestRule.runOnUiThread {
            h.navController.navigateToOverlay(MainRoute.EmailDetail("sys-inbox"))
        }
        composeTestRule.waitForIdle()
        assertTrue(h.navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.EmailDetail>() == true)

        doubleSystemBack(h.backDispatcherOwner)

        assertTrue(h.navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Inbox>() == true)
    }

    @Test
    fun green_systemBack_doubleFromDetail_toTrash() {
        val h = setupMainScreen()
        composeTestRule.runOnUiThread {
            h.navController.navigateToTopLevel(MainRoute.Trash)
            h.navController.navigateToOverlay(MainRoute.EmailDetail("sys-trash"))
        }
        composeTestRule.waitForIdle()
        doubleSystemBack(h.backDispatcherOwner)
        assertTrue(
            "Destino final inesperado: ${h.navController.currentBackStackEntry?.destination?.route}",
            h.navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Trash>() == true
        )
    }

    @Test
    fun green_systemBack_doubleFromDetail_toSearch() {
        val h = setupMainScreen()
        composeTestRule.runOnUiThread {
            h.navController.navigateToOverlay(MainRoute.Search)
            h.navController.navigateToOverlay(MainRoute.EmailDetail("sys-search"))
        }
        composeTestRule.waitForIdle()
        doubleSystemBack(h.backDispatcherOwner)
        assertTrue(
            "Destino final inesperado: ${h.navController.currentBackStackEntry?.destination?.route}",
            h.navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Search>() == true
        )
    }

    // ── Search system back ───────────────────────────────────────

    @Test
    fun alreadyGreen_systemBack_doubleFromSearch() {
        val h = setupMainScreen()
        composeTestRule.runOnUiThread {
            h.navController.navigateToOverlay(MainRoute.Search)
        }
        composeTestRule.waitForIdle()
        assertTrue(h.navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Search>() == true)

        doubleSystemBack(h.backDispatcherOwner)

        assertTrue(h.navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Inbox>() == true)
    }

    // ── Compose system back ──────────────────────────────────────

    @Test
    fun alreadyGreen_systemBack_doubleFromComposeWrite() {
        val h = setupMainScreen()
        composeTestRule.runOnUiThread {
            h.navController.navigateToOverlay(MainRoute.Compose(ComposeMode.WRITE))
        }
        composeTestRule.waitForIdle()
        doubleSystemBack(h.backDispatcherOwner)
        assertTrue(h.navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Inbox>() == true)
    }

    @Test
    fun green_systemBack_doubleFromComposeReply() {
        val h = setupMainScreen()
        composeTestRule.runOnUiThread {
            h.navController.navigateToOverlay(MainRoute.EmailDetail("sys-reply"))
            h.navController.navigateToOverlay(MainRoute.Compose(ComposeMode.REPLY, "sys-reply"))
        }
        composeTestRule.waitForIdle()
        doubleSystemBack(h.backDispatcherOwner)
        assertTrue(h.navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.EmailDetail>() == true)
    }

    @Test
    fun green_systemBack_doubleFromComposeForward() {
        val h = setupMainScreen()
        composeTestRule.runOnUiThread {
            h.navController.navigateToOverlay(MainRoute.EmailDetail("sys-fwd"))
            h.navController.navigateToOverlay(MainRoute.Compose(ComposeMode.FORWARD, "sys-fwd"))
        }
        composeTestRule.waitForIdle()
        doubleSystemBack(h.backDispatcherOwner)
        assertTrue(h.navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.EmailDetail>() == true)
    }

    // ── Settings Hub system back ─────────────────────────────────

    @Test
    fun alreadyGreen_systemBack_doubleFromSettingsHub() {
        val h = setupMainScreen()
        composeTestRule.runOnUiThread {
            h.navController.navigateToTopLevel(MainRoute.Settings)
        }
        composeTestRule.waitForIdle()
        assertTrue(h.navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Settings>() == true)

        doubleSystemBack(h.backDispatcherOwner)

        assertTrue(h.navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Inbox>() == true)
    }

    // ── Settings internal sheets system back ─────────────────────

    @Test
    fun alreadyGreen_systemBack_doubleFromSettingsAppearance() {
        val h = setupMainScreen()
        composeTestRule.runOnUiThread {
            h.navController.navigateToTopLevel(MainRoute.Settings)
        }
        composeTestRule.waitForIdle()
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        composeTestRule.onNodeWithText(ctx.getString(R.string.settings_appearance)).performClick()
        composeTestRule.waitForIdle()

        // Stack: outer(Inbox,Settings), inner(Hub,Appearance)
        doubleSystemBack(h.backDispatcherOwner)

        // Debe quedar en Settings Hub (externo), no avanzar hasta Inbox
        assertTrue(h.navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Settings>() == true)
        assertSettingsHubDisplayed()
    }

    @Test
    fun alreadyGreen_systemBack_doubleFromSettingsAccount() {
        val h = setupMainScreen()
        composeTestRule.runOnUiThread { h.navController.navigateToTopLevel(MainRoute.Settings) }
        composeTestRule.waitForIdle()
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        composeTestRule.onNodeWithText(ctx.getString(R.string.settings_account)).performClick()
        composeTestRule.waitForIdle()
        doubleSystemBack(h.backDispatcherOwner)
        assertTrue(h.navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Settings>() == true)
        assertSettingsHubDisplayed()
    }

    @Test
    fun alreadyGreen_systemBack_doubleFromSettingsNotifications() {
        val h = setupMainScreen()
        composeTestRule.runOnUiThread { h.navController.navigateToTopLevel(MainRoute.Settings) }
        composeTestRule.waitForIdle()
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        composeTestRule.onNodeWithText(ctx.getString(R.string.settings_notifications)).performClick()
        composeTestRule.waitForIdle()
        doubleSystemBack(h.backDispatcherOwner)
        assertTrue(h.navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Settings>() == true)
        assertSettingsHubDisplayed()
    }

    @Test
    fun alreadyGreen_systemBack_doubleFromSettingsPrivacy() {
        val h = setupMainScreen()
        composeTestRule.runOnUiThread { h.navController.navigateToTopLevel(MainRoute.Settings) }
        composeTestRule.waitForIdle()
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        composeTestRule.onNodeWithText(ctx.getString(R.string.settings_privacy)).performClick()
        composeTestRule.waitForIdle()
        doubleSystemBack(h.backDispatcherOwner)
        assertTrue(h.navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Settings>() == true)
        assertSettingsHubDisplayed()
    }

    @Test
    fun alreadyGreen_systemBack_doubleFromSettingsSecurity() {
        val h = setupMainScreen()
        composeTestRule.runOnUiThread { h.navController.navigateToTopLevel(MainRoute.Settings) }
        composeTestRule.waitForIdle()
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        composeTestRule.onNodeWithText(ctx.getString(R.string.settings_security)).performClick()
        composeTestRule.waitForIdle()
        doubleSystemBack(h.backDispatcherOwner)
        assertTrue(h.navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Settings>() == true)
        assertSettingsHubDisplayed()
    }

    @Test
    fun alreadyGreen_systemBack_doubleFromSettingsAbout() {
        val h = setupMainScreen()
        composeTestRule.runOnUiThread { h.navController.navigateToTopLevel(MainRoute.Settings) }
        composeTestRule.waitForIdle()
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        composeTestRule.onNodeWithText(ctx.getString(R.string.settings_about)).performClick()
        composeTestRule.waitForIdle()
        doubleSystemBack(h.backDispatcherOwner)
        assertTrue(h.navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Settings>() == true)
        assertSettingsHubDisplayed()
    }

    @Test
    fun green_systemBack_doubleFromSettingsChangelog() {
        val h = setupMainScreen()
        composeTestRule.runOnUiThread { h.navController.navigateToTopLevel(MainRoute.Settings) }
        composeTestRule.waitForIdle()
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        // Settings → About
        composeTestRule.onNodeWithText(ctx.getString(R.string.settings_about)).performClick()
        composeTestRule.waitForIdle()
        // About → Changelog
        composeTestRule.onNodeWithText(ctx.getString(R.string.about_changelog_title)).performClick()
        composeTestRule.waitForIdle()

        // Pila: inner(Hub,About,Changelog)
        doubleSystemBack(h.backDispatcherOwner)

        // Debe quedar en About, no avanzar hasta Hub ni permanecer en Changelog.
        composeTestRule.onNodeWithText(ctx.getString(R.string.about_version_label)).assertIsDisplayed()
        // La ruta exterior sigue siendo Settings
        assertTrue(h.navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Settings>() == true)
    }

    // ── Drawer priority ──────────────────────────────────────────

    @Test
    fun alreadyGreen_systemBack_doubleWithDrawerOpen() {
        val h = setupMainScreen()
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()

        composeTestRule.onNodeWithContentDescription(ctx.getString(R.string.action_menu)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("drawer_item_inbox").assertIsDisplayed()

        doubleSystemBack(h.backDispatcherOwner)

        // Debe cerrar el drawer y permanecer en Inbox
        composeTestRule.onNodeWithTag("drawer_item_inbox").assertIsNotDisplayed()
        assertTrue(h.navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Inbox>() == true)
    }

    // ═══════════════════════════════════════════════════════════════
    //  SECTION C — Arrow back (SemanticsActions.OnClick capturado)
    //  Una sola acción OnClick invocada dos veces en el mismo bloque UI.
    // ═══════════════════════════════════════════════════════════════

    private fun doubleClickArrowBack() {
        doubleClickAction(R.string.detail_back)
    }

    private fun doubleClickSearchArrow() {
        doubleClickAction(R.string.search_back_description)
    }

    @Test
    fun green_arrowBack_doubleFromDetail_toInbox() {
        val h = setupMainScreen()
        composeTestRule.runOnUiThread {
            h.navController.navigateToOverlay(MainRoute.EmailDetail("arrow-inbox"))
        }
        composeTestRule.waitForIdle()
        assertTrue(h.navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.EmailDetail>() == true)

        doubleClickArrowBack()

        assertTrue(h.navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Inbox>() == true)
    }

    @Test
    fun green_arrowBack_doubleFromDetail_toTrash() {
        val h = setupMainScreen()
        composeTestRule.runOnUiThread {
            h.navController.navigateToTopLevel(MainRoute.Trash)
            h.navController.navigateToOverlay(MainRoute.EmailDetail("arrow-trash"))
        }
        composeTestRule.waitForIdle()
        doubleClickArrowBack()
        assertTrue(h.navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Trash>() == true)
    }

    @Test
    fun green_arrowBack_doubleFromDetail_toSearch() {
        val h = setupMainScreen()
        composeTestRule.runOnUiThread {
            h.navController.navigateToOverlay(MainRoute.Search)
            h.navController.navigateToOverlay(MainRoute.EmailDetail("arrow-search"))
        }
        composeTestRule.waitForIdle()
        doubleClickArrowBack()
        assertTrue(h.navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Search>() == true)
    }

    @Test
    fun green_arrowBack_doubleFromSearch() {
        val h = setupMainScreen()
        composeTestRule.runOnUiThread {
            h.navController.navigateToOverlay(MainRoute.Search)
        }
        composeTestRule.waitForIdle()
        doubleClickSearchArrow()
        assertTrue(h.navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Inbox>() == true)
    }

    // ── Compose close visual ─────────────────────────────────

    @Test
    fun green_visualClose_doubleFromComposeWrite() {
        val h = setupMainScreen()
        composeTestRule.runOnUiThread {
            h.navController.navigateToOverlay(MainRoute.Compose(ComposeMode.WRITE))
        }
        composeTestRule.waitForIdle()
        doubleClickAction(R.string.action_close)
        assertTrue(h.navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Inbox>() == true)
    }

    @Test
    fun green_visualClose_doubleFromComposeReply() {
        val h = setupMainScreen()
        composeTestRule.runOnUiThread {
            h.navController.navigateToOverlay(MainRoute.EmailDetail("visual-reply"))
            h.navController.navigateToOverlay(MainRoute.Compose(ComposeMode.REPLY, "visual-reply"))
        }
        composeTestRule.waitForIdle()
        doubleClickAction(R.string.action_close)
        assertTrue(h.navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.EmailDetail>() == true)
    }

    @Test
    fun green_visualClose_doubleFromComposeForward() {
        val h = setupMainScreen()
        composeTestRule.runOnUiThread {
            h.navController.navigateToOverlay(MainRoute.EmailDetail("visual-forward"))
            h.navController.navigateToOverlay(MainRoute.Compose(ComposeMode.FORWARD, "visual-forward"))
        }
        composeTestRule.waitForIdle()
        doubleClickAction(R.string.action_close)
        assertTrue(h.navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.EmailDetail>() == true)
    }

    // ── Settings visual back ──────────────────────────────────

    @Test
    fun green_visualBack_doubleFromSettingsHub() {
        val h = setupMainScreen()
        composeTestRule.runOnUiThread { h.navController.navigateToTopLevel(MainRoute.Settings) }
        composeTestRule.waitForIdle()
        doubleClickAction(R.string.action_back)
        assertTrue(h.navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Inbox>() == true)
    }

    @Test
    fun green_visualBack_doubleFromSettingsAppearance() {
        val h = setupMainScreen()
        composeTestRule.runOnUiThread { h.navController.navigateToTopLevel(MainRoute.Settings) }
        composeTestRule.waitForIdle()
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        composeTestRule.onNodeWithText(ctx.getString(R.string.settings_appearance)).performClick()
        composeTestRule.waitForIdle()
        doubleClickAction(R.string.action_back)
        assertTrue(h.navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Settings>() == true)
        assertSettingsHubDisplayed()
    }

    @Test
    fun green_visualBack_doubleFromSettingsAccount() {
        val h = setupMainScreen()
        composeTestRule.runOnUiThread { h.navController.navigateToTopLevel(MainRoute.Settings) }
        composeTestRule.waitForIdle()
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        composeTestRule.onNodeWithText(ctx.getString(R.string.settings_account)).performClick()
        composeTestRule.waitForIdle()
        doubleClickAction(R.string.action_back)
        assertSettingsHubDisplayed()
    }

    @Test
    fun green_visualBack_doubleFromSettingsNotifications() {
        val h = setupMainScreen()
        composeTestRule.runOnUiThread { h.navController.navigateToTopLevel(MainRoute.Settings) }
        composeTestRule.waitForIdle()
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        composeTestRule.onNodeWithText(ctx.getString(R.string.settings_notifications)).performClick()
        composeTestRule.waitForIdle()
        doubleClickAction(R.string.action_back)
        assertSettingsHubDisplayed()
    }

    @Test
    fun green_visualBack_doubleFromSettingsPrivacy() {
        val h = setupMainScreen()
        composeTestRule.runOnUiThread { h.navController.navigateToTopLevel(MainRoute.Settings) }
        composeTestRule.waitForIdle()
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        composeTestRule.onNodeWithText(ctx.getString(R.string.settings_privacy)).performClick()
        composeTestRule.waitForIdle()
        doubleClickAction(R.string.action_back)
        assertSettingsHubDisplayed()
    }

    @Test
    fun green_visualBack_doubleFromSettingsSecurity() {
        val h = setupMainScreen()
        composeTestRule.runOnUiThread { h.navController.navigateToTopLevel(MainRoute.Settings) }
        composeTestRule.waitForIdle()
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        composeTestRule.onNodeWithText(ctx.getString(R.string.settings_security)).performClick()
        composeTestRule.waitForIdle()
        doubleClickAction(R.string.action_back)
        assertSettingsHubDisplayed()
    }

    @Test
    fun green_visualBack_doubleFromSettingsAbout() {
        val h = setupMainScreen()
        composeTestRule.runOnUiThread { h.navController.navigateToTopLevel(MainRoute.Settings) }
        composeTestRule.waitForIdle()
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        composeTestRule.onNodeWithText(ctx.getString(R.string.settings_about)).performClick()
        composeTestRule.waitForIdle()
        doubleClickAction(R.string.action_back)
        assertSettingsHubDisplayed()
    }

    @Test
    fun green_visualBack_doubleFromSettingsChangelog() {
        val h = setupMainScreen()
        composeTestRule.runOnUiThread { h.navController.navigateToTopLevel(MainRoute.Settings) }
        composeTestRule.waitForIdle()
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        composeTestRule.onNodeWithText(ctx.getString(R.string.settings_about)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(ctx.getString(R.string.about_changelog_title)).performClick()
        composeTestRule.waitForIdle()
        doubleClickAction(R.string.action_back)
        composeTestRule.onNodeWithText(ctx.getString(R.string.about_version_label)).assertIsDisplayed()
        assertTrue(h.navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Settings>() == true)
    }

    // ═══════════════════════════════════════════════════════════════
    //  SECTION D — Green baseline (back simple y legítimo)
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun baseline_singleBack_fromDetail_toInbox() {
        val h = setupMainScreen()
        composeTestRule.runOnUiThread {
            h.navController.navigateToOverlay(MainRoute.EmailDetail("base-1"))
        }
        composeTestRule.waitForIdle()
        val entry = checkNotNull(h.navController.currentBackStackEntry)
        composeTestRule.runOnUiThread { h.navController.closeEmailDetail(entry, "base-1") }
        composeTestRule.waitForIdle()
        assertTrue(h.navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Inbox>() == true)
        assertEquals("base-1", h.navController.currentBackStackEntry?.savedStateHandle?.get<String>(KEY_CLOSED_EMAIL_ID))
    }

    @Test
    fun baseline_singleSystemBack_fromDetail_toInbox() {
        val h = setupMainScreen()
        composeTestRule.runOnUiThread {
            h.navController.navigateToOverlay(MainRoute.EmailDetail("sys-base"))
        }
        composeTestRule.waitForIdle()
        composeTestRule.runOnUiThread { h.backDispatcherOwner.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()
        assertTrue(h.navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Inbox>() == true)
    }

    @Test
    fun baseline_singleBack_fromSearch_toInbox() {
        val h = setupMainScreen()
        composeTestRule.runOnUiThread { h.navController.navigateToOverlay(MainRoute.Search) }
        composeTestRule.waitForIdle()
        composeTestRule.runOnUiThread { h.navController.popBackStack() }
        composeTestRule.waitForIdle()
        assertTrue(h.navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Inbox>() == true)
    }

    @Test
    fun baseline_singleSystemBack_fromSettingsAppearance_toHub() {
        val h = setupMainScreen()
        composeTestRule.runOnUiThread { h.navController.navigateToTopLevel(MainRoute.Settings) }
        composeTestRule.waitForIdle()
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        composeTestRule.onNodeWithText(ctx.getString(R.string.settings_appearance)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.runOnUiThread { h.backDispatcherOwner.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()
        assertTrue(h.navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Settings>() == true)
        composeTestRule.onNodeWithText(ctx.getString(R.string.settings_appearance)).assertIsDisplayed()
    }

    @Test
    fun baseline_twoSeparateSystemBacks_fromComposeReply_toDetail_toInbox() {
        val h = setupMainScreen()
        composeTestRule.runOnUiThread {
            h.navController.navigateToOverlay(MainRoute.EmailDetail("reply-base"))
            h.navController.navigateToOverlay(MainRoute.Compose(ComposeMode.REPLY, "reply-base"))
        }
        composeTestRule.waitForIdle()

        // Primer Back: Compose Reply → EmailDetail.
        singleSystemBack(h.backDispatcherOwner)
        assertTrue(h.navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.EmailDetail>() == true)

        // Segundo Back legítimo, después de recomponer: EmailDetail → Inbox.
        singleSystemBack(h.backDispatcherOwner)
        assertTrue(h.navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Inbox>() == true)
    }

    @Test
    fun baseline_singleSystemBack_fromSettingsHub_toInbox() {
        val h = setupMainScreen()
        composeTestRule.runOnUiThread { h.navController.navigateToTopLevel(MainRoute.Settings) }
        composeTestRule.waitForIdle()
        composeTestRule.runOnUiThread { h.backDispatcherOwner.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()
        assertTrue(h.navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Inbox>() == true)
    }

    @Test
    fun baseline_singleBack_drawerOpen_closesDrawer() {
        val h = setupMainScreen()
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        composeTestRule.onNodeWithContentDescription(ctx.getString(R.string.action_menu)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("drawer_item_inbox").assertIsDisplayed()
        composeTestRule.runOnUiThread { h.backDispatcherOwner.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("drawer_item_inbox").assertIsNotDisplayed()
        assertTrue(h.navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Inbox>() == true)
    }

    @Test
    fun baseline_twoSeparateBacks_fromAppearance_toHub_toInbox() {
        val h = setupMainScreen()
        composeTestRule.runOnUiThread { h.navController.navigateToTopLevel(MainRoute.Settings) }
        composeTestRule.waitForIdle()
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        composeTestRule.onNodeWithText(ctx.getString(R.string.settings_appearance)).performClick()
        composeTestRule.waitForIdle()

        // Primer Back → Hub
        composeTestRule.runOnUiThread { h.backDispatcherOwner.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()
        assertTrue(h.navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Settings>() == true)

        // Segundo Back → Inbox (legítimo, tras recomposición)
        composeTestRule.runOnUiThread { h.backDispatcherOwner.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()
        assertTrue(h.navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Inbox>() == true)
    }

    @Test
    fun baseline_singleArrow_fromSearch_toInbox() {
        val h = setupMainScreen()
        composeTestRule.runOnUiThread { h.navController.navigateToOverlay(MainRoute.Search) }
        composeTestRule.waitForIdle()
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        composeTestRule
            .onNodeWithContentDescription(ctx.getString(R.string.search_back_description))
            .performClick()
        composeTestRule.waitForIdle()
        assertTrue(h.navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Inbox>() == true)
    }

    @Test
    fun baseline_singleVisualClose_fromComposeWrite_toInbox() {
        val h = setupMainScreen()
        composeTestRule.runOnUiThread {
            h.navController.navigateToOverlay(MainRoute.Compose(ComposeMode.WRITE))
        }
        composeTestRule.waitForIdle()
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        composeTestRule.onNodeWithContentDescription(ctx.getString(R.string.action_close)).performClick()
        composeTestRule.waitForIdle()
        assertTrue(h.navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Inbox>() == true)
    }

    @Test
    fun baseline_singleArrow_fromSettingsHub_toInbox() {
        val h = setupMainScreen()
        composeTestRule.runOnUiThread { h.navController.navigateToTopLevel(MainRoute.Settings) }
        composeTestRule.waitForIdle()
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        composeTestRule.onNodeWithContentDescription(ctx.getString(R.string.action_back)).performClick()
        composeTestRule.waitForIdle()
        assertTrue(h.navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Inbox>() == true)
    }

    @Test
    fun baseline_singleArrow_fromSettingsAppearance_toHub() {
        val h = setupMainScreen()
        composeTestRule.runOnUiThread { h.navController.navigateToTopLevel(MainRoute.Settings) }
        composeTestRule.waitForIdle()
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        composeTestRule.onNodeWithText(ctx.getString(R.string.settings_appearance)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(ctx.getString(R.string.action_back)).performClick()
        composeTestRule.waitForIdle()
        assertTrue(h.navController.currentBackStackEntry?.destination?.hasRoute<MainRoute.Settings>() == true)
        assertSettingsHubDisplayed()
    }

    // ═══════════════════════════════════════════════════════════════
    //  SECTION E — EmailDetail expanded panel priority
    // ══════════════════════════════════════════════════════════════

    private fun setExpandedPanelContent(onOuterBack: () -> Unit): TestBackPressedDispatcherOwner {
        val owner = TestBackPressedDispatcherOwner()
        composeTestRule.setContent {
            CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides owner) {
                BackHandler(onBack = onOuterBack)
                MaterialTheme {
                    EmailDetailPresentation(
                        uiState = EmailDetailUiState.Ready(testEmail("panel-characterization")),
                        pdfDownloadStates = emptyMap(),
                        savingStableIds = emptySet(),
                        traceMail = "panel-characterization",
                        snackbarHostState = remember { SnackbarHostState() },
                        onBack = {},
                        onReply = {},
                        onForward = {},
                        onRetry = {},
                        onRetryBody = {},
                        onPdfAttachmentClick = {},
                        onPdfSaveClick = {},
                        modifier = Modifier
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        composeTestRule
            .onNodeWithContentDescription(ctx.getString(R.string.detail_expand_header))
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithContentDescription(ctx.getString(R.string.detail_collapse_header))
            .assertIsDisplayed()
        return owner
    }

    @Test
    fun alreadyGreen_doubleSystemBack_withExpandedPanel_onlyClosesPanel() {
        var outerBackCount = 0
        val owner = setExpandedPanelContent { outerBackCount++ }

        doubleSystemBack(owner)

        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        composeTestRule
            .onNodeWithContentDescription(ctx.getString(R.string.detail_expand_header))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(ctx.getString(R.string.detail_collapse_header))
            .assertDoesNotExist()
        assertEquals(0, outerBackCount)
    }

    @Test
    fun baseline_twoSeparateBacks_fromExpandedPanel_reachesOuterOnce() {
        var outerBackCount = 0
        val owner = setExpandedPanelContent { outerBackCount++ }
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()

        singleSystemBack(owner)
        composeTestRule
            .onNodeWithContentDescription(ctx.getString(R.string.detail_expand_header))
            .assertIsDisplayed()
        assertEquals(0, outerBackCount)

        singleSystemBack(owner)
        assertEquals(1, outerBackCount)
        assertEquals(0, owner.fallbackCount)
    }
}
