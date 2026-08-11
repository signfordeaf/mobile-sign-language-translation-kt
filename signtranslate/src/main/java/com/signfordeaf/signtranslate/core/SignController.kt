// signtranslate/src/main/java/com/signfordeaf/signtranslate/core/SignController.kt

package com.signfordeaf.signtranslate.core

import com.signfordeaf.signtranslate.SignLanguageConfig
import com.signfordeaf.signtranslate.events.SignErrorCode
import com.signfordeaf.signtranslate.events.SignEvent
import com.signfordeaf.signtranslate.events.SignEventType
import com.signfordeaf.signtranslate.network.TranslateResult
import com.signfordeaf.signtranslate.network.Translator
import com.signfordeaf.signtranslate.sensitive.SensitiveDataGuard
import com.signfordeaf.signtranslate.storage.SignStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/**
 * The single source of truth: all state and every action (doc 02). Views call actions, the
 * controller mutates [state] and emits [events], views re-render from that state.
 *
 * The [requestToken] is the mechanism that stops a slow older response from overwriting a newer
 * translation: every step re-checks it and aborts silently if it no longer matches.
 */
class SignController(
    private var config: SignLanguageConfig,
    private val translator: Translator,
    private val storage: SignStorage,
    video: VideoController = VideoController.Noop,
    private val isSensitive: (String) -> Boolean = SensitiveDataGuard::isSensitive,
    private val scope: CoroutineScope = MainScope()
) {

    // The player is per-Activity while the controller is process-global, so the video seam is
    // reassigned as overlays attach/detach. Defaults to a no-op before any player is attached.
    @Volatile private var video: VideoController = video

    /** Attach the current Activity's player as the playback target. */
    fun setVideoController(controller: VideoController) { video = controller }

    /** Detach on Activity teardown, reverting to the no-op. */
    fun clearVideoController(current: VideoController) {
        if (video === current) video = VideoController.Noop
    }

    private val _state = MutableStateFlow(
        SignUiState(
            speed = config.card.defaultSpeed,
            looping = config.card.defaultLooping,
            tid = config.tid,
            fdid = config.fdid
        )
    )
    val state: StateFlow<SignUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<SignEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<SignEvent> = _events.asSharedFlow()

    private val requestToken = AtomicInteger(0)
    private var currentJob: Job? = null
    private val cache = TranslationCache()
    private val prefetching = Collections.synchronizedSet(HashSet<String>())
    private var prefsRestored = false

    // Ids currently in effect (may be adopted from a backend response, doc 10).
    @Volatile private var currentTid: String? = config.tid
    @Volatile private var currentFdid: String? = config.fdid

    // ---------------------------------------------------------------- Public actions

    /** Open the player without translating anything (button tap / expand). Shows the idle loop. */
    fun openCard() {
        _state.value = _state.value.copy(openedByUser = true, collapsed = false)
    }

    /** Translate a paragraph's [segments], starting at [index] (from the tap plan). */
    fun submit(segments: List<String>, index: Int, openedByUser: Boolean = true) {
        if (segments.isEmpty()) return
        val i = index.coerceIn(0, segments.size - 1)
        _state.value = _state.value.copy(
            segments = segments,
            segmentIndex = i,
            openedByUser = _state.value.openedByUser || openedByUser
        )
        translateSegment(i)
    }

    /** Programmatic single-text translation (no paragraph context). */
    fun translateText(text: String) = submit(listOf(text), 0, openedByUser = true)

    /** Move to another sentence of the current paragraph (host-driven; no on-screen nav in v2). */
    fun selectSegment(index: Int) {
        val segs = _state.value.segments
        if (index !in segs.indices) return
        emit(SignEvent(SignEventType.SEGMENT_CHANGED, text = segs[index], value = index))
        _state.value = _state.value.copy(segmentIndex = index)
        translateSegment(index)
    }

    fun nextSegment() = selectSegment(_state.value.segmentIndex + 1)
    fun previousSegment() = selectSegment(_state.value.segmentIndex - 1)

    fun setSpeed(speed: Float) {
        if (speed <= 0f) return
        storage.setItem(SignStorage.KEY_PLAYBACK_SPEED, speed.toString())
        video.setSpeed(speed)
        _state.value = _state.value.copy(speed = speed)
        emit(SignEvent(SignEventType.PLAYBACK_SPEED_CHANGED, text = _state.value.caption, value = speed))
    }

    /** Cycle to the next configured speed (doc 06). */
    fun cycleSpeed() {
        val speeds = config.card.speeds
        if (speeds.isEmpty()) return
        val idx = speeds.indexOfFirst { kotlin.math.abs(it - _state.value.speed) < 0.001f }
        setSpeed(speeds[(idx + 1).mod(speeds.size)])
    }

    fun setLooping(looping: Boolean) {
        storage.setItem(SignStorage.KEY_LOOPING, looping.toString())
        video.setLooping(looping)
        _state.value = _state.value.copy(looping = looping)
    }

    fun collapse() {
        if (_state.value.collapsed) return
        _state.value = _state.value.copy(collapsed = true)
        emit(SignEvent(SignEventType.CARD_COLLAPSED, text = _state.value.caption, value = true))
    }

    fun expand() {
        if (!_state.value.collapsed) return
        _state.value = _state.value.copy(collapsed = false)
        emit(SignEvent(SignEventType.CARD_COLLAPSED, text = _state.value.caption, value = false))
    }

    /** Close (✕): cancel anything in flight, release the video, clear segments (doc 02). */
    fun close() {
        val wasPlayable = _state.value.state == SignState.READY
        requestToken.incrementAndGet()
        currentJob?.cancel()
        currentJob = null
        video.release()
        _state.value = SignUiState(
            speed = _state.value.speed,
            looping = _state.value.looping,
            tid = currentTid,
            fdid = currentFdid
        )
        if (wasPlayable) emit(SignEvent(SignEventType.PANEL_CLOSE))
    }

    /** Cancel an in-flight translation without closing (returns to idle, distinct from failure). */
    fun cancel() {
        val wasLoading = _state.value.state == SignState.LOADING
        requestToken.incrementAndGet()
        currentJob?.cancel()
        currentJob = null
        _state.value = _state.value.copy(state = SignState.IDLE, errorCode = null)
        if (wasLoading) {
            emit(SignEvent(SignEventType.TRANSLATION_ERROR, errorCode = SignErrorCode.CANCELLED))
        }
    }

    /** Clear an error/blocked state back to idle (dismiss). */
    fun clearError() {
        if (_state.value.state == SignState.ERROR || _state.value.state == SignState.BLOCKED) {
            _state.value = _state.value.copy(state = SignState.IDLE, errorCode = null)
        }
    }

    fun dispose() {
        requestToken.incrementAndGet()
        currentJob?.cancel()
        video.release()
    }

    // ---------------------------------------------------------------- Translation flow

    private fun translateSegment(index: Int) {
        val segment = _state.value.segments.getOrNull(index) ?: return
        val token = requestToken.incrementAndGet()

        restorePrefsOnce()

        // 4. Sensitive check — nothing is sent.
        if (isSensitive(segment)) {
            _state.value = _state.value.copy(
                state = SignState.BLOCKED, caption = segment, segmentIndex = index,
                cid = null, videoUrl = null, errorCode = null, feedbackVote = null, feedbackAck = false
            )
            emit(SignEvent(SignEventType.BLOCKED_SENSITIVE, text = segment))
            return
        }

        // 5. textSelected — including on a cache hit.
        emit(SignEvent(SignEventType.TEXT_SELECTED, text = segment))

        val cached = cache.get(segment)
        // 6/7. Go to loading (caption shows immediately, before the video).
        _state.value = _state.value.copy(
            state = SignState.LOADING, caption = segment, segmentIndex = index,
            cid = cached?.cid, videoUrl = cached?.videoUrl,
            errorCode = null, feedbackVote = null, feedbackAck = false
        )

        currentJob = scope.launch {
            if (cached != null) {
                // Cache hit: no request, no translationStart event.
                startPrefetch(index)
                initVideo(token, segment, cached.videoUrl, cached.cid)
                return@launch
            }

            emit(SignEvent(SignEventType.TRANSLATION_START, text = segment))
            when (val result = translator.translate(segment, currentTid, currentFdid)) {
                is TranslateResult.Failure -> {
                    if (token != requestToken.get()) return@launch
                    _state.value = _state.value.copy(state = SignState.ERROR, errorCode = result.code)
                    emit(
                        SignEvent(
                            SignEventType.TRANSLATION_ERROR, text = segment,
                            errorCode = result.code, errorMessage = result.message
                        )
                    )
                }
                is TranslateResult.Success -> {
                    if (token != requestToken.get()) return@launch
                    adoptIds(result.tid, result.fdid) // adopt before the failure check (doc 10)
                    cache.put(segment, TranslationCache.Entry(result.videoUrl, result.cid))
                    startPrefetch(index)
                    initVideo(token, segment, result.videoUrl, result.cid)
                }
            }
        }
    }

    /** 9. Initialise the video: apply loop+speed, play, go ready, emit the three open events. */
    private suspend fun initVideo(token: Int, segment: String, videoUrl: String, cid: String?) {
        val ok = video.prepare(videoUrl, _state.value.speed, _state.value.looping)
        if (token != requestToken.get()) return
        if (!ok) {
            _state.value = _state.value.copy(state = SignState.ERROR, errorCode = SignErrorCode.VIDEO_ERROR)
            emit(SignEvent(SignEventType.TRANSLATION_ERROR, text = segment, errorCode = SignErrorCode.VIDEO_ERROR))
            return
        }
        _state.value = _state.value.copy(state = SignState.READY, videoUrl = videoUrl, cid = cid, caption = segment)
        emit(SignEvent(SignEventType.PANEL_OPEN, text = segment))
        emit(SignEvent(SignEventType.VIDEO_START, text = segment, videoUrl = videoUrl, cid = cid))
        emit(SignEvent(SignEventType.TRANSLATION_COMPLETE, text = segment, videoUrl = videoUrl, cid = cid))
    }

    /** Prefetch exactly one sentence ahead — silent, skips cached/sensitive/in-flight (doc 02). */
    private fun startPrefetch(currentIndex: Int) {
        val next = _state.value.segments.getOrNull(currentIndex + 1) ?: return
        if (cache.contains(next) || prefetching.contains(next) || isSensitive(next)) return
        prefetching.add(next)
        scope.launch {
            try {
                // Foreground ids, but a prefetch MUST NOT adopt served ids.
                val r = translator.translate(next, currentTid, currentFdid)
                if (r is TranslateResult.Success) {
                    cache.put(next, TranslationCache.Entry(r.videoUrl, r.cid))
                }
            } catch (_: Throwable) {
                // A prefetch failure is ignored — the sentence is simply fetched on demand later.
            } finally {
                prefetching.remove(next)
            }
        }
    }

    /** Adopt backend-served ids as the current pair (doc 10); absent/empty/unchanged do nothing. */
    private fun adoptIds(tid: String?, fdid: String?) {
        var changed = false
        if (!tid.isNullOrEmpty() && tid != currentTid) { currentTid = tid; changed = true }
        if (!fdid.isNullOrEmpty() && fdid != currentFdid) { currentFdid = fdid; changed = true }
        if (changed) _state.value = _state.value.copy(tid = currentTid, fdid = currentFdid)
    }

    /** Restore stored speed/loop once per session, never overriding a same-session change. */
    private fun restorePrefsOnce() {
        if (prefsRestored) return
        prefsRestored = true
        val speed = storage.getItem(SignStorage.KEY_PLAYBACK_SPEED)?.toFloatOrNull()?.takeIf { it > 0f }
            ?: config.card.defaultSpeed
        val looping = storage.getItem(SignStorage.KEY_LOOPING)?.let { it == "true" } ?: config.card.defaultLooping
        _state.value = _state.value.copy(speed = speed, looping = looping)
    }

    private fun emit(event: SignEvent) {
        _events.tryEmit(event)
    }

    /** Replace the resolved config at runtime (setters take effect from the next read). */
    fun updateConfig(newConfig: SignLanguageConfig) {
        config = newConfig
    }
}
