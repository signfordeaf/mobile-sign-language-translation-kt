package com.signfordeaf.signtranslate

import com.signfordeaf.signtranslate.core.SignController
import com.signfordeaf.signtranslate.core.SignState
import com.signfordeaf.signtranslate.core.VideoController
import com.signfordeaf.signtranslate.events.SignEvent
import com.signfordeaf.signtranslate.events.SignEventType
import com.signfordeaf.signtranslate.network.TranslateResult
import com.signfordeaf.signtranslate.network.Translator
import com.signfordeaf.signtranslate.storage.MemoryStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SignControllerTest {

    private fun config() = SignLanguageConfig(apiKey = "k", apiUrl = "https://example.com")

    /** Fake backend: returns a preset result per segment and records every call's (segment,tid,fdid). */
    private class FakeTranslator(
        private val results: MutableMap<String, TranslateResult> = mutableMapOf(),
        private val default: (String) -> TranslateResult = { seg ->
            TranslateResult.Success("https://v/$seg.mp4", cid = "cid-$seg", tid = null, fdid = null)
        }
    ) : Translator {
        val calls = mutableListOf<Triple<String, String?, String?>>()
        fun on(segment: String, result: TranslateResult) { results[segment] = result }
        override suspend fun translate(segment: String, tid: String?, fdid: String?): TranslateResult {
            calls.add(Triple(segment, tid, fdid))
            return results[segment] ?: default(segment)
        }
    }

    private class FailingVideo : VideoController {
        override suspend fun prepare(videoUrl: String, speed: Float, looping: Boolean) = false
        override fun setSpeed(speed: Float) {}
        override fun setLooping(looping: Boolean) {}
        override fun release() {}
    }

    @Test
    fun `sensitive text is blocked and no request is made`() = runTest {
        val fake = FakeTranslator()
        val c = SignController(config(), fake, MemoryStorage(), scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)))
        val events = collect(c)

        c.submit(listOf("iletisim: ali@example.com"), 0)
        advanceUntilIdle()

        assertEquals(SignState.BLOCKED, c.state.value.state)
        assertTrue(fake.calls.isEmpty())
        assertTrue(events.any { it.type == SignEventType.BLOCKED_SENSITIVE })
        assertTrue(events.none { it.type == SignEventType.TRANSLATION_START })
    }

    @Test
    fun `successful translation reaches ready and emits the terminal complete event`() = runTest {
        val fake = FakeTranslator()
        val c = SignController(config(), fake, MemoryStorage(), scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)))
        val events = collect(c)

        c.submit(listOf("Merhaba dünya"), 0)
        advanceUntilIdle()

        assertEquals(SignState.READY, c.state.value.state)
        assertEquals("https://v/Merhaba dünya.mp4", c.state.value.videoUrl)
        val types = events.map { it.type }
        assertTrue(types.contains(SignEventType.TEXT_SELECTED))
        assertTrue(types.contains(SignEventType.TRANSLATION_START))
        assertTrue(types.contains(SignEventType.TRANSLATION_COMPLETE))
    }

    @Test
    fun `the same sentence twice does not hit the backend twice`() = runTest {
        val fake = FakeTranslator()
        val c = SignController(config(), fake, MemoryStorage(), scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)))

        c.submit(listOf("Tekrar"), 0)
        advanceUntilIdle()
        c.submit(listOf("Tekrar"), 0)
        advanceUntilIdle()

        assertEquals(1, fake.calls.count { it.first == "Tekrar" })
    }

    @Test
    fun `the next sentence is prefetched and then served from cache`() = runTest {
        val fake = FakeTranslator()
        val c = SignController(config(), fake, MemoryStorage(), scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)))

        c.submit(listOf("bir", "iki"), 0)
        advanceUntilIdle()
        // "iki" was prefetched exactly once.
        assertEquals(1, fake.calls.count { it.first == "iki" })

        c.selectSegment(1)
        advanceUntilIdle()
        // Still one — the second view was a cache hit.
        assertEquals(1, fake.calls.count { it.first == "iki" })
        assertEquals(SignState.READY, c.state.value.state)
    }

    @Test
    fun `no prefetch on the last sentence or for sensitive next text`() = runTest {
        val fake = FakeTranslator()
        val c = SignController(config(), fake, MemoryStorage(), scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)))

        c.submit(listOf("son"), 0) // last sentence: nothing to prefetch
        advanceUntilIdle()
        assertEquals(1, fake.calls.size)

        val fake2 = FakeTranslator()
        val c2 = SignController(config(), fake2, MemoryStorage(), scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)))
        c2.submit(listOf("normal", "0532 123 45 67"), 0) // next is sensitive: skip prefetch
        advanceUntilIdle()
        assertTrue(fake2.calls.none { it.first == "0532 123 45 67" })
    }

    @Test
    fun `a different translator served by the backend is adopted and switches the signer`() = runTest {
        // The account starts unpinned (Hesna default); the backend serves the translation under
        // Jason (tid 44 / fdid 36). The controller must adopt it for the state and next request.
        val fake = FakeTranslator()
        fake.on("first", TranslateResult.Success("https://v/first.mp4", "c1", tid = "44", fdid = "36"))
        val c = SignController(config(), fake, MemoryStorage(), scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)))

        // Before any response the placeholder resolves to the fallback (Hesna).
        assertEquals(
            com.signfordeaf.signtranslate.core.Signer.HESNA,
            com.signfordeaf.signtranslate.core.SignerResolver.resolve(null, c.state.value.tid, c.state.value.fdid)
        )

        c.submit(listOf("first"), 0)
        advanceUntilIdle()

        // The served ids are now in effect, resolving the idle signer to Jason.
        assertEquals("44", c.state.value.tid)
        assertEquals("36", c.state.value.fdid)
        assertEquals(
            com.signfordeaf.signtranslate.core.Signer.JASON,
            com.signfordeaf.signtranslate.core.SignerResolver.resolve(null, c.state.value.tid, c.state.value.fdid)
        )

        // And they go out on the next request.
        c.submit(listOf("second"), 0)
        advanceUntilIdle()
        val secondCall = fake.calls.first { it.first == "second" }
        assertEquals("44", secondCall.second)
        assertEquals("36", secondCall.third)
    }

    @Test
    fun `a video init failure becomes an error`() = runTest {
        val fake = FakeTranslator()
        val c = SignController(config(), fake, MemoryStorage(), video = FailingVideo(), scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)))
        val events = collect(c)

        c.submit(listOf("Merhaba"), 0)
        advanceUntilIdle()

        assertEquals(SignState.ERROR, c.state.value.state)
        assertTrue(events.any { it.type == SignEventType.TRANSLATION_ERROR })
    }

    @Test
    fun `cancel returns to idle and reports cancelled`() = runTest {
        // A translator that suspends forever, so the request is genuinely in flight when cancelled.
        val hanging = object : Translator {
            override suspend fun translate(segment: String, tid: String?, fdid: String?): TranslateResult {
                kotlinx.coroutines.awaitCancellation()
            }
        }
        val c = SignController(config(), hanging, MemoryStorage(), scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)))
        val events = collect(c)

        c.submit(listOf("Merhaba"), 0)
        advanceUntilIdle()
        assertEquals(SignState.LOADING, c.state.value.state)

        c.cancel()
        advanceUntilIdle()
        assertEquals(SignState.IDLE, c.state.value.state)
        assertTrue(events.any {
            it.type == SignEventType.TRANSLATION_ERROR &&
                it.errorCode == com.signfordeaf.signtranslate.events.SignErrorCode.CANCELLED
        })
    }

    @Test
    fun `stored speed is restored and not overridden by a same-session change`() = runTest {
        val store = MemoryStorage()
        store.setItem(com.signfordeaf.signtranslate.storage.SignStorage.KEY_PLAYBACK_SPEED, "1.5")
        val c = SignController(config(), FakeTranslator(), store, scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)))

        c.submit(listOf("Merhaba"), 0)
        advanceUntilIdle()
        assertEquals(1.5f, c.state.value.speed)

        c.setSpeed(2.0f) // user changes it this session
        c.submit(listOf("Başka"), 0)
        advanceUntilIdle()
        assertEquals(2.0f, c.state.value.speed) // restore must not clobber the user's choice
    }

    /**
     * Collect events into a list. Uses an unconfined dispatcher so the collector subscribes
     * eagerly — before the synchronous emits in `submit` — instead of only when the scheduler runs.
     */
    private fun kotlinx.coroutines.test.TestScope.collect(c: SignController): List<SignEvent> {
        val out = mutableListOf<SignEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { c.events.collect { out.add(it) } }
        return out
    }
}
