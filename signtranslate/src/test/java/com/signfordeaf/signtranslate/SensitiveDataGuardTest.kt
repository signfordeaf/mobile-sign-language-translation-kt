package com.signfordeaf.signtranslate

import com.signfordeaf.signtranslate.sensitive.SensitiveDataGuard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mirrors the Flutter package's `sensitive_data_guard_test.dart` to keep the two
 * implementations behaviorally identical.
 */
class SensitiveDataGuardTest {

    @Test
    fun `valid TCKN is blocked`() {
        assertTrue(SensitiveDataGuard.isSensitive("10000000146"))
        assertTrue(SensitiveDataGuard.isSensitive("TCKN: 10000000146"))
    }

    @Test
    fun `invalid TCKN is not blocked`() {
        assertFalse(SensitiveDataGuard.isSensitive("12345678901"))
        assertFalse(SensitiveDataGuard.isSensitive("Bu butona 12345678901 kez bastınız"))
    }

    /**
     * Regression for the non-negative-modulo rule (doc 11): a valid TCKN whose checksum
     * intermediate `(oddSum*7 - evenSum)` is negative was slipping past the guard with Kotlin's
     * sign-following `%`. 18000000098 is such a number.
     */
    @Test
    fun `valid TCKN with a negative checksum intermediate is blocked`() {
        assertTrue(SensitiveDataGuard.isSensitive("18000000098"))
        assertTrue(SensitiveDataGuard.isSensitive("Kimlik: 18000000098"))
    }

    @Test
    fun `valid credit card is blocked`() {
        assertTrue(SensitiveDataGuard.isSensitive("4242 4242 4242 4242"))
        assertTrue(SensitiveDataGuard.isSensitive("Kart: 4111-1111-1111-1111"))
    }

    @Test
    fun `invalid credit card is not blocked`() {
        assertFalse(SensitiveDataGuard.isSensitive("1234 5678 9012 3456"))
    }

    @Test
    fun `turkish IBAN is blocked`() {
        assertTrue(SensitiveDataGuard.isSensitive("TR33 0006 1005 1978 6457 8413 26"))
    }

    @Test
    fun `email is blocked`() {
        assertTrue(SensitiveDataGuard.isSensitive("iletisim: ali@example.com"))
    }

    @Test
    fun `gsm number is blocked`() {
        assertTrue(SensitiveDataGuard.isSensitive("0532 123 45 67"))
        assertTrue(SensitiveDataGuard.isSensitive("+90 532 123 45 67"))
    }

    @Test
    fun `plain text is not blocked`() {
        assertFalse(SensitiveDataGuard.isSensitive("Merhaba dünya"))
        assertFalse(SensitiveDataGuard.isSensitive("You have pushed the button this many times"))
    }

    @Test
    fun `blank and null are not blocked`() {
        assertFalse(SensitiveDataGuard.isSensitive(""))
        assertFalse(SensitiveDataGuard.isSensitive("   "))
        assertFalse(SensitiveDataGuard.isSensitive(null))
    }

    @Test
    fun `registered text is blocked bidirectionally`() {
        SensitiveDataGuard.register("Gizli Not")
        try {
            assertTrue(SensitiveDataGuard.isSensitive("Gizli Not"))
            assertTrue(SensitiveDataGuard.isSensitive("Gizli"))            // substring of marked
            assertTrue(SensitiveDataGuard.isSensitive("Çok Gizli Not içeriği")) // superstring
        } finally {
            SensitiveDataGuard.unregister("Gizli Not")
        }
        assertFalse(SensitiveDataGuard.isSensitive("Gizli Not"))
    }
}
