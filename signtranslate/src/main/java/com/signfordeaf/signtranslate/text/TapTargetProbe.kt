// signtranslate/src/main/java/com/signfordeaf/signtranslate/text/TapTargetProbe.kt

package com.signfordeaf.signtranslate.text

import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

/** How the SDK classifies a touch (doc 08). */
enum class TapOutcome { TEXT, INTERACTIVE, NONE }

/**
 * The classification result. [charOffset] is the character index under the finger within [text]
 * (from `TextView.getOffsetForPosition`), used to pick the tapped sentence; 0 for a control label.
 */
data class TapResult(
    val outcome: TapOutcome,
    val text: String? = null,
    val charOffset: Int? = null,
    /** True when [text] is a control's own label (Button/CheckBox/…), so a long press can instead
     *  perform the control's action while a tap still translates the label. */
    val fromControl: Boolean = false
)

/**
 * Classifies a touch position against the host view hierarchy (doc 08). Walks deepest-first,
 * declining anything that is not translatable text so the host's own recognizer owns the pointer.
 *
 * Known limitations vs the Flutter hit-test (doc 15): host handlers wired purely via
 * `setOnTouchListener` are invisible (no public getter) — the ambient-80% guard partly
 * compensates; content in a separate window (Dialog/PopupWindow) is not in this tree; the
 * CheckBox/Switch box-vs-label split is an x-region heuristic, not a true sub-hit-test.
 */
class TapTargetProbe {

    private class Node(val view: View, val localX: Float, val localY: Float)

    /** Full smart-passthrough classification. */
    fun classify(root: View, x: Float, y: Float): TapResult {
        val chain = ArrayList<Node>()
        buildChain(root, x, y, chain)
        if (chain.isEmpty()) return TapResult(TapOutcome.NONE)

        var textCandidate: String? = null
        var candidateNode: Node? = null
        // Walk from the deepest node outward toward the root.
        for (i in chain.indices.reversed()) {
            val node = chain[i]
            val v = node.view

            // 1. Stop at the innermost scrollable — its listeners are ancestors of the content.
            if (isScrollable(v)) break

            // 2. Editable field keeps its focus and caret.
            if (v is EditText && isEditable(v)) return TapResult(TapOutcome.INTERACTIVE)

            val ownText = translatableText(v)

            // 3. CheckBox/Switch: read the label, tick the box (x-region heuristic).
            if (v is CompoundButton) {
                return if (inCompoundControlRegion(v, node.localX) || ownText == null) {
                    TapResult(TapOutcome.INTERACTIVE)
                } else {
                    TapResult(TapOutcome.TEXT, ownText, offsetIn(node), fromControl = true)
                }
            }

            // 4. Interactive marker (skip ambient page-wide handlers).
            if (isInteractiveMarker(v) && !isAmbient(v, root)) {
                candidateNode?.let { if (textCandidate != null) return TapResult(TapOutcome.TEXT, textCandidate, offsetIn(it)) }
                // The control's own label → translatable on a tap, but a long press performs its action.
                if (ownText != null) return TapResult(TapOutcome.TEXT, ownText, offsetIn(node), fromControl = true)
                return TapResult(TapOutcome.INTERACTIVE)
            }

            // 5. Plain text node → remember the deepest translatable text.
            if (ownText != null && textCandidate == null) { textCandidate = ownText; candidateNode = node }
        }

        return if (textCandidate != null) {
            TapResult(TapOutcome.TEXT, textCandidate, candidateNode?.let { offsetIn(it) })
        } else {
            TapResult(TapOutcome.NONE)
        }
    }

    /**
     * Long-press classification (doc 08): reaches text the host made tappable, so it ignores
     * interactivity — but stays out of editable/selectable text. Returns the deepest translatable
     * text, or null when it must not participate.
     */
    fun deepestTappableText(root: View, x: Float, y: Float): String? {
        val chain = ArrayList<Node>()
        buildChain(root, x, y, chain)
        for (i in chain.indices.reversed()) {
            val v = chain[i].view
            if (v is EditText) return null // editable/selectable text: do not participate
            translatableText(v)?.let { return it }
        }
        return null
    }

    /** Descends one hit path root → deepest, recording each node's local coordinates. */
    private fun buildChain(view: View, x: Float, y: Float, out: MutableList<Node>) {
        out.add(Node(view, x, y))
        if (view is ViewGroup) {
            for (i in view.childCount - 1 downTo 0) {
                val child = view.getChildAt(i)
                if (child.visibility != View.VISIBLE) continue
                val cx = x + view.scrollX - child.left - child.translationX
                val cy = y + view.scrollY - child.top - child.translationY
                if (cx >= 0 && cx < child.width && cy >= 0 && cy < child.height) {
                    buildChain(child, cx, cy, out)
                    return
                }
            }
        }
    }

    /** The character index under the finger within a text node, or null if unavailable. */
    private fun offsetIn(node: Node): Int? {
        val tv = node.view as? TextView ?: return null
        return try { tv.getOffsetForPosition(node.localX, node.localY) } catch (_: Throwable) { null }
    }

    private fun translatableText(v: View): String? {
        if (v !is TextView) return null
        val t = v.text?.toString()
        return if (!t.isNullOrEmpty() && GlyphFilter.hasTranslatableContent(t)) t else null
    }

    private fun isEditable(v: EditText): Boolean =
        v.isEnabled && v.inputType != InputType.TYPE_NULL

    private fun isInteractiveMarker(v: View): Boolean = when {
        v is android.widget.Button -> true
        v is ImageButton -> true
        v is CompoundButton -> true
        v is SeekBar -> true
        v.isClickable && v.hasOnClickListeners() -> true
        v.isLongClickable -> true
        else -> false
    }

    private fun isScrollable(v: View): Boolean =
        v is ScrollView || v is AbsListView ||
            v is androidx.core.widget.NestedScrollView ||
            v.canScrollVertically(1) || v.canScrollVertically(-1) ||
            v.canScrollHorizontally(1) || v.canScrollHorizontally(-1)

    /** A handler covering ≥80% of the probed area is ambient (e.g. a dismiss-keyboard wrapper). */
    private fun isAmbient(v: View, root: View): Boolean {
        val vArea = v.width.toLong() * v.height.toLong()
        val rArea = root.width.toLong() * root.height.toLong()
        return rArea > 0 && vArea >= 0.8 * rArea
    }

    /** Whether [localX] falls in a CompoundButton's check/thumb drawable region. */
    private fun inCompoundControlRegion(v: CompoundButton, localX: Float): Boolean {
        val left = v.compoundPaddingLeft.toFloat()
        val right = (v.width - v.compoundPaddingRight).toFloat()
        return localX <= left || localX >= right
    }
}
