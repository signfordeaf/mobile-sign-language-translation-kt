// signtranslate/src/main/java/com/signfordeaf/signtranslate/network/ApiService.kt

package com.signfordeaf.signtranslate.network

import com.signfordeaf.signtranslate.Language
import com.signfordeaf.signtranslate.SignLanguageConfig
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiService(private val config: SignLanguageConfig) {

    companion object {
        private const val RETRY_DELAY_MS = 1000L
        private const val MAX_RETRIES = 30
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private var currentCall: Call? = null
    private var retryCount = 0

    fun getSignVideo(text: String, callback: (result: SignModel?, error: Throwable?) -> Unit) {
        retryCount = 0
        makeRequest(text, callback)
    }

    private fun makeRequest(text: String, callback: (result: SignModel?, error: Throwable?) -> Unit) {
        val apiUrl = "${config.apiUrl}/Translate"

        // Language code mapping: tr -> 1, en -> 2, etc.
        val languageCode = when (config.language) {
            Language.TURKISH -> "1"
            Language.ENGLISH -> "2"
            Language.ARABIC -> "6"
        }

        val urlWithParams = apiUrl.toHttpUrlOrNull()?.newBuilder()?.apply {
            addQueryParameter("s", text)
            addQueryParameter("url", config.apiUrl)
            addQueryParameter("rk", config.apiKey)
            addQueryParameter("fdid", config.fdid ?: "16")
            addQueryParameter("tid", config.tid ?: "23")
            addQueryParameter("language", languageCode)
        }?.build()

        if (urlWithParams == null) {
            callback(null, IOException("Invalid URL"))
            return
        }

        val request = Request.Builder()
            .url(urlWithParams)
            .get()
            .addHeader("Accept", "application/json")
            .addHeader("Origin", config.apiUrl)
            .build()

        currentCall = client.newCall(request)

        currentCall?.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (call.isCanceled()) {
                    callback(null, null)
                } else {
                    callback(null, e)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (call.isCanceled()) {
                    callback(null, null)
                    return
                }

                if (!response.isSuccessful) {
                    callback(null, IOException("Unexpected response code: ${response.code}"))
                    return
                }

                try {
                    val responseBody = response.body?.string() ?: "{}"
                    val signModel = SignModel.fromJson(responseBody)

                    if (signModel.state == true) {
                        callback(signModel, null)
                    } else {
                        // Retry if not ready
                        if (retryCount < MAX_RETRIES) {
                            retryCount++
                            Thread.sleep(RETRY_DELAY_MS)
                            makeRequest(text, callback)
                        } else {
                            callback(null, IOException("Translation timeout"))
                        }
                    }
                } catch (e: Exception) {
                    callback(null, e)
                }
            }
        })
    }

    fun cancelRequest() {
        currentCall?.cancel()
        currentCall = null
        retryCount = MAX_RETRIES // Stop retries
    }
}
