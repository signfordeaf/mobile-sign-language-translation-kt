// signtranslate/src/main/java/com/signfordeaf/signtranslate/SignLanguage.kt

package com.signfordeaf.signtranslate

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.signfordeaf.signtranslate.bottomsheet.SignLanguageBottomSheet
import com.signfordeaf.signtranslate.floating.SignLanguageFloatingButton
import com.signfordeaf.signtranslate.loading.SignLanguageLoadingOverlay
import com.signfordeaf.signtranslate.network.ApiService
import com.signfordeaf.signtranslate.sensitive.SensitiveDataGuard
import com.signfordeaf.signtranslate.sensitive.SignLanguageNoticeDialog
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * Public entry point for the SignForDeaf Android SDK (v2.0.0).
 *
 * Typical usage from an [AppCompatActivity]:
 * ```
 * SignLanguage.configure(this, SignLanguageConfig(
 *     apiKey = "YOUR-API-KEY",
 *     apiUrl = "https://your-server.example.com",
 *     language = Language.TURKISH,
 *     theme = SignLanguageTheme(primaryColor = "#6750A4")))
 * // optional: draggable tap-to-translate button
 * SignLanguage.showFloatingButton(this)
 * ```
 *
 * Once configured, every [TextView]/[EditText] in the activity gains a
 * "Sign Language" item in its text-selection menu that plays a looping
 * sign-language video in a bottom sheet.
 */
object SignLanguage {

    private const val TAG = "SignLanguage"
    private const val BOTTOM_SHEET_TAG = "sign_language_bottom_sheet"

    private var config: SignLanguageConfig? = null
    private var apiService: ApiService? = null
    private var isModuleEnabled = false
    private var tapToTranslateEnabled = false
    private var bottomSheet: SignLanguageBottomSheet? = null
    private var floatingButton: SignLanguageFloatingButton? = null

    // Frosted loading screen shown while a video is prepared, and the ExoPlayer
    // being pre-buffered before the sheet opens. The player's ownership transfers
    // to the sheet once it is ready (see onVideoReady).
    private var loadingOverlay: SignLanguageLoadingOverlay? = null
    private var pendingPlayer: ExoPlayer? = null
    // Bumped on each new request and on cancel; late callbacks whose id no longer
    // matches are dropped, so canceling truly stops the flow.
    private var translationRequestId = 0

    private var activityRef: WeakReference<Activity>? = null
    private var lifecycleCallbacks: Application.ActivityLifecycleCallbacks? = null

    // Tracks text views that already have the tap-to-translate detector attached,
    // so the periodic view observer doesn't attach it twice. Weak keys avoid leaks.
    private val tapAttachedViews = WeakHashMap<TextView, Boolean>()

    private var themePrimaryColor: String = "#6750A4"
    private var themeTextColor: String = "#1C1B1F"

    private var translationListener: TranslationListener? = null
    private var bottomSheetListener: BottomSheetListener? = null
    private var tapToTranslateModeListener: ((Boolean) -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())
    private var viewObserverRunnable: Runnable? = null

    private val currentActivity: Activity? get() = activityRef?.get()

    // MARK: - Listeners

    /** Callbacks for the translation lifecycle (text selection, start, complete, error). */
    interface TranslationListener {
        fun onTextSelected(text: String) {}
        fun onTranslationStart(text: String) {}
        fun onTranslationComplete(text: String, videoUrl: String) {}
        fun onTranslationError(code: String, message: String) {}
        /** The text was blocked before any request was sent because it contains sensitive data. */
        fun onTranslationBlocked(text: String) {}
    }

    /** Callbacks for the bottom sheet / video playback lifecycle. */
    interface BottomSheetListener {
        fun onBottomSheetOpen() {}
        fun onBottomSheetClose() {}
        fun onVideoStart() {}
        fun onVideoEnd() {}
    }

    fun setOnTranslationListener(listener: TranslationListener?) {
        translationListener = listener
    }

    fun setOnBottomSheetListener(listener: BottomSheetListener?) {
        bottomSheetListener = listener
    }

    // MARK: - Configuration

