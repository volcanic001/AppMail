package com.david.mailapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

internal class DestinationViewModelStoreOwner : ViewModelStoreOwner {
    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore
        get() = store

    fun clear() {
        store.clear()
    }
}

@Composable
internal fun rememberDestinationViewModelStoreOwner(): DestinationViewModelStoreOwner {
    val owner = remember { DestinationViewModelStoreOwner() }
    DisposableEffect(Unit) {
        onDispose {
            owner.clear()
        }
    }
    return owner
}
