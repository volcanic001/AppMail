package com.david.mailapp.feature.emaildetail.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebView.HitTestResult
import android.webkit.WebViewClient
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.espresso.Espresso
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.david.mailapp.ui.theme.ColorPalette
import com.david.mailapp.ui.theme.MailAppTheme
import com.david.mailapp.core.webview.WebViewStartupGate
import com.david.mailapp.core.webview.WebViewStartupState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.hamcrest.Matcher
import org.hamcrest.Matchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Baseline characterization suite for [EmailBodyWebView].
 *
 * Subfase 2.3 (6 cases): mounting with a pending body, hardening settings,
 * image policy, algorithmic darkening, canonical fixture reuse and
 * stale-callback rejection, all on a real WebView via the public API.
 *
 * Subfase 3.1 (12 cases): the closed S01–S08 / S10–S13 load-and-document
 * matrix. Each scenario saves its real MailRenderTrace Logcat lines and, for
 * every scenario except S10, a full native PNG capture, and publishes them to
 * a persistent device location (see [publishEvidence]). Only the public
 * composable and the Compose/Espresso tree are used; no production
 * parameters, interfaces, tags, flags or hooks are added.
 *
 * Subfase 3.2 (4 cases): S09 Custom Tabs round-trip, S14 scroll + pause/
 * resume, S15 long-press on the inline data: image, S16 release/reopen,
 * reusing the same evidence pipeline (see [publishEvidence]).
 *
 * Subfase 4.1 (physical regression): the same 22 cases also run on the Pixel 9
 * (API 37). The environment (emulator vs physical) selects the evidence suffix
 * (-fisico) and device directory (emailbody-3.2 vs emailbody-4.1); native
 * display dimensions replace the fixed 1080x2400 checks, the S14 content
 * comparison region scales with the screen, and S09 resolves the real HTTPS
 * browser instead of hardcoding Chrome.
 */
