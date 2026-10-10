package me.ash.reader.ui.page.settings.color.inprogress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fork patch 9: in-progress style + color.
 * Pure JVM test — ARGB int math only, no Android, no Compose.
 */
class InProgressColorsTest {

    @Test
    fun `hsv primaries convert exactly`() {
        assertEquals(0xFFFF0000.toInt(), hsvToArgb(0f, 1f, 1f))
        assertEquals(0xFF00FF00.toInt(), hsvToArgb(120f, 1f, 1f))
        assertEquals(0xFF0000FF.toInt(), hsvToArgb(240f, 1f, 1f))
    }

    @Test
    fun `hsv neutrals convert exactly`() {
        assertEquals(0xFF000000.toInt(), hsvToArgb(0f, 0f, 0f))
        assertEquals(0xFFFFFFFF.toInt(), hsvToArgb(0f, 0f, 1f))
        assertEquals(0xFF808080.toInt(), hsvToArgb(200f, 0f, 0.5f))
    }

    @Test
    fun `hsv wraps around the wheel`() {
        assertEquals(hsvToArgb(0f, 1f, 1f), hsvToArgb(360f, 1f, 1f))
    }

    @Test
    fun `strengths keep hue and step alpha`() {
        val strengths = highlightStrengths(0xFF4C83EB.toInt())

        assertEquals(5, strengths.size)
        // Same rgb in every step...
        strengths.forEach {
            assertEquals(0x4C, it shr 16 and 0xFF)
            assertEquals(0x83, it shr 8 and 0xFF)
            assertEquals(0xEB, it and 0xFF)
        }
        // ...with strictly rising alpha.
        val alphas = strengths.map { it ushr 24 }
        assertEquals(alphas.sorted(), alphas)
        assertTrue(alphas.first() < alphas.last())
    }

    @Test
    fun `shades bracket the input luminance`() {
        val base = 0xFF4C83EB.toInt()
        val shades = accentShades(base)

        assertEquals(5, shades.size)
        // Middle step is the input itself.
        assertEquals(base, shades[2])
        // Luminance falls then rises across the row.
        val lum = shades.map { (0.299 * (it shr 16 and 0xFF) + 0.587 * (it shr 8 and 0xFF) + 0.114 * (it and 0xFF)) }
        assertTrue(lum[0] < lum[2])
        assertTrue(lum[4] > lum[2])
    }

    @Test
    fun `auto resolves to the theme accent`() {
        assertEquals(0xFF123456.toInt(), resolveReadingColorArgb(COLOR_AUTO, 0xFF123456.toInt()))
        assertEquals(0xFFABCDEF.toInt(), resolveReadingColorArgb(0xFFABCDEF.toInt(), 0xFF123456.toInt()))
    }

    @Test
    fun `matching swatch is selected`() {
        val row = listOf(0xFF111111.toInt(), 0xFF222222.toInt())

        assertEquals(0xFF222222.toInt(), selectedSwatch(0xFF222222.toInt(), row))
    }

    @Test
    fun `off-row color selects nothing`() {
        assertNull(selectedSwatch(0xFF333333.toInt(), listOf(0xFF111111.toInt())))
        assertNull(selectedSwatch(0xFF111111.toInt(), emptyList()))
    }
}
