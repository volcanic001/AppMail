package com.david.mailapp.feature.search.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.david.mailapp.ui.theme.MotionTokens
import kotlinx.coroutines.delay

/**
 * Shows recent search history as chips with staggered entrance animation.
 *
 * Each chip enters with delay = index * [MotionTokens.staggerDelayMs],
 * animating alpha 0→1 + translationY 12dp→0.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchSuggestionChips(
    history: List<String>,
    onQuerySelected: (String) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Recientes",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            AnimatedVisibility(
                visible = history.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                TextButton(onClick = onClearHistory) {
                    Text("Borrar", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (history.isEmpty()) {
            Text(
                "Tus búsquedas recientes aparecerán aquí",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                history.forEachIndexed { index, query ->
                    StaggeredChip(
                        query = query,
                        delayMs = index * MotionTokens.staggerDelayMs,
                        onClick = { onQuerySelected(query) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StaggeredChip(
    query: String,
    delayMs: Int,
    onClick: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = MotionTokens.resultStagger,
        label = "chipAlpha"
    )

    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        visible = true
    }

    SuggestionChip(
        onClick = onClick,
        icon = {
            Icon(
                Icons.Outlined.History,
                contentDescription = null,
                modifier = Modifier.alpha(alpha)
            )
        },
        label = {
            Text(query, modifier = Modifier.alpha(alpha))
        },
        modifier = Modifier.alpha(alpha)
    )
}
