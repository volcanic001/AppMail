package com.david.mailapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.david.mailapp.feature.compose.ComposeArgs

/**
 * Minimal back-stack navigator for the top-level [MainScreen].
 *
 * Replaces the previous single `selectedScreen` state, which always returned to
 * [Screen.Inbox] on back regardless of where the user came from. With a real
 * stack, "back" pops to the actual previous destination — so opening an email
 * from Trash or Search returns *there* instead of jumping to the inbox.
 *
 * Model: [Screen.Inbox] is the permanent root. Drawer destinations rebase the
 * stack onto the root; Search and EmailDetail are pushed as overlays.
 */
@Stable
class Navigator(initial: Screen = Screen.Inbox) {
    private val backStack = mutableStateListOf(initial)

    /** Destination currently on top of the stack. */
    val current: Screen get() = backStack.last()

    /** True when there is a previous destination to pop back to. */
    val canPop: Boolean get() = backStack.size > 1

    /**
     * ID of the email that was most recently closed/popped from [Screen.EmailDetail].
     * When returning to the email list (Inbox, Trash, or Search), the item matching
     * this ID triggers a soft tonal highlight animation (800ms) to guide the user's focus.
     */
    var highlightedEmailId by mutableStateOf<String?>(null)
        private set

    /** Pushes an overlay destination (e.g. Search, EmailDetail) onto the stack. */
    fun push(screen: Screen) {
        backStack.add(screen)
    }

    /** Opens the email composition screen with the given [args]. */
    fun openCompose(args: ComposeArgs) {
        push(Screen.Compose(args))
    }

    /** Clears the active email highlight after the list item finishes animating. */
    fun clearHighlightedEmail() {
        highlightedEmailId = null
    }

    /**
     * Selects a top-level destination from the drawer, rebasing the stack onto
     * [Screen.Inbox] so backing out of any tab returns to the inbox.
     */
    fun switchTab(screen: Screen) {
        clearHighlightedEmail()
        backStack.clear()
        backStack.add(Screen.Inbox)
        if (screen != Screen.Inbox) backStack.add(screen)
    }

    /** Pops the top destination. No-op at the root. Returns true if it popped. */
    fun pop(): Boolean {
        if (!canPop) return false
        val popped = backStack.removeAt(backStack.lastIndex)
        if (popped is Screen.EmailDetail) {
            highlightedEmailId = popped.emailId
        } else {
            clearHighlightedEmail()
        }
        return true
    }
}

@Composable
fun rememberNavigator(): Navigator = remember { Navigator() }
