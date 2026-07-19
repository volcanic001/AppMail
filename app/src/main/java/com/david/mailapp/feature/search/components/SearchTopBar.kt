package com.david.mailapp.feature.search.components

import android.util.Log
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.david.mailapp.ui.theme.MotionTokens
import kotlinx.coroutines.delay

/**
 * Custom search bar that replaces the standard TopAppBar on SearchScreen.
 *
 * Animations:
 * - Corner radius: 28dp (pill) → 0dp (flat) after entry (tween 250ms)
 * - Auto-focus on keyboard via [FocusRequester]
 */
@Composable
fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    entryKey: Any = Unit
) {
    val focusRequester = remember { FocusRequester() }
    var isEntered by remember(entryKey) { mutableStateOf(false) }

    // Animate corner radius: pill → flat on entry with explicit 28.dp start value
    val cornerRadius by animateDpAsState(
        targetValue = if (isEntered) 0.dp else 28.dp,
        animationSpec = tween(MotionTokens.short),
        label = "searchCorner"
    )

    LaunchedEffect(entryKey) {
        isEntered = false
        delay(16) // 1 frame to ensure initial 28.dp pill render
        isEntered = true
        Log.d("SearchDebug", "[SearchTopBar] LaunchedEffect started (entryKey=$entryKey). Waiting 350ms before requesting focus...")
        delay(350) // wait for slideInVertically spring animation to settle to prevent keyboard resize clipping
        Log.d("SearchDebug", "[SearchTopBar] Requesting focus on text field now!")
        focusRequester.requestFocus()
    }

    Surface(
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(bottomStart = cornerRadius, bottomEnd = cornerRadius),
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back arrow
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back"
                )
            }

            // Text field
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = {
                    Log.d("SearchDebug", "[SearchTopBar] BasicTextField onValueChange: '$it'")
                    onQueryChange(it)
                },
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (query.isEmpty()) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        if (query.isEmpty()) {
                            Text(
                                "Buscar en correos…",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 18.sp
                            )
                        }
                        innerTextField()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
            )

            // Clear button
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Clear"
                    )
                }
            }
        }
    }
}
