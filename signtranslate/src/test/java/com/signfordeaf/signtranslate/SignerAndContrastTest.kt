package com.signfordeaf.signtranslate

import com.signfordeaf.signtranslate.core.Signer
import com.signfordeaf.signtranslate.core.SignerResolver
import com.signfordeaf.signtranslate.tokens.ContrastGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Doc 15 sections F (signer identity) and K (contrast). */
class SignerAndContrastTest {

    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()
    private val yellow = 0xFFFFEB3B.toInt()
    private val purple = 0xFF6750A4.toInt()

    // --- Contrast

    @Test
    fun `black on white is the maximum ratio`() {
        assertEquals(21.0, ContrastGuard.ratio(black, white), 0.05)
    }

    @Test
    fun `a color against itself is the minimum ratio`() {
        assertEquals(1.0, ContrastGuard.ratio(purple, purple), 0.001)
    }

    @Test
    fun `ratio is symmetric`() {
        assertEquals(ContrastGuard.ratio(purple, white), ContrastGuard.ratio(white, purple), 0.0001)
    }

    @Test
    fun `a passing foreground is kept`() {
        // White on purple already passes 4.5:1.
        assertEquals(white, ContrastGuard.resolve(white, purple))
    }

    @Test
    fun `a failing foreground is replaced with the better of black or white`() {
        // White on yellow fails; black reads better on yellow.
        assertEquals(black, ContrastGuard.resolve(white, yellow))
        // White on black fails; white reads better on black.
        assertEquals(white, ContrastGuard.resolve(black, black))
    }

    // --- Signer resolution

    @Test
    fun `an exact pair resolves to its signer`() {
        assertEquals(Signer.KADIR, SignerResolver.resolve(null, "23", "16"))
        assertEquals(Signer.JASON, SignerResolver.resolve(null, "44", "36"))
    }

    @Test
    fun `either id alone still resolves`() {
        assertEquals(Signer.JASON, SignerResolver.resolve(null, "44", null))
        assertEquals(Signer.OWAIS, SignerResolver.resolve(null, null, "29"))
    }

    @Test
    fun `tid wins when the two ids disagree`() {
        // tid 23 = Kadir, fdid 35 = Hesna's dictionary. No exact pair, so tid decides.
        assertEquals(Signer.KADIR, SignerResolver.resolve(null, "23", "35"))
    }

    @Test
    fun `unknown or empty ids fall back to the stand-in`() {
        assertEquals(SignerResolver.FALLBACK, SignerResolver.resolve(null, "999", "999"))
        assertEquals(SignerResolver.FALLBACK, SignerResolver.resolve(null, "", ""))
        assertEquals(SignerResolver.FALLBACK, SignerResolver.resolve(null, null, null))
    }

    @Test
    fun `a pinned signer overrides the ids`() {
        assertEquals(Signer.JASON, SignerResolver.resolve(PlaceholderAvatar.JASON, "23", "16"))
    }

    @Test
    fun `tid and fdid are optional and auto-resolve to Hesna`() {
        val cfg = SignLanguageConfig(apiKey = "k", apiUrl = "u")
        assertTrue(cfg.tid == null && cfg.fdid == null) // not required
        assertEquals(Signer.HESNA, SignerResolver.resolve(cfg.card.placeholderAvatar, cfg.tid, cfg.fdid))
    }
}
