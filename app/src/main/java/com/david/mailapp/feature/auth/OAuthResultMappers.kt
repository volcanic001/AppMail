package com.david.mailapp.feature.auth

import com.david.mailapp.R
import com.david.mailapp.core.auth.OAuthLaunchResult
import com.david.mailapp.core.auth.OAuthRedirectResult
import com.david.mailapp.core.localization.UiErrorReason
import com.david.mailapp.core.localization.UiText
import com.david.mailapp.core.localization.toUiText

/**
 * Mapeo interno y puro de [OAuthLaunchResult] a [UiText].
 *
 * - [OAuthLaunchResult.Launched] → null (sin mensaje)
 * - [OAuthLaunchResult.NoBrowserAvailable] → NO_COMPATIBLE_BROWSER
 * - [OAuthLaunchResult.Failed] → AUTH_LAUNCH_FAILED
 */
internal fun OAuthLaunchResult.toUiTextOrNull(): UiText? {
    return when (this) {
        OAuthLaunchResult.Launched -> null
        OAuthLaunchResult.NoBrowserAvailable -> UiErrorReason.NO_COMPATIBLE_BROWSER.toUiText()
        OAuthLaunchResult.Failed -> UiErrorReason.AUTH_LAUNCH_FAILED.toUiText()
    }
}

/**
 * Mapeo interno y puro de [OAuthRedirectResult] a [UiText].
 *
 * - [OAuthRedirectResult.Success], [OAuthRedirectResult.NotOAuthRedirect] → null
 * - [OAuthRedirectResult.UserCancelled] → R.string.session_auth_cancelled
 * - [OAuthRedirectResult.InvalidSession], [OAuthRedirectResult.ExpiredSession] → OAUTH_INVALID_SESSION
 * - [OAuthRedirectResult.MissingAuthorizationCode], [OAuthRedirectResult.TokenExchangeFailed] → SIGN_IN_FAILED
 */
internal fun OAuthRedirectResult.toUiTextOrNull(): UiText? {
    return when (this) {
        OAuthRedirectResult.Success -> null
        OAuthRedirectResult.NotOAuthRedirect -> null
        OAuthRedirectResult.UserCancelled -> UiText.Resource(R.string.session_auth_cancelled)
        OAuthRedirectResult.InvalidSession -> UiErrorReason.OAUTH_INVALID_SESSION.toUiText()
        OAuthRedirectResult.ExpiredSession -> UiErrorReason.OAUTH_INVALID_SESSION.toUiText()
        OAuthRedirectResult.MissingAuthorizationCode -> UiErrorReason.SIGN_IN_FAILED.toUiText()
        OAuthRedirectResult.TokenExchangeFailed -> UiErrorReason.SIGN_IN_FAILED.toUiText()
    }
}
