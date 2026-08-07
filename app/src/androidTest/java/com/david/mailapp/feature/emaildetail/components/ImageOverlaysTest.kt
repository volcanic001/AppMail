package com.david.mailapp.feature.emaildetail.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import com.david.mailapp.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ImageOverlaysTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun actionSheet_openForwardsUrlAndDismissesMenu() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var openedUrl: String? = null

        composeRule.setContent {
            MaterialTheme {
                val imageSaveScope = rememberCoroutineScope()
                var activeUrl by remember { mutableStateOf<String?>(VALID_IMAGE_DATA_URI) }
                activeUrl?.let { imageUrl ->
                    ImageActionSheet(
                        activeImageUrl = imageUrl,
                        saveCoroutineScope = imageSaveScope,
                        onOpenFullscreen = { openedUrl = it },
                        onDismiss = { activeUrl = null }
                    )
                }
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.image_open))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.image_save))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.image_open))
            .performClick()
        composeRule.runOnIdle { assertEquals(VALID_IMAGE_DATA_URI, openedUrl) }
        composeRule.onNodeWithText(context.getString(R.string.image_open))
            .assertDoesNotExist()
    }

    @Test
    fun actionSheet_backDismissesMenu() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var dismissCount = 0

        composeRule.setContent {
            MaterialTheme {
                val imageSaveScope = rememberCoroutineScope()
                var activeUrl by remember { mutableStateOf<String?>(VALID_IMAGE_DATA_URI) }
                activeUrl?.let { imageUrl ->
                    ImageActionSheet(
                        activeImageUrl = imageUrl,
                        saveCoroutineScope = imageSaveScope,
                        onOpenFullscreen = {},
                        onDismiss = {
                            dismissCount++
                            activeUrl = null
                        }
                    )
                }
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.image_open))
            .assertIsDisplayed()
        pressBack()
        composeRule.runOnIdle { assertEquals(1, dismissCount) }
        composeRule.onNodeWithText(context.getString(R.string.image_open))
            .assertDoesNotExist()
    }

    @Test
    fun fullscreenImage_decodesAndClosesOnTap() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        composeRule.setContent {
            MaterialTheme {
                var imageUrl by remember { mutableStateOf<String?>(VALID_IMAGE_DATA_URI) }
                imageUrl?.let { url ->
                    FullscreenImageDialog(
                        imageUrl = url,
                        onDismiss = { imageUrl = null }
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription(context.getString(R.string.image_fullscreen))
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.image_fullscreen))
            .assertDoesNotExist()
    }

    @Test
    fun fullscreenImage_invalidDataShowsErrorAndClosesOnTap() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        composeRule.setContent {
            MaterialTheme {
                var imageUrl by remember { mutableStateOf<String?>("invalid-image") }
                imageUrl?.let { url ->
                    FullscreenImageDialog(
                        imageUrl = url,
                        onDismiss = { imageUrl = null }
                    )
                }
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.image_load_error))
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.image_load_error))
            .assertDoesNotExist()
    }

    private companion object {
        const val VALID_IMAGE_DATA_URI =
            "data:image/png;base64," +
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
    }
}
