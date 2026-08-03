package com.david.mailapp.core.auth

import com.david.mailapp.core.localization.UiErrorReason
import com.david.mailapp.data.pdf.PdfCacheClearResult
import kotlinx.coroutines.CancellationException

internal sealed interface OAuthLaunchPreflightResult {
    data object Ready : OAuthLaunchPreflightResult
    data class Failed(val reason: UiErrorReason) : OAuthLaunchPreflightResult
}

internal suspend fun runOAuthLaunchPreflight(
    isPendingPdfCleanup: suspend () -> Boolean,
    clearPdfCache: suspend () -> PdfCacheClearResult,
    markPdfCleanupCompleted: suspend () -> Unit
): OAuthLaunchPreflightResult {
    return try {
        if (!isPendingPdfCleanup()) {
            OAuthLaunchPreflightResult.Ready
        } else {
            val result = clearPdfCache()
            when (result) {
                is PdfCacheClearResult.Success -> {
                    markPdfCleanupCompleted()
                    OAuthLaunchPreflightResult.Ready
                }
                is PdfCacheClearResult.Failure -> {
                    OAuthLaunchPreflightResult.Failed(UiErrorReason.TEMP_CLEANUP_FAILED)
                }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        OAuthLaunchPreflightResult.Failed(UiErrorReason.LOCAL_CLEANUP_CHECK_FAILED)
    }
}
