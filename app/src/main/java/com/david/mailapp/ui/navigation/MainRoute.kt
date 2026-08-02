package com.david.mailapp.ui.navigation

import kotlinx.serialization.Serializable
import com.david.mailapp.feature.compose.ComposeMode

/**
 * **MainRoute** defines the top-level serializable routes for the application.
 *
 * **CRITICAL DESIGN INVARIANT:**
 * In order to support robust state restoration and process death survival (Fase 4.4 / 4.5),
 * routes must carry ONLY the minimum primitive identifiers (e.g., emailId) required to
 * reconstruct the screen's state from Room.
 *
 * - NEVER add complex objects (such as Email), sender/recipient strings, subjects, bodies,
 *   attachments, Gmail tokens, or visual UI states to these routes.
 */
@Serializable
sealed interface MainRoute {

    @Serializable
    data object Inbox : MainRoute

    @Serializable
    data object Trash : MainRoute

    @Serializable
    data object Settings : MainRoute

    @Serializable
    data object Search : MainRoute

    @Serializable
    data class EmailDetail(val emailId: String) : MainRoute {
        init {
            require(emailId.isNotBlank()) {
                "emailId must not be empty or consist only of whitespace"
            }
        }
    }

    @Serializable
    data class Compose(
        val mode: ComposeMode,
        val originalEmailId: String? = null
    ) : MainRoute {
        init {
            when (mode) {
                ComposeMode.WRITE -> require(originalEmailId == null) {
                    "WRITE mode requires originalEmailId to be null"
                }
                ComposeMode.REPLY, ComposeMode.FORWARD -> require(!originalEmailId.isNullOrBlank()) {
                    "$mode mode requires a non-empty, non-blank originalEmailId"
                }
            }
        }
    }
}
