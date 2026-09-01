package com.david.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

@OptIn(ExperimentalMetricApi::class)
@LargeTest
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class EmailOpenMacrobenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private val targetPackage = "com.david.mailapp"

    private val standardMetrics = listOf(
        TraceSectionMetric("EmailOpen.Total"),
        TraceSectionMetric("EmailOpen.Resolve"),
        TraceSectionMetric("EmailOpen.BodyFetch"),
        TraceSectionMetric("EmailOpen.HtmlBuild"),
        TraceSectionMetric("EmailOpen.WebViewVisual"),
        TraceSectionMetric("EmailOpen.NetworkFull"),
        FrameTimingMetric()
    )

    /**
     * Scenario 1: First opening of 10 distinct plain text emails.
     * Iterations = 13 (3 warmups excluded + 10 measured samples).
     * startupMode = null.
     */
    @Test
    fun benchmark_01_plainTextFirstOpen() {
        var iterationIndex = 0
        benchmarkRule.measureRepeated(
            packageName = targetPackage,
            metrics = standardMetrics,
            compilationMode = CompilationMode.DEFAULT,
            startupMode = null,
            iterations = 13,
            setupBlock = {
                ensureInboxVisible(isColdRestart = false)
            }
        ) {
            val emailIndex = String.format("%02d", (iterationIndex % 10) + 1)
            val subjectPrefix = if (iterationIndex < 3) {
                "MAILAPP_PERF_E0_TEXT_W0${iterationIndex + 1}"
            } else {
                "MAILAPP_PERF_E0_TEXT_$emailIndex"
            }
            iterationIndex++

            measureOpenEmailToVisualReady(subjectPrefix)
        }
    }

    /**
     * Scenario 2: Reopening plain text emails with warm process (app kept alive).
     * Iterations = 13 (3 warmups excluded + 10 measured samples).
     * startupMode = null.
     */
    @Test
    fun benchmark_02_plainTextReopenWarmProcess() {
        var iterationIndex = 0
        benchmarkRule.measureRepeated(
            packageName = targetPackage,
            metrics = standardMetrics,
            compilationMode = CompilationMode.DEFAULT,
            startupMode = null,
            iterations = 13,
            setupBlock = {
                ensureInboxVisible(isColdRestart = false)
            }
        ) {
            val emailIndex = String.format("%02d", (iterationIndex % 10) + 1)
            val subjectPrefix = if (iterationIndex < 3) {
                "MAILAPP_PERF_E0_TEXT_W0${iterationIndex + 1}"
            } else {
                "MAILAPP_PERF_E0_TEXT_$emailIndex"
            }
            iterationIndex++

            measureOpenEmailToVisualReady(subjectPrefix)
        }
    }

    /**
     * Scenario 3: Reopening plain text emails with cold process (process killed before each reopening).
     * Iterations = 13 (3 warmups excluded + 10 measured samples).
     * startupMode = null. SetupBlock kills the process, relaunches, and waits for Inbox list before measuring.
     */
    @Test
    fun benchmark_03_plainTextReopenColdProcess() {
        var iterationIndex = 0
        benchmarkRule.measureRepeated(
            packageName = targetPackage,
            metrics = standardMetrics,
            compilationMode = CompilationMode.DEFAULT,
            startupMode = null,
            iterations = 13,
            setupBlock = {
                ensureInboxVisible(isColdRestart = true)
            }
        ) {
            val emailIndex = String.format("%02d", (iterationIndex % 10) + 1)
            val subjectPrefix = if (iterationIndex < 3) {
                "MAILAPP_PERF_E0_TEXT_W0${iterationIndex + 1}"
            } else {
                "MAILAPP_PERF_E0_TEXT_$emailIndex"
            }
            iterationIndex++

            measureOpenEmailToVisualReady(subjectPrefix)
        }
    }

    private fun MacrobenchmarkScope.ensureInboxVisible(isColdRestart: Boolean) {
        if (isColdRestart) {
            device.executeShellCommand("am force-stop $targetPackage")
            pressHome()
            startActivityAndWait()
            val inboxFound = device.wait(Until.hasObject(By.res("inbox_list")), 15_000)
            if (!inboxFound) {
                throw AssertionError("Inbox list was not found after cold restart of $targetPackage")
            }
        } else {
            val hasInbox = device.hasObject(By.res("inbox_list"))
            if (!hasInbox) {
                // If detail is still mounted, press back to return to Inbox
                device.pressBack()
                val returnOk = device.wait(Until.hasObject(By.res("inbox_list")), 5_000)
                if (!returnOk) {
                    pressHome()
                    startActivityAndWait()
                    device.wait(Until.hasObject(By.res("inbox_list")), 10_000)
                        ?: throw AssertionError("Inbox list was not found in setupBlock")
                }
            }
        }
    }

    private fun MacrobenchmarkScope.measureOpenEmailToVisualReady(subjectPrefix: String) {
        val emailItem = device.wait(
            Until.findObject(By.textContains(subjectPrefix)),
            5_000
        ) ?: throw AssertionError("Fixture email with subject containing '$subjectPrefix' was not found in Inbox")

        emailItem.click()

        // Wait strictly for the visual ready marker (rendered after WebView visual callback + loader dismissed)
        val visualReady = device.wait(
            Until.hasObject(By.res("email_detail_visual_ready")),
            15_000
        )
        if (!visualReady) {
            throw AssertionError("Visual ready marker 'email_detail_visual_ready' was not found within 15s for fixture: $subjectPrefix")
        }
    }
}
