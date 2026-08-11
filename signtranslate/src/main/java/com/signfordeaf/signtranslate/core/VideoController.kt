// signtranslate/src/main/java/com/signfordeaf/signtranslate/core/VideoController.kt

package com.signfordeaf.signtranslate.core

/**
 * The controller initialises playback through this seam (doc 02, step 9), so the state machine
 * stays free of Android/ExoPlayer types and can be unit-tested with a fake.
 *
 * [prepare] disposes the previous video, applies the stored loop and speed, starts playback and
 * returns whether it succeeded — a failure here is an `error` (`videoError`). It is a suspend
 * function because a real implementation waits for the decoder to reach a playable state.
 */
interface VideoController {
    suspend fun prepare(videoUrl: String, speed: Float, looping: Boolean): Boolean
    fun setSpeed(speed: Float)
    fun setLooping(looping: Boolean)
    fun release()

    /** A no-op used by tests and before a real player is attached. */
    object Noop : VideoController {
        override suspend fun prepare(videoUrl: String, speed: Float, looping: Boolean) = true
        override fun setSpeed(speed: Float) {}
        override fun setLooping(looping: Boolean) {}
        override fun release() {}
    }
}
