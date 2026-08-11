// signtranslate/src/main/java/com/signfordeaf/signtranslate/text/SentenceSegmenter.kt

package com.signfordeaf.signtranslate.text

import com.signfordeaf.signtranslate.Granularity
import java.util.Locale

/**
 * Explicit sentence segmentation (doc 09). There is deliberately no ICU/platform sentence
 * breaker in play — the rules are reproduced exactly, because platform breakers disagree on the
 * constructs these were tuned for (`T.C.`, `A.Ş.`, `5.000.000 TL`, numbered clauses), and a
 * different split changes what is sent to the backend and what the cache keys on.
 *
 * **Losslessness invariant:** the ranges returned for a text partition `[0, length)` exactly —
 * concatenating them reproduces the input character for character. Trailing whitespace belongs
 * to the segment that precedes it.
 */
object SentenceSegmenter {

    /** A half-open range `[start, end)` into the source text. */
    data class Span(val start: Int, val end: Int) {
        val length: Int get() = end - start
    }

    /** The outcome of [plan]: the segments to report and which one the tap hit. */
    data class Plan(val segments: List<String>, val index: Int)

    // Multi-letter Turkish abbreviations (doc 09). Single-letter initialisms are handled
    // structurally by the initialism rule below. Matched case-insensitively.
    private val ABBREVIATIONS = setOf(
        "vb", "vs", "vd", "bkz", "örn", "age", "çev", "haz", "ed", "dr", "doç", "prof", "av", "sn",
        "bay", "bayan", "öğr", "gör", "arş", "ltd", "şti", "tic", "san", "md", "gen", "alb", "yzb",
        "ütğm", "mah", "cad", "sok", "apt", "blv", "no", "tel", "faks", "kat", "üniv", "fak", "böl",
        "ans", "yy", "mö", "ms"
    )

    private const val TERMINATORS = ".!?…"
    private const val CLOSERS = ")]}\"'»”’"
    private const val OPENERS = "([{\"'«“‘-–—"

    // Coordinating conjunctions used as length-chunk cut points (doc 09). Longest first so
    // "ya da" is tried before "ya"/"da" fragments would ever matter.
    private val CONJUNCTIONS = listOf("ya da", "ancak", "fakat", "çünkü", "veya", "ise", "ki", "ve")

    // --- Character helpers (doc 09). A "letter" is a char whose cases differ; this keeps Turkish
    // ı/İ and ş/Ş working without a locale-specific alphabet. Uncased scripts (Arabic) have none.
    private fun isLetter(c: Char): Boolean = c.lowercaseChar() != c.uppercaseChar()
    private fun isUpper(c: Char): Boolean = isLetter(c) && c == c.uppercaseChar()
    private fun isDigit(c: Char): Boolean = c in '0'..'9'
    private fun isWs(c: Char): Boolean = c == ' ' || c == '\n' || c == '\t' || c == '\r' || c == ' '
    private fun isTerminator(c: Char) = TERMINATORS.indexOf(c) >= 0
    private fun isCloser(c: Char) = CLOSERS.indexOf(c) >= 0
    private fun looksLikeStart(c: Char) = isDigit(c) || OPENERS.indexOf(c) >= 0 || isUpper(c)

    // ---------------------------------------------------------------- Sentence boundaries

