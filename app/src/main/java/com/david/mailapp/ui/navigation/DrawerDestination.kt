package com.david.mailapp.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.david.mailapp.R

data class DrawerDestination(
    val route: MainRoute,
    @StringRes val labelResId: Int,
    val outlinedIcon: ImageVector,
    val filledIcon: ImageVector
) {
    companion object {
        val all = listOf(
            DrawerDestination(
                route = MainRoute.Inbox,
                labelResId = R.string.nav_inbox,
                outlinedIcon = Icons.Outlined.Inbox,
                filledIcon = Icons.Filled.Inbox
            ),
            DrawerDestination(
                route = MainRoute.Trash,
                labelResId = R.string.nav_trash,
                outlinedIcon = Icons.Outlined.Delete,
                filledIcon = Icons.Filled.Delete
            ),
            DrawerDestination(
                route = MainRoute.Settings,
                labelResId = R.string.nav_settings,
                outlinedIcon = Icons.Outlined.Settings,
                filledIcon = Icons.Filled.Settings
            )
        )
    }
}
