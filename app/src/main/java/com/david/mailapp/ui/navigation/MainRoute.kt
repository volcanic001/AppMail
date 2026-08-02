package com.david.mailapp.ui.navigation

import kotlinx.serialization.Serializable
import com.david.mailapp.feature.compose.ComposeMode

/**
2. **MainRoute** define los destinos del NavHost superior de forma serializable.
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
    data class EmailDetail(val emailId: String) : MainRoute

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
                ComposeMode.REPLY, ComposeMode.FORWARD -> require(!originalEmailId.isNullOrEmpty()) {
                    "$mode mode requires a non-empty originalEmailId"
                }
            }
        }
    }
}
