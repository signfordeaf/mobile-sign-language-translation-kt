// signtranslate/src/main/java/com/signfordeaf/signtranslate/network/SignModel.kt

package com.signfordeaf.signtranslate.network

import org.json.JSONObject

/**
 * The `/Translate` response (doc 03).
 *
 * `tid`/`fdid` are an **optional override** — present only when the backend served the
 * translation under a different pair than requested. They MUST be read leniently: the API is not
 * consistent about quoting them, so both `"44"` and `44` parse to the same value. The SDK keeps
 * ids as strings everywhere.
 */
data class SignModel(
    val state: Boolean?,
    val baseUrl: String?,
    val name: String?,
    val cid: String?,
    val st: Boolean?,
    val tid: String?,
    val fdid: String?
) {
    /** `baseUrl + name`, with the first `http:` rewritten to `https:` (doc 03). Null if incomplete. */
    val videoUrl: String?
        get() {
            if (baseUrl.isNullOrEmpty() || name.isNullOrEmpty()) return null
            return (baseUrl + name).replaceFirst("http:", "https:")
        }

    companion object {
        fun fromJson(json: String): SignModel {
            val o = JSONObject(json)
            return SignModel(
                state = lenientBool(o, "state"),
                baseUrl = optStringOrNull(o, "baseUrl"),
                name = optStringOrNull(o, "name"),
                cid = optStringOrNull(o, "cid"),
                st = lenientBool(o, "st"),
                tid = lenientId(o, "tid"),
                fdid = lenientId(o, "fdid")
            )
        }

        private fun optStringOrNull(o: JSONObject, key: String): String? =
            if (o.has(key) && !o.isNull(key)) o.getString(key) else null

        /** Accepts `true`/`false`, `"true"`/`"false"`, and `1`/`0`. */
        private fun lenientBool(o: JSONObject, key: String): Boolean? {
            if (!o.has(key) || o.isNull(key)) return null
            return when (val v = o.get(key)) {
                is Boolean -> v
                is Number -> v.toInt() != 0
                is String -> when (v.trim().lowercase()) {
                    "true", "1" -> true
                    "false", "0" -> false
                    else -> null
                }
                else -> null
            }
        }

        /** Reads an id whether the API quotes it (`"44"`) or not (`44`); empty/absent → null. */
        private fun lenientId(o: JSONObject, key: String): String? {
            if (!o.has(key) || o.isNull(key)) return null
            val s = when (val v = o.get(key)) {
                is Number -> {
                    // Drop a trailing ".0" the JSON layer may attach to an integer id.
                    val d = v.toDouble()
                    if (d == d.toLong().toDouble()) d.toLong().toString() else v.toString()
                }
                else -> v.toString()
            }
            return s.trim().ifEmpty { null }
        }
    }
}
