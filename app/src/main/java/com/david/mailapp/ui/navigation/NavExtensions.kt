package com.david.mailapp.ui.navigation

import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController

import androidx.annotation.MainThread
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry

/**
 * Key used in SavedStateHandle to pass the closed email ID back to the origin destination.
 */
internal const val KEY_CLOSED_EMAIL_ID = "closed_email_id"

/**
 * Evaluates whether a pop-back request originated from [originatingEntryId] is still valid.
 *
 * Returns true exclusively when:
 * - The originating entry is still the current destination (IDs match).
 * - Its lifecycle state is exactly [Lifecycle.State.RESUMED].
 * - There is a previous back stack entry to pop to.
 */
internal fun canPopBackFrom(
    originatingEntryId: String,
    currentEntryId: String?,
    originatingLifecycleState: Lifecycle.State,
    hasPreviousEntry: Boolean
): Boolean {
    return originatingEntryId == currentEntryId &&
            originatingLifecycleState == Lifecycle.State.RESUMED &&
            hasPreviousEntry
}

/**
 * Pop the back stack only if [originatingEntry] is still the current RESUMED entry.
 *
 * This is the idempotent primitive: a stale or forwarded entry never consumes
 * another destination.
 *
 * @return true if popBackStack() removed the authorized entry, false otherwise.
 */
@MainThread
internal fun NavHostController.popBackStackFrom(
    originatingEntry: NavBackStackEntry
): Boolean {
    val currentEntry = currentBackStackEntry
    if (!canPopBackFrom(
            originatingEntryId = originatingEntry.id,
            currentEntryId = currentEntry?.id,
            originatingLifecycleState = originatingEntry.lifecycle.currentState,
            hasPreviousEntry = previousBackStackEntry != null
        )
    ) {
        return false
    }
    return popBackStack()
}

/**
 * Extension on [NavHostController] to navigate to a top-level destination.
 *
 * It configures standard Navigation Compose state preservation policy:
 * - Pop up to the start destination of the graph to avoid building up a large stack.
 * - Save the state of popped destinations.
 * - Avoid multiple copies of the same destination when reselecting the same item.
 * - Restore state when re-selecting a previously selected item.
 */
internal fun NavHostController.navigateToTopLevel(route: MainRoute) {
    require(route is MainRoute.Inbox || route is MainRoute.Trash || route is MainRoute.Settings) {
        "Route $route is not a top-level destination"
    }
    navigate(route) {
        // Pop up to the start destination of the graph to
        // avoid building up a large stack of destinations
        // on the back stack as users select items
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        // Avoid multiple copies of the same destination when
        // reselecting the same item
        launchSingleTop = true
        // Restore state when reselecting a previously selected item
        restoreState = true
    }
}

/**
 * Extension on [NavHostController] to navigate to an overlay destination (Search, Detail, Compose).
 *
 * Configured without popUpTo, saveState or restoreState, but with launchSingleTop = true
 * to prevent duplicates from rapid double clicks.
 */
internal fun NavHostController.navigateToOverlay(route: MainRoute) {
    require(route is MainRoute.Search || route is MainRoute.EmailDetail || route is MainRoute.Compose) {
        "Route $route is not an overlay destination"
    }
    navigate(route) {
        launchSingleTop = true
    }
}

/**
 * Closes the Email Detail screen idempotently.
 *
 * Only acts when [originatingEntry] is still the current RESUMED entry.
 * Publishes [KEY_CLOSED_EMAIL_ID] to the previous entry's SavedStateHandle
 * only when the pop succeeded and the previous destination is Inbox, Trash
 * or Search.
 *
 * @return true if the entry was popped, false for a stale or forwarded request.
 */
@MainThread
internal fun NavHostController.closeEmailDetail(
    originatingEntry: NavBackStackEntry,
    emailId: String
): Boolean {
    val previousEntry = previousBackStackEntry
    val popped = popBackStackFrom(originatingEntry)
    if (popped && previousEntry != null) {
        val dest = previousEntry.destination
        val isValidOrigin = dest.hasRoute<MainRoute.Inbox>() ||
                dest.hasRoute<MainRoute.Trash>() ||
                dest.hasRoute<MainRoute.Search>()
        if (isValidOrigin) {
            previousEntry.savedStateHandle[KEY_CLOSED_EMAIL_ID] = emailId
        }
    }
    return popped
}
