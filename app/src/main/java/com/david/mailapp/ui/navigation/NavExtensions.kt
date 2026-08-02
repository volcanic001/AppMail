package com.david.mailapp.ui.navigation

import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController

/**
 * Key used in SavedStateHandle to pass the closed email ID back to the origin destination.
 */
internal const val KEY_CLOSED_EMAIL_ID = "closed_email_id"

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
 * Closes the Email Detail screen, popping it and delivering the emailId to the origin
 * destination's SavedStateHandle if it is a valid destination (Inbox, Trash, Search).
 */
internal fun NavHostController.closeEmailDetail(emailId: String) {
    val previousEntry = previousBackStackEntry
    val popped = popBackStack()
    if (popped && previousEntry != null) {
        val dest = previousEntry.destination
        val isValidOrigin = dest.hasRoute<MainRoute.Inbox>() ||
                dest.hasRoute<MainRoute.Trash>() ||
                dest.hasRoute<MainRoute.Search>()
        if (isValidOrigin) {
            previousEntry.savedStateHandle[KEY_CLOSED_EMAIL_ID] = emailId
        }
    }
}
