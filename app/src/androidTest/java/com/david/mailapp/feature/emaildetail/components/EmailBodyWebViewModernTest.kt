package com.david.mailapp.feature.emaildetail.components

import android.content.Context
import android.widget.FrameLayout
import android.webkit.WebView
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.platform.app.InstrumentationRegistry
import com.david.mailapp.core.webview.WebViewStartupGate
import com.david.mailapp.core.webview.WebViewStartupState
import java.lang.ref.WeakReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EmailBodyWebViewModernTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun startupGate_preventsWebViewCreationUntilReady() {
        val gate = MutableStartupGate(WebViewStartupState.Starting)
        composeRule.setContent {
            MaterialTheme {
                EmailBodyWebView(
                    body = "<p>ready</p>",
                    isDark = false,
                    traceMail = "modern-startup",
                    startupGate = gate,
                    modifier = Modifier
                )
            }
        }

        onView(isAssignableFrom(WebView::class.java)).check(doesNotExist())

        composeRule.runOnIdle { gate.mutableState.value = WebViewStartupState.Ready }
        composeRule.waitForIdle()

        onView(isAssignableFrom(WebView::class.java)).check(matches(isDisplayed()))
    }

    @Test
    fun rendererTermination_removesInstanceAndOffersSingleRetry() {
        lateinit var parent: FrameLayout
        lateinit var webView: WebView
        val runtime = EmailBodyWebViewRuntimeState()

        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            parent = FrameLayout(context)
            webView = WebView(context)
            parent.addView(webView)
            runtime.webViewRef.value = WeakReference(webView)
            handleRenderProcessGone(
                webView = webView,
                runtimeState = runtime,
                traceMail = "modern-renderer",
                didCrash = true
            )
        }

        assertEquals(0, parent.childCount)
        assertNull(runtime.webViewRef.value)
        assertTrue(runtime.released.value)
        assertTrue(runtime.rendererFailure.value?.didCrash == true)
        assertTrue(runtime.rendererFailure.value?.canRetry == true)
    }

    @Test
    fun pageFinished_isDiagnosticOnly() {
        var readyCallbacks = 0

        instrumentation.runOnMainSync {
            val webView = WebView(instrumentation.targetContext)
            val client = CustomTabsWebViewClient(
                ctx = instrumentation.targetContext,
                traceMail = "modern-page-finished",
                loadKey = "document",
                onPageReady = { readyCallbacks++ },
                onRendererGone = { _, _ -> }
            )

            client.onPageFinished(webView, "about:blank")
            webView.destroy()
        }

        assertEquals(0, readyCallbacks)
    }

    private class MutableStartupGate(initial: WebViewStartupState) : WebViewStartupGate {
        val mutableState = MutableStateFlow(initial)
        override val state: StateFlow<WebViewStartupState> = mutableState
        override fun start(context: Context) = Unit
        override fun retry(context: Context) {
            mutableState.value = WebViewStartupState.Starting
        }
    }
}
