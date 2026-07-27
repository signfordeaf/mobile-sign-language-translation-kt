// signtranslate/src/main/java/com/signfordeaf/signtranslate/SignLanguageConfig.kt

package com.signfordeaf.signtranslate

data class SignLanguageConfig(
    val apiKey: String,
    val apiUrl: String,
    val language: Language = Language.TURKISH,
    val fdid: String? = "16",
    val tid: String? = "23",
    val theme: SignLanguageTheme = SignLanguageTheme(),
    val accessibility: SignLanguageAccessibility = SignLanguageAccessibility(),
    val floatingButton: FloatingButtonConfig = FloatingButtonConfig()
)

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
    val primaryColor: String = "#6750A4",
    val backgroundColor: String = "#FFFFFF",
    val textColor: String = "#1C1B1F",
    val bottomSheetBackgroundColor: String = "#FFFFFF"
)

data class SignLanguageAccessibility(
    val enabled: Boolean = true,
    val announceTranslations: Boolean = true,
    val highContrastMode: Boolean = false
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
