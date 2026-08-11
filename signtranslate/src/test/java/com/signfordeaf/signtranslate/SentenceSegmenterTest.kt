package com.signfordeaf.signtranslate

import com.signfordeaf.signtranslate.text.SentenceSegmenter
import com.signfordeaf.signtranslate.text.SentenceSegmenter.Span
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mirrors the Flutter package's `sentence_splitter_test.dart` — doc 15 section D. The single
 * most important property is losslessness (the ranges partition the input exactly).
 */
class SentenceSegmenterTest {

    private fun reconstruct(text: String, spans: List<Span>): String =
        spans.joinToString("") { text.substring(it.start, it.end) }

    private fun assertLossless(text: String, spans: List<Span>) {
        // Contiguous, ordered, covering [0, length).
        if (text.isEmpty()) {
            assertTrue(spans.isEmpty()); return
        }
        assertEquals(0, spans.first().start)
        assertEquals(text.length, spans.last().end)
        for (i in 1 until spans.size) assertEquals(spans[i - 1].end, spans[i].start)
        assertEquals(text, reconstruct(text, spans))
    }

    @Test
    fun `partition is lossless over varied input`() {
        val samples = listOf(
            "Bugün okula gittim. Yarın gelir.",
            "T.C. Kimlik numarası 12345678901 şeklindedir. Doğru mu?",
            "Toplam 5.000.000 TL ödendi. Teşekkürler!",
            "www.ziraatbank.com.tr adresine gidin. Sonra giriş yapın.",
            "Satır bir\nSatır iki\nSatır üç",
            "Merhaba dünya",
            "   ",
            "Gerçekten mi? Evet! Kesinlikle…"
        )
        for (s in samples) assertLossless(s, SentenceSegmenter.splitSentences(s))
    }

    @Test
    fun `empty yields none, blank yields one`() {
        assertTrue(SentenceSegmenter.splitSentences("").isEmpty())
        assertEquals(1, SentenceSegmenter.splitSentences("   ").size)
    }

    @Test
    fun `no split inside abbreviations, numbers, domains, emails`() {
        assertEquals(1, SentenceSegmenter.splitSentences("T.C. Kimlik numarası budur.").size)
        assertEquals(1, SentenceSegmenter.splitSentences("A.Ş. bir şirkettir.").size)
        assertEquals(1, SentenceSegmenter.splitSentences("Toplam 5.000.000 TL ödendi.").size)
        assertEquals(1, SentenceSegmenter.splitSentences("Adres www.site.com.tr sayfasıdır.").size)
        assertEquals(1, SentenceSegmenter.splitSentences("Mail ali@example.com adresidir.").size)
        assertEquals(1, SentenceSegmenter.splitSentences("Lütfen Prof. Ahmet ile görüşün.").size)
    }

    @Test
    fun `no split when next word is lowercase`() {
        assertEquals(1, SentenceSegmenter.splitSentences("Geldi. sonra gitti.").size)
    }

    @Test
    fun `clause number stays with its sentence`() {
        val spans = SentenceSegmenter.splitSentences("7. Para yatırma işlemi. 8. Para çekme işlemi.")
        assertEquals(2, spans.size)
        assertTrue(text(spans, "7. Para yatırma işlemi. 8. Para çekme işlemi.", 0).startsWith("7."))
        assertTrue(text(spans, "7. Para yatırma işlemi. 8. Para çekme işlemi.", 1).trim().startsWith("8."))
    }

    private fun text(spans: List<Span>, src: String, i: Int) = src.substring(spans[i].start, spans[i].end)

    @Test
    fun `bang question and line break split`() {
        assertEquals(2, SentenceSegmenter.splitSentences("Gerçekten mi? Evet.").size)
        assertEquals(2, SentenceSegmenter.splitSentences("Satır bir\nSatır iki").size)
    }

    @Test
    fun `short fragments merge into a neighbour`() {
        val src = "A! B! Merhaba dünya güzel."
        val spans = SentenceSegmenter.splitSentences(src)
        assertEquals(2, spans.size)
        assertEquals("A! B! ", text(spans, src, 0))
        assertLossless(src, spans)
    }

    @Test
    fun `single sentence yields one range`() {
        assertEquals(1, SentenceSegmenter.splitSentences("Merhaba dünya.").size)
    }

    @Test
    fun `offset maps to its sentence and clamps past the end`() {
        val src = "Bir cümle. İki cümle."
        val spans = SentenceSegmenter.splitSentences(src)
        assertEquals(2, spans.size)
        assertEquals(0, SentenceSegmenter.offsetToIndex(spans, 2))
        assertEquals(1, SentenceSegmenter.offsetToIndex(spans, src.length - 2))
        assertEquals(1, SentenceSegmenter.offsetToIndex(spans, src.length + 5)) // past the end -> last
        assertEquals(-1, SentenceSegmenter.offsetToIndex(spans, -1))            // negative -> not found
    }

    @Test
    fun `text under the limit is never chunked`() {
        val src = "Bu cümle 900 karakterin çok altında."
        assertEquals(1, SentenceSegmenter.chunk(src, Span(0, src.length), 900).size)
    }

    @Test
    fun `over-limit text chunks losslessly and every chunk fits`() {
        val src = (1..60).joinToString(", ") { "kelime$it" } // long, comma-separated
        val chunks = SentenceSegmenter.chunk(src, Span(0, src.length), 40)
        assertLossless(src, chunks)
        assertTrue(chunks.all { it.length <= 40 })
        assertTrue(chunks.all { it.length > 0 })
    }

    @Test
    fun `comma is preferred over a conjunction`() {
        val src = "aaaa, bbbb ve cccc dddd eeee"
        val chunks = SentenceSegmenter.chunk(src, Span(0, src.length), 25)
        assertEquals("aaaa, ", src.substring(chunks[0].start, chunks[0].end))
    }

    @Test
    fun `a conjunction cut opens the next chunk`() {
        val src = "aaaa bbbb ve cccc dddd"
        val chunks = SentenceSegmenter.chunk(src, Span(0, src.length), 15)
        assertTrue(src.substring(chunks[1].start, chunks[1].end).startsWith("ve"))
    }

    @Test
    fun `pathological no-whitespace input is hard cut`() {
        val src = "a".repeat(50)
        val chunks = SentenceSegmenter.chunk(src, Span(0, src.length), 20)
        assertTrue(chunks.all { it.length <= 20 })
        assertEquals(src, chunks.joinToString("") { src.substring(it.start, it.end) })
    }

    @Test
    fun `normalization strips object replacement and collapses whitespace`() {
        assertEquals("a b c d", SentenceSegmenter.normalize("a￼b   c\nd"))
        assertEquals("hello world", SentenceSegmenter.normalize("  hello\t\n world  "))
    }

    @Test
    fun `plan reports all sentences and the tapped index`() {
        val src = "Bir cümle. İki cümle. Üç cümle."
        val plan = SentenceSegmenter.plan(src, Granularity.SENTENCE, 900, tapOffset = src.indexOf("İki"))
        requireNotNull(plan)
        assertEquals(3, plan.segments.size)
        assertEquals(1, plan.index)
    }

    @Test
    fun `plan returns null for empty text`() {
        assertNull(SentenceSegmenter.plan("   ", Granularity.SENTENCE, 900, 0))
    }
}
