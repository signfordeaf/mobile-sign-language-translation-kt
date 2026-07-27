// signtranslate/src/main/java/com/signfordeaf/signtranslate/network/SignModel.kt

package com.signfordeaf.signtranslate.network

import org.json.JSONObject

data class SignModel(
    val state: Boolean?,
    val baseUrl: String?,
    val name: String?,
    val cid: String?,
    val st: Boolean?
) {
    companion object {
        fun fromJson(json: String): SignModel {
            val jsonObject = JSONObject(json)
            fun optStringOrNull(key: String): String? =
                if (jsonObject.has(key) && !jsonObject.isNull(key)) jsonObject.getString(key) else null
            return SignModel(
                state = if (jsonObject.has("state")) jsonObject.getBoolean("state") else null,
                baseUrl = optStringOrNull("baseUrl"),
                name = optStringOrNull("name"),
                cid = optStringOrNull("cid"),
                st = if (jsonObject.has("st")) jsonObject.getBoolean("st") else null
            )
        }
    }

    val videoUrl: String?
        get() {
            if (baseUrl == null || name == null) return null
            val url = "$baseUrl$name"
            return url.replace("http://", "https://")
        }
}
