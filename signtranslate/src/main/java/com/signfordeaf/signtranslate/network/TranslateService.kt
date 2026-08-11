// signtranslate/src/main/java/com/signfordeaf/signtranslate/network/TranslateService.kt

package com.signfordeaf.signtranslate.network

import com.signfordeaf.signtranslate.Language
import com.signfordeaf.signtranslate.SignLanguageConfig
import com.signfordeaf.signtranslate.events.SignErrorCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Coroutine-based `/Translate` client (doc 03). Cancellation is the coroutine's — cancelling the
 * job cancels the in-flight call — so there is no shared retry-counter hack.
 *
 * Polling: a `state: false` response means the video is still rendering; the same request is
 * repeated every [RETRY_DELAY_MS] up to [MAX_RETRIES]. On exhaustion the result is
 * [TranslateResult.Failure] with [SignErrorCode.API_ERROR] (no video available).
 */
class TranslateService(
    private val config: SignLanguageConfig,
    private val client: OkHttpClient = defaultClient()
) : Translator {

    /**
     * @param tid translator id currently in effect (may have been adopted from a prior response).
     * @param fdid dictionary id currently in effect. Fall back to the config defaults when null.
     */
    override suspend fun translate(
        segment: String,
        tid: String?,
        fdid: String?
    ): TranslateResult = withContext(Dispatchers.IO) {
        val languageCode = when (config.language) {
            Language.TURKISH -> "1"
            Language.ENGLISH -> "2"
            Language.ARABIC -> "6"
        }
        val origin = config.resolvedOrigin
        val url = "${config.apiUrl}/Translate".toHttpUrlOrNull()?.newBuilder()?.apply {
            addQueryParameter("s", segment)
            addQueryParameter("rk", config.apiKey)
            // tid/fdid are optional: when the integration pins neither, auto-fill the SDK's default
            // translator (Hesna). The backend may still serve a different pair, which the controller
            // adopts (doc 10).
            addQueryParameter("fdid", fdid ?: config.fdid ?: com.signfordeaf.signtranslate.core.SignerResolver.FALLBACK.fdid)
            addQueryParameter("tid", tid ?: config.tid ?: com.signfordeaf.signtranslate.core.SignerResolver.FALLBACK.tid)
            addQueryParameter("language", languageCode)
            addQueryParameter("url", origin)
        }?.build() ?: return@withContext TranslateResult.Failure(SignErrorCode.API_ERROR, "Invalid URL")

        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Accept", "application/json")
            .addHeader("Origin", origin)
            .build()

        var attempts = 0
        while (true) {
            coroutineContext.ensureActive()

            val response: Response = try {
                client.newCall(request).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                android.util.Log.w(TAG, "network error: ${e.message}")
                return@withContext TranslateResult.Failure(SignErrorCode.NETWORK_ERROR, e.message ?: "network error")
            }

            val code = response.code
            val body: String = response.use { r ->
                if (!r.isSuccessful) {
                    android.util.Log.w(TAG, "HTTP $code")
                    return@withContext TranslateResult.Failure(SignErrorCode.API_ERROR, "HTTP $code")
                }
                r.body?.string() ?: "{}"
            }

            val model = try {
                SignModel.fromJson(body)
            } catch (e: Exception) {
                android.util.Log.w(TAG, "parse error: ${e.message}")
                return@withContext TranslateResult.Failure(SignErrorCode.API_ERROR, e.message ?: "parse error")
            }

            if (model.state == true) {
                val videoUrl = model.videoUrl
                    ?: return@withContext TranslateResult.Failure(SignErrorCode.API_ERROR, "response without a video")
                return@withContext TranslateResult.Success(videoUrl, model.cid, model.tid, model.fdid)
            }

            // Still rendering — poll the same request.
            attempts++
            if (attempts >= MAX_RETRIES) {
                return@withContext TranslateResult.Failure(SignErrorCode.API_ERROR, "translation timed out")
            }
            delay(RETRY_DELAY_MS)
        }
        @Suppress("UNREACHABLE_CODE")
        TranslateResult.Failure(SignErrorCode.UNKNOWN, "unreachable")
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isCancelled) return
                cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                cont.resume(response)
            }
        })
        cont.invokeOnCancellation { runCatching { cancel() } }
    }

    companion object {
        private const val TAG = "SignForDeafSvc"
        const val RETRY_DELAY_MS = 1000L
        const val MAX_RETRIES = 30
        private const val TIMEOUT_SECONDS = 30L

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }
}
