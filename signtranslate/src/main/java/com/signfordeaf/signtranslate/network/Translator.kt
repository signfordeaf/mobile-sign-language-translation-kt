// signtranslate/src/main/java/com/signfordeaf/signtranslate/network/Translator.kt

package com.signfordeaf.signtranslate.network

import com.signfordeaf.signtranslate.events.SignErrorCode

/** Outcome of a single `/Translate` call. Cancellation surfaces as coroutine cancellation. */
sealed class TranslateResult {
    data class Success(
        val videoUrl: String,
        val cid: String?,
        val tid: String?,
        val fdid: String?
    ) : TranslateResult()

    data class Failure(val code: SignErrorCode, val message: String) : TranslateResult()
}

/**
 * The controller talks to the backend through this seam, so tests can drive the state machine
 * with a fake instead of real network.
 */
interface Translator {
    suspend fun translate(segment: String, tid: String?, fdid: String?): TranslateResult
}
