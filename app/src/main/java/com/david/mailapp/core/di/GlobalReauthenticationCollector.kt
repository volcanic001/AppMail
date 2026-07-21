package com.david.mailapp.core.di

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Consumes reauthentication events resiliently.
 *
 * Each event is processed inside its own try/catch to ensure that ordinary
 * exceptions during invalidation do not terminate the collector forever.
 * [CancellationException] is strictly propagated to allow cooperative cancellation.
 */
class GlobalReauthenticationCollector(
    private val reauthenticationEvents: Flow<Unit>,
    private val invalidateExpiredSession: suspend () -> SessionCoordinator.InvalidationResult,
    private val sessionExpiredSignal: MutableStateFlow<Boolean>,
    private val errorLogger: (Exception) -> Unit = {}
) {
    /**
     * Suspends indefinitely consuming events.
     * Must be launched in an application-scoped SupervisorJob.
     */
    suspend fun collectEvents() {
        reauthenticationEvents.collect {
            try {
                val result = invalidateExpiredSession()
                if (result is SessionCoordinator.InvalidationResult.Completed) {
                    sessionExpiredSignal.value = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorLogger(e)
            }
        }
    }
}
