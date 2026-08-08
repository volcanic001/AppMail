package com.david.mailapp.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.DialogNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import com.david.mailapp.R
import com.david.mailapp.feature.settings.SettingsNavHost
import com.david.mailapp.ui.theme.ColorPalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Fase 4.5A — Presentación saveable.
 *
 * Verifica que Inbox, Trash y Search poseen estados de scroll independientes
 * ligados a su propia entrada de navegación, y que Settings conserva su
 * destino interno mediante estado saveable.
 *
 * Harness: replica la estructura de MainNavHost (estados dentro de cada
 * composable con rememberLazyListState) con contenido determinista para no
 * depender de datos reales de la bandeja.
 */
class ScrollStatePresentationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ── Harness de navegación ─────────────────────────────────

    private fun setNavHarness(
        onNavController: (TestNavHostController) -> Unit = {},
        onSearchState: (LazyListState) -> Unit = {}
    ) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        composeTestRule.setContent {
            val navController = TestNavHostController(context).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
                navigatorProvider.addNavigator(DialogNavigator())
            }
            onNavController(navController)
            NavHost(
                navController = navController,
                startDestination = MainRoute.Inbox,
                modifier = Modifier.fillMaxSize()
            ) {
                composable<MainRoute.Inbox> {
                    val listState = rememberLazyListState()
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().testTag("inbox_list")
                    ) {
                        items((0 until 100).toList()) { i ->
                            Text("Inbox $i", Modifier.testTag("inbox_$i"))
                        }
                    }
                }
                composable<MainRoute.Trash> {
                    val listState = rememberLazyListState()
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().testTag("trash_list")
                    ) {
                        items((0 until 100).toList()) { i ->
                            Text("Trash $i", Modifier.testTag("trash_$i"))
                        }
                    }
                }
                composable<MainRoute.Search> {
                    val listState = rememberLazyListState()
                    onSearchState(listState)
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().testTag("search_list")
                    ) {
                        items((0 until 100).toList()) { i ->
                            Text("Search $i", Modifier.testTag("search_$i"))
                        }
                    }
                }
                composable<MainRoute.EmailDetail> {
                    Box(Modifier.fillMaxSize().testTag("detail_screen"))
                }
                composable<MainRoute.Compose> {
                    Box(Modifier.fillMaxSize().testTag("compose_screen"))
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun TestNavHostController.navigateTop(route: MainRoute) {
        composeTestRule.runOnUiThread { navigateToTopLevel(route) }
        composeTestRule.waitForIdle()
    }

    private fun TestNavHostController.navigateOverlay(route: MainRoute) {
        composeTestRule.runOnUiThread { navigateToOverlay(route) }
        composeTestRule.waitForIdle()
    }

    private fun TestNavHostController.popSearch() {
        composeTestRule.runOnUiThread { popBackStack() }
        composeTestRule.waitForIdle()
    }

    // ── 1. Inbox y Trash: posiciones independientes ─────────────

    @Test
    fun inboxTrash_preserveIndependentScrollPositions() {
        lateinit var navController: TestNavHostController
        setNavHarness(onNavController = { navController = it })

        // Inbox → scroll a 30
        composeTestRule.onNodeWithTag("inbox_list").performScrollToIndex(30)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("inbox_30").assertIsDisplayed()

        // Trash → scroll a 15
        navController.navigateTop(MainRoute.Trash)
        composeTestRule.onNodeWithTag("trash_list").performScrollToIndex(15)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("trash_15").assertIsDisplayed()

        // Volver a Inbox → conserva índice 30
        navController.navigateTop(MainRoute.Inbox)
        composeTestRule.onNodeWithTag("inbox_30").assertIsDisplayed()

        // Volver a Trash → conserva índice 15
        navController.navigateTop(MainRoute.Trash)
        composeTestRule.onNodeWithTag("trash_15").assertIsDisplayed()
    }

    // ── 2. Volver desde Detail conserva el scroll del origen ────

    @Test
    fun returnFromDetail_preservesOriginScroll() {
        lateinit var navController: TestNavHostController
        setNavHarness(onNavController = { navController = it })

        composeTestRule.onNodeWithTag("inbox_list").performScrollToIndex(30)
        composeTestRule.waitForIdle()

        navController.navigateOverlay(MainRoute.EmailDetail("e1"))
        composeTestRule.onNodeWithTag("detail_screen").assertIsDisplayed()

        composeTestRule.runOnUiThread {
            val currentEntry = navController.currentBackStackEntry
            checkNotNull(currentEntry)
            navController.closeEmailDetail(currentEntry, "e1")
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("inbox_30").assertIsDisplayed()
    }

    // ── 3. Search conserva posición al volver desde Detail ──────

    @Test
    fun searchPreservesPositionWhenReturningFromDetail() {
        lateinit var navController: TestNavHostController
        setNavHarness(onNavController = { navController = it })

        navController.navigateOverlay(MainRoute.Search)
        composeTestRule.onNodeWithTag("search_list").performScrollToIndex(20)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("search_20").assertIsDisplayed()

        navController.navigateOverlay(MainRoute.EmailDetail("e2"))
        composeTestRule.onNodeWithTag("detail_screen").assertIsDisplayed()

        composeTestRule.runOnUiThread {
            val currentEntry = navController.currentBackStackEntry
            checkNotNull(currentEntry)
            navController.closeEmailDetail(currentEntry, "e2")
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("search_20").assertIsDisplayed()
    }

    // ── 4. Cerrar Search y abrir entrada nueva reinicia scroll ──

    @Test
    fun closingSearchAndReopening_resetsScrollToZero() {
        lateinit var navController: TestNavHostController
        var searchState: LazyListState? = null
        setNavHarness(
            onNavController = { navController = it },
            onSearchState = { searchState = it }
        )

        navController.navigateOverlay(MainRoute.Search)
        composeTestRule.onNodeWithTag("search_list").performScrollToIndex(20)
        composeTestRule.waitForIdle()
        assertEquals(20, searchState?.firstVisibleItemIndex)

        // Cerrar Search: entrada destruida
        navController.popSearch()

        // Abrir Search de nuevo: entrada nueva → scroll en cero
        navController.navigateOverlay(MainRoute.Search)
        composeTestRule.waitForIdle()
        assertEquals(0, searchState?.firstVisibleItemIndex)
    }

    // ── 5. Restaurar un estado no modifica los otros ────────────

    @Test
    fun restoringOneState_doesNotAffectOthers() {
        lateinit var navController: TestNavHostController
        setNavHarness(onNavController = { navController = it })

        // Inbox → 30
        composeTestRule.onNodeWithTag("inbox_list").performScrollToIndex(30)
        composeTestRule.waitForIdle()

        // Trash → 15
        navController.navigateTop(MainRoute.Trash)
        composeTestRule.onNodeWithTag("trash_list").performScrollToIndex(15)
        composeTestRule.waitForIdle()

        // Search → 20 (overlay sobre Inbox)
        navController.navigateTop(MainRoute.Inbox)
        navController.navigateOverlay(MainRoute.Search)
        composeTestRule.onNodeWithTag("search_list").performScrollToIndex(20)
        composeTestRule.waitForIdle()
        navController.popSearch()

        // Restaurar Inbox: 30 intacto
        composeTestRule.onNodeWithTag("inbox_30").assertIsDisplayed()

        // Restaurar Trash: 15 intacto
        navController.navigateTop(MainRoute.Trash)
        composeTestRule.onNodeWithTag("trash_15").assertIsDisplayed()

        // Volver a abrir Search: 0 (entrada nueva, sin reutilizar 20)
        navController.navigateTop(MainRoute.Inbox)
        navController.navigateOverlay(MainRoute.Search)
        composeTestRule.onNodeWithTag("search_0").assertIsDisplayed()
    }

    // ── 6. StateRestorationTester: save/restore de los tres ────

    @Test
    fun stateRestoration_savesAndRestoresThreeLazyListStates() {
        val restorationTester = StateRestorationTester(composeTestRule)
        val inboxState = mutableStateOf<LazyListState?>(null)
        val trashState = mutableStateOf<LazyListState?>(null)
        val searchState = mutableStateOf<LazyListState?>(null)
        val scope = mutableStateOf<CoroutineScope?>(null)

        restorationTester.setContent {
            val compositionScope = rememberCoroutineScope()
            scope.value = compositionScope
            Column(Modifier.fillMaxSize()) {
                val s1 = rememberLazyListState()
                val s2 = rememberLazyListState()
                val s3 = rememberLazyListState()
                inboxState.value = s1
                trashState.value = s2
                searchState.value = s3
                LazyColumn(state = s1, modifier = Modifier.weight(1f).testTag("inbox_list")) {
                    items((0 until 100).toList()) { i ->
                        Text("Inbox $i", Modifier.testTag("inbox_$i").height(40.dp))
                    }
                }
                LazyColumn(state = s2, modifier = Modifier.weight(1f).testTag("trash_list")) {
                    items((0 until 100).toList()) { i ->
                        Text("Trash $i", Modifier.testTag("trash_$i").height(40.dp))
                    }
                }
                LazyColumn(state = s3, modifier = Modifier.weight(1f).testTag("search_list")) {
                    items((0 until 100).toList()) { i ->
                        Text("Search $i", Modifier.testTag("search_$i").height(40.dp))
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        // Posicionar programáticamente: índice + offset exactos
        composeTestRule.runOnUiThread {
            scope.value?.launch {
                inboxState.value?.scrollToItem(30, 11)
                trashState.value?.scrollToItem(15, 22)
                searchState.value?.scrollToItem(20, 33)
            }
        }
        composeTestRule.waitForIdle()

        // Comprobar ambos valores antes de restaurar
        assertEquals(30, inboxState.value?.firstVisibleItemIndex)
        assertEquals(11, inboxState.value?.firstVisibleItemScrollOffset)
        assertEquals(15, trashState.value?.firstVisibleItemIndex)
        assertEquals(22, trashState.value?.firstVisibleItemScrollOffset)
        assertEquals(20, searchState.value?.firstVisibleItemIndex)
        assertEquals(33, searchState.value?.firstVisibleItemScrollOffset)

        restorationTester.emulateSavedInstanceStateRestore()
        composeTestRule.waitForIdle()

        // Comprobar nuevamente los seis valores con las nuevas instancias
        assertEquals(30, inboxState.value?.firstVisibleItemIndex)
        assertEquals(11, inboxState.value?.firstVisibleItemScrollOffset)
        assertEquals(15, trashState.value?.firstVisibleItemIndex)
        assertEquals(22, trashState.value?.firstVisibleItemScrollOffset)
        assertEquals(20, searchState.value?.firstVisibleItemIndex)
        assertEquals(33, searchState.value?.firstVisibleItemScrollOffset)
    }

    // ── 7. Settings real: Appearance activo tras restaurar ──────

    @Test
    fun settingsNavHost_restoresAppearanceThenBackToHub() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val restorationTester = StateRestorationTester(composeTestRule)

        restorationTester.setContent {
            SettingsNavHost(
                currentPalette = ColorPalette.Blue,
                isDarkMode = false,
                useCustomFont = false,
                onPaletteChange = {},
                onDarkModeChange = {},
                onUseCustomFontChange = {},
                onSignOut = {},
                onBack = {}
            )
        }
        composeTestRule.waitForIdle()

        // Hub: theme_title (exclusivo de Appearance) no existe
        composeTestRule.onNodeWithText(context.getString(R.string.theme_title)).assertDoesNotExist()

        // Entrar en Appearance: theme_title visible
        composeTestRule.onNodeWithText(context.getString(R.string.settings_appearance)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.theme_title)).assertIsDisplayed()

        // Restaurar estado de composición: Appearance sigue activo
        restorationTester.emulateSavedInstanceStateRestore()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.theme_title)).assertIsDisplayed()

        // Back → Hub: settings_account visible y theme_title desaparece
        Espresso.pressBack()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.settings_account)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.theme_title)).assertDoesNotExist()
    }
}
