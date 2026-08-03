package com.david.mailapp.ui.test

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.david.mailapp.core.session.SessionWriteGuard
import com.david.mailapp.core.session.SessionWriteLease
import com.david.mailapp.feature.compose.ComposeArgs
import com.david.mailapp.feature.compose.ComposeMode
import com.david.mailapp.feature.compose.ComposeUiState
import com.david.mailapp.feature.compose.ComposeViewModel
import com.david.mailapp.feature.search.SearchUiState
import com.david.mailapp.feature.search.SearchViewModel
import com.david.mailapp.feature.settings.SettingsNavHost
import com.david.mailapp.ui.navigation.MainRoute
import com.david.mailapp.ui.navigation.navigateToOverlay
import com.david.mailapp.ui.navigation.navigateToTopLevel
import com.david.mailapp.ui.navigation.toComposeArgs
import com.david.mailapp.ui.theme.ColorPalette
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

class RestorationTestActivity : ComponentActivity() {

    companion object {
        @Volatile var mode: RecreateMode = RecreateMode.ACTIVITY
        @Volatile var creationCount = 0
    }

    val creationLog = mutableStateListOf<String>()
    private val featureStore: RestorationFeatureStore by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        creationCount++
        val id = creationCount
        creationLog.add("Activity#$id=${System.identityHashCode(this)}")
        RestorationProbe.activityIds += System.identityHashCode(this)

        setContent {
            val historyStore = remember { RestorationTestStores.historyStore(this) }
            RestorationNavHost(
                activityNumber = id,
                historyStore = historyStore,
                creationLog = creationLog,
                featureStoreOwner = featureStore
            )
        }
    }

    override fun onDestroy() {
        // Preserve Navigation's Activity store, but discard feature ViewModels
        // after saved-instance-state capture. Search and Compose must therefore
        // be rebuilt from SavedStateHandle while the presentation stack restores.
        if (isChangingConfigurations && mode == RecreateMode.PROCESS_EQUIVALENT) {
            featureStore.clearFeatures()
            mode = RecreateMode.ACTIVITY
        }
        super.onDestroy()
    }
}

class RestorationFeatureStore : ViewModel(), ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()

    fun clearFeatures() = viewModelStore.clear()

    override fun onCleared() {
        viewModelStore.clear()
    }
}

enum class RecreateMode { ACTIVITY, PROCESS_EQUIVALENT }

data class SearchCall(val query: String, val pageToken: String?)

object RestorationProbe {
    val activityIds = CopyOnWriteArrayList<Int>()
    val searchViewModelIds = CopyOnWriteArrayList<Int>()
    val composeViewModelIds = CopyOnWriteArrayList<Int>()
    val composeViewModels = CopyOnWriteArrayList<ComposeViewModel>()
    val composeSources = CopyOnWriteArrayList<FakeComposeEmailSource>()
    val searchCalls = CopyOnWriteArrayList<SearchCall>()

    fun reset() {
        activityIds.clear()
        searchViewModelIds.clear()
        composeViewModelIds.clear()
        composeViewModels.clear()
        composeSources.clear()
        searchCalls.clear()
    }
}

private object RestorationTestStores {
    @Volatile private var history: DataStore<Preferences>? = null

    fun historyStore(activity: ComponentActivity): DataStore<Preferences> =
        history ?: synchronized(this) {
            history ?: PreferenceDataStoreFactory.create {
                File(activity.cacheDir, "restore_test_history.prefs_pb")
            }.also { history = it }
        }
}

// ── NavHost ──────────────────────────────────────

