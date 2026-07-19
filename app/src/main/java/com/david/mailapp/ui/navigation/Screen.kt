package com.david.mailapp.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.david.mailapp.feature.compose.ComposeArgs

/**
 * Top-level navigation destinations.
 *
 * Using a sealed class instead of Jetpack Navigation because:
 * - 3 destinations max → no need for nav graph complexity
 * - Enables custom physics transitions via AnimatedContent
 * - Type-safe — no string-based route arguments
 * - Each screen carries its own icon pair (outlined/filled)
 */
sealed class Screen(
    val route: String,
    val label: String,
    val outlinedIcon: ImageVector,
    val filledIcon: ImageVector
) {
    data object Inbox : Screen(
        route = "inbox",
        label = "Inbox",
        outlinedIcon = Icons.Outlined.Inbox,
        filledIcon = Icons.Filled.Inbox
    )

    data object Trash : Screen(
        route = "trash",
        label = "Trash",
        outlinedIcon = Icons.Outlined.Delete,
        filledIcon = Icons.Filled.Delete
    )

    data object Settings : Screen(
        route = "settings",
        label = "Settings",
        outlinedIcon = Icons.Outlined.Settings,
        filledIcon = Icons.Filled.Settings
    )

    /** Search screen — activated only via the search icon in TopAppBar. Not shown in drawer. */
    data object Search : Screen(
        route = "search",
        label = "Search",
        outlinedIcon = Icons.Outlined.Search,
        filledIcon = Icons.Filled.Search
    )

    /** Email detail screen — programmatic-only, carries [emailId]. Not shown in drawer. */
    data class EmailDetail(val emailId: String) : Screen(
        route = "email_detail",
        label = "Detalle",
        outlinedIcon = Icons.Outlined.Email,
        filledIcon = Icons.Filled.Email
    )

    /** Email composition screen — programmatic-only, carries [ComposeArgs]. Not shown in drawer. */
    data class Compose(val args: ComposeArgs) : Screen(
        route = "compose",
        label = "Redactar",
        outlinedIcon = Icons.Outlined.Edit,
        filledIcon = Icons.Filled.Edit
    )

    companion object {
        /** Screens shown in the navigation drawer. Search is excluded — programmatic-only. */
        val all: List<Screen>
            get() = listOf(Inbox, Trash, Settings)
    }
}
