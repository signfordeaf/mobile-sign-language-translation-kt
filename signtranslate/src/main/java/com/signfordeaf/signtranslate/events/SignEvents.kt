// signtranslate/src/main/java/com/signfordeaf/signtranslate/events/SignEvents.kt

package com.signfordeaf.signtranslate.events

/**
 * The SDK's lifecycle event stream (doc 12). Events are **observational**: nothing in the SDK
 * depends on a host consuming them.
 *
 * Ordering guarantees: every translation emits exactly one terminal event
 * ([SignEventType.TRANSLATION_COMPLETE], [SignEventType.TRANSLATION_ERROR] or
 * [SignEventType.BLOCKED_SENSITIVE]); a superseded translation emits nothing further; prefetches
 * emit nothing at all.
 */
enum class SignEventType {
    BLOCKED_SENSITIVE,
    TEXT_SELECTED,
    TRANSLATION_START,
    TRANSLATION_ERROR,
    PANEL_OPEN,
    VIDEO_START,
    TRANSLATION_COMPLETE,
    VIDEO_END,
    SEGMENT_CHANGED,
    PLAYBACK_SPEED_CHANGED,
    CARD_COLLAPSED,
    FEEDBACK_SENT,
    CONTACT_REQUESTED,
    PANEL_CLOSE
}

/** Error codes carried on [SignEventType.TRANSLATION_ERROR] (doc 12). */
enum class SignErrorCode {
    NETWORK_ERROR,   // the request threw — transport failure, timeout
    API_ERROR,       // the response arrived without a usable video, incl. after polling exhausted
    VIDEO_ERROR,     // the video URL could not be initialised or played
    CONFIGURATION_ERROR, // reserved
    CANCELLED,       // the request was cancelled by the user or superseded
    UNKNOWN          // reserved
}

/**
 * A single lifecycle event. [value] is free-form for the v2 events (new segment index, new speed,
 * collapsed flag, positive-vote flag). The [error] message is for logs only and MUST NOT be shown
 * to the user — the player shows the localized generic failure string.
 */
data class SignEvent(
    val type: SignEventType,
    val text: String? = null,
    val videoUrl: String? = null,
    val errorCode: SignErrorCode? = null,
    val errorMessage: String? = null,
    val value: Any? = null,
    val cid: String? = null,
    val timestampMs: Long = System.currentTimeMillis()
)