@Composable
internal fun RestorationNavHost(
    activityNumber: Int,
    historyStore: DataStore<Preferences>,
    creationLog: MutableList<String>,
    featureStoreOwner: ViewModelStoreOwner
) {
    val navController = rememberNavController()

    NavHost(navController, startDestination = MainRoute.Inbox, Modifier.fillMaxSize()) {

        composable<MainRoute.Inbox> {
            Column(Modifier.fillMaxSize()) {
                NavRow(navController)
                val st = rememberLazyListState()
                Text("IP:${st.firstVisibleItemIndex}:${st.firstVisibleItemScrollOffset}", Modifier.testTag("inbox_position"))
                LazyColumn(state = st, modifier = Modifier.fillMaxSize().testTag("inbox_list")) {
                    items(100) { i -> Text("Inbox $i", Modifier.testTag("inbox_$i").height(40.dp)) }
                }
            }
        }

        composable<MainRoute.Trash> {
            Column(Modifier.fillMaxSize()) {
                NavRow(navController)
                val st = rememberLazyListState()
                Text("TP:${st.firstVisibleItemIndex}:${st.firstVisibleItemScrollOffset}", Modifier.testTag("trash_position"))
                LazyColumn(state = st, modifier = Modifier.fillMaxSize().testTag("trash_list")) {
                    items(100) { i -> Text("Trash $i", Modifier.testTag("trash_$i").height(40.dp)) }
                }
            }
        }

        composable<MainRoute.Search> { backStackEntry ->
            Column(Modifier.fillMaxSize()) {
                NavRow(navController)
                val fakeSource = remember { FakeSearchEmailSource() }
                val fakeGuard = remember { FakeSessionWriteGuard() }
                val vm: SearchViewModel = viewModel(
                    key = "Search:${backStackEntry.id}",
                    viewModelStoreOwner = featureStoreOwner,
                    factory = fakeSearchFactory(fakeSource, historyStore, fakeGuard, "#${activityNumber}", creationLog),
                    extras = backStackEntry.defaultViewModelCreationExtras
                )
                val query by vm.query.collectAsState()
                val uiState by vm.uiState.collectAsStateWithLifecycle()
                DeterministicSearchUi(
                    modifier = Modifier.fillMaxSize(),
                    listState = rememberLazyListState(),
                    query = query, uiState = uiState,
                    onQueryChange = vm::onQueryChange, onClear = vm::clearQuery,
                    onLoadNextPage = vm::loadNextPage
                )
            }
        }

        composable<MainRoute.EmailDetail> { backStackEntry ->
            val r: MainRoute.EmailDetail = backStackEntry.toRoute()
            Column {
                NavRow(navController)
                Box(Modifier.weight(1f).testTag("detail_screen")) {
                    Text("Detail ${r.emailId}", Modifier.testTag("detail_text"))
                }
            }
        }

        composable<MainRoute.Compose> { backStackEntry ->
            Column(Modifier.fillMaxSize()) {
                NavRow(navController)
                val route: MainRoute.Compose = backStackEntry.toRoute()
                val args = route.toComposeArgs()
                val fakeSource = remember { FakeComposeEmailSource() }
                val sp = remember { FakeStringProvider() }
                val vm: ComposeViewModel = viewModel(
                    key = "Compose:${backStackEntry.id}",
                    viewModelStoreOwner = featureStoreOwner,
                    factory = fakeComposeFactory(args, fakeSource, sp, "#${activityNumber}", creationLog),
                    extras = backStackEntry.defaultViewModelCreationExtras
                )
                val uiState by vm.uiState.collectAsStateWithLifecycle()
                DeterministicComposeUi(
                    modifier = Modifier.fillMaxSize(),
                    state = uiState,
                    onToChanged = vm::onToChanged, onCcChanged = vm::onCcChanged,
                    onBccChanged = vm::onBccChanged, onSubjectChanged = vm::onSubjectChanged,
                    onBodyChanged = vm::onBodyChanged, onToggleCcBcc = vm::onToggleCcBcc,
                    onSend = vm::onSend
                )
            }
        }

        composable<MainRoute.Settings> {
            SettingsNavHost(
                currentPalette = ColorPalette.Blue,
                isDarkMode = false, useCustomFont = false,
                onPaletteChange = {}, onDarkModeChange = {},
                onUseCustomFontChange = {}, onSignOut = {}, onBack = {}
            )
        }
    }
}

