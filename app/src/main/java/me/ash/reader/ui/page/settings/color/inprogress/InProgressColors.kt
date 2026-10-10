package me.ash.reader.ui.page.settings.color.inprogress

import kotlin.math.roundToInt

/** Follow the theme accent instead of a fixed color. */
const val COLOR_AUTO = -1

private const val ALPHA_MASK = 0xFF000000.toInt()

/** HSV (h 0..360, s/v 0..1) to opaque ARGB. Pure int math, JVM-testable. */
fun hsvToArgb(h: Float, s: Float, v: Float): Int {
    val hh = ((h % 360f) + 360f) % 360f
    val c = v * s
    val x = c * (1f - kotlin.math.abs((hh / 60f) % 2f - 1f))
    val m = v - c
    val (r, g, b) =
        when {
            hh < 60f -> Triple(c, x, 0f)
            hh < 120f -> Triple(x, c, 0f)
            hh < 180f -> Triple(0f, c, x)
            hh < 240f -> Triple(0f, x, c)
            hh < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
    fun channel(f: Float) = ((f + m) * 255f).roundToInt().coerceIn(0, 255)
    return ALPHA_MASK or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
}

/** Same hue at rising highlight strengths (alpha steps). */
fun highlightStrengths(argb: Int): List<Int> {
    val rgb = argb and 0x00FFFFFF
    return listOf(0.06f, 0.10f, 0.16f, 0.24f, 0.32f).map { alpha ->
        ((alpha * 255f).roundToInt() shl 24) or rgb
    }
}

/** The input bracketed by darker and lighter mixes ([-0.35, -0.18, 0, +0.18, +0.35]). */
fun accentShades(argb: Int): List<Int> {
    val r = argb shr 16 and 0xFF
    val g = argb shr 8 and 0xFF
    val b = argb and 0xFF
    fun mix(channel: Int, t: Float): Int =
        if (t < 0) (channel * (1f + t)).roundToInt()
        else (channel + (255 - channel) * t).roundToInt()
    return listOf(-0.35f, -0.18f, 0f, 0.18f, 0.35f).map { t ->
        ALPHA_MASK or
            (mix(r, t).coerceIn(0, 255) shl 16) or
            (mix(g, t).coerceIn(0, 255) shl 8) or
            mix(b, t).coerceIn(0, 255)
    }
}

/** A stored color setting resolves to itself, [COLOR_AUTO] to the theme. */
fun resolveReadingColorArgb(customArgb: Int, themeArgb: Int): Int =
    if (customArgb == COLOR_AUTO) themeArgb else customArgb
