package com.david.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class MacrobenchmarkPerfettoPreflight {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private val targetPackage = "com.david.mailapp"

    @Test
    fun perfettoCanRecordAndProcessSingleIteration() {
        benchmarkRule.measureRepeated(
            packageName = targetPackage,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.DEFAULT,
            startupMode = null,
            iterations = 1,
            setupBlock = {
                pressHome()
                startActivityAndWait()
            }
        ) {
            val appWindowVisible = device.wait(
                Until.hasObject(By.pkg(targetPackage).depth(0)),
                10_000
            )
            if (!appWindowVisible) {
                throw AssertionError("Target package window was not visible during Macrobenchmark/Perfetto preflight")
            }
        }
    }
}
