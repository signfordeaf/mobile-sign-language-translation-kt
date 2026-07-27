// signtranslate/src/main/java/com/signfordeaf/signtranslate/sensitive/SensitiveDataGuard.kt

package com.signfordeaf.signtranslate.sensitive

import java.util.Collections

/**
 * UI-independent helper that decides whether the selected text contains sensitive
 * data before it is sent to the server. Faithful port of the Flutter package's
 * `SensitiveDataGuard`.
 *
 * Two-layer defense:
 *   1. Turkey-specific + general PII patterns via regular expressions (with
 *      checksum/Luhn validation for national ID and credit card).
 *   2. Overlap with texts manually marked sensitive via [register] (e.g. through
 *      `SignLanguage.registerSensitive` / `SignLanguage.markSensitive`).
 *
 * If either matches, the text is treated as sensitive and no translation request
 * is ever sent.
 */
object SensitiveDataGuard {

    /** Email address. */
    private val email = Regex("""[\w.+-]+@[\w-]+\.[\w.-]+""")

    /** Turkish IBAN (TR + 24 digits, may contain spaces). */
    private val ibanTr = Regex("""\bTR\d{2}(?:[ ]?\d{4}){5}[ ]?\d{2}\b""", RegexOption.IGNORE_CASE)

    /** Turkish mobile number: optional +90 / 0, followed by 5xx xxx xx xx. */
    private val gsm = Regex("""(?:\+90|0)?[ ]?5\d{2}[ ]?\d{3}[ ]?\d{2}[ ]?\d{2}""")

    /** 11-digit candidate (Turkish national ID candidate); validated in [isValidTckn]. */
    private val elevenDigits = Regex("""\b\d{11}\b""")

    /** 13-19 digit candidate, possibly with spaces/dashes (credit card); validated in [passesLuhn]. */
    private val cardCandidate = Regex("""\b(?:\d[ -]?){12,18}\d\b""")

    /** Texts manually marked sensitive. Thread-safe (may be read/written off the UI thread). */
    private val registeredTexts: MutableSet<String> = Collections.synchronizedSet(mutableSetOf<String>())

    // MARK: - Registry (manual marking)

    /** Marks [text] as sensitive. Empty/whitespace text is ignored; the value is trimmed. */
    fun register(text: String) {
        val normalized = text.trim()
        if (normalized.isEmpty()) return
        registeredTexts.add(normalized)
    }

    /** Removes a text previously added via [register]. */
    fun unregister(text: String) {
        registeredTexts.remove(text.trim())
    }

    /**
     * True if [selected] overlaps any registered text (bidirectional `contains`), so
     * both a substring of a marked text and a superstring containing it are blocked.
     */
    fun isRegistered(selected: String?): Boolean {
        val normalized = selected?.trim().orEmpty()
        if (normalized.isEmpty()) return false
        synchronized(registeredTexts) {
            if (registeredTexts.isEmpty()) return false
            for (sensitive in registeredTexts) {
                if (normalized.contains(sensitive) || sensitive.contains(normalized)) return true
            }
        }
        return false
    }

    // MARK: - Detection

    /** Returns true if [text] contains sensitive data (marked or auto-detected). */
    fun isSensitive(text: String?): Boolean {
        val trimmed = text?.trim().orEmpty()
        if (trimmed.isEmpty()) return false

        // 1) Overlap with manually marked (registered) texts.
        if (isRegistered(trimmed)) return true

        // 2) Direct pattern matches.
        if (email.containsMatchIn(trimmed)) return true
        if (ibanTr.containsMatchIn(trimmed)) return true
        if (gsm.containsMatchIn(trimmed)) return true

        // 3) Patterns that require checksum validation.
        for (match in elevenDigits.findAll(trimmed)) {
            if (isValidTckn(match.value)) return true
        }
        for (match in cardCandidate.findAll(trimmed)) {
            val digits = match.value.replace(Regex("""[ -]"""), "")
            if (digits.length in 13..19 && passesLuhn(digits)) return true
        }

        return false
    }

    /**
     * Turkish national ID (T.C. Kimlik No) checksum validation: 11 digits, first digit
     * not 0; 10th digit = `((oddSum * 7) - evenSum) % 10`; 11th digit = sum of first 10 % 10.
     */
    private fun isValidTckn(value: String): Boolean {
        if (value.length != 11) return false
        val d = value.map { it - '0' }
        if (d[0] == 0) return false

        val oddSum = d[0] + d[2] + d[4] + d[6] + d[8]
        val evenSum = d[1] + d[3] + d[5] + d[7]
        val tenth = ((oddSum * 7) - evenSum) % 10
        if (tenth != d[9]) return false

        val firstTenSum = d.take(10).sum()
        return firstTenSum % 10 == d[10]
    }

    /** Credit card validation using the Luhn algorithm. */
    private fun passesLuhn(digits: String): Boolean {
        var sum = 0
        var alternate = false
        for (i in digits.length - 1 downTo 0) {
            var n = digits[i] - '0'
            if (alternate) {
                n *= 2
                if (n > 9) n -= 9
            }
            sum += n
            alternate = !alternate
        }
        return sum % 10 == 0
    }
}
