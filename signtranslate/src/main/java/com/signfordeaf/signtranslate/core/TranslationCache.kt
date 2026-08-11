// signtranslate/src/main/java/com/signfordeaf/signtranslate/core/TranslationCache.kt

package com.signfordeaf.signtranslate.core

/**
 * Session-only cache of resolved translations (doc 02). Keyed by the exact (normalized) segment
 * text; value is the video URL and translation id. Bounded at [MAX_ENTRIES], evicting the oldest
 * insertion first; re-inserting an existing key moves it to the newest position.
 *
 * A cache hit MUST NOT re-request — stepping back and forth through the sentences of a paragraph,
 * or tapping the same text twice, is free. The cache does not survive an app launch (doc 14).
 */
class TranslationCache(private val maxEntries: Int = MAX_ENTRIES) {

    data class Entry(val videoUrl: String, val cid: String?)

    // accessOrder=false → iteration/eviction order is *insertion* order (doc 02: oldest insertion
    // first). We move a re-inserted key to newest by removing then putting in [put].
    private val map = object : LinkedHashMap<String, Entry>(16, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean =
            size > maxEntries
    }

    @Synchronized
    fun get(key: String): Entry? = map[key]

    @Synchronized
    fun contains(key: String): Boolean = map.containsKey(key)

    @Synchronized
    fun put(key: String, entry: Entry) {
        map.remove(key)     // move an existing key to the newest position
        map[key] = entry
    }

    @Synchronized
    fun clear() = map.clear()

    @get:Synchronized
    val size: Int get() = map.size

    companion object {
        const val MAX_ENTRIES = 40
    }
}
