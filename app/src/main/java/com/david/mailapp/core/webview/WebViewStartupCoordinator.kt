package com.david.mailapp.core.webview

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewOutcomeReceiver
import androidx.webkit.WebViewStartUpConfig
import androidx.webkit.WebViewStartUpResult
import androidx.webkit.WebViewStartupException
import com.david.mailapp.feature.emaildetail.EmailRenderTrace
import java.util.concurrent.Executor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal sealed interface WebViewStartupState {
    data object Idle : WebViewStartupState
    data object Starting : WebViewStartupState
    data object Ready : WebViewStartupState
    data class Failed(val reason: String) : WebViewStartupState
}

internal interface WebViewStartupGate {
    val state: StateFlow<WebViewStartupState>
    fun start(context: Context)
    fun retry(context: Context)
}

internal class WebViewStartupStateController {
    private val mutableState = MutableStateFlow<WebViewStartupState>(WebViewStartupState.Idle)
    val state: StateFlow<WebViewStartupState> = mutableState.asStateFlow()

    @Synchronized
    fun begin(forceRetry: Boolean = false): Boolean {
        val current = mutableState.value
        if (current == WebViewStartupState.Ready || current == WebViewStartupState.Starting) return false
        if (current is WebViewStartupState.Failed && !forceRetry) return false
        mutableState.value = WebViewStartupState.Starting
        return true
    }

    fun complete() {
        mutableState.value = WebViewStartupState.Ready
    }

    fun fail(error: Throwable) {
        mutableState.value = WebViewStartupState.Failed(error.javaClass.simpleName)
    }
}

internal object ProcessWebViewStartupGate : WebViewStartupGate {
    private const val TRACE_MAIL = "webview_startup"
    private val controller = WebViewStartupStateController()
    private val executor: Executor = Dispatchers.Default.asExecutor()

    override val state: StateFlow<WebViewStartupState> = controller.state

    override fun start(context: Context) {
        startInternal(context, forceRetry = false)
    }

    override fun retry(context: Context) {
        startInternal(context, forceRetry = true)
    }

    private fun startInternal(context: Context, forceRetry: Boolean) {
        if (!controller.begin(forceRetry)) return
        EmailRenderTrace.d(TRACE_MAIL, "WV", "WV_STARTUP_START")
        val config = WebViewStartUpConfig.Builder(executor)
            .setShouldRunUiThreadStartUpTasks(true)
            .build()
        try {
            WebViewCompat.startUpWebView(
                context.applicationContext,
                config,
                object : WebViewOutcomeReceiver<WebViewStartUpResult, WebViewStartupException> {
                    override fun onResult(result: WebViewStartUpResult) {
                        controller.complete()
                        EmailRenderTrace.d(TRACE_MAIL, "WV", "WV_STARTUP_READY")
                    }

                    override fun onError(error: WebViewStartupException) {
                        reportFailure(error)
                    }
                }
            )
        } catch (error: Exception) {
            reportFailure(error)
        }
    }

    private fun reportFailure(error: Throwable) {
        controller.fail(error)
        EmailRenderTrace.d(
            TRACE_MAIL,
            "WV",
            "WV_STARTUP_FAILED",
            "reason=${error.javaClass.simpleName}"
        )
    }
}

@Composable
internal fun WebViewStartupAfterFirstSessionFrame() {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        // The second frame starts only after the authenticated UI has produced its first frame.
        withFrameNanos { }
        withFrameNanos { }
        ProcessWebViewStartupGate.start(context)
    }
}
