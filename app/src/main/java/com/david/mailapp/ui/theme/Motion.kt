package com.david.mailapp.ui.theme

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset

/**
 * Centralized motion tokens — every animation in the app references these.
 */
object MotionTokens {

    // ── Spring specs ──────────────────────────────────────────────

    /** Tap animation: 0.96 → 1.02 → 1.00 (overshoot + settle) */
    val iconTap: SpringSpec<Float> = spring(
        dampingRatio = 0.4f,
        stiffness = 800f
    )

    /** Email list item press: 1.00 → 0.97 → 1.00 (subtle squish) */
    val itemPress: SpringSpec<Float> = spring(
        dampingRatio = 0.5f,
        stiffness = 600f
    )

    /** Pull-to-refresh settle */
    val pullToRefresh: SpringSpec<Float> = spring(
        dampingRatio = 0.7f,
        stiffness = 250f
    )

    /** Shared element / screen transitions */
    val sharedElement: SpringSpec<Float> = spring(
        dampingRatio = 0.65f,
        stiffness = 350f
    )

    // ── Search screen springs ─────────────────────────────────────

    /** Search screen open — elastic expand from the search icon */
    val searchExpand: SpringSpec<Float> = spring(
        dampingRatio = 0.55f,
        stiffness = 280f
    )

    /** Search screen close — quick, firm collapse */
    val searchCollapse: SpringSpec<Float> = spring(
        dampingRatio = 0.7f,
        stiffness = 400f
    )

    /** Search results staggered entrance */
    val resultStagger: SpringSpec<Float> = spring(
        dampingRatio = 0.6f,
        stiffness = 320f
    )

    /** Swipe return — underdamped spring, subtle overshoot (rubber band) */
    val swipeReturn: SpringSpec<Float> = spring(
        dampingRatio = 0.55f,
        stiffness = 300f
    )

    /** Swipe dismiss — momentum-preserving exit */
    val swipeDismiss: SpringSpec<Float> = spring(
        dampingRatio = 0.8f,
        stiffness = 350f
    )

    /** List reorganization after item removal */
    val listReorganize: SpringSpec<IntOffset> = spring(
        dampingRatio = 0.5f,
        stiffness = 280f
    )

    // ── Duration tokens (ms) ──────────────────────────────────────

    const val micro = 150
    const val short = 250
    const val medium = 350
    const val long = 500

    /** Scale on press for interactive elements */
    const val pressScale = 0.97f

    /** Swipe dismiss threshold as fraction of screen width */
    const val swipeThreshold = 0.35f

    /** Maximum resistance factor for drag damping */
    const val swipeResistance = 0.4f

    /** Delay between each search result item's staggered entrance (ms) */
    const val staggerDelayMs = 40

    /** Tonal highlight duration when returning from email detail to list item (ms) */
    const val highlightDurationMs = 800

    // ── Tween specs ───────────────────────────────────────────────

    fun tweenMicro() = tween<Float>(durationMillis = micro)
    fun tweenShort() = tween<Float>(durationMillis = short)
    fun tweenMedium() = tween<Float>(durationMillis = medium)
}