// ── Navigation helper row ────────────────────────

@Composable
private fun NavRow(navController: NavHostController) {
    Row {
        TextButton(onClick = { navController.navigateToTopLevel(MainRoute.Inbox) },
            modifier = Modifier.testTag("nav_inbox")) { Text("Inbox") }
        TextButton(onClick = { navController.navigateToTopLevel(MainRoute.Trash) },
            modifier = Modifier.testTag("nav_trash")) { Text("Trash") }
        TextButton(onClick = { navController.navigateToOverlay(MainRoute.Search) },
            modifier = Modifier.testTag("nav_search")) { Text("Search") }
        TextButton(onClick = { navController.navigateToTopLevel(MainRoute.Settings) },
            modifier = Modifier.testTag("nav_settings")) { Text("Settings") }
        TextButton(onClick = { navController.navigateToOverlay(MainRoute.EmailDetail("d1")) },
            modifier = Modifier.testTag("nav_detail")) { Text("Detail") }
        TextButton(onClick = { navController.navigateToOverlay(MainRoute.Compose(ComposeMode.WRITE)) },
            modifier = Modifier.testTag("nav_compose")) { Text("Compose") }
        TextButton(onClick = { navController.navigateToOverlay(MainRoute.Compose(ComposeMode.REPLY, "reply-42")) },
            modifier = Modifier.testTag("nav_compose_reply")) { Text("Reply") }
        TextButton(onClick = { navController.popBackStack() },
            modifier = Modifier.testTag("nav_back")) { Text("Back") }
    }
}

// ── Factory builders ───────────────────────────

private fun fakeSearchFactory(
    source: FakeSearchEmailSource, historyStore: DataStore<Preferences>,
    guard: FakeSessionWriteGuard, tag: String, creationLog: MutableList<String>
) = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val handle = extras.createSavedStateHandle()
        val vm = SearchViewModel(source, historyStore, guard, handle)
        creationLog.add("SearchVM@$tag=${System.identityHashCode(vm)}")
        RestorationProbe.searchViewModelIds += System.identityHashCode(vm)
        return vm as T
    }
}

private fun fakeComposeFactory(
    args: ComposeArgs, source: FakeComposeEmailSource,
    sp: FakeStringProvider, tag: String, creationLog: MutableList<String>
) = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val handle = extras.createSavedStateHandle()
        val vm = ComposeViewModel(args, source, sp, handle)
        creationLog.add("ComposeVM@$tag=${System.identityHashCode(vm)}")
        RestorationProbe.composeViewModelIds += System.identityHashCode(vm)
        RestorationProbe.composeViewModels += vm
        RestorationProbe.composeSources += source
        return vm as T
    }
}

// ── FakeSessionWriteGuard ────────────────────────

class FakeSessionWriteGuard : SessionWriteGuard {
    private var active = true
    override suspend fun activate() { active = true }
    override suspend fun capture(): SessionWriteLease? =
        if (active) object : SessionWriteLease { override val generation = 1L } else null
    override suspend fun <T> commit(lease: SessionWriteLease, block: suspend () -> T): T? = block()
    override suspend fun invalidate() { active = false }
}

// ── Deterministic UI ─────────────────────────────

