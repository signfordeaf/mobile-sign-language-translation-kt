// signtranslate/src/main/java/com/signfordeaf/signtranslate/SignLanguageConfig.kt

package com.signfordeaf.signtranslate

data class SignLanguageConfig(
    val apiKey: String,
    val apiUrl: String,
    /** Identifies the calling app: `Origin` header + `url` parameter. Defaults to [apiUrl]. */
    val originUrl: String? = null,
    val language: Language = Language.TURKISH,
    /**
     * Dictionary id. **Optional** — leave null and the SDK auto-selects a default translator
     * (Hesna) for the idle placeholder and the first request. Set it only to *pin* a translator;
     * either way, a `tid`/`fdid` the backend serves back overrides it for the rest of the session
     * (doc 10).
     */
    val fdid: String? = null,
    /** Translator id. Optional — see [fdid]. */
    val tid: String? = null,
    val theme: SignLanguageTheme = SignLanguageTheme(),
    val accessibility: SignLanguageAccessibility = SignLanguageAccessibility(),
    val floatingButton: FloatingButtonConfig = FloatingButtonConfig(),
    /** Player (card) appearance and controls (doc 04/06). */
    val card: SignLanguageCardConfig = SignLanguageCardConfig(),
    /** Whether a tap translates the sentence under the finger or the whole paragraph (doc 08/09). */
    val granularity: Granularity = Granularity.SENTENCE,
    /** Longest text sent in one request; longer text is clause-chunked (doc 09). */
    val maxSegmentChars: Int = 900,
    /** Long press translates text the host made tappable (doc 08). Off by default. */
    val longPressToTranslate: Boolean = false,
    /** Hand taps the SDK should not claim back to the host app (doc 08). On by default. */
    val smartPassthrough: Boolean = true,
    /** Whether the SDK turns itself on at start (doc 04). Off by default. */
    val autoEnable: Boolean = false
) {
    /** The origin sent to the backend: [originUrl] if given, otherwise [apiUrl] (doc 03). */
    val resolvedOrigin: String get() = originUrl?.takeIf { it.isNotBlank() } ?: apiUrl
}

/** Whether a tap translates the tapped sentence (default) or the whole paragraph (doc 09). */
enum class Granularity { SENTENCE, PARAGRAPH }

/** Corner the player animates in from when there is no button gesture to follow (doc 06). */
enum class InitialCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

/**
 * Pins the idle signer regardless of ids (doc 10). `null` means *follow the ids*.
 * Each entry maps to a bundled `tid`/`fdid` pair and a clip.
 */
enum class PlaceholderAvatar { KADIR, HESNA, JASON, OWAIS }

enum class Language(val code: String) {
    TURKISH("tr"),
    ENGLISH("en"),
    ARABIC("ar");

    companion object {
        fun fromString(value: String): Language {
            return when (value.lowercase()) {
                "tr", "turkish" -> TURKISH
                "en", "english" -> ENGLISH
                "ar", "arabic" -> ARABIC
                else -> TURKISH
            }
        }
    }

    val displayName: String
        get() = when (this) {
            TURKISH -> "Türkçe"
            ENGLISH -> "English"
            ARABIC -> "العربية"
        }

    val menuTitle: String
        get() = when (this) {
            TURKISH -> "İşaret Dili"
            ENGLISH -> "Sign Language"
            ARABIC -> "لغة الإشارة"
        }

    val businessName: String
        get() = when (this) {
            TURKISH -> "Engelsiz Çeviri"
            else -> "SignForDeaf"
        }

    /** Hint shown by the floating tap-to-translate button. */
    val tapToTranslateHint: String
        get() = when (this) {
            TURKISH -> "Çevirmek için bir yazıya dokunun"
            ARABIC -> "اضغط على نص للترجمة"
            else -> "Tap on any text to translate"
        }

    /** Label shown on the loading overlay while the sign-language video is prepared. */
    val loadingText: String
        get() = when (this) {
            TURKISH -> "Çevriliyor…"
            ENGLISH -> "Translating…"
            ARABIC -> "جارٍ الترجمة…"
        }

    /** Message shown when a translation is blocked because the text contains sensitive data. */
    val sensitiveBlockedText: String
        get() = when (this) {
            TURKISH -> "Bu içerik hassas veri içerdiği için işaret diline çevrilemez."
            ENGLISH -> "This content contains sensitive data and cannot be translated."
            ARABIC -> "يحتوي هذا المحتوى على بيانات حساسة ولا يمكن ترجمته."
        }

