// signtranslate/src/main/java/com/signfordeaf/signtranslate/tokens/ContrastGuard.kt

package com.signfordeaf.signtranslate.tokens

import java.util.concurrent.ConcurrentHashMap

/**
 * WCAG 2.1 contrast enforcement (doc 05). The host chooses `primaryColor`/`surfaceColor` freely
 * and pairs them with a foreground guessed at configuration time; an unreadable pairing is a
 * defect here, not a styling opinion.
 *
 * Before painting any foreground on a configured background, [resolve] computes the contrast
 * ratio. If it is below [MIN_RATIO] (4.5:1) it substitutes black or white — whichever scores
 * higher against that background. Colors are ARGB [Int]s. Results are cached per `(bg, fg)` pair.
 */
object ContrastGuard {

    const val MIN_RATIO = 4.5

    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val BLACK = 0xFF000000.toInt()

    private val cache = ConcurrentHashMap<Long, Int>()

    /** The foreground actually painted for [foreground] over [background]. */
    fun resolve(foreground: Int, background: Int): Int {
        val key = (background.toLong() and 0xFFFFFFFFL shl 32) or (foreground.toLong() and 0xFFFFFFFFL)
        cache[key]?.let { return it }
        val resolved = if (ratio(foreground, background) >= MIN_RATIO) {
            foreground
        } else {
            if (ratio(WHITE, background) >= ratio(BLACK, background)) WHITE else BLACK
        }
        cache[key] = resolved
        return resolved
    }

    /** WCAG contrast ratio, 1 (identical) … 21 (black on white). Symmetric in its arguments. */
    fun ratio(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    /** WCAG relative luminance of an ARGB color (alpha ignored). */
    fun luminance(color: Int): Double {
        val r = ((color shr 16) and 0xFF) / 255.0
        val g = ((color shr 8) and 0xFF) / 255.0
        val b = (color and 0xFF) / 255.0
        return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b)
    }

    private fun linear(c: Double): Double =
        if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
}
