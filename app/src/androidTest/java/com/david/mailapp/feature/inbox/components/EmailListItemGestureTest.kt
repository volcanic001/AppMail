package com.david.mailapp.feature.inbox.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.EmailFolder
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class EmailListItemGestureTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val email = Email(
        id = "e1",
        threadId = "t1",
        from = "sender@example.com",
        fromInitials = "S",
        to = "me@example.com",
        subject = "Correo con swipe rápido",
        snippet = "Contenido",
        timestamp = 1_000L,
        isRead = false,
        isStarred = false,
        hasAttachments = false,
        labels = emptyList(),
        folder = EmailFolder.Inbox
    )

    @Test
    fun rapid_second_swipe_does_not_lock_same_row() {
        val deleteCalls = AtomicInteger(0)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MaterialTheme {
                EmailListItem(
                    email = email,
                    onClick = {},
                    onDelete = { deleteCalls.incrementAndGet() }
                )
            }
        }

        val row = composeRule.onNodeWithText(email.subject)
        row.performTouchInput { swipeLeft(durationMillis = 100) }
        composeRule.mainClock.advanceTimeByFrame()
        assertEquals(1, deleteCalls.get())

        // This gesture lands while the accepted dismiss still owns offsetX.
        // It must be ignored without cancelling that animation.
        row.performTouchInput { swipeLeft(durationMillis = 50) }
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        // A later gesture on the same row must work without recreating the app.
        row.performTouchInput { swipeLeft(durationMillis = 100) }
        composeRule.waitForIdle()

        assertEquals(2, deleteCalls.get())
    }
}