    /** Lossless sentence ranges over [text] (no length chunking). Empty text yields no ranges. */
    fun splitSentences(text: String): List<Span> {
        if (text.isEmpty()) return emptyList()
        val cuts = ArrayList<Int>()
        var segStart = 0
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\n') {
                var j = i
                while (j < text.length && isWs(text[j])) j++
                if (j < text.length) { cuts.add(j); segStart = j; i = j; continue }
                break
            } else if (isTerminator(c) && endsSentence(text, i, segStart)) {
                var k = i + 1
                while (k < text.length && isCloser(text[k])) k++
                if (k < text.length && isWs(text[k])) {
                    var j = k
                    while (j < text.length && isWs(text[j])) j++
                    if (j < text.length && looksLikeStart(text[j])) {
                        cuts.add(j); segStart = j; i = j; continue
                    }
                }
            }
            i++
        }
        val ranges = ArrayList<Span>()
        var start = 0
        for (cut in cuts) { ranges.add(Span(start, cut)); start = cut }
        ranges.add(Span(start, text.length))
        return mergeShort(text, ranges)
    }

    /** Whether a terminator at [i] genuinely ends a sentence. `!`,`?`,`…` always do; `.` is picky. */
    private fun endsSentence(text: String, i: Int, segStart: Int): Boolean {
        if (text[i] != '.') return true

        // Decimal / thousands separator: a digit on both sides (5.000.000, 1.2).
        if (i > 0 && i + 1 < text.length && isDigit(text[i - 1]) && isDigit(text[i + 1])) return false

        // Initialism: preceded by a lone uppercase letter, the char before that not a letter (T.C.).
        if (i > 0 && isUpper(text[i - 1]) && (i - 2 < 0 || !isLetter(text[i - 2]))) return false

        // Known abbreviation: the letter run before the period is in the list (vb., Ltd., Prof.).
        run {
            var j = i - 1
            while (j >= 0 && isLetter(text[j])) j--
            val letterRun = text.substring(j + 1, i)
            if (letterRun.isNotEmpty() && letterRun.lowercase(Locale.ROOT) in ABBREVIATIONS) return false
        }

        // List marker: only digits and periods precede it, and the segment up to that number is
        // whitespace (7. Para yatırma…).
        run {
            var j = i - 1
            while (j >= segStart && (isDigit(text[j]) || text[j] == '.')) j--
            val runStart = j + 1
            if (runStart <= i - 1) {
                var allWs = true
                var t = segStart
                while (t <= j) { if (!isWs(text[t])) { allWs = false; break }; t++ }
                if (allWs) {
                    var hasDigit = false
                    var u = runStart
                    while (u < i) { if (isDigit(text[u])) { hasDigit = true; break }; u++ }
                    if (hasDigit) return false
                }
            }
        }
        return true
    }

    /** Merge any segment with fewer than 2 cased letters into a neighbour (doc 09). Lossless. */
    private fun mergeShort(text: String, ranges: List<Span>): List<Span> {
        val merged = ArrayList<Span>()
        var curStart = -1
        var curEnd = -1
        for (r in ranges) {
            if (curStart < 0) curStart = r.start
            curEnd = r.end
            if (countCased(text, curStart, curEnd) >= 2) {
                merged.add(Span(curStart, curEnd)); curStart = -1; curEnd = -1
            }
        }
        if (curStart >= 0) {
            if (merged.isNotEmpty()) {
                val last = merged.removeAt(merged.size - 1)
                merged.add(Span(last.start, curEnd))
            } else {
                merged.add(Span(curStart, curEnd))
            }
        }
        return merged
    }

    private fun countCased(text: String, start: Int, end: Int): Int {
        var n = 0
        var t = start
        while (t < end) { if (isLetter(text[t])) { n++; if (n >= 2) return n }; t++ }
        return n
    }

    // ---------------------------------------------------------------- Length chunking

    /** Subdivide any range longer than [maxChars] at a clause boundary near the middle (doc 09). */
    fun chunk(text: String, span: Span, maxChars: Int): List<Span> {
        if (span.length <= maxChars) return listOf(span)
        val cut = findCut(text, span, maxChars)
        return chunk(text, Span(span.start, cut), maxChars) + chunk(text, Span(cut, span.end), maxChars)
    }

    private fun findCut(text: String, span: Span, maxChars: Int): Int {
        val mid = (span.start + span.end) / 2
        fun closest(cands: List<Int>): Int? = cands.filter { it > span.start && it < span.end }
            .minByOrNull { kotlin.math.abs(it - mid) }

        closest(punctuationCuts(text, span))?.let { return it }
        closest(conjunctionCuts(text, span))?.let { return it }
        closest(wordCuts(text, span))?.let { return it }
        // Hard cut — only when there is no usable boundary at all.
        return span.start + maxChars
    }

    private fun punctuationCuts(text: String, span: Span): List<Int> {
        val out = ArrayList<Int>()
        var q = span.start
        while (q < span.end) {
            val c = text[q]
            val isCutChar = c == ',' || c == ';' || c == ':' || c == '—' || c == '–' ||
                (c == '-' && q > span.start && isWs(text[q - 1]))
            if (isCutChar && q + 1 < span.end && isWs(text[q + 1])) {
                var p = q + 1
                while (p < span.end && isWs(text[p])) p++
                if (p < span.end) out.add(p)
            }
            q++
        }
        return out
    }

    private fun conjunctionCuts(text: String, span: Span): List<Int> {
        val out = ArrayList<Int>()
        for (conj in CONJUNCTIONS) {
            var from = span.start
            while (true) {
                val m = indexOf(text, conj, from, span.end)
                if (m < 0) break
                val before = if (m == span.start) ' ' else text[m - 1]
                val afterIdx = m + conj.length
                val after = if (afterIdx >= span.end) ' ' else text[afterIdx]
                if (!isLetter(before) && !isLetter(after)) out.add(m)
                from = m + 1
            }
        }
        return out
    }

    private fun wordCuts(text: String, span: Span): List<Int> {
        val out = ArrayList<Int>()
        var p = span.start + 1
        while (p < span.end) {
            if (isWs(text[p - 1]) && !isWs(text[p])) out.add(p)
            p++
        }
        return out
    }

    private fun indexOf(text: String, sub: String, from: Int, end: Int): Int {
        val limit = end - sub.length
        var i = from
        while (i <= limit) {
            var k = 0
            while (k < sub.length && text[i + k].equals(sub[k], ignoreCase = true)) k++
            if (k == sub.length) return i
            i++
        }
        return -1
    }

    // ---------------------------------------------------------------- Mapping & normalization

    /** Index of the range containing [offset]; a tap past the end clamps to the last (doc 09). */
    fun offsetToIndex(spans: List<Span>, offset: Int): Int {
        if (offset < 0 || spans.isEmpty()) return -1
        for ((idx, s) in spans.withIndex()) if (offset >= s.start && offset < s.end) return idx
        if (offset >= spans.last().end) return spans.size - 1
        return -1
    }

    /**
     * What is actually sent for a segment (doc 09): replace `U+FFFC` with a space, collapse every
     * whitespace run — including the soft line breaks of a wrapped paragraph — to a single space,
     * and trim. This is also the cache key, so the same sentence always produces the same string.
     */
    fun normalize(text: String): String {
        val sb = StringBuilder(text.length)
        var prevWs = false
        for (raw in text) {
            val c = if (raw == '￼') ' ' else raw
            if (isWs(c)) {
                if (!prevWs) sb.append(' ')
                prevWs = true
            } else {
                sb.append(c); prevWs = false
            }
        }
        return sb.toString().trim()
    }

    /**
     * The tap → segments step (doc 08). Produces the segments to report (never empty) and the
     * index of the tapped one (always inside the list). [tapOffset] is the character index under
     * the finger, or null for paths without a position.
     *
     * Returns null when there is nothing to translate (the text normalizes to empty).
     */
    fun plan(text: String, granularity: Granularity, maxChars: Int, tapOffset: Int?): Plan? {
        if (normalize(text).isEmpty()) return null

        if (granularity == Granularity.PARAGRAPH) {
            return build(text, chunk(text, Span(0, text.length), maxChars), 0)
                ?: paragraphFallback(text, maxChars)
        }

        val spans = splitSentences(text).flatMap { chunk(text, it, maxChars) }
        val idx = if (tapOffset != null) offsetToIndex(spans, tapOffset) else 0
        // Fallback: an unmappable touch position translates the whole paragraph as one segment.
        if (idx < 0) return paragraphFallback(text, maxChars)
        return build(text, spans, idx) ?: paragraphFallback(text, maxChars)
    }

    private fun paragraphFallback(text: String, maxChars: Int): Plan? =
        build(text, chunk(text, Span(0, text.length), maxChars), 0)

    /** Normalize each span, drop empties, and keep the tapped index pointing at its sentence. */
    private fun build(text: String, spans: List<Span>, tappedIndex: Int): Plan? {
        val out = ArrayList<String>()
        var newIdx = tappedIndex
        for ((i, s) in spans.withIndex()) {
            val norm = normalize(text.substring(s.start, s.end))
            if (norm.isEmpty()) { if (i < tappedIndex) newIdx--; continue }
            out.add(norm)
        }
        if (out.isEmpty()) return null
        return Plan(out, newIdx.coerceIn(0, out.size - 1))
    }
}
