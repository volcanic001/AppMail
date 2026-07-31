package com.david.mailapp.ui.components

import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Test

class ExpressiveLoadingIndicatorTest {

    @Test
    fun morph_cycle_ends_with_the_same_geometry_it_started_with() {
        for (step in 0..72) {
            val angle = (step * 2 * PI / 72).toFloat()

            assertEquals(
                expressiveRadiusFactor(angle, 0f),
                expressiveRadiusFactor(angle, EXPRESSIVE_MORPH_CYCLE),
                0.000_001f
            )
        }
    }

    @Test
    fun frames_on_both_sides_of_the_morph_boundary_are_continuous() {
        val epsilon = 0.000_01f

        for (step in 0..72) {
            val angle = (step * 2 * PI / 72).toFloat()

            assertEquals(
                expressiveRadiusFactor(angle, EXPRESSIVE_MORPH_CYCLE - epsilon),
                expressiveRadiusFactor(angle, epsilon),
                0.000_01f
            )
        }
    }
}
