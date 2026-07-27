// signtranslate/src/main/java/com/signfordeaf/signtranslate/floating/SignLanguageFloatingButton.kt

package com.signfordeaf.signtranslate.floating

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.signfordeaf.signtranslate.FloatingButtonConfig
import com.signfordeaf.signtranslate.FloatingButtonIdleBehavior
import com.signfordeaf.signtranslate.R

/**
 * Draggable, edge-sticky floating logo that toggles "tap-to-translate" mode.
 *
 * Faithful native port of the React Native `SignLanguageFloatingButton`:
 * it rests flush against a side edge as a "tab" (flat on the resting edge,
 * rounded on the inner side), can be dragged anywhere, springs to the nearest
 * left/right edge on release, and after [FloatingButtonConfig.idleDelayMs] of
 * inactivity tucks 35% off the edge ("peek") while fading. The first touch on a
 * peeked button only wakes it; a tap in place toggles the mode. While the mode
 * is active a hint bubble is shown above the button.
 */
class SignLanguageFloatingButton(
    private val activity: Activity,
    private val config: FloatingButtonConfig,
    private val hint: String,
    private val onToggle: (active: Boolean) -> Unit
) {

    private companion object {
        const val TAP_MOVEMENT_THRESHOLD_DP = 6f
        const val IDLE_OPACITY = 0.55f
        const val PEEK_HIDDEN_FRACTION = 0.35f
        const val HINT_WIDTH_DP = 180f
        const val PREFS = "weaccess_sl_prefs"
        const val KEY_HINT_COUNT = "weaccess_sl_hint_shown_count"
    }

    private val density = activity.resources.displayMetrics.density
    private val sizePx = (config.sizeDp * density).toInt()
    private val radius = sizePx / 2f
    private val tapSlop = TAP_MOVEMENT_THRESHOLD_DP * density

    private val handler = Handler(Looper.getMainLooper())
    private var idleRunnable: Runnable? = null

    private var active = false

    // Whether the hint bubble should be displayed for the CURRENT activation.
    // Decided once per toggle-to-active (respecting the persisted show budget) so
    // that later reflows (drag/settle/wake) don't re-derive it and over-show.
    private var hintShownThisActivation = false

    // Which edge the button rests against; null while dragging (full circle).
    private var restingIsLeft: Boolean? = false // starts on the right edge
    private var isResting = true
    private var isPeeked = false
    private var wasPeekedOnGrant = false

    // Snapped (un-peeked) top-left position, so a peeked button can restore.
    private var snappedX = 0f
    private var snappedY = 0f

    private var overlay: FrameLayout? = null
    private var button: FrameLayout? = null
    private var icon: ImageView? = null
    private var hintView: TextView? = null

    private fun dp(v: Float) = v * density
    private fun parse(color: String, fallback: Int) =
        try { Color.parseColor(color) } catch (e: Exception) { fallback }

    // Bounds for the button's top-left within the overlay.
    private fun minX() = 0f
    private fun maxX() = (overlay?.width ?: activity.resources.displayMetrics.widthPixels).toFloat() - sizePx
    private fun minY() = 0f
    private fun maxY() = (overlay?.height ?: activity.resources.displayMetrics.heightPixels).toFloat() - sizePx

    fun show() {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return

        // Full-screen, touch-transparent overlay (only the button captures touches).
        val ov = FrameLayout(activity)
        ov.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        ov.isClickable = false
        ov.clipChildren = false
        ov.clipToPadding = false

        // Hint bubble (added first, sits under the button in z-order).
        val hv = TextView(activity).apply {
            layoutParams = FrameLayout.LayoutParams((HINT_WIDTH_DP * density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            background = GradientDrawable().apply {
                cornerRadius = dp(12f)
                setColor(Color.parseColor("#CC000000")) // rgba(0,0,0,0.8)
            }
            setPadding(dp(12f).toInt(), dp(8f).toInt(), dp(12f).toInt(), dp(8f).toInt())
            setTextColor(Color.WHITE)
            textSize = 13f
            gravity = Gravity.CENTER
            maxLines = 2
            // NB: `text = hint` here would resolve `hint` to TextView.hint (placeholder),
            // not our constructor param — qualify it explicitly.
            text = this@SignLanguageFloatingButton.hint
            visibility = View.GONE
        }
        ov.addView(hv)

        // The button "tab".
        val btn = FrameLayout(activity)
        btn.layoutParams = FrameLayout.LayoutParams(sizePx, sizePx)
        btn.elevation = dp(6f)

        val ic = ImageView(activity).apply {
            val s = (sizePx * 0.6f).toInt()
            layoutParams = FrameLayout.LayoutParams(s, s, Gravity.CENTER)
            setImageResource(R.drawable.logo_head)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        btn.addView(ic)

        ov.addView(btn)
        content.addView(ov)

        overlay = ov
        button = btn
        icon = ic
        hintView = hv

        applyShape()
        applyIconTint()

        // Initial rest position: middle of the right edge, flush.
        btn.post {
            snappedX = maxX()
            snappedY = (minY() + maxY()) / 2f
            btn.x = snappedX
            btn.y = snappedY
            restingIsLeft = false
            isResting = true
            applyShape()
            attachTouchHandling(btn)
            scheduleIdle()
        }
    }

    fun remove() {
        cancelIdle()
        overlay?.let { (it.parent as? ViewGroup)?.removeView(it) }
        overlay = null; button = null; icon = null; hintView = null
    }

    // MARK: - Appearance

    private fun applyShape() {
        val btn = button ?: return
        val fill = if (active) parse(config.activeBackgroundColor, Color.parseColor("#6750A4"))
                   else parse(config.backgroundColor, Color.WHITE)
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadii = radiiForSide(restingIsLeft)
            if (!active) setStroke(dp(2f).toInt(), parse(config.borderColor, Color.parseColor("#6750A4")))
        }
        btn.background = bg
    }

    /** Flat on the resting edge, rounded on the inner side; full circle while dragging (null). */
    private fun radiiForSide(isLeft: Boolean?): FloatArray {
        val r = radius
        return when (isLeft) {
            // resting left: flat left corners, rounded right
            true -> floatArrayOf(0f, 0f, r, r, r, r, 0f, 0f)
            // resting right: flat right corners, rounded left
            false -> floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r)
            // dragging: full circle
            null -> floatArrayOf(r, r, r, r, r, r, r, r)
        }
    }

    private fun applyIconTint() {
        val tint = if (active) parse(config.activeIconColor, Color.WHITE)
                   else parse(config.iconColor, Color.parseColor("#6750A4"))
        icon?.setColorFilter(tint, PorterDuff.Mode.SRC_IN)
    }

    // MARK: - Touch handling

    @SuppressLint("ClickableViewAccessibility")
    private fun attachTouchHandling(btn: FrameLayout) {
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0f
        var startY = 0f
        var dragging = false

        btn.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = snappedX
                    startY = snappedY
                    dragging = false
                    cancelIdle()
                    wasPeekedOnGrant = isPeeked
                    isPeeked = false
                    // Wake: restore to snapped position as a full circle while dragging.
                    isResting = false
                    restingIsLeft = null
                    view.animate().cancel()
                    view.x = startX
                    view.y = startY
                    view.alpha = 1f
                    applyShape()
                    updateHint()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragging && (kotlin.math.abs(dx) > tapSlop || kotlin.math.abs(dy) > tapSlop)) {
                        dragging = true
                    }
                    if (dragging) {
                        view.x = (startX + dx).coerceIn(minX(), maxX().coerceAtLeast(0f))
                        view.y = (startY + dy).coerceIn(minY(), maxY().coerceAtLeast(0f))
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!dragging) {
                        if (wasPeekedOnGrant) {
                            // First touch on a peeked button only wakes it — no toggle.
                            settleAt(snappedX, snappedY, restingIsLeft ?: false)
                        } else {
                            toggleActive()
                            settleAt(snappedX, snappedY, restingIsLeft ?: false)
                        }
                    } else {
                        // Snap to nearest edge, keep vertical free.
                        val x = view.x.coerceIn(minX(), maxX())
                        val y = view.y.coerceIn(minY(), maxY())
                        val isLeft = x + sizePx / 2f < (minX() + maxX() + sizePx) / 2f
                        settleAt(if (isLeft) minX() else maxX(), y, isLeft)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun toggleActive() {
        active = !active
        applyShape()
        applyIconTint()
        onToggle(active)
        if (active) decideHintForActivation() else hintShownThisActivation = false
        updateHint()
    }

    /**
     * Externally sync the visual active state — e.g. when tap-to-translate is toggled
     * from a switch elsewhere. Unlike [toggleActive] this does NOT fire [onToggle], so
     * it won't loop back into the caller.
     */
    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        applyShape()
        applyIconTint()
        if (active) decideHintForActivation() else hintShownThisActivation = false
        updateHint()
    }

    private fun settleAt(targetX: Float, targetY: Float, isLeft: Boolean) {
        val btn = button ?: return
        snappedX = targetX
        snappedY = targetY
        restingIsLeft = isLeft
        isResting = true
        isPeeked = false
        applyShape()
        btn.animate()
            .x(targetX).y(targetY).alpha(1f)
            .setInterpolator(OvershootInterpolator(1.1f))
            .setDuration(260)
            .withEndAction { scheduleIdle() }
            .start()
        updateHint()
    }

    // MARK: - Idle peek

    private fun scheduleIdle() {
        cancelIdle()
        if (config.idleBehavior == FloatingButtonIdleBehavior.NONE) return
        idleRunnable = Runnable { runIdle() }
        handler.postDelayed(idleRunnable!!, config.idleDelayMs)
    }

    private fun cancelIdle() {
        idleRunnable?.let { handler.removeCallbacks(it) }
        idleRunnable = null
    }

    private fun runIdle() {
        val btn = button ?: return
        if (!isResting) return
        isPeeked = true
        updateHint()
        val anim = btn.animate().alpha(IDLE_OPACITY).setInterpolator(DecelerateInterpolator()).setDuration(220)
        if (config.idleBehavior == FloatingButtonIdleBehavior.PEEK) {
            val hidden = sizePx * PEEK_HIDDEN_FRACTION
            val peekX = if (restingIsLeft == true) minX() - hidden else maxX() + hidden
            anim.x(peekX)
        }
        anim.start()
    }

    // MARK: - Hint bubble

    /**
     * Decide, once per activation, whether the hint bubble should be shown — and
     * if so consume one unit of the persisted budget ([FloatingButtonConfig.hintMaxShows]).
     * After the budget is exhausted the hint never appears again, across launches.
     */
    private fun decideHintForActivation() {
        if (hint.isEmpty()) {
            hintShownThisActivation = false
            return
        }
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val count = prefs.getInt(KEY_HINT_COUNT, 0)
        if (count >= config.hintMaxShows) {
            hintShownThisActivation = false
            return
        }
        prefs.edit().putInt(KEY_HINT_COUNT, count + 1).apply()
        hintShownThisActivation = true
    }

    /** Show the hint above the button while active, not peeked, and chosen for this activation. */
    private fun updateHint() {
        val hv = hintView ?: return
        val shouldShow = active && !isPeeked && hintShownThisActivation && hint.isNotEmpty()
        if (!shouldShow) {
            hv.visibility = View.GONE
            return
        }
        hv.text = hint
        hv.visibility = View.VISIBLE
        positionHint(hv)
    }

    /** Position the bubble flush above the button, measuring it first so the very
     *  first show isn't mis-placed (height not yet known) behind the button. */
    private fun positionHint(hv: TextView) {
        val place = {
            var w = hv.width
            var h = hv.height
            if (w == 0 || h == 0) {
                hv.measure(
                    View.MeasureSpec.makeMeasureSpec((HINT_WIDTH_DP * density).toInt(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                )
                w = hv.measuredWidth
                h = hv.measuredHeight
            }
            // Horizontally align the hint's near side to the button's near side.
            hv.x = if (restingIsLeft == true) snappedX else (snappedX + sizePx - w)
            // Vertically: sit above the button.
            hv.y = snappedY - h - dp(8f)
        }
        place()          // immediate (uses measured size) so the first frame is correct
        hv.post(place)   // and again after layout to settle exact bounds
    }
}
