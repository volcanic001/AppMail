package com.david.mailapp.feature.settings

import com.david.mailapp.ui.theme.ColorPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests JVM para los contratos de Ajustes (Fase 2B).
 *
 * Verifica [ColorPalette] y la tabla del changelog.
 */
class SettingsContractsTest {

    // ── ColorPalette ─────────────────────────────────────────────

    @Test
    fun `ColorPalette tiene exactamente 9 valores`() {
        assertEquals(9, ColorPalette.values().size)
    }

    @Test
    fun `ColorPalette conserva orden esperado`() {
        val values = ColorPalette.values()
        assertEquals(ColorPalette.Dynamic, values[0])
        assertEquals(ColorPalette.Blue, values[1])
        assertEquals(ColorPalette.Green, values[2])
        assertEquals(ColorPalette.Purple, values[3])
        assertEquals(ColorPalette.Orange, values[4])
        assertEquals(ColorPalette.Pink, values[5])
        assertEquals(ColorPalette.Teal, values[6])
        assertEquals(ColorPalette.Yellow, values[7])
        assertEquals(ColorPalette.Monochrome, values[8])
    }

    @Test
    fun `todos los valores de ColorPalette tienen labelResId no nulo`() {
        for (palette in ColorPalette.values()) {
            assertNotEquals(
                "ColorPalette.${palette.name} debería tener labelResId válido",
                0,
                palette.labelResId
            )
        }
    }

    @Test
    fun `cada ColorPalette tiene un labelResId unico`() {
        val ids = ColorPalette.values().map { it.labelResId }.toSet()
        assertEquals(9, ids.size)
    }

    @Test
    fun `ColorPalette Dynamic tiene labelResId diferente de las demas`() {
        val nonDynamic = ColorPalette.values().filter { it != ColorPalette.Dynamic }
        for (palette in nonDynamic) {
            assertNotEquals(ColorPalette.Dynamic.labelResId, palette.labelResId)
        }
    }

    // ── Changelog ───────────────────────────────────────────────

    @Test
    fun `la tabla del changelog contiene exactamente 15 versiones`() {
        assertEquals(15, changelogTable.size)
    }

    @Test
    fun `la tabla del changelog tiene IDs de version unicos`() {
        val versionIds = changelogTable.map { it.versionResId }.toSet()
        assertEquals(15, versionIds.size)
    }

    @Test
    fun `la tabla del changelog tiene IDs de arrays unicos`() {
        val arrayIds = changelogTable.map { it.changesResId }.toSet()
        assertEquals(15, arrayIds.size)
    }

    @Test
    fun `todos los entries del changelog tienen versionResId y changesResId validos`() {
        for (entry in changelogTable) {
            assertNotEquals("versionResId no debería ser 0", 0, entry.versionResId)
            assertNotEquals("changesResId no debería ser 0", 0, entry.changesResId)
        }
    }
}
