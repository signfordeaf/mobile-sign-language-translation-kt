package com.signfordeaf.signtranslate

import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import java.io.IOException

class ApiService {

    private val client = OkHttpClient()
    private val retryDelayMillis = 1000L
    private var currentCall: Call? = null

    fun getSignVideo(text: String, callback: (result: String?, error: Throwable?) -> Unit) {
        val apiUrl = "${SignForDeafConfig.requestUrl}/Translate"
        val urlWithParams = apiUrl.toHttpUrlOrNull()?.newBuilder()?.apply {
            addQueryParameter("s", text)
            addQueryParameter("rk", SignForDeafConfig.requestKey)
            addQueryParameter("fdid", Constant.TranslateDictionary.turkish)
            addQueryParameter("tid", Constant.TranslatePerson.man)
            addQueryParameter("language", Constant.TranslateLanguage.TR)
        }?.build().toString()

        val request = Request.Builder()
            .url(urlWithParams)
            .get()
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
                if (!call.isCanceled()) {
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        val jsonResponse = JSONObject(responseBody)
                        val state = jsonResponse.optBoolean("state", false)
                        if (state) {
                            callback(responseBody, null)
                        } else {
                            // Retry after a delay
                            Thread.sleep(retryDelayMillis)
                            getSignVideo(text, callback)
                        }
                    } else {
                        callback(null, IOException("Unexpected code $response"))
                    }
                }
            }
        })
    }

    fun cancelRequest() {
        currentCall?.cancel()
        currentCall = null
    }
}


