// signtranslate/src/main/java/com/signfordeaf/signtranslate/l10n/SignStrings.kt

package com.signfordeaf.signtranslate.l10n

import com.signfordeaf.signtranslate.Language

/**
 * The SDK's string table (doc 13). Every user-visible string, including every screen-reader
 * label, is localized — an accessibility SDK that ships a hardcoded label is a contradiction.
 *
 * Resolve with [of]. A language with no table falls back to English. Turkish, English and
 * Arabic are the only supported languages (the backend does not support German/French/Spanish).
 */
data class SignStrings(
    val menuTitle: String,
    val businessName: String,
    val loading: String,
    val error: String,
    val close: String,
    val videoPlayerLabel: String,
    val translationReady: String,
    val tapToTranslateHint: String,
    val sensitiveBlocked: String,
    val translationModeLabel: String,
    val playLabel: String,
    val pauseLabel: String,
    val loopLabel: String,
    val speedLabel: String,
    val contactLabel: String,
    val collapseLabel: String,
    val expandLabel: String,
    val previousSentenceLabel: String,
    val nextSentenceLabel: String,
    val feedbackPositiveLabel: String,
    val feedbackNegativeLabel: String,
    val feedbackThanks: String,
    /** Right-to-left layout (Arabic). */
    val isRtl: Boolean = false
) {
    /** The sentence counter, `"{index + 1} / {total}"`. */
    fun sentenceCounter(index: Int, total: Int): String = "${index + 1} / $total"

    companion object {
        fun of(language: Language): SignStrings = when (language) {
            Language.TURKISH -> TR
            Language.ENGLISH -> EN
            Language.ARABIC -> AR
        }

        private val TR = SignStrings(
            menuTitle = "İşaret Dili",
            businessName = "Engelsiz Çeviri",
            loading = "Çeviriliyor...",
            error = "Çeviri işlemi şu anda gerçekleştirilemiyor. Lütfen daha sonra tekrar deneyiniz.",
            close = "Kapat",
            videoPlayerLabel = "İşaret dili videosu oynatılıyor",
            translationReady = "İşaret dili çevirisi hazır",
            tapToTranslateHint = "Cümlelere tıklayarak işaret dili çevirilerini başlatabilirsiniz.",
            sensitiveBlocked = "Bu içerik hassas veri içerdiği için işaret diline çevrilemez.",
            translationModeLabel = "İşaret dili çeviri modu",
            playLabel = "Oynat",
            pauseLabel = "Duraklat",
            loopLabel = "Tekrarla",
            speedLabel = "Oynatma hızı",
            contactLabel = "İletişime geçin",
            collapseLabel = "Küçült",
            expandLabel = "Genişlet",
            previousSentenceLabel = "Önceki cümle",
            nextSentenceLabel = "Sonraki cümle",
            feedbackPositiveLabel = "Çeviri anlaşılır",
            feedbackNegativeLabel = "Çeviri anlaşılır değil",
            feedbackThanks = "Geri bildiriminiz için teşekkürler"
        )

        private val EN = SignStrings(
            menuTitle = "Sign Language",
            businessName = "SignForDeaf",
            loading = "Translating...",
            error = "Translation is not available at the moment. Please try again later.",
            close = "Close",
            videoPlayerLabel = "Sign language video is playing",
            translationReady = "Sign language translation is ready",
            tapToTranslateHint = "Tap a sentence to start its sign language translation.",
            sensitiveBlocked = "This content contains sensitive data and cannot be translated.",
            translationModeLabel = "Sign language translation mode",
            playLabel = "Play",
            pauseLabel = "Pause",
            loopLabel = "Repeat",
            speedLabel = "Playback speed",
            contactLabel = "Contact us",
            collapseLabel = "Collapse",
            expandLabel = "Expand",
            previousSentenceLabel = "Previous sentence",
            nextSentenceLabel = "Next sentence",
            feedbackPositiveLabel = "Translation is clear",
            feedbackNegativeLabel = "Translation is unclear",
            feedbackThanks = "Thanks for your feedback"
        )

        private val AR = SignStrings(
            menuTitle = "لغة الإشارة",
            businessName = "SignForDeaf",
            loading = "جارٍ الترجمة...",
            error = "لا يمكن إجراء عملية الترجمة في الوقت الحالي. يرجى المحاولة مرة أخرى في وقت لاحق.",
            close = "إغلاق",
            videoPlayerLabel = "يتم تشغيل فيديو لغة الإشارة",
            translationReady = "ترجمة لغة الإشارة جاهزة",
            tapToTranslateHint = "انقر على جملة لبدء ترجمتها إلى لغة الإشارة.",
            sensitiveBlocked = "يحتوي هذا المحتوى على بيانات حساسة ولا يمكن ترجمته.",
            translationModeLabel = "وضع الترجمة بلغة الإشارة",
            playLabel = "تشغيل",
            pauseLabel = "إيقاف مؤقت",
            loopLabel = "تكرار",
            speedLabel = "سرعة التشغيل",
            contactLabel = "تواصل معنا",
            collapseLabel = "تصغير",
            expandLabel = "توسيع",
            previousSentenceLabel = "الجملة السابقة",
            nextSentenceLabel = "الجملة التالية",
            feedbackPositiveLabel = "الترجمة واضحة",
            feedbackNegativeLabel = "الترجمة غير واضحة",
            feedbackThanks = "شكرًا على ملاحظاتك",
            isRtl = true
        )
    }
}
