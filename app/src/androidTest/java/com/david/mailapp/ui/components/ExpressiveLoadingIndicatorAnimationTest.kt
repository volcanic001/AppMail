package com.david.mailapp.ui.components

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ExpressiveLoadingIndicatorAnimationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun completed_pull_and_first_refresh_frame_have_continuous_geometry() {
        val progress = mutableStateOf<Float?>(1f)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            ExpressiveLoadingIndicator(
                modifier = Modifier.testTag(INDICATOR_TAG),
                size = 64.dp,
                color = Color.Black,
                progress = progress.value
            )
        }

        val indicator = composeRule.onNodeWithTag(INDICATOR_TAG)
        val completedPull = indicator.captureToImage()

        composeRule.runOnIdle {
            progress.value = null
        }
        val firstRefreshFrame = indicator.captureToImage()

        assertEquals(completedPull.width, firstRefreshFrame.width)
        assertEquals(completedPull.height, firstRefreshFrame.height)
        assertTrue(
            "The indicator jumped when switching from pull progress to refresh animation",
            changedPixelRatio(completedPull, firstRefreshFrame) < 0.005f
        )
    }

    private fun changedPixelRatio(first: ImageBitmap, second: ImageBitmap): Float {
        val firstPixels = first.toPixelMap()
        val secondPixels = second.toPixelMap()
        var changedPixels = 0

        for (y in 0 until first.height) {
            for (x in 0 until first.width) {
                if (firstPixels[x, y] != secondPixels[x, y]) {
                    changedPixels++
                }
            }
        }

        return changedPixels.toFloat() / (first.width * first.height)
    }

    private companion object {
        const val INDICATOR_TAG = "expressive-loading-indicator"
    }
}
