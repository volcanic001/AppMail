package com.david.mailapp.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

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

    @Test
    fun destination_clears_viewmodel_when_removed_and_creates_new_one_on_remount() {
        var showScreen by mutableStateOf(true)
        var firstVm: TestLifecycleViewModel? = null
        var secondVm: TestLifecycleViewModel? = null

        composeRule.setContent {
            if (showScreen) {
                val owner = rememberDestinationViewModelStoreOwner()
                val vm: TestLifecycleViewModel = viewModel(
                    viewModelStoreOwner = owner,
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return TestLifecycleViewModel() as T
                        }
                    }
                )
                if (firstVm == null) {
                    firstVm = vm
                } else {
                    secondVm = vm
                }
            }
        }

        composeRule.waitForIdle()
        val vm1 = firstVm
        assertTrue("ViewModel should be initialized", vm1 != null)
        assertTrue("Job should be active", vm1!!.testJob.isActive)

        // Retire from composition
        showScreen = false
        composeRule.waitForIdle()

        assertTrue("ViewModel should be cleared", vm1.isCleared)
        assertEquals("Should be cleared exactly once", 1, vm1.clearCount)
        assertTrue("Job should be cancelled", vm1.testJob.isCancelled)

        // Mount again
        showScreen = true
        composeRule.waitForIdle()

        val vm2 = secondVm
        assertTrue("Second ViewModel should be initialized", vm2 != null)
        assertNotSame("Should be a fresh instance", vm1, vm2)
        assertTrue("New job should be active", vm2!!.testJob.isActive)
    }
}