@Composable
private fun DeterministicSearchUi(
    modifier: Modifier = Modifier,
    listState: LazyListState, query: String, uiState: SearchUiState,
    onQueryChange: (String) -> Unit, onClear: () -> Unit, onLoadNextPage: () -> Unit
) {
    Column(modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.testTag("search_input"),
            singleLine = true
        )
        Text("Q:$query", Modifier.testTag("search_query"))
        Text("SP:${listState.firstVisibleItemIndex}:${listState.firstVisibleItemScrollOffset}", Modifier.testTag("search_position"))
        when (uiState) {
            is SearchUiState.Idle -> Text("Idle", Modifier.testTag("search_idle"))
            is SearchUiState.Loading -> Text("Loading", Modifier.testTag("search_loading"))
            is SearchUiState.Error -> Text("Error", Modifier.testTag("search_error"))
            is SearchUiState.Empty -> Text("Empty", Modifier.testTag("search_empty"))
            is SearchUiState.Results -> {
                Text("R:${uiState.emails.size}", Modifier.testTag("search_results_count"))
                Text("T:${uiState.nextPageToken}", Modifier.testTag("search_token"))
                TextButton(onClick = onLoadNextPage, modifier = Modifier.testTag("search_load_next")) { Text("More") }
                LazyColumn(state = listState, modifier = Modifier.testTag("search_list")) {
                    items(uiState.emails.size) { i ->
                        Text("Item $i", Modifier.testTag("search_result_$i").height(200.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DeterministicComposeUi(
    modifier: Modifier = Modifier,
    state: ComposeUiState,
    onToChanged: (String) -> Unit, onCcChanged: (String) -> Unit,
    onBccChanged: (String) -> Unit, onSubjectChanged: (String) -> Unit,
    onBodyChanged: (String) -> Unit, onToggleCcBcc: () -> Unit, onSend: () -> Unit
) {
    Column(modifier) {
        Text("mode:${state.composeMode}", Modifier.testTag("compose_mode"))
        Text("original:${state.originalEmail?.id}", Modifier.testTag("compose_original_id"))
        Text("sending:${state.isSending}", Modifier.testTag("compose_is_sending"))
        Text("result:${state.sendResult}", Modifier.testTag("compose_send_result"))
        Text("resultNull:${state.sendResult == null}", Modifier.testTag("compose_result_null"))
        TextButton(onClick = onToggleCcBcc, modifier = Modifier.testTag("compose_toggle_ccbcc")) { Text("Toggle") }
        TextButton(onClick = onSend, modifier = Modifier.testTag("compose_send")) { Text("Send") }
        OutlinedTextField(value = state.toField, onValueChange = onToChanged, modifier = Modifier.testTag("compose_input_to"), singleLine = true)
        OutlinedTextField(value = state.ccField, onValueChange = onCcChanged, modifier = Modifier.testTag("compose_input_cc"), singleLine = true)
        OutlinedTextField(value = state.bccField, onValueChange = onBccChanged, modifier = Modifier.testTag("compose_input_bcc"), singleLine = true)
        OutlinedTextField(value = state.subject, onValueChange = onSubjectChanged, modifier = Modifier.testTag("compose_input_subject"), singleLine = true)
        OutlinedTextField(value = state.bodyText, onValueChange = onBodyChanged, modifier = Modifier.testTag("compose_input_body"), singleLine = true)
        Text("to:${state.toField}", Modifier.testTag("compose_to"))
        Text("cc:${state.ccField}", Modifier.testTag("compose_cc"))
        Text("bcc:${state.bccField}", Modifier.testTag("compose_bcc"))
        Text("subj:${state.subject}", Modifier.testTag("compose_subject"))
        Text("body:${state.bodyText}", Modifier.testTag("compose_body"))
        Text("ccBcc:${state.isCcBccExpanded}", Modifier.testTag("compose_cc_bcc_expanded"))
    }
}

// ── Fake StringProvider ───────────────────────────

private class FakeStringProvider : com.david.mailapp.core.localization.StringProvider {
    override fun getString(resId: Int, vararg formatArgs: Any): String = when (resId) {
        com.david.mailapp.R.string.date_pattern_short -> "yyyy-MM-dd"
        com.david.mailapp.R.string.subject_prefix_reply -> "Re: ${formatArgs.firstOrNull() ?: ""}"
        com.david.mailapp.R.string.subject_prefix_forward -> "Fwd: ${formatArgs.firstOrNull() ?: ""}"
        com.david.mailapp.R.string.compose_reply_body_format ->
            "On ${formatArgs.getOrNull(0)}, ${formatArgs.getOrNull(1)} wrote:\n> ${formatArgs.getOrNull(2)}"
        else -> "str_$resId"
    }
}