    /** Message shown when a translation request fails (network/server/video error). */
    val errorNoticeText: String
        get() = when (this) {
            TURKISH -> "Bir hata oluştu, lütfen daha sonra tekrar deneyin."
            ENGLISH -> "Something went wrong. Please try again later."
            ARABIC -> "حدث خطأ ما. يرجى المحاولة مرة أخرى لاحقًا."
        }
}

data class SignLanguageTheme(
    /** Control bar fill, logo tint, active button fill, spinner. */
    val primaryColor: String = "#6750A4",
    val backgroundColor: String = "#FFFFFF",
    /** Caption and in-stage messages, over the surface (read through the contrast guard). */
    val textColor: String = "#1C1B1F",
    val bottomSheetBackgroundColor: String = "#FFFFFF",
    /** Anything drawn *on* the primary color — control icons, caption glyphs (contrast-guarded). */
    val onPrimaryColor: String = "#FFFFFF",
    /** Background behind the avatar video. */
    val surfaceColor: String = "#FFFFFF",
    /** Outer radius of the stage and control bar, in dp. */
    val cornerRadius: Float = 16f
)

data class SignLanguageAccessibility(
    val enabled: Boolean = true,
    val announceTranslations: Boolean = true,
    val highContrastMode: Boolean = false,
    /** Screen-reader announcement when a translation becomes playable (doc 13). */
    val announceOnOpen: Boolean = true,
    /** Announcement when the player goes away (doc 13). Off by default. */
    val announceOnClose: Boolean = false,
    /** Accessibility label of the video; localized default when null. */
    val videoPlayerLabel: String? = null,
    /** Accessibility label of ✕; localized default when null. */
    val closeButtonLabel: String? = null,
    /** Accessibility hint for the player surface; localized default when null. */
    val bottomSheetHint: String? = null
)

/**
 * Player (card) appearance and controls (doc 04/06). `avatarHeight`/`avatarMaxWidth` are
 * *requests, not guarantees* — the real size comes from the screen-height budget in doc 06.
 */
data class SignLanguageCardConfig(
    val draggable: Boolean = true,
    val initialCorner: InitialCorner = InitialCorner.BOTTOM_RIGHT,
    /** Requested stage height in dp; width follows from the video aspect ratio. */
    val avatarHeight: Float = 240f,
    /** Width ceiling in dp, used when a video turns out landscape. */
    val avatarMaxWidth: Float = 212f,
    /** Pins the idle signer. `null` means *follow the ids* (doc 10). */
    val placeholderAvatar: PlaceholderAvatar? = null,
    /** Host-supplied idle clip overriding the bundled one. `""` opts out of video entirely. */
    val placeholderAsset: String? = null,
    /** 👍/👎 pill over the avatar. Off — competes with the avatar and its endpoint is stubbed. */
    val showFeedback: Boolean = false,
    /** Contact button in the control bar. Off — its endpoint is stubbed (doc 03). */
    val showContact: Boolean = false,
    val showSpeed: Boolean = true,
    val showLoop: Boolean = true,
    val speeds: List<Float> = listOf(1.0f, 1.2f, 1.5f, 2.0f),
    val defaultSpeed: Float = 1.0f,
    val defaultLooping: Boolean = true
)

/** Idle behavior for the floating tap-to-translate button. */
enum class FloatingButtonIdleBehavior { PEEK, FADE, NONE }

/**
 * Configuration for the native draggable, edge-snapping floating button that
 * toggles tap-to-translate mode. Mirrors the React Native FloatingButtonConfig.
 */
data class FloatingButtonConfig(
    val enabled: Boolean = true,
    val idleBehavior: FloatingButtonIdleBehavior = FloatingButtonIdleBehavior.PEEK,
    val idleDelayMs: Long = 2500L,
    val hintMaxShows: Int = 2,
    val sizeDp: Int = 44,
    val backgroundColor: String = "#FFFFFF",
    val activeBackgroundColor: String = "#6750A4",
    val iconColor: String = "#6750A4",
    val activeIconColor: String = "#FFFFFF",
    val borderColor: String = "#6750A4"
)
