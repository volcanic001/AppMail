package com.david.mailapp.ui.navigation

import com.david.mailapp.feature.compose.ComposeArgs
import com.david.mailapp.feature.compose.ComposeMode

/**
 * Maps Compose navigation route parameters directly to ComposeArgs.
 *
 * Ensures that IDs are preserved exactly and that invalid routes
 * are rejected.
 */
internal fun MainRoute.Compose.toComposeArgs(): ComposeArgs = when (mode) {
    ComposeMode.WRITE -> ComposeArgs.Write
    ComposeMode.REPLY -> ComposeArgs.Reply(
        requireNotNull(originalEmailId) {
            "originalEmailId must not be null for REPLY mode"
        }
    )
    ComposeMode.FORWARD -> ComposeArgs.Forward(
        requireNotNull(originalEmailId) {
            "originalEmailId must not be null for FORWARD mode"
        }
    )
}
