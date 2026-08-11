// signtranslate/src/main/java/com/signfordeaf/signtranslate/core/SignerResolver.kt

package com.signfordeaf.signtranslate.core

import com.signfordeaf.signtranslate.PlaceholderAvatar

/**
 * A bundled idle-loop signer (doc 10). Each is a specific person identified on the backend by a
 * `tid`/`fdid` pair, with a clip in `res/raw` named [rawName] (added later; resolved by name so
 * the module compiles before the files land).
 */
enum class Signer(val tid: String, val fdid: String, val rawName: String) {
    KADIR("23", "16", "placeholder_kadir"),
    HESNA("43", "35", "placeholder_hesna"),
    JASON("44", "36", "placeholder_jason"),
    OWAIS("37", "29", "placeholder_owais");

    companion object {
        fun of(avatar: PlaceholderAvatar): Signer = when (avatar) {
            PlaceholderAvatar.KADIR -> KADIR
            PlaceholderAvatar.HESNA -> HESNA
            PlaceholderAvatar.JASON -> JASON
            PlaceholderAvatar.OWAIS -> OWAIS
        }
    }
}

/**
 * Chooses which signer the idle loop shows (doc 10). The loop MUST follow the ids in use so the
 * idle person and the translated person are never two different people.
 *
 * Precedence: an explicitly pinned signer wins; then an exact `tid`+`fdid` pair; then `tid`
 * alone; then `fdid` alone; then a stand-in (Hesna), never a bare spinner. `tid` beats `fdid`
 * when they disagree, because `tid` *is* the translator. Empty strings count as absent.
 */
object SignerResolver {

    val FALLBACK = Signer.HESNA

    fun resolve(pinned: PlaceholderAvatar?, tid: String?, fdid: String?): Signer {
        if (pinned != null) return Signer.of(pinned)

        val t = tid?.takeIf { it.isNotEmpty() }
        val f = fdid?.takeIf { it.isNotEmpty() }

        if (t != null && f != null) {
            Signer.values().firstOrNull { it.tid == t && it.fdid == f }?.let { return it }
        }
        if (t != null) {
            Signer.values().firstOrNull { it.tid == t }?.let { return it }
        }
        if (f != null) {
            Signer.values().firstOrNull { it.fdid == f }?.let { return it }
        }
        return FALLBACK
    }
}
