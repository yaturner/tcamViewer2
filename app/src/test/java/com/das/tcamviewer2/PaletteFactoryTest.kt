package com.das.tcamviewer2

import com.das.tcamviewer2.factory.PaletteFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PaletteFactoryTest {

    private lateinit var factory: PaletteFactory

    @Before
    fun setUp() {
        factory = PaletteFactory()
    }

    @Test
    fun allTenPalettesArePresent() {
        val names = listOf(
            "Arctic", "Banded", "Blackhot", "DoubleRainbow", "Fusion",
            "Gray", "Ironblack", "Isotherm", "Rainbow", "Sepia"
        )
        for (name in names) {
            assertNotNull("Palette '$name' should be found", factory.getPaletteByName(name))
        }
    }

    @Test
    fun unknownNameReturnsNull() {
        assertNull(factory.getPaletteByName("NotAPalette"))
        assertNull(factory.getPaletteByName(""))
        assertNull(factory.getPaletteByName(null))
    }

    @Test
    fun lookupIsCaseInsensitive() {
        assertNotNull(factory.getPaletteByName("rainbow"))
        assertNotNull(factory.getPaletteByName("RAINBOW"))
        assertNotNull(factory.getPaletteByName("IRONBLACK"))
        assertNotNull(factory.getPaletteByName("doublerainbow"))
    }

    @Test
    fun eachPaletteHasExactly256Entries() {
        for (name in factory.paletteNames) {
            val palette = factory.getPaletteByName(name)
            assertNotNull("Palette '$name' must exist", palette)
            assertEquals("Palette '$name' must have 256 entries", 256, palette!!.size)
        }
    }

    @Test
    fun allRgbValuesAreInValidRange() {
        for (name in factory.paletteNames) {
            val palette = factory.getPaletteByName(name) ?: continue
            for (i in palette.indices) {
                val rgb = palette[i]
                assertNotNull("Palette '$name' entry $i must not be null", rgb)
                assertEquals("Palette '$name' entry $i must have 3 channels", 3, rgb!!.size)
                for (channel in rgb) {
                    assertTrue(
                        "Palette '$name' entry $i: channel value $channel out of [0,255]",
                        channel in 0..255
                    )
                }
            }
        }
    }

    @Test
    fun paletteNameArrayHasTenEntries() {
        assertEquals(10, factory.paletteNames.size)
    }

    @Test
    fun getPaletteNameByIndexReturnsCorrectNames() {
        assertEquals("Arctic", factory.getPaletteName(0))
        assertEquals("Rainbow", factory.getPaletteName(8))
        assertEquals("Sepia", factory.getPaletteName(9))
    }

    @Test
    fun getPaletteNameOutOfBoundsReturnsNull() {
        assertNull(factory.getPaletteName(10))
        assertNull(factory.getPaletteName(100))
    }

    @Test
    fun lookupAndIndexAreConsistent() {
        for (i in factory.paletteNames.indices) {
            val name = factory.getPaletteName(i)
            assertNotNull("Name at index $i should not be null", name)
            assertNotNull("Palette at index $i should be found by name", factory.getPaletteByName(name))
        }
    }
}
