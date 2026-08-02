package com.david.mailapp.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlinx.serialization.Serializable

class DestinationLifecycleTest {

    @get:Rule
    val composeRule = createComposeRule()

    class TestLifecycleViewModel : ViewModel() {
        var isCleared = false
            private set
        var clearCount = 0
            private set
        lateinit var testJob: Job

        init {
            testJob = viewModelScope.launch {
                try {
                    delay(1000000)
                } catch (e: CancellationException) {
                    // expected
                }
            }
        }

        override fun onCleared() {
            super.onCleared()
            isCleared = true
            clearCount++
        }
    }

    @Serializable
    data object RouteA

    @Serializable
    data object RouteB

    @Test
    fun destination_lifecycle_with_real_navigation() {
        var firstVm: TestLifecycleViewModel? = null
        var recomposeTrigger by mutableStateOf(0)

        composeRule.setContent {
            val navController = rememberNavController()
            NavHost(navController = navController, startDestination = RouteA) {
                composable<RouteA> {
                    val trigger = recomposeTrigger
                    val vm: TestLifecycleViewModel = viewModel(
                        factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return TestLifecycleViewModel() as T
                            }
                        }
                    )
                    firstVm = vm
                }
                composable<RouteB> {
                    // Route B
                }
            }
        }

        composeRule.waitForIdle()
        val vm1 = firstVm
        assertTrue("ViewModel should be initialized", vm1 != null)
        assertTrue("Job should be active", vm1!!.testJob.isActive)

        // Trigger recomposition
        recomposeTrigger++
        composeRule.waitForIdle()
        val vm1AfterRecompose = firstVm
        assertTrue("ViewModel instance should be preserved after recomposition", vm1 === vm1AfterRecompose)
        assertTrue("Job should remain active after recomposition", vm1.testJob.isActive)
        assertTrue("ViewModel should not be cleared on recomposition", !vm1.isCleared)
    }

    @Test
    fun navigate_away_to_overlay_retains_viewmodel() {
        var firstVm: TestLifecycleViewModel? = null
        lateinit var localNavController: androidx.navigation.NavHostController

        composeRule.setContent {
            localNavController = rememberNavController()
            NavHost(navController = localNavController, startDestination = RouteA) {
                composable<RouteA> {
                    val vm: TestLifecycleViewModel = viewModel(
                        factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return TestLifecycleViewModel() as T
                            }
                        }
                    )
                    firstVm = vm
                }
                composable<RouteB> {
                    // overlay/detail screen
                }
            }
        }

        composeRule.waitForIdle()
        val vm1 = firstVm
        assertTrue("ViewModel should be initialized", vm1 != null)
        assertTrue("Job should be active", vm1!!.testJob.isActive)

        // Navigate from RouteA to RouteB (retains RouteA in backstack)
        composeRule.runOnUiThread {
            localNavController.navigate(RouteB)
        }
        composeRule.waitForIdle()

        // Verify vm1 is NOT cleared
        assertTrue("ViewModel should NOT be cleared when covered by another screen", !vm1.isCleared)
        assertTrue("Job should still be active", vm1.testJob.isActive)

        // Now pop back to RouteA
        composeRule.runOnUiThread {
            localNavController.popBackStack()
        }
        composeRule.waitForIdle()

        // Verify vm1 is still not cleared
        assertTrue("ViewModel should NOT be cleared after returning to it", !vm1.isCleared)
    }

    @Test
    fun pop_definitively_clears_viewmodel_exactly_once() {
        var bVm: TestLifecycleViewModel? = null
        lateinit var localNavController: androidx.navigation.NavHostController

        composeRule.setContent {
            localNavController = rememberNavController()
            NavHost(navController = localNavController, startDestination = RouteA) {
                composable<RouteA> {
                    // root
                }
                composable<RouteB> {
                    val vm: TestLifecycleViewModel = viewModel(
                        factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return TestLifecycleViewModel() as T
                            }
                        }
                    )
                    bVm = vm
                }
            }
        }

        composeRule.waitForIdle()

        // Navigate to RouteB
        composeRule.runOnUiThread {
            localNavController.navigate(RouteB)
        }
        composeRule.waitForIdle()

        val vmB = bVm
        assertTrue("RouteB ViewModel should be initialized", vmB != null)
        assertTrue("Job should be active", vmB!!.testJob.isActive)

        // Pop back to RouteA (definitively removes RouteB)
        composeRule.runOnUiThread {
            localNavController.popBackStack()
        }
        composeRule.waitForIdle()

        // Verify vmB is cleared
        assertTrue("ViewModel should be cleared when popped definitively", vmB.isCleared)
        assertEquals("Should be cleared exactly once", 1, vmB.clearCount)
        assertTrue("Job should be cancelled", vmB.testJob.isCancelled)
    }
}
