package com.das.tcamviewer2.factory

import com.das.tcamviewer2.palette.Arctic
import com.das.tcamviewer2.palette.Banded
import com.das.tcamviewer2.palette.Blackhot
import com.das.tcamviewer2.palette.DoubleRainbow
import com.das.tcamviewer2.palette.Fusion
import com.das.tcamviewer2.palette.Gray
import com.das.tcamviewer2.palette.Ironblack
import com.das.tcamviewer2.palette.Isotherm
import com.das.tcamviewer2.palette.Rainbow
import com.das.tcamviewer2.palette.Sepia

class PaletteFactory {
    val paletteNames: Array<String?> = arrayOf<String?>(
        "Arctic",
        "Banded",
        "Blackhot",
        "DoubleRainbow",
        "Fusion",
        "Gray",
        "Ironblack",
        "Isotherm",
        "Rainbow",
        "Sepia"
    )
    private val palettes = arrayOf<Array<IntArray?>?>(
        Arctic.palette,
        Banded.palette,
        Blackhot.pallete,
        DoubleRainbow.palette,
        Fusion.palette,
        Gray.palette,
        Ironblack.palette,
        Isotherm.palette,
        Rainbow.palette,
        Sepia.palette
    )

    fun getPaletteName(index: Int): String? {
        if (index < paletteNames.size) {
            return paletteNames[index]
        } else {
            return null
        }
    }

    fun getPaletteByName(name: String?): Array<IntArray?>? {
        for (index in paletteNames.indices) {
            if (paletteNames[index].equals(name, ignoreCase = true)) {
                return palettes[index]
            }
        }
        return null
    }
}