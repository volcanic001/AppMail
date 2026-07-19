package com.david.mailapp.feature.trash

import com.david.mailapp.domain.model.Email

sealed interface TrashUiState {
    data object Loading : TrashUiState
    data class Success(
        val emails: List<Email>,
        val nextPageToken: String? = null,
        val isRefreshing: Boolean = false,
        val isLoadingNextPage: Boolean = false
    ) : TrashUiState
    data class Error(val message: String) : TrashUiState
}