    /**
     * Configure the SDK and automatically enable text selection on [activity].
     * Call once, typically from `onCreate` after `setContentView`.
     */
    fun configure(activity: Activity, config: SignLanguageConfig) {
        try {
            this.config = config
            themePrimaryColor = config.theme.primaryColor
            themeTextColor = config.theme.textColor
            apiService = ApiService(config)

            activityRef = WeakReference(activity)
            registerLifecycleCallbacks(activity.application)

            Log.d(TAG, "SDK configured. API URL: ${config.apiUrl}")

            // Automatically enable text selection after configuration
            isModuleEnabled = true
            runOnUiThread {
                enableTextSelectionForCurrentActivity()
                startViewObserver()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Configure error: ${e.message}", e)
        }
    }

    // MARK: - Enable/Disable

    /** Re-enable text selection, updating the tracked activity. */
    fun enable(activity: Activity) {
        activityRef = WeakReference(activity)
        isModuleEnabled = true
        runOnUiThread { enableTextSelectionForCurrentActivity() }
    }

    fun disable() {
        isModuleEnabled = false
        tapToTranslateEnabled = false
        viewObserverRunnable?.let { handler.removeCallbacks(it) }
    }

    fun isEnabled(): Boolean = isModuleEnabled

    /**
     * Toggle "tap-to-translate" mode. When enabled, a single tap on any text view
     * translates it immediately (bypassing the selection menu).
     *
     * [tapToTranslateEnabled] is the single source of truth: this keeps the floating
     * button's visual state in sync and notifies any registered mode-change listener
     * (so a host switch/toggle can reflect changes made from the floating button too).
     */
    fun setTapToTranslateMode(enabled: Boolean) {
        val changed = tapToTranslateEnabled != enabled
        tapToTranslateEnabled = enabled
        runOnUiThread {
            floatingButton?.setActive(enabled)
            if (enabled) enableTextSelectionForCurrentActivity()
        }
        if (changed) tapToTranslateModeListener?.invoke(enabled)
    }

    fun isTapToTranslateEnabled(): Boolean = tapToTranslateEnabled

    /**
     * Observe tap-to-translate mode changes from any source (switch, floating button,
     * or a programmatic call), e.g. to keep a UI switch in sync.
     */
    fun setOnTapToTranslateModeChangeListener(listener: ((enabled: Boolean) -> Unit)?) {
        tapToTranslateModeListener = listener
    }

    // MARK: - Floating button

    /** Show the native draggable floating button that toggles tap-to-translate mode. */
    fun showFloatingButton(activity: Activity) {
        activityRef = WeakReference(activity)
        val cfg = config?.floatingButton ?: FloatingButtonConfig()
        runOnUiThread {
            floatingButton?.remove()
            floatingButton = SignLanguageFloatingButton(
                activity = activity,
                config = cfg,
                hint = config?.language?.tapToTranslateHint ?: "",
                onToggle = { active -> setTapToTranslateMode(active) }
            ).also {
                it.show()
                it.setActive(tapToTranslateEnabled) // reflect current mode on (re)show
            }
        }
    }

    fun hideFloatingButton() {
        runOnUiThread {
            floatingButton?.remove()
            floatingButton = null
        }
    }

    // MARK: - Sensitive data protection

    /**
     * Mark [text] as sensitive so it is never sent for translation. A selection is
     * blocked if it overlaps a marked text (bidirectional `contains`).
     */
    fun registerSensitive(text: String) = SensitiveDataGuard.register(text)

    /** Remove a text previously added via [registerSensitive]. */
    fun unregisterSensitive(text: String) = SensitiveDataGuard.unregister(text)

    /** Whether [text] overlaps any manually marked sensitive text. */
    fun isRegisteredSensitive(text: String): Boolean = SensitiveDataGuard.isRegistered(text)

    /**
     * Whether [text] would be blocked — either manually marked or auto-detected as
     * personal data (T.C. Kimlik No, credit card, IBAN, e-mail, phone).
     */
    fun isSensitive(text: String?): Boolean = SensitiveDataGuard.isSensitive(text)

    /**
     * Mark every [TextView]/[EditText] within [view]'s subtree as sensitive — the
     * Android equivalent of wrapping content in a "sensitive" widget. The text stays
     * visible/selectable, but translating it is blocked.
     */
    fun markSensitive(view: View) {
        when (view) {
            is TextView -> SensitiveDataGuard.register(view.text?.toString().orEmpty())
            is ViewGroup -> for (i in 0 until view.childCount) markSensitive(view.getChildAt(i))
        }
    }

    // MARK: - Text Selection

    private fun enableTextSelectionForCurrentActivity() {
        currentActivity?.let { activity ->
            val rootView = activity.findViewById<View>(android.R.id.content)
            rootView?.let { enableTextSelection(it) }
        }
    }

    private fun enableTextSelection(view: View) {
        when {
            // Recurse into containers first so text inside clickable cards still works.
            view is ViewGroup -> {
                for (i in 0 until view.childCount) {
                    enableTextSelection(view.getChildAt(i))
                }
            }
            // Only genuine, readable text views — never interactive controls.
            view is TextView -> {
                if (!isInteractiveControl(view)) setupTextViewForSelection(view)
            }
        }
    }

    /**
     * Interactive controls (Buttons, Switches, CheckBoxes, ImageButtons, or any view the
     * host wired with its own click behavior) must NOT become translate targets — tapping
     * them should do their own thing, not trigger a translation. Note that Button /
     * CompoundButton / MaterialButton / MaterialSwitch all extend [TextView], which is why
     * they'd otherwise be picked up by the text-view walk.
     */
    private fun isInteractiveControl(view: View): Boolean {
        return when {
            view is android.widget.Button -> true          // Button, AppCompatButton, MaterialButton
            view is android.widget.CompoundButton -> true  // Switch, MaterialSwitch, CheckBox, RadioButton, ToggleButton, Chip
            view is android.widget.ImageButton -> true
            view.isClickable && view.hasOnClickListeners() -> true // host-defined clickable text acting as a button
            else -> false
        }
    }

    private fun setupTextViewForSelection(textView: TextView) {
        textView.isFocusable = true
        textView.isFocusableInTouchMode = true
        textView.setTextIsSelectable(true)

        val callback = com.signfordeaf.signtranslate.textselection.CustomActionModeCallback(
            menuTitle = getLocalizedMenuTitle(),
            onSignLanguageSelected = { selectedText -> onTextSelected(selectedText) }
        ) { tv ->
            val selectionStart = tv.selectionStart
            val selectionEnd = tv.selectionEnd
            if (selectionStart >= 0 && selectionEnd > selectionStart) {
                tv.text.subSequence(selectionStart, selectionEnd).toString()
            } else ""
        }
        callback.setTextView(textView)

        textView.customSelectionActionModeCallback = callback

        attachTapToTranslate(textView)
    }

    /**
     * Attach a non-consuming single-tap detector to a text view. When tap-to-translate
     * mode is on, a tap translates the view's text directly. The OnTouchListener always
     * returns false, so scrolling, presses and the long-press selection menu keep working.
     */
    private fun attachTapToTranslate(textView: TextView) {
        if (tapAttachedViews.containsKey(textView)) return
        tapAttachedViews[textView] = true

        val gestureDetector = GestureDetector(
            textView.context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    if (!tapToTranslateEnabled) return false
                    // Tap-to-translate targets read-only text; tapping an input field
                    // means "edit", so don't translate on EditText taps.
                    if (textView is EditText) return false
                    val text = textView.text?.toString().orEmpty()
                    if (text.isNotEmpty()) {
                        onTextSelected(text)
                    }
                    return false
                }
            }
        )

