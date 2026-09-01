package com.david.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
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
     */
    @Test
    fun benchmark_01_plainTextFirstOpen() {
        var iterationIndex = 0
        benchmarkRule.measureRepeated(
            packageName = targetPackage,
            metrics = standardMetrics,
            compilationMode = CompilationMode.DEFAULT,
            startupMode = StartupMode.COLD,
            iterations = 13,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                device.wait(Until.hasObject(By.res("inbox_list")), 10_000)
            }
        ) {
            val emailIndex = String.format("%02d", (iterationIndex % 10) + 1)
            val subjectPrefix = if (iterationIndex < 3) {
                "MAILAPP_PERF_E0_TEXT_W0${iterationIndex + 1}"
            } else {
                "MAILAPP_PERF_E0_TEXT_$emailIndex"
            }
            iterationIndex++

            openEmailAndAwaitReady(subjectPrefix)
        }
    }

    /**
     * Scenario 2: Reopening plain text emails with warm process (app kept alive).
     * Iterations = 13 (3 warmups excluded + 10 measured samples).
     */
    @Test
    fun benchmark_02_plainTextReopenWarmProcess() {
        var iterationIndex = 0
        benchmarkRule.measureRepeated(
            packageName = targetPackage,
            metrics = standardMetrics,
            compilationMode = CompilationMode.DEFAULT,
            startupMode = StartupMode.WARM,
            iterations = 13,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                device.wait(Until.hasObject(By.res("inbox_list")), 10_000)
            }
        ) {
            val emailIndex = String.format("%02d", (iterationIndex % 10) + 1)
            val subjectPrefix = if (iterationIndex < 3) {
                "MAILAPP_PERF_E0_TEXT_W0${iterationIndex + 1}"
            } else {
                "MAILAPP_PERF_E0_TEXT_$emailIndex"
            }
            iterationIndex++

            openEmailAndAwaitReady(subjectPrefix)
        }
    }

    /**
     * Scenario 3: Reopening plain text emails with cold process (process killed before each reopening).
     * Iterations = 13 (3 warmups excluded + 10 measured samples).
     */
    @Test
    fun benchmark_03_plainTextReopenColdProcess() {
        var iterationIndex = 0
        benchmarkRule.measureRepeated(
            packageName = targetPackage,
            metrics = standardMetrics,
            compilationMode = CompilationMode.DEFAULT,
            startupMode = StartupMode.COLD,
            iterations = 13,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                device.wait(Until.hasObject(By.res("inbox_list")), 10_000)
            }
        ) {
            val emailIndex = String.format("%02d", (iterationIndex % 10) + 1)
            val subjectPrefix = if (iterationIndex < 3) {
                "MAILAPP_PERF_E0_TEXT_W0${iterationIndex + 1}"
            } else {
                "MAILAPP_PERF_E0_TEXT_$emailIndex"
            }
            iterationIndex++

            openEmailAndAwaitReady(subjectPrefix)
        }
    }

    private fun MacrobenchmarkScope.openEmailAndAwaitReady(subjectPrefix: String) {
        val emailItem = device.wait(
            Until.findObject(By.textContains(subjectPrefix)),
            5_000
        ) ?: device.findObject(By.res("inbox_list"))?.children?.firstOrNull()

        emailItem?.click()

        // Wait for detail screen content to be rendered (either WebView or detail subject)
        device.wait(Until.hasObject(By.textContains(subjectPrefix)), 10_000)

        // Wait until top progress indicator / loader is gone
        device.wait(Until.gone(By.res("inbox_next_page_loader")), 5_000)

        // Return to inbox for the next iteration
        device.pressBack()
        device.wait(Until.hasObject(By.res("inbox_list")), 5_000)
    }
}
