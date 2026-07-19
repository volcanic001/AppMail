package com.david.mailapp.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Content of the ModalNavigationDrawer.
 *
 * Layout (top to bottom):
 * - Header: "MailApp" branding
 * - Divider
 * - Navigation items: Inbox / Trash / Settings
 * - Spacer (pushes footer to bottom)
 * - Footer: version info
 */
@Composable
fun DrawerContent(
    selectedScreen: Screen,
    onScreenSelected: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    ModalDrawerSheet(
        modifier = modifier.width(300.dp)
    ) {
        // ── Header ────────────────────────────────────────────
        Text(
            text = "MailApp",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(
                start = 28.dp,
                top = 16.dp,
                bottom = 8.dp
            )
        )

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 28.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ── Navigation ────────────────────────────────────────
        Screen.all.forEach { screen ->
            DrawerItem(
                screen = screen,
                isSelected = screen == selectedScreen,
                onClick = { onScreenSelected(screen) },
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }

        // ── Footer ────────────────────────────────────────────
        Spacer(modifier = Modifier.weight(1f))

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 28.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        Text(
            text = "MailApp v1.0",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                start = 28.dp,
                top = 16.dp,
                bottom = 24.dp
            )
        )
    }
}
