// signtranslate/src/main/java/com/signfordeaf/signtranslate/SignLanguage.kt

package com.signfordeaf.signtranslate

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.signfordeaf.signtranslate.core.SignController
import com.signfordeaf.signtranslate.events.SignErrorCode
import com.signfordeaf.signtranslate.events.SignEvent
import com.signfordeaf.signtranslate.events.SignEventType
import com.signfordeaf.signtranslate.integration.SignHost
import com.signfordeaf.signtranslate.l10n.SignStrings
import com.signfordeaf.signtranslate.network.TranslateService
import com.signfordeaf.signtranslate.sensitive.SensitiveDataGuard
import com.signfordeaf.signtranslate.storage.PrefsStorage
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.lang.ref.WeakReference

/**
 * Public entry point for the SignForDeaf Android SDK (v2).
 *
 * v2 is a non-modal corner player: while the SDK is on and the player is open, a tap on text in
 * the host app translates the sentence under the finger and plays it in a small floating player,
 * without taking the app away from the user. The v1 API is preserved — see the KDoc on each method.
 *
 * ```
 * SignLanguage.configure(this, SignLanguageConfig(
 *     apiKey = "YOUR-API-KEY",
 *     apiUrl = "https://your-server.example.com"))
 * SignLanguage.enable(this)   // shows the floating button; tapping it opens the player
 * ```
 */
object SignLanguage {

    private const val TAG = "SignLanguage"

    private var config: SignLanguageConfig? = null
    private var controller: SignController? = null
    private var strings: SignStrings = SignStrings.of(Language.TURKISH)
    private var host: SignHost? = null
    private var enabled = false

    private val scope = MainScope()
    private var activityRef: WeakReference<Activity>? = null
    private var lifecycleCallbacks: Application.ActivityLifecycleCallbacks? = null
    private var eventsBound = false

    private var translationListener: TranslationListener? = null
    private var bottomSheetListener: BottomSheetListener? = null
    private var tapToTranslateModeListener: ((Boolean) -> Unit)? = null
    private var lastTapMode = false

    // MARK: - Listeners

    interface TranslationListener {
        fun onTextSelected(text: String) {}
        fun onTranslationStart(text: String) {}
        fun onTranslationComplete(text: String, videoUrl: String) {}
        fun onTranslationError(code: String, message: String) {}
        fun onTranslationBlocked(text: String) {}
    }

    interface BottomSheetListener {
        fun onBottomSheetOpen() {}
        fun onBottomSheetClose() {}
        fun onVideoStart() {}
        fun onVideoEnd() {}
    }

    fun setOnTranslationListener(listener: TranslationListener?) { translationListener = listener }
    fun setOnBottomSheetListener(listener: BottomSheetListener?) { bottomSheetListener = listener }
    fun setOnTapToTranslateModeChangeListener(listener: ((enabled: Boolean) -> Unit)?) {
        tapToTranslateModeListener = listener
    }

    // MARK: - Configuration

    /** Configure the SDK. Call once, from `onCreate` after `setContentView`. */
    fun configure(activity: Activity, config: SignLanguageConfig) {
        try {
            this.config = config
            strings = SignStrings.of(config.language)
            val storage = PrefsStorage(activity)
            val service = TranslateService(config)
            val c = SignController(config, service, storage, scope = scope)
            controller = c
            bindEvents(c)

            registerLifecycleCallbacks(activity.application)
            activityRef = WeakReference(activity)
            installHost(activity)

            enabled = config.autoEnable
            host?.enabled = enabled
            Log.d(TAG, "SDK configured. API URL: ${config.apiUrl}")
        } catch (e: Exception) {
            Log.e(TAG, "Configure error: ${e.message}", e)
        }
    }

    // MARK: - Enable / disable

    fun enable(activity: Activity) {
        activityRef = WeakReference(activity)
        installHost(activity)
        enabled = true
        host?.enabled = true
    }

    fun disable() {
        enabled = false
        controller?.close()
        host?.enabled = false
    }

    fun isEnabled(): Boolean = enabled

