package com.david.mailapp.feature.settings

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation route hierarchy for the Settings hub.
 *
 * Each leaf route maps to a dedicated screen composable in [SettingsNavHost].
 * Using sealed interface + @Serializable data objects enables Navigation
 * Compose's type-safe APIs — no string-based route matching.
 */
sealed interface SettingsRoute {

    /** Settings hub — landing screen with navigation cards. */
    @Serializable
    data object Hub : SettingsRoute

    /** Appearance — theme, color palette, dynamic colors. */
    @Serializable
    data object Appearance : SettingsRoute

    /** Account — connected account info, sign-in / sign-out. */
    @Serializable
    data object Account : SettingsRoute

    /** Placeholder — future screen. */
    @Serializable
    data object Notifications : SettingsRoute

    /** Placeholder — future screen. */
    @Serializable
    data object Privacy : SettingsRoute

    /** Placeholder — future screen. */
    @Serializable
    data object Security : SettingsRoute

    /** About — app version, licenses, acknowledgements. */
    @Serializable
    data object About : SettingsRoute

    /** Changelog — list of changes per version. */
    @Serializable
    data object Changelog : SettingsRoute
}
