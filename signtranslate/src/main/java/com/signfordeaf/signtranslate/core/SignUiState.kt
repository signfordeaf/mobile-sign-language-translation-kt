// signtranslate/src/main/java/com/signfordeaf/signtranslate/core/SignUiState.kt

package com.signfordeaf.signtranslate.core

import com.signfordeaf.signtranslate.events.SignErrorCode

/** The five translation states (doc 02). Exactly one is active at any time. */
enum class SignState { IDLE, LOADING, READY, ERROR, BLOCKED }

/**
 * The single source of truth the views render from (doc 02). Views hold no translation state of
 * their own. Derived flags ([playerVisible], [playbackAvailable]) are **computed, not stored**.
 */
data class SignUiState(
    val state: SignState = SignState.IDLE,
    /** The sentence being translated — shown as the caption from the moment a translation starts. */
    val caption: String = "",
    val videoUrl: String? = null,
    val cid: String? = null,
    val errorCode: SignErrorCode? = null,
    /** All sentences of the tapped paragraph, so the host can step through without re-reading. */
    val segments: List<String> = emptyList(),
    val segmentIndex: Int = 0,
    val speed: Float = 1.0f,
    val looping: Boolean = true,
    /** Whether the user collapsed the player to its control bar. */
    val collapsed: Boolean = false,
    /** Whether the user opened the player (via the button or expand). */
    val openedByUser: Boolean = false,
    /** Resolved signer ids currently in effect (drive the idle loop). */
    val tid: String? = null,
    val fdid: String? = null,
    /** 👍/👎 vote for the current translation: null = none, true = positive, false = negative. */
    val feedbackVote: Boolean? = null,
    val feedbackAck: Boolean = false
) {
    /** Player visible = opened by the user OR the state is not idle (doc 02). */
    val playerVisible: Boolean get() = openedByUser || state != SignState.IDLE

    /** Playback available = state is ready (doc 02). */
    val playbackAvailable: Boolean get() = state == SignState.READY
}
