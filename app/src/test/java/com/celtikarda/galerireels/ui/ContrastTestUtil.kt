package com.celtikarda.galerireels.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/** ARGB tamsayısı (0xAARRGGBB); JVM testinde Android'e dokunmadan */
fun Color.argb(): Int = (value shr 32).toInt()

/** WCAG 2 kontrast oranı (opak renkler için) */
fun contrast(a: Color, b: Color): Double {
    fun lum(c: Color): Double {
        fun ch(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        val x = c.argb()
        return 0.2126 * ch(x shr 16 and 0xFF) + 0.7152 * ch(x shr 8 and 0xFF) + 0.0722 * ch(x and 0xFF)
    }
    val (hi, lo) = listOf(lum(a), lum(b)).sortedDescending()
    return (hi + 0.05) / (lo + 0.05)
}