    /**
     * v2 tap mode *is* the player being open and expanded. `true` opens the player (turning tap
     * mode on); `false` closes it.
     */
    fun setTapToTranslateMode(on: Boolean) {
        if (on) {
            enabled = true
            host?.enabled = true
            controller?.openCard()
        } else {
            controller?.close()
        }
    }

    fun isTapToTranslateEnabled(): Boolean {
        val s = controller?.state?.value ?: return false
        return s.playerVisible && !s.collapsed
    }

    // MARK: - Floating button (managed by the integration layer in v2)

    /** In v2 the button appears whenever the SDK is enabled — this enables the SDK. */
    fun showFloatingButton(activity: Activity) = enable(activity)

    /** Hide the button by disabling the SDK. */
    fun hideFloatingButton() = disable()

    // MARK: - Translation

    /** Translate [text] programmatically. Opens the player in the configured corner. */
    fun translate(text: String) {
        if (controller == null) { Log.e(TAG, "SDK not configured"); return }
        enabled = true
        host?.enabled = true
        controller?.translateText(text)
    }

    fun cancelTranslation() { controller?.cancel() }

    fun dismissBottomSheet() { controller?.close() }

    fun isBottomSheetVisible(): Boolean = controller?.state?.value?.playerVisible ?: false

    // MARK: - Sensitive data

    fun registerSensitive(text: String) = SensitiveDataGuard.register(text)
    fun unregisterSensitive(text: String) = SensitiveDataGuard.unregister(text)
    fun isRegisteredSensitive(text: String): Boolean = SensitiveDataGuard.isRegistered(text)
    fun isSensitive(text: String?): Boolean = SensitiveDataGuard.isSensitive(text)

    fun markSensitive(view: View) {
        when (view) {
            is TextView -> SensitiveDataGuard.register(view.text?.toString().orEmpty())
            is ViewGroup -> for (i in 0 until view.childCount) markSensitive(view.getChildAt(i))
        }
    }

    // MARK: - Internals

    private fun installHost(activity: Activity) {
        val c = controller ?: return
        val cfg = config ?: return
        if (host != null) { host?.enabled = enabled; return }
        host = SignHost(activity, c, cfg, strings).also {
            it.install()
            it.enabled = enabled
        }
    }

    private fun bindEvents(c: SignController) {
        if (eventsBound) return
        eventsBound = true
        c.events.onEach { forward(it) }.launchIn(scope)
        c.state.onEach { s ->
            val tapMode = s.playerVisible && !s.collapsed
            if (tapMode != lastTapMode) { lastTapMode = tapMode; tapToTranslateModeListener?.invoke(tapMode) }
        }.launchIn(scope)
    }

    private fun forward(ev: SignEvent) {
        when (ev.type) {
            SignEventType.TEXT_SELECTED -> translationListener?.onTextSelected(ev.text.orEmpty())
            SignEventType.TRANSLATION_START -> translationListener?.onTranslationStart(ev.text.orEmpty())
            SignEventType.TRANSLATION_COMPLETE ->
                translationListener?.onTranslationComplete(ev.text.orEmpty(), ev.videoUrl.orEmpty())
            SignEventType.TRANSLATION_ERROR ->
                translationListener?.onTranslationError(
                    (ev.errorCode ?: SignErrorCode.UNKNOWN).name, ev.errorMessage ?: ""
                )
            SignEventType.BLOCKED_SENSITIVE -> translationListener?.onTranslationBlocked(ev.text.orEmpty())
            SignEventType.PANEL_OPEN -> bottomSheetListener?.onBottomSheetOpen()
            SignEventType.PANEL_CLOSE -> bottomSheetListener?.onBottomSheetClose()
            SignEventType.VIDEO_START -> bottomSheetListener?.onVideoStart()
            SignEventType.VIDEO_END -> bottomSheetListener?.onVideoEnd()
            else -> Unit
        }
    }

    private fun registerLifecycleCallbacks(application: Application) {
        if (lifecycleCallbacks != null) return
        lifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {
                activityRef = WeakReference(activity)
                if (controller != null && host == null) installHost(activity)
            }
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {
                if (activityRef?.get() === activity) {
                    host?.teardown()
                    host = null
                }
            }
        }
        application.registerActivityLifecycleCallbacks(lifecycleCallbacks)
    }
}
