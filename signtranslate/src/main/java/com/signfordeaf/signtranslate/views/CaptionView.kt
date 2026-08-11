// signtranslate/src/main/java/com/signfordeaf/signtranslate/views/CaptionView.kt

package com.signfordeaf.signtranslate.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.MotionEvent
import android.widget.ScrollView
import android.widget.TextView
import com.signfordeaf.signtranslate.tokens.SignTokens
import kotlin.math.max

/**
 * The sentence being translated (doc 06). Two lines at 12sp; if the text is taller it auto-scrolls
 * vertically with a hold at each end, and pauses for 4s after the user scrolls by hand. A new
 * sentence jumps back to the top. Shares the control surface — draws no background of its own.
 */
class CaptionView(context: Context) : ScrollView(context) {

    private val label = TextView(context).apply {
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, SignTokens.CAPTION_FONT_SIZE_SP)
        setLineSpacing(0f, SignTokens.CAPTION_LINE_HEIGHT)
        typeface = Typeface.DEFAULT
        gravity = Gravity.CENTER_HORIZONTAL
    }

    private var animator: ValueAnimator? = null
    private var resumeAt = 0L
    private var current: String = ""

    init {
        isVerticalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        addView(label)
        // Vertical padding sets the visible height (two lines); horizontal padding keeps the text
        // off the rounded corners of the control block. A little extra padding below the text keeps
        // it clear of the block's bottom edge.
        val topPad = SignTokens.dpInt(context, SignTokens.SPACE_SM_DP)
        val bottomPad = SignTokens.dpInt(context, SignTokens.SPACE_MD_DP)
        val hPad = SignTokens.dpInt(context, SignTokens.SPACE_MD_DP)
        label.setPadding(hPad, topPad, hPad, bottomPad)
    }

    fun setTextColor(color: Int) = label.setTextColor(color)

    fun setSentence(text: String) {
        if (text == current) return
        current = text
        label.text = text
        cancelAnim()
        scrollTo(0, 0)
        post { maybeStartScroll() }
    }

    private fun maybeStartScroll() {
        // Only a caption longer than the visible two lines scrolls; anything that fits stays still.
        if (label.lineCount <= SignTokens.CAPTION_MAX_LINES) return
        val overflow = label.height - height
        if (overflow <= 0) return
        val travelMs = (overflow / SignTokens.dp(context, SignTokens.CAPTION_SCROLL_DP_PER_SEC) * 1000f)
            .toLong().coerceIn(SignTokens.CAPTION_SCROLL_MIN_MS, SignTokens.CAPTION_SCROLL_MAX_MS)
        animator = ValueAnimator.ofInt(0, overflow).apply {
            duration = travelMs
            startDelay = SignTokens.CAPTION_HOLD_MS
            addUpdateListener { scrollTo(0, it.animatedValue as Int) }
            addListener(onEnd = {
                postDelayed({ if (current.isNotEmpty()) rewind() }, SignTokens.CAPTION_HOLD_MS)
            })
            start()
        }
    }

    private fun rewind() {
        ValueAnimator.ofInt(scrollY, 0).apply {
            duration = SignTokens.CAPTION_REWIND_MS
            addUpdateListener { scrollTo(0, it.animatedValue as Int) }
            addListener(onEnd = { post { maybeStartScroll() } })
            start()
        }
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        // A manual scroll stops the auto-scroll; it resumes 4s after the user stops.
        cancelAnim()
        resumeAt = ev.eventTime + SignTokens.CAPTION_RESUME_AFTER_MANUAL_MS
        postDelayed({ if (System.currentTimeMillis() >= resumeAt - 50) maybeStartScroll() },
            SignTokens.CAPTION_RESUME_AFTER_MANUAL_MS)
        return super.onTouchEvent(ev)
    }

    private fun cancelAnim() {
        animator?.cancel()
        animator = null
    }

    /** The view's measured height is clamped to two lines plus the label's vertical padding. */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val lineHeight = (SignTokens.sp(context, SignTokens.CAPTION_FONT_SIZE_SP) * SignTokens.CAPTION_LINE_HEIGHT)
        val twoLines = (lineHeight * SignTokens.CAPTION_MAX_LINES).toInt()
        val pad = label.paddingTop + label.paddingBottom
        val h = MeasureSpec.makeMeasureSpec(twoLines + pad, MeasureSpec.EXACTLY)
        super.onMeasure(widthMeasureSpec, h)
    }
}

private fun ValueAnimator.addListener(onEnd: () -> Unit) {
    addListener(object : android.animation.AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: android.animation.Animator) { onEnd() }
    })
}
