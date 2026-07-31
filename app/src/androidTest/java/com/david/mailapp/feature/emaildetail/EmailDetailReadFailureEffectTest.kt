package com.david.mailapp.feature.emaildetail

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.david.mailapp.core.localization.StringProvider
import com.david.mailapp.core.localization.UiErrorReason
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EmailDetailReadFailureEffectTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun read_failure_is_presented_as_localized_snackbar() {
        val failures = MutableSharedFlow<UiErrorReason>(extraBufferCapacity = 1)

        composeRule.setContent {
            MaterialTheme {
                val host = remember { SnackbarHostState() }
                Box {
                    EmailDetailReadFailureEffect(
                        failureEvents = failures,
                        snackbarHostState = host,
                        stringProvider = TestStringProvider
                    )
                    SnackbarHost(hostState = host)
                }
            }
        }

        composeRule.runOnIdle {
            assertTrue(failures.tryEmit(UiErrorReason.NO_CONNECTION))
        }

        composeRule.onNodeWithText(OFFLINE_MESSAGE).assertExists()
    }

    private object TestStringProvider : StringProvider {
        override fun getString(resId: Int, vararg formatArgs: Any): String {
            return OFFLINE_MESSAGE
        }
    }

    private companion object {
        const val OFFLINE_MESSAGE = "Sin conexión de prueba"
    }
}
