// signtranslate/src/main/java/com/signfordeaf/signtranslate/text/PassthroughContainer.kt

package com.signfordeaf.signtranslate.text

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Wraps the host's content and does smart-passthrough tap classification (doc 08).
 *
 * The decision is made **during hit testing**, not after: on ACTION_DOWN it classifies the touch
 * and returns `false` (so scrollables and host recognizers see the DOWN); it only claims on
 * ACTION_UP, and only for a real tap on translatable text. Claiming routes the UP to
 * [onTouchEvent] and sends ACTION_CANCEL to the host children — so a labelled button is *read, not
 * pressed*.
 */
class PassthroughContainer(context: Context) : FrameLayout(context) {

    /** Whether tap mode is live: SDK enabled, player open **and** expanded. */
    var tapModeActive: Boolean = false

    /** false restores v1 capture (claim every tap, return the deepest text). */
    var smartPassthrough: Boolean = true

    /** Long press translates host-tappable text (doc 08). */
    var longPressEnabled: Boolean = false

    /** Fired with the classified text and the character offset under the finger (may be null). */
    var onTranslate: ((text: String, charOffset: Int?) -> Unit)? = null

    /** The host content view classification runs against (child index 0). */
    var hostContent: View? = null

    /**
     * True when (x, y) is over the SDK's own overlay (the corner player). Such taps belong to the
     * player's controls — the passthrough must not classify or claim them, or a control tap would be
     * cancelled and the host text *underneath* the player would be translated instead.
     */
    var isOverSdkUi: ((x: Float, y: Float) -> Boolean)? = null

    private val probe = TapTargetProbe()
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressTimeout = 600L // deliberately longer than the framework default of 500 (doc 08)
    // Hold this long on a labelled control and the tap is treated as a long press: the SDK steps
    // aside so the control performs its own action, instead of translating its label.
    private val controlActionThreshold = ViewConfiguration.getLongPressTimeout().toLong()

    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var pending: TapResult? = null
    private var becameScroll = false

    private val handler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!tapModeActive) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y; downTime = ev.eventTime; becameScroll = false
                // A tap on the SDK's own player must never be classified/claimed — let its controls
                // handle it, and never translate the host text sitting behind the player.
                pending = if (isOverSdkUi?.invoke(ev.x, ev.y) == true) null else classifyAt(ev.x, ev.y)
                if (pending != null) scheduleLongPress(ev.x, ev.y)
                return false // let children see the DOWN
            }
            MotionEvent.ACTION_MOVE -> {
                if (!becameScroll && (abs(ev.x - downX) > slop || abs(ev.y - downY) > slop)) {
                    becameScroll = true
                    cancelPendingLongPress()
                }
                return false
            }
            MotionEvent.ACTION_UP -> {
                cancelPendingLongPress()
                // A child (e.g. a ScrollView) is handling the gesture. Intercepting on UP only
                // sends CANCEL to that child — the framework never routes the UP to our
                // onTouchEvent — so fire the translation right here and claim (to cancel the child,
                // which is what makes a labelled button *read, not pressed*).
                if (shouldClaim()) {
                    // A long press on a labelled control performs the control's own action instead:
                    // don't claim, so the child button/checkbox receives the gesture.
                    if (isControlLongPress(ev)) { pending = null; return false }
                    fireTranslate()
                    return true
                }
                return false
            }
            MotionEvent.ACTION_CANCEL -> cancelPendingLongPress()
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        // Reached only when no child consumed the DOWN (a tap on plain, non-scrolling host area).
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE ->
                if (!becameScroll && (abs(ev.x - downX) > slop || abs(ev.y - downY) > slop)) {
                    becameScroll = true
                    cancelPendingLongPress()
                }
            MotionEvent.ACTION_UP -> {
                cancelPendingLongPress()
                if (shouldClaim() && !isControlLongPress(ev)) fireTranslate() else pending = null
            }
        }
        return true
    }

    /** A deliberate hold on a labelled control — the SDK yields so the control does its own thing. */
    private fun isControlLongPress(ev: MotionEvent): Boolean =
        pending?.fromControl == true && (ev.eventTime - downTime) >= controlActionThreshold

    private fun shouldClaim(): Boolean {
        val p = pending ?: return false
        return !becameScroll && (
            (smartPassthrough && p.outcome == TapOutcome.TEXT && p.text != null) ||
                (!smartPassthrough && p.text != null)
            )
    }

    private fun fireTranslate() {
        pending?.let { if (it.text != null) onTranslate?.invoke(it.text, it.charOffset) }
        pending = null
    }

    private fun classifyAt(x: Float, y: Float): TapResult {
        val host = hostContent ?: return TapResult(TapOutcome.NONE)
        return probe.classify(host, x - host.left, y - host.top)
    }

    private fun scheduleLongPress(x: Float, y: Float) {
        if (!longPressEnabled) return
        cancelPendingLongPress()
        val r = Runnable {
            val host = hostContent ?: return@Runnable
            // Reaches host-tappable text; ignores interactivity but stays out of editable text.
            probe.deepestTappableText(host, x - host.left, y - host.top)?.let { onTranslate?.invoke(it, null) }
        }
        longPressRunnable = r
        handler.postDelayed(r, longPressTimeout)
    }

    private fun cancelPendingLongPress() {
        longPressRunnable?.let { handler.removeCallbacks(it) }
        longPressRunnable = null
    }
}
