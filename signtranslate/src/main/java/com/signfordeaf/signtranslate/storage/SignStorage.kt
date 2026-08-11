// signtranslate/src/main/java/com/signfordeaf/signtranslate/storage/SignStorage.kt

package com.signfordeaf.signtranslate.storage

import android.content.Context

/**
 * The SDK's tiny persistence seam (doc 14). Two operations, string-keyed and string-valued.
 *
 * Keeping this behind an interface lets tests pin down preference behavior with an in-memory
 * store and no platform underneath it — exactly what the Flutter package does.
 *
 * Keys are the short names ([KEY_HINT_SHOWN_COUNT], [KEY_PLAYBACK_SPEED], [KEY_LOOPING]); the
 * persistent implementation prepends [PREFIX] so the SDK can never collide with the host app's
 * own preferences, and a device migrating between SDK versions keeps its settings.
 */
interface SignStorage {
    fun getItem(key: String): String?
    fun setItem(key: String, value: String)

    companion object {
        /** Prepended to every key on disk. MUST stay stable across versions (doc 14). */
        const val PREFIX = "weaccess_sl_"

        const val KEY_HINT_SHOWN_COUNT = "hint_shown_count"
        const val KEY_PLAYBACK_SPEED = "playback_speed"
        const val KEY_LOOPING = "looping"
    }
}

/** In-memory store — the default, and what tests use. Lives for the current session only. */
class MemoryStorage : SignStorage {
    private val map = HashMap<String, String>()
    override fun getItem(key: String): String? = map[key]
    override fun setItem(key: String, value: String) { map[key] = value }
}

/**
 * SharedPreferences-backed store used by the integration layer in a real app. Survives launches.
 * Prefixes every key with [SignStorage.PREFIX].
 */
class PrefsStorage(context: Context) : SignStorage {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun getItem(key: String): String? = prefs.getString(SignStorage.PREFIX + key, null)

    override fun setItem(key: String, value: String) {
        prefs.edit().putString(SignStorage.PREFIX + key, value).apply()
    }

    private companion object {
        const val FILE = "weaccess_sl_prefs"
    }
}
