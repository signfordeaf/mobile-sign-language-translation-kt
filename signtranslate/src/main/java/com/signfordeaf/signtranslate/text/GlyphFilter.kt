// signtranslate/src/main/java/com/signfordeaf/signtranslate/text/GlyphFilter.kt

package com.signfordeaf.signtranslate.text

/**
 * Decides whether a text candidate carries real, translatable content (doc 08).
 *
 * Icon fonts draw a glyph from a **private use area** codepoint, and UI frameworks routinely
 * render icons as text nodes. Without this filter, tapping an icon would send a meaningless
 * glyph to the translation API — and, since a control's label makes it translatable, would also
 * stop icon buttons from ever being pressed.
 *
 * This MUST be an **exclusion** test, not an "is this a letter" test: Arabic is a supported,
 * uncased script, so any heuristic built on letter case quietly rejects it.
 */
object GlyphFilter {

    /** True if [text] has at least one char that is neither whitespace nor a private-use glyph. */
    fun hasTranslatableContent(text: CharSequence?): Boolean {
        if (text.isNullOrEmpty()) return false
        var i = 0
        while (i < text.length) {
            val cp = Character.codePointAt(text, i)
            if (!isWhitespace(cp) && !isPrivateUse(cp)) return true
            i += Character.charCount(cp)
        }
        return false
    }

    private fun isWhitespace(cp: Int): Boolean =
        cp == ' '.code || cp == '\t'.code || cp == '\r'.code || cp == '\n'.code

    /** BMP PUA, Supplementary PUA-A and PUA-B (doc 08). */
    private fun isPrivateUse(cp: Int): Boolean =
        (cp in 0xE000..0xF8FF) || (cp in 0xF0000..0xFFFFD) || (cp in 0x100000..0x10FFFD)
}