@OptIn(ExperimentalTestApi::class)
class EmailBodyWebViewBaselineTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    // ═══════════════════════════════════════════════════════════════
    // Subfase 2.3 — characterization cases
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun bodyPending_keepsWebViewMounted_andLoadsWhenBodyArrives() {
        val body = mutableStateOf<String?>(null)
        val showImages = mutableStateOf(true)
        val isDark = mutableStateOf(false)
        val fixtureId = mutableStateOf<String?>(null)
        val renderedIds = CopyOnWriteArrayList<String>()

        composeRule.mountBodyWebView(body, showImages, isDark, fixtureId, renderedIds, TRACE_KEY)

        // A single WebView is mounted while the body is still pending.
        assertSingleWebView()
        val pendingWebView = captureWebView()

        // No rendered callback can be dispatched without a prepared document.
        composeRule.waitForIdle()
        assertTrue("no callback while body is pending", renderedIds.isEmpty())

        // The body arrives: exactly one visual load and one callback.
        composeRule.runOnIdle {
            fixtureId.value = F01
            body.value = fixture(F01_FILE)
        }
        waitForRendered(F01, renderedIds)

        assertEquals(listOf(F01), renderedIds.toList())
        val loadedWebView = captureWebView()
        assertTrue("loaded document has a non-null url", onMainSync { loadedWebView.url != null })
        assertSame("the WebView instance must survive the body transition", pendingWebView, loadedWebView)
    }

    @Test
    fun hardeningViewportAndZoomSettings_matchBaseline() {
        val body = mutableStateOf<String?>(fixture(F01_FILE))
        val showImages = mutableStateOf(true)
        val isDark = mutableStateOf(false)
        val fixtureId = mutableStateOf<String?>(F01)
        val renderedIds = CopyOnWriteArrayList<String>()

        composeRule.mountBodyWebView(body, showImages, isDark, fixtureId, renderedIds, TRACE_KEY)
        waitForRendered(F01, renderedIds)

        val webView = captureWebView()

        val layout = onMainSync { webView.layoutParams }
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, layout.width)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, layout.height)
        assertFalse("vertical scrollbar disabled", onMainSync { webView.isVerticalScrollBarEnabled })
        assertFalse("horizontal scrollbar disabled", onMainSync { webView.isHorizontalScrollBarEnabled })

        val settings = onMainSync { webView.settings }
        assertFalse("JavaScript disabled", onMainSync { settings.javaScriptEnabled })
        assertFalse("DOM storage disabled", onMainSync { settings.domStorageEnabled })
        assertFalse("file access disabled", onMainSync { settings.allowFileAccess })
        assertFalse("content access disabled", onMainSync { settings.allowContentAccess })
        assertFalse("file URL file access disabled", onMainSync { settings.allowFileAccessFromFileURLs })
        assertFalse("file URL universal access disabled", onMainSync { settings.allowUniversalAccessFromFileURLs })
        assertTrue("media playback requires user gesture", onMainSync { settings.mediaPlaybackRequiresUserGesture })
        assertEquals(WebSettings.LOAD_NO_CACHE, onMainSync { settings.cacheMode })
        assertTrue("wide viewport enabled", onMainSync { settings.useWideViewPort })
        assertTrue("overview mode enabled", onMainSync { settings.loadWithOverviewMode })
        assertEquals(100, onMainSync { settings.textZoom })
        assertTrue("built-in zoom controls enabled", onMainSync { settings.builtInZoomControls })
        assertFalse("display zoom controls hidden", onMainSync { settings.displayZoomControls })
        assertTrue("zoom supported", onMainSync { settings.supportZoom() })

        // showImages=true (default): network loads are not blocked.
        assertFalse("network images allowed", onMainSync { settings.blockNetworkImage })
        assertFalse("network loads allowed", onMainSync { settings.blockNetworkLoads })
    }

    @Test
    fun networkBlocking_followsShowImagesAcrossRecomposition() {
        val body = mutableStateOf<String?>(fixture(F01_FILE))
        val showImages = mutableStateOf(true)
        val isDark = mutableStateOf(false)
        val fixtureId = mutableStateOf<String?>(F01)
        val renderedIds = CopyOnWriteArrayList<String>()

        composeRule.mountBodyWebView(body, showImages, isDark, fixtureId, renderedIds, TRACE_KEY)
        waitForRendered(F01, renderedIds)

        val webView = captureWebView()
        assertFalse(onMainSync { webView.settings.blockNetworkImage })
        assertFalse(onMainSync { webView.settings.blockNetworkLoads })

        composeRule.runOnIdle { showImages.value = false }
        waitForRenderedCount(2, renderedIds)
        assertTrue("remote images blocked", onMainSync { webView.settings.blockNetworkImage })
        assertTrue("network loads blocked", onMainSync { webView.settings.blockNetworkLoads })
        assertSame(webView, captureWebView())

        composeRule.runOnIdle { showImages.value = true }
        waitForRenderedCount(3, renderedIds)
        assertFalse("remote images allowed again", onMainSync { webView.settings.blockNetworkImage })
        assertFalse("network loads allowed again", onMainSync { webView.settings.blockNetworkLoads })
        assertSame(webView, captureWebView())

        assertEquals(listOf(F01, F01, F01), renderedIds.toList())
    }

    @Test
    fun algorithmicDarkening_followsIsDark() {
        // Contractual devices: the API 36 emulator AVD, or the physical Pixel 9
        // on API 37. Any other SDK level is outside the characterization matrix.
        assertEquals(
            "Baseline suite must run on a contractual device (emulator API 36 or Pixel 9 API 37)",
            if (isEmulator) 36 else 37,
            Build.VERSION.SDK_INT
        )

        val body = mutableStateOf<String?>(fixture(F01_FILE))
        val showImages = mutableStateOf(true)
        val isDark = mutableStateOf(false)
        val fixtureId = mutableStateOf<String?>(F01)
        val renderedIds = CopyOnWriteArrayList<String>()

        composeRule.mountBodyWebView(body, showImages, isDark, fixtureId, renderedIds, TRACE_KEY)
        waitForRendered(F01, renderedIds)

        val webView = captureWebView()

        // Native getter (API 33+); guaranteed to run by the SDK_INT assertion.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            assertFalse(onMainSync { webView.settings.isAlgorithmicDarkeningAllowed })

            composeRule.runOnIdle { isDark.value = true }
            waitForRenderedCount(2, renderedIds)
            assertTrue(onMainSync { webView.settings.isAlgorithmicDarkeningAllowed })
            assertSame(webView, captureWebView())

            composeRule.runOnIdle { isDark.value = false }
            waitForRenderedCount(3, renderedIds)
            assertFalse(onMainSync { webView.settings.isAlgorithmicDarkeningAllowed })
            assertSame(webView, captureWebView())

            // AndroidX path, when the WebView reports support (expected on this AVD).
            assertTrue(
                "AVD WebView must support algorithmic darkening",
                WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)
            )
            assertFalse(onMainSync { WebSettingsCompat.isAlgorithmicDarkeningAllowed(webView.settings) })
        }

        assertEquals(listOf(F01, F01, F01), renderedIds.toList())
    }

    @Test
    fun canonicalFixtures_loadSequentially_inSameWebView() {
        val body = mutableStateOf<String?>(null)
        val showImages = mutableStateOf(true)
        val isDark = mutableStateOf(false)
        val fixtureId = mutableStateOf<String?>(null)
        val renderedIds = CopyOnWriteArrayList<String>()

        composeRule.mountBodyWebView(body, showImages, isDark, fixtureId, renderedIds, TRACE_KEY)
        val firstWebView = captureWebView()

        composeRule.runOnIdle {
            fixtureId.value = F01
            body.value = fixture(F01_FILE)
        }
        waitForRendered(F01, renderedIds)

        composeRule.runOnIdle {
            fixtureId.value = F02
            body.value = fixture(F02_FILE)
        }
        waitForRendered(F02, renderedIds)

        // The remote-image fixture loads with network blocked (no Internet dependency).
        composeRule.runOnIdle {
            fixtureId.value = F03
            showImages.value = false
            body.value = fixture(F03_FILE)
        }
        waitForRendered(F03, renderedIds)

        composeRule.runOnIdle {
            fixtureId.value = F04
            body.value = fixture(F04_FILE)
        }
        waitForRendered(F04, renderedIds)

        composeRule.runOnIdle {
            fixtureId.value = F05
            body.value = fixture(F05_FILE)
        }
        waitForRendered(F05, renderedIds)

        // One callback per canonical document, in load order, no duplicates,
        // all five loads reusing the same WebView instance.
        assertEquals(listOf(F01, F02, F03, F04, F05), renderedIds.toList())
        assertSame("the five canonical loads reuse one WebView", firstWebView, captureWebView())
    }

    @Test
    fun replacedDocument_doesNotDispatchStaleCallback() {
        val body = mutableStateOf<String?>(null)
        val showImages = mutableStateOf(true)
        val isDark = mutableStateOf(false)
        val fixtureId = mutableStateOf<String?>(null)
        val renderedIds = CopyOnWriteArrayList<String>()

        composeRule.mountBodyWebView(body, showImages, isDark, fixtureId, renderedIds, TRACE_KEY)

        // Long newsletter derived from fixture 02: inner table repeated 20x.
        val longNewsletter = buildLongNewsletter()
        composeRule.runOnIdle {
            fixtureId.value = F02_LONG
            body.value = longNewsletter
        }
        waitForRendered(F02_LONG, renderedIds, timeoutMillis = 45_000L)

        val webView = captureWebView()
        val firstClient = onMainSync { webView.webViewClient }
        assertNotNull("the first load installs a WebViewClient", firstClient)

        // Replace with the simple fixture: a brand-new WebViewClient is installed.
        composeRule.runOnIdle {
            fixtureId.value = F01
            body.value = fixture(F01_FILE)
        }
        waitForNewWebViewClient(firstClient!!)

        // From the replacement on, the previous document must never dispatch.
        val staleCountAtReplacement = renderedIds.count { it == F02_LONG }
        waitForRendered(F01, renderedIds, timeoutMillis = 45_000L)
        assertEquals(
            "no stale callback from the replaced document after the new load started",
            staleCountAtReplacement,
            renderedIds.count { it == F02_LONG }
        )
        assertEquals(listOf(F02_LONG, F01), renderedIds.toList())
        assertSame(webView, captureWebView())
    }

    // ═══════════════════════════════════════════════════════════════
    // Subfase 3.1 — matrix de carga y documento (S01–S08, S10–S13)
    // ═══════════════════════════════════════════════════════════════

    // S01: body=null → F01, claro, imágenes activas.
    @Test
    fun s01_bodyNullToSimple_light_loadsAndTraces() {
        val body = mutableStateOf<String?>(null)
        val showImages = mutableStateOf(true)
        val isDark = mutableStateOf(false)
        val fixtureId = mutableStateOf<String?>(null)
        val renderedIds = CopyOnWriteArrayList<String>()

        clearLogcat()
        composeRule.mountBodyWebView(body, showImages, isDark, fixtureId, renderedIds, TRACE_S01)

        // Nothing loads while the body is pending.
        assertSingleWebView()
        val pendingWebView = captureWebView()
        composeRule.waitForIdle()
        assertTrue("S01: no callback before the body arrives", renderedIds.isEmpty())

        // The body_pending traces must be observable BEFORE the body arrives:
        // the mount-time effects can be cancelled by the body change if the
        // change wins the scheduling race, so wait for the traces first
        // (bounded observable poll, not a blind sleep).
        waitForTrace(TRACE_S01, "HTML_BUILD_WAITING reason=body_pending")

        // Body arrives: the document prepares and renders exactly once.
        composeRule.runOnIdle {
            fixtureId.value = F01
            body.value = fixture(F01_FILE)
        }
        waitForRendered(F01, renderedIds)

        assertEquals(listOf(F01), renderedIds.toList())
        assertSame(pendingWebView, captureWebView())

        val traces = saveTrace(TRACE_S01, "S01-null-a-simple-claro.log")
        assertSingleLoadContract(traces, expectBodyPending = true)
        assertNoStaleCallbacks(traces)

        assertNativeCapture("S01-null-a-simple-claro.png")
    }

    // S02: F01 inicial, claro, imágenes activas.
    @Test
    fun s02_simpleLight_initial() {
        val body = mutableStateOf<String?>(fixture(F01_FILE))
        val showImages = mutableStateOf(true)
        val isDark = mutableStateOf(false)
        val fixtureId = mutableStateOf<String?>(F01)
        val renderedIds = CopyOnWriteArrayList<String>()

        clearLogcat()
        composeRule.mountBodyWebView(body, showImages, isDark, fixtureId, renderedIds, TRACE_S02)
        waitForRendered(F01, renderedIds)

        val traces = saveTrace(TRACE_S02, "S02-simple-claro.log")
        assertSingleLoadContract(traces, expectBodyPending = false)
        assertNoStaleCallbacks(traces)
        assertEquals(listOf(F01), renderedIds.toList())

        assertNativeCapture("S02-simple-claro.png")
    }

    // S03: F01 inicial, oscuro, imágenes activas.
    @Test
    fun s03_simpleDark_initial() {
        val body = mutableStateOf<String?>(fixture(F01_FILE))
        val showImages = mutableStateOf(true)
        val isDark = mutableStateOf(true)
        val fixtureId = mutableStateOf<String?>(F01)
        val renderedIds = CopyOnWriteArrayList<String>()

        clearLogcat()
        composeRule.mountBodyWebView(body, showImages, isDark, fixtureId, renderedIds, TRACE_S03)
        waitForRendered(F01, renderedIds)

        val traces = saveTrace(TRACE_S03, "S03-simple-oscuro.log")
        assertSingleLoadContract(traces, expectBodyPending = false)
        assertNoStaleCallbacks(traces)
        assertEquals(listOf(F01), renderedIds.toList())

        assertNativeCapture("S03-simple-oscuro.png")
    }

    // S04: F02 inicial, claro, imágenes activas.
    @Test
    fun s04_newsletterLight_initial() {
        val body = mutableStateOf<String?>(fixture(F02_FILE))
        val showImages = mutableStateOf(true)
        val isDark = mutableStateOf(false)
        val fixtureId = mutableStateOf<String?>(F02)
        val renderedIds = CopyOnWriteArrayList<String>()

        clearLogcat()
        composeRule.mountBodyWebView(body, showImages, isDark, fixtureId, renderedIds, TRACE_S04)
        waitForRendered(F02, renderedIds)

        val traces = saveTrace(TRACE_S04, "S04-newsletter-claro.log")
        assertSingleLoadContract(traces, expectBodyPending = false)
        assertNoStaleCallbacks(traces)
        assertEquals(listOf(F02), renderedIds.toList())

        assertNativeCapture("S04-newsletter-claro.png")
    }

    // S05: F02 inicial, oscuro, imágenes activas.
    @Test
    fun s05_newsletterDark_initial() {
        val body = mutableStateOf<String?>(fixture(F02_FILE))
        val showImages = mutableStateOf(true)
        val isDark = mutableStateOf(true)
        val fixtureId = mutableStateOf<String?>(F02)
        val renderedIds = CopyOnWriteArrayList<String>()

        clearLogcat()
        composeRule.mountBodyWebView(body, showImages, isDark, fixtureId, renderedIds, TRACE_S05)
        waitForRendered(F02, renderedIds)

        val traces = saveTrace(TRACE_S05, "S05-newsletter-oscuro.log")
        assertSingleLoadContract(traces, expectBodyPending = false)
        assertNoStaleCallbacks(traces)
        assertEquals(listOf(F02), renderedIds.toList())

        assertNativeCapture("S05-newsletter-oscuro.png")
    }

    // S06: F03 inicial, claro, imágenes activas. No se exige descarga desde .invalid.
    @Test
    fun s06_remoteImageEnabled_light() {
        val body = mutableStateOf<String?>(fixture(F03_FILE))
        val showImages = mutableStateOf(true)
        val isDark = mutableStateOf(false)
        val fixtureId = mutableStateOf<String?>(F03)
        val renderedIds = CopyOnWriteArrayList<String>()

        clearLogcat()
        composeRule.mountBodyWebView(body, showImages, isDark, fixtureId, renderedIds, TRACE_S06)
        waitForRendered(F03, renderedIds)

        val traces = saveTrace(TRACE_S06, "S06-remota-habilitada.log")
        assertSingleLoadContract(traces, expectBodyPending = false)
        assertNoStaleCallbacks(traces)
        assertEquals(listOf(F03), renderedIds.toList())

        assertNativeCapture("S06-remota-habilitada.png")
    }

    // S07: F03 inicial, claro, imágenes bloqueadas.
    @Test
    fun s07_remoteImageBlocked_light() {
        val body = mutableStateOf<String?>(fixture(F03_FILE))
        val showImages = mutableStateOf(false)
        val isDark = mutableStateOf(false)
        val fixtureId = mutableStateOf<String?>(F03)
        val renderedIds = CopyOnWriteArrayList<String>()

        clearLogcat()
        composeRule.mountBodyWebView(body, showImages, isDark, fixtureId, renderedIds, TRACE_S07)
        waitForRendered(F03, renderedIds)

        val traces = saveTrace(TRACE_S07, "S07-remota-bloqueada.log")
        assertSingleLoadContract(traces, expectBodyPending = false)
        assertNoStaleCallbacks(traces)
        assertEquals(listOf(F03), renderedIds.toList())

        assertNativeCapture("S07-remota-bloqueada.png")
    }

    // S08: F04 inicial, claro, imágenes remotas bloqueadas (data: sigue cargando).
    @Test
    fun s08_dataImageRemoteBlocked_light() {
        val body = mutableStateOf<String?>(fixture(F04_FILE))
        val showImages = mutableStateOf(false)
        val isDark = mutableStateOf(false)
        val fixtureId = mutableStateOf<String?>(F04)
        val renderedIds = CopyOnWriteArrayList<String>()

        clearLogcat()
        composeRule.mountBodyWebView(body, showImages, isDark, fixtureId, renderedIds, TRACE_S08)
        waitForRendered(F04, renderedIds)

        val traces = saveTrace(TRACE_S08, "S08-data-bloqueo-remoto.log")
        assertSingleLoadContract(traces, expectBodyPending = false)
        assertNoStaleCallbacks(traces)
        assertEquals(listOf(F04), renderedIds.toList())

        assertNativeCapture("S08-data-bloqueo-remoto.png")
    }

    // S10: F01 y recomposición sin cambiar entradas — solo traza, misma instancia y cliente.
    @Test
    fun s10_equivalentRecomposition_noReload() {
        val body = mutableStateOf<String?>(fixture(F01_FILE))
        val showImages = mutableStateOf(true)
        val isDark = mutableStateOf(false)
        val fixtureId = mutableStateOf<String?>(F01)
        val renderedIds = CopyOnWriteArrayList<String>()
        val recomposeTick = mutableStateOf(0)

        clearLogcat()
        composeRule.mountBodyWebView(
            body, showImages, isDark, fixtureId, renderedIds, TRACE_S10, recomposeTick
        )
        waitForRendered(F01, renderedIds)

        val webView = captureWebView()
        val clientBefore = onMainSync { webView.webViewClient }

        // Equivalent recomposition: the same inputs, forced through the tree.
        composeRule.runOnIdle { recomposeTick.value++ }
        composeRule.waitForIdle()

        val traces = saveTrace(TRACE_S10, "S10-recomposicion-equivalente.log")
        assertSingleLoadContract(traces, expectBodyPending = false)
        assertNoStaleCallbacks(traces)
        assertEquals(
            "S10: exactly one already_loaded skip",
            1,
            traces.count {
                it.event == "WV_UPDATE" &&
                    it.details.startsWith("action=skip") &&
                    it.details.contains("reason=already_loaded")
            }
        )
        assertEquals(listOf(F01), renderedIds.toList())
        assertSame(webView, captureWebView())
        assertSame(clientBefore, onMainSync { webView.webViewClient })
    }

    // S11: F01 → F02, claro — dos cargas con claves distintas.
    @Test
    fun s11_bodyChange_simpleToNewsletter_light() {
        val body = mutableStateOf<String?>(fixture(F01_FILE))
        val showImages = mutableStateOf(true)
        val isDark = mutableStateOf(false)
        val fixtureId = mutableStateOf<String?>(F01)
        val renderedIds = CopyOnWriteArrayList<String>()

        clearLogcat()
        composeRule.mountBodyWebView(body, showImages, isDark, fixtureId, renderedIds, TRACE_S11)
        waitForRendered(F01, renderedIds)

        composeRule.runOnIdle {
            fixtureId.value = F02
            body.value = fixture(F02_FILE)
        }
        waitForRendered(F02, renderedIds)

        val traces = saveTrace(TRACE_S11, "S11-cambio-body.log")
        assertTwoLoadContract(traces)
        assertNoStaleCallbacks(traces)
        assertEquals(listOf(F01, F02), renderedIds.toList())

        assertNativeCapture("S11-cambio-body.png")
    }

    // S12: F01 claro → oscuro — dos claves distintas por el tema.
    @Test
    fun s12_themeChange_lightToDark() {
        val body = mutableStateOf<String?>(fixture(F01_FILE))
        val showImages = mutableStateOf(true)
        val isDark = mutableStateOf(false)
        val fixtureId = mutableStateOf<String?>(F01)
        val renderedIds = CopyOnWriteArrayList<String>()

        clearLogcat()
        composeRule.mountBodyWebView(body, showImages, isDark, fixtureId, renderedIds, TRACE_S12)
        waitForRendered(F01, renderedIds)

        composeRule.runOnIdle { isDark.value = true }
        waitForRenderedCount(2, renderedIds)

        val traces = saveTrace(TRACE_S12, "S12-cambio-tema.log")
        assertTwoLoadContract(traces)
        assertNoStaleCallbacks(traces)
        assertEquals(listOf(F01, F01), renderedIds.toList())

        assertNativeCapture("S12-cambio-tema.png")
    }

    // S13: F03 showImages=true → false — dos claves distintas por la política de imágenes.
    @Test
    fun s13_imagePolicyChange_enabledToBlocked() {
        val body = mutableStateOf<String?>(fixture(F03_FILE))
        val showImages = mutableStateOf(true)
        val isDark = mutableStateOf(false)
        val fixtureId = mutableStateOf<String?>(F03)
        val renderedIds = CopyOnWriteArrayList<String>()

        clearLogcat()
        composeRule.mountBodyWebView(body, showImages, isDark, fixtureId, renderedIds, TRACE_S13)
        waitForRendered(F03, renderedIds)

        composeRule.runOnIdle { showImages.value = false }
        waitForRenderedCount(2, renderedIds)

        val traces = saveTrace(TRACE_S13, "S13-cambio-politica-imagenes.log")
        assertTwoLoadContract(traces)
        assertNoStaleCallbacks(traces)
        assertEquals(listOf(F03, F03), renderedIds.toList())

        assertNativeCapture("S13-cambio-politica-imagenes.png")
    }

    // ═══════════════════════════════════════════════════════════════
    // Subfase 3.2 — matriz de interacción y lifecycle (S09, S14–S16)
    // ═══════════════════════════════════════════════════════════════

    // S09: F05, claro — pulsar el enlace externo abre Custom Tabs y el detalle sobrevive.
    @Test
    fun s09_externalLink_opensCustomTab_andDetailSurvives() {
        val body = mutableStateOf<String?>(fixture(F05_FILE))
        val showImages = mutableStateOf(true)
        val isDark = mutableStateOf(false)
        val fixtureId = mutableStateOf<String?>(F05)
        val renderedIds = CopyOnWriteArrayList<String>()

        clearLogcat()
        composeRule.mountBodyWebView(body, showImages, isDark, fixtureId, renderedIds, TRACE_S09)
        waitForRendered(F05, renderedIds)

        val webView = captureWebView()
        val clientBefore = onMainSync { webView.webViewClient }
        val testComponent = resumedActivityComponent()
            ?: error("S09: test activity component must be resolvable")
        assertTrue(
            "S09: test activity must be the Compose ComponentActivity ($testComponent)",
            testComponent.endsWith("/androidx.activity.ComponentActivity")
        )

        // Resolve the real HTTPS browser FIRST, so a resolution failure aborts
        // before opening any external activity and never cascades to the next
        // scenario.
        val browser = resolveHttpsBrowser()

        // Locate the visible anchor through the WebView's accessibility tree
        // (no JavaScript) and tap it with a real injected event.
        val anchor = findVisibleNode(text = "Abrir destino externo de referencia")
            ?: error("S09: anchor of fixture 05 must be locatable via the accessibility tree")
        realTap(anchor[0], anchor[1])

        // ActivityManager can report Chrome before its surface replaces the
        // WebView frame. Wait for the browser's own accessibility window and the
        // synthetic host before taking a real framebuffer capture.
        waitForTopActivityPackage(browser, timeoutMillis = 20_000L)
        waitForActiveWindowContent(
            packageName = browser,
            textToken = "Example Domain",
            timeoutMillis = 30_000L
        )
        assertNativeShellCapture("S09-enlace-custom-tab.png")

        // Back to the same detail: same activity, same WebView, same client, no reload.
        executeShell("input keyevent KEYCODE_BACK")
        // The standalone Compose test activity is not the launcher task. Some
        // Chrome builds close the Custom Tab to HOME instead of foregrounding
        // that test-only task. Bring the existing activity task forward; the
        // identity assertions below guarantee this does not mask recreation.
        if (!awaitResumedActivityComponent(testComponent, timeoutMillis = 3_000L)) {
            bringActivityToFrontWithSingleTop(testComponent)
        }
        waitForResumedActivityComponent(testComponent, timeoutMillis = 20_000L)
        waitForTrace(TRACE_S09, "WV_ON_RESUME", timeoutMillis = 15_000L)

        composeRule.waitForIdle()
        assertSame("S09: the WebView must survive the Custom Tab round-trip", webView, captureWebView())
        assertSame("S09: the WebViewClient must not be replaced", clientBefore, onMainSync { webView.webViewClient })

        val traces = saveTrace(TRACE_S09, "S09-enlace-custom-tab.log")
        assertSingleLoadContract(traces, expectBodyPending = false)
        assertNoStaleCallbacks(traces)
        assertEquals(
            "S09: exactly one load dispatch and no release",
            1,
            traces.count { it.event == "WV_PAGE_RENDERED_DISPATCH" }
        )
        assertEquals("S09: no release during the round-trip", 0, traces.count { it.event == "WV_RELEASE" })
        assertEquals("S09: a single rendered callback", listOf(F05), renderedIds.toList())
    }

    // S14: newsletter largo — scroll, HOME (pausa) y retorno con scroll restaurado, sin recarga.
    @Test
    fun s14_longNewsletter_scrollAndLifecycle_restoresScrollWithoutReload() {
        val body = mutableStateOf<String?>(buildLongNewsletter())
        val showImages = mutableStateOf(true)
        val isDark = mutableStateOf(false)
        val fixtureId = mutableStateOf<String?>(F02_LONG)
        val renderedIds = CopyOnWriteArrayList<String>()

        clearLogcat()
        composeRule.mountBodyWebView(body, showImages, isDark, fixtureId, renderedIds, TRACE_S14)
        waitForRendered(F02_LONG, renderedIds, timeoutMillis = 45_000L)

        val webView = captureWebView()
        val testComponent = resumedActivityComponent()
            ?: error("S14: test activity component must be resolvable")
        assertTrue(
            "S14: test activity must be the Compose ComponentActivity ($testComponent)",
            testComponent.endsWith("/androidx.activity.ComponentActivity")
        )

        // Vertical scroll past the fold; the exact position is the contract.
        scrollWebViewTo(webView, SCROLL_Y)
        val savedScroll = onMainSync { webView.scrollY }
        assertTrue("S14: the long newsletter must scroll past the fold", savedScroll > 0)
        waitForWebViewVisualState()
        assertNativeShellCapture("S14-scroll-antes-pausa.png")

        // Background with HOME: the component must trace the pause with the saved scroll.
        executeShell("input keyevent KEYCODE_HOME")
        waitForLogcatToken(TRACE_S14, "WV_ON_PAUSE", timeoutMillis = 15_000L)

        // Bring the SAME activity back to front (singleTop): same instance, same WebView.
        bringActivityToFrontWithSingleTop(testComponent)
        waitForResumedActivityComponent(testComponent, timeoutMillis = 20_000L)
        waitForLogcatToken(TRACE_S14, "WV_RESUME_SCROLL_APPLIED", timeoutMillis = 20_000L)
        waitForTrace(TRACE_S14, "WV_ON_RESUME", timeoutMillis = 15_000L)

        composeRule.waitForIdle()
        assertSame("S14: the WebView instance must survive the lifecycle round-trip", webView, captureWebView())
        assertEquals("S14: the scroll position must be restored", savedScroll, onMainSync { webView.scrollY })
        waitForWebViewVisualState()
        assertNativeShellCapture("S14-scroll-despues-resume.png")
        assertContentRegionEquivalent(
            "S14-scroll-antes-pausa.png",
            "S14-scroll-despues-resume.png"
        )

        val traces = saveTrace(TRACE_S14, "S14-scroll-lifecycle.log")
        assertSingleLoadContract(traces, expectBodyPending = false)
        assertNoStaleCallbacks(traces)
        assertEquals("S14: a single rendered callback", listOf(F02_LONG), renderedIds.toList())
        assertEquals("S14: no release during the round-trip", 0, traces.count { it.event == "WV_RELEASE" })
        assertTrue(
            "S14: the pause must log the saved scroll",
            traces.any { it.event == "WV_ON_PAUSE" && it.details.contains("scrollY=$savedScroll") }
        )
        assertTrue(
            "S14: the resume must apply the saved scroll",
            traces.any { it.event == "WV_RESUME_SCROLL_APPLIED" && it.details.contains("scrollY=$savedScroll") }
        )
    }

    // S15: F04 con red bloqueada — long-press sobre la imagen data: entrega exactamente su URL.
    @Test
    fun s15_longPress_onDataImage_deliversExactDataUrl() {
        val body = mutableStateOf<String?>(fixture(F04_FILE))
        val showImages = mutableStateOf(false)
        val isDark = mutableStateOf(false)
        val fixtureId = mutableStateOf<String?>(F04)
        val renderedIds = CopyOnWriteArrayList<String>()
        val imageUrls = CopyOnWriteArrayList<String>()

        clearLogcat()
        composeRule.mountBodyWebView(
            body, showImages, isDark, fixtureId, renderedIds, TRACE_S15, imageUrls = imageUrls
        )
        waitForRendered(F04, renderedIds)

        val webView = captureWebView()
        val imageTarget = findVisibleNode(contentDescription = "Píxel inline sintético")
            ?: findTargetByRealHitTest(webView, HitTestResult.IMAGE_TYPE)
            ?: error("S15: the inline data: image must be locatable via the accessibility tree or a real hit-test scan")
        realLongPress(imageTarget[0], imageTarget[1])

        val deadline = System.currentTimeMillis() + 5_000L
        while (imageUrls.isEmpty() && System.currentTimeMillis() < deadline) {
            composeRule.waitForIdle()
            SystemClock.sleep(50)
        }
        // The component emits no long-press trace of its own: the exact callback
        // recorded here (and the XML) is the evidence for S15.
        assertEquals(
            "S15: exactly one long-press callback with the full data: URL",
            listOf(EXPECTED_DATA_URL),
            imageUrls.toList()
        )

        assertNativeCapture("S15-long-press-data.png")
        val traces = saveTrace(TRACE_S15, "S15-long-press-data.log")
        assertSingleLoadContract(traces, expectBodyPending = false)
        assertNoStaleCallbacks(traces)
        assertEquals(listOf(F04), renderedIds.toList())
    }

    // S16: F01 — desmontar libera la instancia (un WV_RELEASE) y remontar crea otra.
    @Test
    fun s16_release_andReopen_createsNewInstance() {
        val body = mutableStateOf<String?>(fixture(F01_FILE))
        val showImages = mutableStateOf(true)
        val isDark = mutableStateOf(false)
        val fixtureId = mutableStateOf<String?>(F01)
        val renderedIds = CopyOnWriteArrayList<String>()
        val mounted = mutableStateOf(true)

        clearLogcat()
        composeRule.mountBodyWebView(
            body, showImages, isDark, fixtureId, renderedIds, TRACE_S16, mounted = mounted
        )
        waitForRendered(F01, renderedIds)

        val firstWebView = captureWebView()
        val firstClient = onMainSync { firstWebView.webViewClient }
        assertNotNull("S16: the first mount installs a client", firstClient)

        // Remove the composable: exactly one release, then no WebView in the tree.
        composeRule.runOnIdle { mounted.value = false }
        composeRule.waitForIdle()
        assertNoWebView()
        waitForLogcatToken(TRACE_S16, "WV_RELEASE", timeoutMillis = 15_000L)

        // Re-mount: a brand-new instance, client and document load.
        composeRule.runOnIdle { mounted.value = true }
        composeRule.waitForIdle()
        waitForRenderedCount(2, renderedIds)

        val secondWebView = captureWebView()
        assertNotSame("S16: the reopened WebView must be a new instance", firstWebView, secondWebView)
        assertNotSame(
            "S16: the reopened WebView must install a new client",
            firstClient,
            onMainSync { secondWebView.webViewClient }
        )

        val traces = saveTrace(TRACE_S16, "S16-release-reapertura.log")
        assertEquals("S16: two factory events", 2, traces.count { it.event == "WV_FACTORY" })
        assertEquals("S16: exactly one release", 1, traces.count { it.event == "WV_RELEASE" })
        assertEquals("S16: two build cycles", 2, traces.count { it.event == "HTML_BUILD_START" })
        assertEquals("S16: two loads", 2, traces.count { it.event == "WV_LOAD_DATA" })
        assertEquals("S16: two dispatches", 2, traces.count { it.event == "WV_PAGE_RENDERED_DISPATCH" })
        assertEquals("S16: two rendered callbacks", listOf(F01, F01), renderedIds.toList())

        val dispatches = traces.mapIndexedNotNull { i, l -> if (l.event == "WV_PAGE_RENDERED_DISPATCH") i else null }
        val factories = traces.mapIndexedNotNull { i, l -> if (l.event == "WV_FACTORY") i else null }
        val release = traces.indexOfFirst { it.event == "WV_RELEASE" }
        assertEquals("S16: two dispatches indexed", 2, dispatches.size)
        assertEquals("S16: two factories indexed", 2, factories.size)
        assertTrue("S16: the release must sit between the two dispatches", dispatches[0] < release && release < dispatches[1])
        assertTrue("S16: the second factory must follow the release", factories[1] > release && factories[1] < dispatches[1])

        assertNoStaleCallbacks(traces)
        assertNativeCapture("S16-reapertura.png")
    }

    // ═══════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════

    private fun ComposeContentTestRule.mountBodyWebView(
        body: MutableState<String?>,
        showImages: MutableState<Boolean>,
        isDark: MutableState<Boolean>,
        fixtureId: MutableState<String?>,
        renderedIds: MutableList<String>,
        traceMail: String,
        recomposeTick: MutableState<Int>? = null,
        mounted: MutableState<Boolean>? = null,
        imageUrls: MutableList<String>? = null
    ) {
        setContent {
            MailAppTheme(
                darkTheme = isDark.value,
                palette = ColorPalette.Blue,
                useDynamicColor = false
            ) {
                // Reading the tick subscribes this composition to it, so bumping
                // it forces an equivalent recomposition without changing inputs.
                recomposeTick?.value
                // The callback captures the fixture id stable for THIS composition;
                // it never infers it from mutable global state at callback time.
                val stableFixtureId = fixtureId.value
                if (mounted?.value != false) {
                    EmailBodyWebView(
                        body = body.value,
                        showImages = showImages.value,
                        isDark = isDark.value,
                        traceMail = traceMail,
                        onPageRendered = {
                            if (stableFixtureId != null) renderedIds.add(stableFixtureId)
                        },
                        onImageLongPress = { url -> imageUrls?.add(url) },
                        startupGate = ReadyWebViewStartupGate,
                        modifier = Modifier
                    )
                }
            }
        }
    }

    private object ReadyWebViewStartupGate : WebViewStartupGate {
        override val state: StateFlow<WebViewStartupState> =
            MutableStateFlow(WebViewStartupState.Ready)

        override fun start(context: Context) = Unit

        override fun retry(context: Context) = Unit
    }

    private fun waitForTrace(
        traceMail: String,
        token: String,
        timeoutMillis: Long = 10_000L
    ) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
            val fd = instrumentation.uiAutomation.executeShellCommand(
                "logcat -d -v threadtime -s MailRenderTrace"
            )
            val output = fd?.let { f ->
                ParcelFileDescriptor.AutoCloseInputStream(f).use { input ->
                    input.readBytes().toString(Charsets.UTF_8)
                }
            }.orEmpty()
            if (output.contains("mail=$traceMail") && output.contains(token)) return
            android.os.SystemClock.sleep(100)
        }
        fail("trace token '$token' for $traceMail not observed within $timeoutMillis ms")
    }

    private fun clearLogcat() {
        instrumentation.uiAutomation.executeShellCommand("logcat -c")?.let { fd ->
            // Draining blocks until logcat -c actually completes, so the first
            // traces of the scenario can never be wiped by the in-flight clear.
            ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
        }
    }

    private fun saveTrace(traceMail: String, fileName: String): List<TraceLine> {
        val fd = instrumentation.uiAutomation.executeShellCommand(
            "logcat -d -v threadtime -s MailRenderTrace"
        ) ?: error("logcat dump failed")
        val output = ParcelFileDescriptor.AutoCloseInputStream(fd).use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        }
        val lines = output.lineSequence()
            .filter { it.contains("mail=$traceMail") }
            .toList()
        assertTrue("trace $fileName must contain scenario lines", lines.isNotEmpty())
        val file = File(evidenceDir(), evidenceFileName(fileName))
        file.writeText(lines.joinToString("\n") + "\n")
        publishEvidence(file)
        return lines.mapNotNull { parseTraceLine(it) }
    }

    private fun assertNativeCapture(fileName: String) {
        val bitmap = takeScreenshot()
        val expected = nativeDisplaySize
        assertEquals("native display width", expected.first, bitmap.width)
        assertEquals("native display height", expected.second, bitmap.height)

        val file = File(evidenceDir(), evidenceFileName(fileName))
        FileOutputStream(file).use { out ->
            assertTrue(
                "PNG compression for $fileName must succeed",
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            )
        }
        assertTrue("capture $fileName must not be empty", file.length() > 0)

        val magic = ByteArray(8)
        FileInputStream(file).use { input ->
            assertEquals("PNG header must be readable", 8, input.read(magic))
        }
        val pngMagic = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        assertTrue("$fileName must be a PNG", magic.contentEquals(pngMagic))

        publishEvidence(file)
    }

    /** Captures the currently composed display surface rather than UiAutomation's cached frame. */
    private fun assertNativeShellCapture(fileName: String) {
        val remoteName = evidenceFileName(fileName)
        val remoteDir = "/data/local/tmp/$evidenceSubdir"
        executeShell("mkdir -p $remoteDir")
        executeShell("screencap -p $remoteDir/$remoteName")

        val file = File(evidenceDir(), remoteName)
        executeShell("cp $remoteDir/$remoteName ${file.absolutePath}")
        assertTrue("shell capture $remoteName must not be empty", file.length() > 0)

        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            ?: error("shell capture $remoteName must decode as PNG")
        try {
            val expected = nativeDisplaySize
            assertEquals("native display width", expected.first, bitmap.width)
            assertEquals("native display height", expected.second, bitmap.height)
        } finally {
            bitmap.recycle()
        }

        val magic = ByteArray(8)
        FileInputStream(file).use { input ->
            assertEquals("PNG header must be readable", 8, input.read(magic))
        }
        val pngMagic = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        assertTrue("$remoteName must be a PNG", magic.contentEquals(pngMagic))

        val remoteSize = executeShell("wc -c $remoteDir/$remoteName")
            .trim()
            .substringBefore(' ')
        assertEquals("published $remoteName must match byte-for-byte", file.length().toString(), remoteSize)
    }

    private fun assertContentRegionEquivalent(beforeName: String, afterName: String) {
        val before = BitmapFactory.decodeFile(File(evidenceDir(), evidenceFileName(beforeName)).absolutePath)
            ?: error("$beforeName must decode")
        val after = BitmapFactory.decodeFile(File(evidenceDir(), evidenceFileName(afterName)).absolutePath)
            ?: error("$afterName must decode")
        try {
            assertEquals(before.width, after.width)
            assertEquals(before.height, after.height)

            // Exclude roughly the top/bottom 5% (system bars) regardless of the
            // physical resolution. Inside the WebView content, allow only a tiny
            // compositor/antialiasing delta between equivalent frames.
            val top = (before.height * 0.05).toInt()
            val bottom = (before.height * 0.95).toInt()
            val rowBefore = IntArray(before.width)
            val rowAfter = IntArray(after.width)
            var materiallyDifferent = 0L
            var compared = 0L
            for (y in top until bottom) {
                before.getPixels(rowBefore, 0, before.width, 0, y, before.width, 1)
                after.getPixels(rowAfter, 0, after.width, 0, y, after.width, 1)
                for (x in rowBefore.indices) {
                    val a = rowBefore[x]
                    val b = rowAfter[x]
                    val delta = kotlin.math.abs(((a shr 16) and 0xff) - ((b shr 16) and 0xff)) +
                        kotlin.math.abs(((a shr 8) and 0xff) - ((b shr 8) and 0xff)) +
                        kotlin.math.abs((a and 0xff) - (b and 0xff))
                    if (delta > CONTENT_PIXEL_DELTA) materiallyDifferent++
                    compared++
                }
            }
            val ratio = materiallyDifferent.toDouble() / compared.toDouble()
            assertTrue(
                "S14 content frames must match after resume " +
                    "(materiallyDifferent=$materiallyDifferent/$compared ratio=$ratio)",
                ratio <= CONTENT_DIFFERENCE_RATIO
            )
        } finally {
            before.recycle()
            after.recycle()
        }
    }

    @Suppress("DEPRECATION")
    private fun takeScreenshot(): Bitmap = instrumentation.uiAutomation.takeScreenshot()

    private fun evidenceDir(): File {
        // AndroidJUnitRunner executes the instrumentation inside the TARGET app
        // process, so the test package's own directories are not writable from
        // here. Evidence goes to the target app's EXTERNAL dir: the real app
        // process has FUSE access, and the shell can copy it to the durable
        // /data/local/tmp location with a plain `cp` (see [publishEvidence]).
        val base = instrumentation.targetContext.getExternalFilesDir(null)
            ?: error("target external files dir unavailable")
        val dir = File(base, evidenceSubdir)
        assertTrue("evidence dir must exist or be created ($dir)", dir.exists() || dir.mkdirs())
        return dir
    }

    /**
     * Publishes an evidence file to the persistent device location
     * /data/local/tmp/<evidenceSubdir>/ so it survives the connected-test APK
     * uninstall and is pullable after the run. Uses only shell commands without
     * operators: UiAutomation.executeShellCommand does not process shell
     * metacharacters reliably.
     */
    private fun publishEvidence(file: File) {
        val remoteDir = "/data/local/tmp/$evidenceSubdir"
        // Draining the fd blocks until the command finishes: executeShellCommand
        // returns immediately and a verification that races the copy would see
        // a partial file.
        instrumentation.uiAutomation.executeShellCommand("mkdir -p $remoteDir")?.let { fd ->
            ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
        }
        instrumentation.uiAutomation.executeShellCommand("cp ${file.absolutePath} $remoteDir/${file.name}")?.let { fd ->
            ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
        }
        val remoteSize = executeShell("wc -c $remoteDir/${file.name}")
            .trim()
            .substringBefore(' ')
        assertEquals(
            "published ${file.name} must match byte-for-byte",
            file.length().toString(),
            remoteSize
        )
    }

    private fun executeShell(command: String): String {
        val fd = instrumentation.uiAutomation.executeShellCommand(command) ?: return ""
        return ParcelFileDescriptor.AutoCloseInputStream(fd).use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        }
    }

    // ── Subfase 4.1 — environment awareness (emulator vs physical Pixel 9) ──

    /** True on the emulator (ro.kernel.qemu=1); false on the physical device. */
    private val isEmulator: Boolean by lazy {
        executeShell("getprop ro.kernel.qemu").trim() == "1"
    }

    /** Persistent device evidence directory: emulator keeps 3.2, physical uses 4.1. */
    private val evidenceSubdir: String by lazy {
        if (isEmulator) "emailbody-3.2" else "emailbody-4.1"
    }

    /**
     * Native display dimensions from `wm size`. A physical run is blocked when
     * an override is present, so the captured frame always matches the native
     * resolution.
     */
    private val nativeDisplaySize: Pair<Int, Int> by lazy {
        val out = executeShell("wm size")
        if (!isEmulator && out.contains("Override size")) {
            fail("physical device has a resolution override; execution blocked per Subfase 4.1 (wm size=$out)")
        }
        val line = out.lineSequence().firstOrNull { it.startsWith("Physical size:") }
            ?: error("wm size did not report a physical size (output=$out)")
        val dims = line.substringAfter("Physical size:").trim().substringBefore(' ')
        val parts = dims.split('x')
        require(parts.size == 2) { "unexpected physical size format: $dims" }
        parts[0].toInt() to parts[1].toInt()
    }

    /** Physical evidence inserts -fisico before the extension; emulator keeps the name. */
    private fun evidenceFileName(fileName: String): String {
        if (isEmulator) return fileName
        val dot = fileName.lastIndexOf('.')
        return if (dot >= 0) fileName.substring(0, dot) + "-fisico" + fileName.substring(dot)
        else fileName + "-fisico"
    }

    /**
     * Resolves the real HTTPS browser for S09 and rejects a chooser,
     * ResolverActivity or a browser without an operational Custom Tabs provider.
     */
    private fun resolveHttpsBrowser(): String {
        val out = executeShell(
            "cmd package resolve-activity --brief -a android.intent.action.VIEW -d https://example.com/"
        )
        val component = out.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.contains('/') }
            ?: ""
        if (component.isEmpty() ||
            component.startsWith("android/com.android.internal.app.ChooserActivity") ||
            component.startsWith("android/com.android.internal.app.ResolverActivity")
        ) {
            fail("no HTTPS browser resolved without a chooser (resolved='$component')")
        }
        val pkg = component.substringBefore('/')
        // The app context cannot resolve other packages' services under the
        // Android 11+ package-visibility rules, so Custom Tabs support is
        // verified through the shell, which is not subject to those rules.
        // --brief prints the component line (<package>/<service>) directly.
        val services = executeShell(
            "cmd package query-services --brief -a android.support.customtabs.action.CustomTabsService"
        )
        val supportsCustomTabs = services.lineSequence().any { it.trim().startsWith("$pkg/") }
        if (!supportsCustomTabs) {
            fail("resolved browser $pkg does not advertise a Custom Tabs service")
        }
        return pkg
    }

    // ── Subfase 3.2 helpers (hit targets, scroll, activity, lifecycle) ──

    private fun resumedActivityComponent(): String? {
        val line = resumedActivityLine() ?: return null
        val marker = " u0 "
        val idx = line.indexOf(marker)
        if (idx < 0) return null
        return line.substring(idx + marker.length).substringBefore(' ')
    }

    private fun resumedActivityLine(): String? {
        val out = executeShell("dumpsys activity activities")
        return out.lineSequence().firstOrNull {
            it.contains("ResumedActivity=") && it.contains("ActivityRecord")
        }
    }

    private fun waitForTopActivityPackage(pkg: String, timeoutMillis: Long) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            val line = resumedActivityLine()
            if (line != null && line.contains(pkg)) return
            SystemClock.sleep(200)
        }
        fail("top activity did not belong to $pkg within $timeoutMillis ms")
    }

    private fun waitForActiveWindowContent(
        packageName: String,
        textToken: String,
        timeoutMillis: Long
    ) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            if (root != null) {
                try {
                    val packageMatches = root.packageName?.toString() == packageName
                    val textMatches = accessibilityTreeContains(root, textToken)
                    if (packageMatches && textMatches) {
                        instrumentation.uiAutomation.waitForIdle(300L, 5_000L)
                        return
                    }
                } finally {
                    root.recycle()
                }
            }
            SystemClock.sleep(200)
        }
        fail("active $packageName window did not expose '$textToken' within $timeoutMillis ms")
    }

    private fun accessibilityTreeContains(node: AccessibilityNodeInfo, token: String): Boolean {
        if (node.text?.toString()?.contains(token, ignoreCase = true) == true ||
            node.contentDescription?.toString()?.contains(token, ignoreCase = true) == true
        ) {
            return true
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                if (accessibilityTreeContains(child, token)) return true
            } finally {
                child.recycle()
            }
        }
        return false
    }

    private fun waitForResumedActivityComponent(component: String, timeoutMillis: Long) {
        if (awaitResumedActivityComponent(component, timeoutMillis)) return
        fail("activity $component did not resume within $timeoutMillis ms")
    }

    private fun awaitResumedActivityComponent(component: String, timeoutMillis: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (resumedActivityComponent() == component) return true
            SystemClock.sleep(200)
        }
        return false
    }

    private fun bringActivityToFrontWithSingleTop(component: String) {
        // The Compose test activity is exported (ui-test-manifest), so the shell
        // can relaunch the SAME instance with FLAG_ACTIVITY_SINGLE_TOP. When the
        // task already exists this prints a benign "brought to the front" warning
        // instead of a launch, which is exactly the desired no-recreation path.
        val out = executeShell("am start -n $component -f 0x30000000")
        assertTrue("am start must succeed (was: $out)", !out.contains("Error"))
    }

    private fun waitForLogcatToken(traceMail: String, token: String, timeoutMillis: Long) {
        // Shell-only poll: usable while the activity is paused/stopped, when the
        // Compose test rule cannot drive the frame clock.
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            val output = executeShell("logcat -d -v threadtime -s MailRenderTrace")
            if (output.contains("mail=$traceMail") && output.contains(token)) return
            SystemClock.sleep(150)
        }
        fail("logcat token '$token' for $traceMail not observed within $timeoutMillis ms")
    }

    private fun scrollWebViewTo(webView: WebView, y: Int) {
        onMainSync { webView.scrollTo(0, y) }
    }

    private fun waitForWebViewVisualState(timeoutMillis: Long = 15_000L) {
        Espresso.onView(isAssignableFrom(WebView::class.java))
            .perform(WaitForVisualStateAction(timeoutMillis))
    }

    /**
     * Locates the on-screen center of the first visible node whose text OR
     * content description matches [text] or [contentDescription] in the active
     * window's accessibility tree. The WebView exposes its content to
     * accessibility while UiAutomation is connected, so the anchor link and the
     * inline image are real nodes with screen bounds (no JavaScript). Returns
     * screen coordinates or null.
     */
    private fun findVisibleNode(
        text: String? = null,
        contentDescription: String? = null,
        timeoutMillis: Long = 15_000L
    ): IntArray? {
        val seen = LinkedHashSet<String>()
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            if (root != null) {
                try {
                    val hit = findNodeInTree(root, text, contentDescription, seen)
                    if (hit != null) {
                        val bounds = Rect()
                        hit.getBoundsInScreen(bounds)
                        return intArrayOf(bounds.centerX(), bounds.centerY())
                    }
                } finally {
                    root.recycle()
                }
            }
            SystemClock.sleep(250)
        }
        Log.w("A11YScan", "target text=$text desc=$contentDescription not found; seen=${seen.take(40)}")
        return null
    }

    private fun findNodeInTree(
        node: AccessibilityNodeInfo,
        text: String?,
        contentDescription: String?,
        seen: MutableSet<String>
    ): AccessibilityNodeInfo? {
        val nodeText = node.text?.toString()
        val nodeDesc = node.contentDescription?.toString()
        if (!nodeText.isNullOrBlank()) seen.add(nodeText)
        if (!nodeDesc.isNullOrBlank()) seen.add(nodeDesc)
        val matches = (text != null && (nodeText == text || nodeDesc == text)) ||
            (contentDescription != null && (nodeDesc == contentDescription || nodeText == contentDescription))
        if (matches) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                try {
                    val match = findNodeInTree(child, text, contentDescription, seen)
                    if (match != null) return match
                } finally {
                    child.recycle()
                }
            }
        }
        return null
    }

    private fun realTap(x: Int, y: Int) {
        executeShell("input tap $x $y")
    }

    private fun realLongPress(x: Int, y: Int) {
        // Same-point swipe: a real DOWN held for 900 ms, which fires the
        // WebView's long-press listener exactly like a user's finger.
        executeShell("input swipe $x $y $x $y 900")
    }

    /**
     * Fallback locator for large targets: probes a coarse lattice with REAL
     * injected DOWN/CANCEL events (which populate WebView.hitTestResult, unlike
     * synthetic dispatches) and returns the first point whose hit test matches
     * [targetType] with a non-blank extra.
     */
    private fun findTargetByRealHitTest(webView: WebView, targetType: Int): IntArray? {
        val width = onMainSync { webView.width }
        val height = onMainSync { webView.height }
        // Two interleaved 72 px lattices: any target larger than ~36 px square
        // is guaranteed to contain a probe point.
        for (offset in intArrayOf(0, 36)) {
            var y = offset
            while (y < height) {
                var x = offset
                while (x < width) {
                    executeShell("input motionevent DOWN $x $y")
                    val hit = pollHitTest(webView, timeoutMillis = 400L)
                    executeShell("input motionevent CANCEL $x $y")
                    if (hit != null && hit.type == targetType && !hit.extra.isNullOrBlank()) {
                        return intArrayOf(x, y)
                    }
                    x += 72
                }
                y += 72
            }
        }
        return null
    }

    private fun pollHitTest(webView: WebView, timeoutMillis: Long): HitTestResult? {
        val deadline = System.currentTimeMillis() + timeoutMillis
        var last: HitTestResult? = null
        while (System.currentTimeMillis() < deadline) {
            last = onMainSync { webView.hitTestResult }
            if (last != null && last.type != HitTestResult.UNKNOWN_TYPE) return last
            SystemClock.sleep(30)
        }
        return last
    }

    private fun assertNoWebView() {
        val missing = try {
            Espresso.onView(isAssignableFrom(WebView::class.java))
                .check(ViewAssertions.matches(Matchers.anything()))
            null
        } catch (expected: NoMatchingViewException) {
            expected
        }
        assertNotNull("no WebView must be mounted after the composable is removed", missing)
    }

    private data class TraceLine(val event: String, val details: String)

    private fun parseTraceLine(line: String): TraceLine? {
        val marker = "event="
        val idx = line.indexOf(marker)
        if (idx < 0) return null
        val rest = line.substring(idx + marker.length)
        val space = rest.indexOf(' ')
        val event = if (space >= 0) rest.substring(0, space) else rest
        val details = if (space >= 0) rest.substring(space + 1) else ""
        return TraceLine(event, details)
    }

    private fun TraceLine.loadKey(): String? {
        val idx = details.indexOf("loadKey=")
        if (idx < 0) return null
        return details.substring(idx + 8).substringBefore(' ')
    }

    private fun List<TraceLine>.coreLoadEvents(): List<String> = mapNotNull { line ->
        when (line.event) {
            "HTML_BUILD_START" -> "START"
            "HTML_BUILD_END" -> "END"
            "HTML_BUILD_READY" -> "READY"
            "WV_LOAD_DATA" -> "LOAD_DATA"
            "WV_PAGE_RENDERED_DISPATCH" -> "DISPATCH"
            "WV_UPDATE" -> if (line.details.startsWith("action=load")) "LOAD" else null
            else -> null
        }
    }

    private fun assertSingleLoadContract(traces: List<TraceLine>, expectBodyPending: Boolean) {
        assertEquals(
            "one complete load cycle in canonical order",
            CANONICAL_LOAD_SEQUENCE,
            traces.coreLoadEvents()
        )
        if (expectBodyPending) {
            val beforeStart = traces.takeWhile { it.event != "HTML_BUILD_START" }
            assertTrue(
                "S01 must log HTML_BUILD_WAITING reason=body_pending before preparing",
                beforeStart.any {
                    it.event == "HTML_BUILD_WAITING" && it.details.startsWith("reason=body_pending")
                }
            )
            // The WV_UPDATE wait reason is timing-dependent: body_pending when
            // the mount apply lands before the body arrives, html_pending when
            // the apply is deferred past it (observed in the full suite).
            // Either one proves the update waited for the pending document.
            assertTrue(
                "S01 must log WV_UPDATE action=wait before preparing",
                beforeStart.any {
                    it.event == "WV_UPDATE" &&
                        it.details.startsWith("action=wait") &&
                        (it.details.contains("reason=body_pending") ||
                            it.details.contains("reason=html_pending"))
                }
            )
        }
        assertEquals(1, traces.count { it.event == "HTML_BUILD_START" })
        assertEquals(1, traces.count { it.event == "HTML_BUILD_END" })
        assertEquals(1, traces.count { it.event == "HTML_BUILD_READY" })
        assertEquals(1, traces.count { it.event == "WV_LOAD_DATA" })
        assertEquals(1, traces.count { it.event == "WV_PAGE_RENDERED_DISPATCH" })
    }

    private fun assertTwoLoadContract(traces: List<TraceLine>) {
        assertEquals(
            "two complete load cycles in transition order",
            CANONICAL_LOAD_SEQUENCE + CANONICAL_LOAD_SEQUENCE,
            traces.coreLoadEvents()
        )
        assertEquals(2, traces.count { it.event == "HTML_BUILD_START" })
        assertEquals(2, traces.count { it.event == "HTML_BUILD_END" })
        assertEquals(2, traces.count { it.event == "HTML_BUILD_READY" })
        assertEquals(2, traces.count { it.event == "WV_LOAD_DATA" })
        assertEquals(2, traces.count { it.event == "WV_PAGE_RENDERED_DISPATCH" })

        val loadKeys = traces
            .filter { it.event == "WV_UPDATE" && it.details.startsWith("action=load") }
            .mapNotNull { it.loadKey() }
        assertEquals(2, loadKeys.size)
        assertNotEquals("the two loads must use different keys", loadKeys[0], loadKeys[1])
    }

    private fun assertNoStaleCallbacks(traces: List<TraceLine>) {
        assertEquals(
            "no unexpected stale-callback rejection must be observed",
            emptyList<TraceLine>(),
            traces.filter { it.event == "WV_PAGE_RENDERED_IGNORED" }
        )
        assertTrue(
            "traces must not contain HTML fragments",
            traces.none { it.details.contains("<") }
        )
    }

    private fun fixture(name: String): String {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        return assets.open(FIXTURES_DIR + name).use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        }
    }

    private fun buildLongNewsletter(): String {
        val f02 = fixture(F02_FILE)
        val innerTableStart = "<table role=\"presentation\" width=\"720\""
        val innerTable = innerTableStart + f02.substringAfter(innerTableStart)
            .substringBefore("</table>") + "</table>"
        require(innerTable in f02) { "inner table block not found in fixture 02" }
        return f02.replaceFirst(innerTable, innerTable.repeat(20))
    }

    private fun assertSingleWebView() {
        // Espresso.onView resolves only when exactly one WebView is mounted:
        // absence (0) raises NoMatchingViewException and ambiguity (2+) raises
        // AmbiguousViewMatcherException, so a successful resolution is the proof.
        captureWebView()
    }

    private fun captureWebView(): WebView {
        val holder = arrayOfNulls<WebView>(1)
        Espresso.onView(isAssignableFrom(WebView::class.java))
            .perform(CaptureWebView(holder))
        return holder[0] ?: error("WebView was not captured")
    }

    private fun waitForRendered(
        expectedFixtureId: String,
        renderedIds: List<String>,
        timeoutMillis: Long = 45_000L
    ) {
        Espresso.onView(isAssignableFrom(WebView::class.java))
            .perform(WaitForRenderedDocument(expectedFixtureId, renderedIds, timeoutMillis))
    }

    private fun waitForRenderedCount(
        expectedCount: Int,
        renderedIds: List<String>,
        timeoutMillis: Long = 45_000L
    ) {
        Espresso.onView(isAssignableFrom(WebView::class.java))
            .perform(WaitForRenderedCountAction(expectedCount, renderedIds, timeoutMillis))
    }

    private fun waitForNewWebViewClient(previousClient: WebViewClient) {
        Espresso.onView(isAssignableFrom(WebView::class.java))
            .perform(WaitForWebViewClientChange(previousClient))
    }

    private fun <T> onMainSync(block: () -> T): T {
        val holder = arrayOfNulls<Any>(1)
        composeRule.runOnIdle { holder[0] = block() }
        @Suppress("UNCHECKED_CAST")
        return holder[0] as T
    }

    private class CaptureWebView(private val holder: Array<WebView?>) : ViewAction {
        override fun getConstraints(): Matcher<View> = isAssignableFrom(WebView::class.java)
        override fun getDescription(): String = "capture the WebView instance"
        override fun perform(uiController: UiController, view: View) {
            holder[0] = view as WebView
        }
    }

    private class WaitForRenderedDocument(
        private val expectedFixtureId: String,
        private val renderedIds: List<String>,
        private val timeoutMillis: Long
    ) : ViewAction {
        override fun getConstraints(): Matcher<View> = isAssignableFrom(WebView::class.java)
        override fun getDescription(): String =
            "wait for WebView progress 100, non-null url and rendered callback for $expectedFixtureId"

        override fun perform(uiController: UiController, view: View) {
            val webView = view as WebView
            val start = System.currentTimeMillis()
            while (webView.progress < 100 || webView.url == null ||
                !renderedIds.contains(expectedFixtureId)
            ) {
                if (System.currentTimeMillis() - start > timeoutMillis) {
                    throw RuntimeException(
                        "WebView did not render $expectedFixtureId " +
                            "(progress=${webView.progress} url=${webView.url} rendered=${renderedIds.toList()})"
                    )
                }
                uiController.loopMainThreadForAtLeast(50)
            }
            uiController.loopMainThreadForAtLeast(500)
        }
    }

    private class WaitForRenderedCountAction(
        private val expectedCount: Int,
        private val renderedIds: List<String>,
        private val timeoutMillis: Long
    ) : ViewAction {
        override fun getConstraints(): Matcher<View> = isAssignableFrom(WebView::class.java)
        override fun getDescription(): String = "wait for $expectedCount rendered callbacks"

        override fun perform(uiController: UiController, view: View) {
            val webView = view as WebView
            val start = System.currentTimeMillis()
            while (webView.progress < 100 || webView.url == null || renderedIds.size < expectedCount) {
                if (System.currentTimeMillis() - start > timeoutMillis) {
                    throw RuntimeException(
                        "WebView rendered callbacks did not reach $expectedCount " +
                            "(progress=${webView.progress} url=${webView.url} rendered=${renderedIds.toList()})"
                    )
                }
                uiController.loopMainThreadForAtLeast(50)
            }
            uiController.loopMainThreadForAtLeast(500)
        }
    }

    private class WaitForWebViewClientChange(
        private val previousClient: WebViewClient,
        private val timeoutMillis: Long = 20_000L
    ) : ViewAction {
        override fun getConstraints(): Matcher<View> = isAssignableFrom(WebView::class.java)
        override fun getDescription(): String = "wait for a new WebViewClient instance"

        override fun perform(uiController: UiController, view: View) {
            val webView = view as WebView
            val start = System.currentTimeMillis()
            while (webView.webViewClient === previousClient) {
                if (System.currentTimeMillis() - start > timeoutMillis) {
                    throw RuntimeException("WebViewClient was not replaced within $timeoutMillis ms")
                }
                uiController.loopMainThreadForAtLeast(50)
            }
        }
    }

    private class WaitForVisualStateAction(
        private val timeoutMillis: Long
    ) : ViewAction {
        override fun getConstraints(): Matcher<View> = isAssignableFrom(WebView::class.java)
        override fun getDescription(): String = "wait for WebView compositor visual state"

        override fun perform(uiController: UiController, view: View) {
            val completed = AtomicBoolean(false)
            (view as WebView).postVisualStateCallback(
                SystemClock.elapsedRealtime(),
                object : WebView.VisualStateCallback() {
                    override fun onComplete(requestId: Long) {
                        completed.set(true)
                    }
                }
            )
            val startedAt = System.currentTimeMillis()
            while (!completed.get()) {
                if (System.currentTimeMillis() - startedAt > timeoutMillis) {
                    throw RuntimeException("WebView visual state did not complete within $timeoutMillis ms")
                }
                uiController.loopMainThreadForAtLeast(16L)
            }
            uiController.loopMainThreadForAtLeast(32L)
        }
    }

    private companion object {
        // Trace keys per scenario (contractual mail=Sxx_3_1).
        const val TRACE_S01 = "S01_3_1"
        const val TRACE_S02 = "S02_3_1"
        const val TRACE_S03 = "S03_3_1"
        const val TRACE_S04 = "S04_3_1"
        const val TRACE_S05 = "S05_3_1"
        const val TRACE_S06 = "S06_3_1"
        const val TRACE_S07 = "S07_3_1"
        const val TRACE_S08 = "S08_3_1"
        const val TRACE_S10 = "S10_3_1"
        const val TRACE_S11 = "S11_3_1"
        const val TRACE_S12 = "S12_3_1"
        const val TRACE_S13 = "S13_3_1"

        // Trace keys per scenario (contractual mail=Sxx_3_2).
        const val TRACE_S09 = "S09_3_2"
        const val TRACE_S14 = "S14_3_2"
        const val TRACE_S15 = "S15_3_2"
        const val TRACE_S16 = "S16_3_2"

        // Subfase 2.3 trace key (no trace evidence is saved by those cases).
        const val TRACE_KEY = "2_3_baseline_test"

        const val FIXTURES_DIR = "emailbody-webview/"
        const val F01 = "F01"
        const val F02 = "F02"
        const val F03 = "F03"
        const val F04 = "F04"
        const val F05 = "F05"
        const val F02_LONG = "F02-long"
        const val F01_FILE = "01-html-simple.html"
        const val F02_FILE = "02-newsletter-tabla.html"
        const val F03_FILE = "03-imagen-remota.html"
        const val F04_FILE = "04-imagen-data.html"
        const val F05_FILE = "05-enlace-externo.html"

        val CANONICAL_LOAD_SEQUENCE = listOf("START", "END", "READY", "LOAD", "LOAD_DATA", "DISPATCH")

        // Subfase 3.2/4.1 scenario constants.
        const val EXPECTED_DATA_URL =
            "data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw=="
        const val SCROLL_Y = 1000
        const val CONTENT_PIXEL_DELTA = 12
        const val CONTENT_DIFFERENCE_RATIO = 0.005
    }
}
