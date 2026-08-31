package com.david.mailapp.feature.inbox

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import com.david.mailapp.R
import com.david.mailapp.ui.theme.MotionTokens
import kotlinx.coroutines.launch

/** Internal visual extraction of the Inbox app bar; behavior and animation values are unchanged. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InboxTopBar(
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val searchIconScale = remember { Animatable(1f) }
    val onSearchTap: () -> Unit = {
        scope.launch {
            searchIconScale.snapTo(MotionTokens.pressScale)
            searchIconScale.animateTo(1.02f, MotionTokens.iconTap)
            searchIconScale.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 500f))
        }
        onSearchClick()
    }

    TopAppBar(
        title = { androidx.compose.material3.Text(stringResource(R.string.inbox_title), style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.action_menu))
            }
        },
        actions = {
            IconButton(onClick = onSearchTap) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.action_search),
                    modifier = Modifier.scale(searchIconScale.value)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background
        )
    )
}
