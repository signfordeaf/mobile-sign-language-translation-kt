// signtranslate/src/main/java/com/signfordeaf/signtranslate/integration/SignHost.kt

package com.signfordeaf.signtranslate.integration

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.signfordeaf.signtranslate.SignLanguageConfig
import com.signfordeaf.signtranslate.core.SignController
import com.signfordeaf.signtranslate.core.SignUiState
import com.signfordeaf.signtranslate.floating.SignLanguageFloatingButton
import com.signfordeaf.signtranslate.l10n.SignStrings
import com.signfordeaf.signtranslate.text.PassthroughContainer
import com.signfordeaf.signtranslate.text.SentenceSegmenter
import com.signfordeaf.signtranslate.tokens.SignTokens
import com.signfordeaf.signtranslate.views.SignPlayerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Per-Activity integration layer (doc 02). Installs the SDK **inside the host's own view
 * hierarchy** — required so smart passthrough can walk the host's views — by reparenting the
 * content root under a [PassthroughContainer] with an overlay layer holding the corner player.
 *
 * Views hold no translation state: everything is rendered one-way from the process-global
 * controller. On teardown the overlay is removed and this Activity's decoders released, but the
 * controller's state is left untouched.
 */
class SignHost(
    private val activity: Activity,
    private val controller: SignController,
    private var config: SignLanguageConfig,
    private var strings: SignStrings
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var passthrough: PassthroughContainer? = null
    private var overlay: FrameLayout? = null
    private var player: SignPlayerView? = null
    private var hostRoot: View? = null
    private var fab: SignLanguageFloatingButton? = null

    /** Whether the SDK is enabled (drives tap-mode and player/button visibility). */
    var enabled: Boolean = false
        set(value) { field = value; render(controller.state.value) }

    fun install() {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        if (content.getChildAt(0) is PassthroughContainer) return // already installed
        val root = content.getChildAt(0) ?: return
        hostRoot = root

        val rootParams = root.layoutParams
        content.removeView(root)

        val pass = PassthroughContainer(activity).apply {
            clipChildren = false
            smartPassthrough = config.smartPassthrough
            longPressEnabled = config.longPressToTranslate
            hostContent = root
            onTranslate = { text, offset -> onHostText(text, offset) }
            isOverSdkUi = { x, y ->
                val p = player
                p != null && p.visibility == View.VISIBLE &&
                    x >= p.x && x <= p.x + p.width && y >= p.y && y <= p.y + p.height
            }
        }
        pass.addView(root, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val ov = FrameLayout(activity).apply { clipChildren = false; clipToPadding = false }
        val p = SignPlayerView(activity, config, strings).apply {
            onCollapse = { controller.collapse() }
            onExpand = { controller.expand() }
            onClose = { controller.close() }
            onTogglePlay = { player?.togglePlay() }
            onCycleSpeed = { controller.cycleSpeed() }
            onToggleLoop = { controller.setLooping(!controller.state.value.looping) }
            onVideoEnded = { /* videoEnd is emitted by the controller path */ }
        }
        player = p
        controller.setVideoController(p)
        ov.addView(p, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        overlay = ov

        pass.addView(ov, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        content.addView(pass, rootParams)
        passthrough = pass

        // The floating button lives in the same overlay; created once so its docked position
        // survives the player opening/closing (doc 07 ★). Tapping it opens the player.
        if (config.floatingButton.enabled) {
            fab = SignLanguageFloatingButton(activity, config.floatingButton, strings.tapToTranslateHint) { active ->
                if (active) controller.openCard()
            }.also { it.show() }
        }

        positionPlayer(p)
        controller.state.onEach { render(it) }.launchIn(scope)
        render(controller.state.value)
    }

    private fun positionPlayer(p: SignPlayerView) {
        p.post {
            val inset = SignTokens.dp(activity, SignTokens.SPACE_MD_DP)
            val parent = p.parent as? ViewGroup ?: return@post
            p.x = (parent.width - p.width - inset).coerceAtLeast(inset)
            p.y = (parent.height - p.height - inset).coerceAtLeast(inset)
        }
    }

    private fun render(state: SignUiState) {
        val p = player ?: return
        p.render(state)
        // Tap mode is live only while enabled and the player is open AND expanded.
        val expanded = state.playerVisible && !state.collapsed
        passthrough?.tapModeActive = enabled && expanded

        // The floating button shows only while enabled and the player is not visible (doc 07).
        val showFab = enabled && config.floatingButton.enabled && !state.playerVisible
        fab?.let { it.setVisible(showFab); if (showFab) it.setActive(false) }
        // Keep the player pinned in-bounds as its size changes with state/collapse.
        p.post {
            val parent = p.parent as? ViewGroup ?: return@post
            p.x = p.x.coerceIn(0f, (parent.width - p.width).coerceAtLeast(0).toFloat())
            p.y = p.y.coerceIn(0f, (parent.height - p.height).coerceAtLeast(0).toFloat())
        }
    }

    /** A classified host tap or long press → build the segment plan and translate. */
    private fun onHostText(text: String, charOffset: Int?) {
        val plan = SentenceSegmenter.plan(text, config.granularity, config.maxSegmentChars, charOffset) ?: return
        controller.submit(plan.segments, plan.index, openedByUser = true)
    }

    fun updateConfig(newConfig: SignLanguageConfig, newStrings: SignStrings) {
        config = newConfig; strings = newStrings
        passthrough?.smartPassthrough = newConfig.smartPassthrough
        passthrough?.longPressEnabled = newConfig.longPressToTranslate
        player?.updateConfig(newConfig, newStrings)
    }

    /** Remove the overlay for this Activity and release its decoders; leave controller state alone. */
    fun teardown() {
        scope.cancel()
        fab?.remove(); fab = null
        player?.let { controller.clearVideoController(it); it.release() }
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val pass = passthrough
        val root = hostRoot
        if (pass != null && root != null) {
            val params = pass.layoutParams
            pass.removeView(root)
            content.removeView(pass)
            content.addView(root, params)
        }
        passthrough = null; overlay = null; player = null; hostRoot = null
    }
}