        textView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }
    }

    // View observer to auto-enable newly-added text views
    private fun startViewObserver() {
        viewObserverRunnable?.let { handler.removeCallbacks(it) }
        viewObserverRunnable = object : Runnable {
            override fun run() {
                if (isModuleEnabled) {
                    enableTextSelectionForCurrentActivity()
                    handler.postDelayed(this, 2000) // Check every 2 seconds
                }
            }
        }
        handler.postDelayed(viewObserverRunnable!!, 2000)
    }

    // MARK: - Translation

    private fun onTextSelected(text: String) {
        // Sensitive-data protection (runs first): if the text contains personal data,
        // no request is ever sent — we only show a notice.
        if (SensitiveDataGuard.isSensitive(text)) {
            translationListener?.onTranslationBlocked(text)
            runOnUiThread { showBlockedNotice() }
            return
        }

        translationListener?.onTextSelected(text)
        runOnUiThread {
            try {
                startTranslationFlow(text)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting translation flow: ${e.message}", e)
                translationListener?.onTranslationError("THEME_ERROR", e.message ?: "Unknown error")
            }
        }
    }

    private fun showBlockedNotice() {
        val activity = currentActivity ?: return
        if (activity.isFinishing || activity.isDestroyed) return
        try {
            SignLanguageNoticeDialog(
                activity = activity,
                primaryColor = themePrimaryColor,
                message = config?.language?.sensitiveBlockedText
                    ?: "This content contains sensitive data and cannot be translated."
            ).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing blocked notice: ${e.message}", e)
        }
    }

    /**
     * On failure we dismiss the loading overlay and show the same minimal branded
     * notice used for blocked content — never an in-overlay error card and never the
     * bottom sheet. The user just gets a "something went wrong, try again later"
     * message they can dismiss.
     */
    private fun showErrorNotice() {
        releasePendingPlayer()
        dismissLoadingOverlay()
        val activity = currentActivity ?: return
        if (activity.isFinishing || activity.isDestroyed) return
        try {
            SignLanguageNoticeDialog(
                activity = activity,
                primaryColor = themePrimaryColor,
                message = config?.language?.errorNoticeText
                    ?: "Something went wrong. Please try again later."
            ).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing error notice: ${e.message}", e)
        }
    }

    /**
     * Programmatically translate [text]: shows the frosted loading screen while the
     * sign-language video is prepared, then opens the bottom sheet already playing.
     */
    fun translate(text: String) {
        onTextSelected(text)
    }

    /**
     * Shows the loading overlay (not the sheet) and kicks off the translation. The
     * bottom sheet is opened only once the video is buffered and ready to play.
     */
    private fun startTranslationFlow(text: String) {
        val activity = currentActivity as? AppCompatActivity ?: run {
            Log.e(TAG, "No AppCompatActivity available for translation")
            return
        }
        if (activity.isFinishing || activity.isDestroyed) {
            Log.e(TAG, "Activity is finishing or destroyed, cannot start translation")
            return
        }

        dismissExistingSheet()
        releasePendingPlayer()

        loadingOverlay?.dismiss()
        loadingOverlay = SignLanguageLoadingOverlay(
            activity = activity,
            primaryColor = themePrimaryColor,
            onCancel = { cancelTranslation() }
        ).also { it.show() }

        requestTranslation(text)
    }

    private fun requestTranslation(text: String) {
        val service = apiService ?: run {
            Log.e(TAG, "SDK not configured")
            translationListener?.onTranslationError("CONFIG_ERROR", "SDK not configured")
            showErrorNotice()
            return
        }

        // Tag this attempt; a cancel or a newer request bumps the id so any late
        // callback from this attempt is ignored (no stray error/complete after cancel).
        val requestId = ++translationRequestId

        translationListener?.onTranslationStart(text)

        Thread {
            service.getSignVideo(text) { result, error ->
                runOnUiThread {
                    if (requestId != translationRequestId) return@runOnUiThread // canceled / superseded

                    if (error != null) {
                        translationListener?.onTranslationError("API_ERROR", error.message ?: "Unknown error")
                        showErrorNotice()
                        return@runOnUiThread
                    }

                    // A null result with no error means the request was canceled.
                    val signModel = result ?: return@runOnUiThread
                    if (signModel.state == true) {
                        val videoUrl = (signModel.videoUrl ?: "").replace("http://", "https://")
                        translationListener?.onTranslationComplete(text, videoUrl)
                        prepareVideoThenOpenSheet(text, videoUrl)
                    } else {
                        translationListener?.onTranslationError("API_ERROR", "Translation not ready")
                        showErrorNotice()
                    }
                }
            }
        }.start()
    }

    /**
     * Pre-buffers the video with a headless ExoPlayer while the overlay is still up;
     * only once [Player.STATE_READY] fires do we dismiss the overlay and open the sheet.
     */
    private fun prepareVideoThenOpenSheet(text: String, videoUrl: String) {
        val activity = currentActivity as? AppCompatActivity ?: run {
            dismissLoadingOverlay()
            return
        }
        if (videoUrl.isEmpty()) {
            translationListener?.onTranslationError("API_ERROR", "Empty video URL")
            showErrorNotice()
            return
        }

        releasePendingPlayer()
        val player = ExoPlayer.Builder(activity).build().apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            repeatMode = Player.REPEAT_MODE_ALL
            playWhenReady = false
        }
        pendingPlayer = player

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    player.removeListener(this)
                    onVideoReady(text, videoUrl, player)
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                player.removeListener(this)
                releasePendingPlayer()
                translationListener?.onTranslationError("VIDEO_ERROR", error.message ?: "Video failed to load")
                showErrorNotice()
            }
        })
        player.prepare()
    }

    /** Video is buffered: dismiss the overlay and open the sheet with the ready player. */
    private fun onVideoReady(text: String, videoUrl: String, player: ExoPlayer) {
        val activity = currentActivity as? AppCompatActivity ?: run {
            releasePendingPlayer()
            return
        }

        // Ownership of the player transfers to the sheet, which releases it.
        pendingPlayer = null
        dismissLoadingOverlay()

        bottomSheet = SignLanguageBottomSheet.newInstance(
            videoUrl = videoUrl,
            text = text,
            businessName = getLocalizedBusinessName(),
            primaryColor = themePrimaryColor,
            textColor = themeTextColor
        ).apply {
            preparedPlayer = player
            onDismissListener = { bottomSheetListener?.onBottomSheetClose() }
            onVideoStartListener = { bottomSheetListener?.onVideoStart() }
            onVideoEndListener = { bottomSheetListener?.onVideoEnd() }
        }

        try {
            showSheet(activity)
            bottomSheetListener?.onBottomSheetOpen()
        } catch (e: Exception) {
            Log.e(TAG, "Error opening bottom sheet: ${e.message}", e)
            player.release()
            translationListener?.onTranslationError("THEME_ERROR", e.message ?: "Unknown error")
        }
    }

    private fun dismissLoadingOverlay() {
        loadingOverlay?.dismiss()
        loadingOverlay = null
    }

    private fun releasePendingPlayer() {
        pendingPlayer?.release()
        pendingPlayer = null
    }

    fun cancelTranslation() {
        translationRequestId++ // invalidate any in-flight callback
        apiService?.cancelRequest()
        releasePendingPlayer()
        dismissLoadingOverlay()
    }

    // MARK: - Bottom Sheet

    fun dismissBottomSheet() {
        runOnUiThread { bottomSheet?.dismiss() }
    }

    fun isBottomSheetVisible(): Boolean = bottomSheet?.isVisible ?: false

    private fun dismissExistingSheet() {
        bottomSheet?.let { existingSheet ->
            if (existingSheet.isAdded || existingSheet.isVisible) {
                try {
                    existingSheet.dismissAllowingStateLoss()
                } catch (e: Exception) {
                    Log.w(TAG, "Error dismissing existing bottom sheet: ${e.message}")
                }
            }
        }
    }

    private fun showSheet(activity: AppCompatActivity) {
        val fragmentManager = activity.supportFragmentManager
        val existingFragment = fragmentManager.findFragmentByTag(BOTTOM_SHEET_TAG)
        if (existingFragment != null) {
            try {
                fragmentManager.beginTransaction().remove(existingFragment).commitAllowingStateLoss()
                fragmentManager.executePendingTransactions()
            } catch (e: Exception) {
                Log.w(TAG, "Error removing existing fragment: ${e.message}")
            }
        }
        // Use commitAllowingStateLoss to prevent IllegalStateException
        fragmentManager.beginTransaction()
            .add(bottomSheet!!, BOTTOM_SHEET_TAG)
            .commitAllowingStateLoss()
    }

    // MARK: - Helpers

    private fun getLocalizedMenuTitle(): String = config?.language?.menuTitle ?: "Sign Language"

    private fun getLocalizedBusinessName(): String = config?.language?.businessName ?: "SignForDeaf"

    private fun runOnUiThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else handler.post(action)
    }

    // MARK: - Lifecycle

    private fun registerLifecycleCallbacks(application: Application) {
        if (lifecycleCallbacks != null) return
        lifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {
                activityRef = WeakReference(activity)
                if (isModuleEnabled) {
                    runOnUiThread { enableTextSelectionForCurrentActivity() }
                }
            }
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {
                if (currentActivity === activity) {
                    bottomSheet?.dismissAllowingStateLoss()
                    bottomSheet = null
                    floatingButton = null
                    apiService?.cancelRequest()
                    releasePendingPlayer()
                    dismissLoadingOverlay()
                }
            }
        }
        application.registerActivityLifecycleCallbacks(lifecycleCallbacks)
    }
}
