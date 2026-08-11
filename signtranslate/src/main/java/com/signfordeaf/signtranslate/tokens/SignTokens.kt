// signtranslate/src/main/java/com/signfordeaf/signtranslate/tokens/SignTokens.kt

package com.signfordeaf.signtranslate.tokens

import android.content.Context
import android.util.TypedValue
import android.view.animation.Interpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator

/**
 * Every fixed value the SDK's own surfaces are drawn from (doc 05 — Design tokens).
 *
 * Brand colors are NOT tokens — they come from the integration's theme so each app keeps its
 * palette. Everything here is fixed so the player, control bar, pills and hint bubble stay
 * visually one system across platforms.
 *
 * All sizes are in **dp** (density-independent points). Convert with [dp] when you need px.
 */
object SignTokens {

    // --- Radii (doc 05). Deliberately small. `radiusLarge` is overridable via theme cornerRadius.
    const val RADIUS_LARGE_DP = 16f   // stage + control bar outer radius
    const val RADIUS_MEDIUM_DP = 12f  // action pills (feedback, collapse/close)
    const val RADIUS_SMALL_DP = 8f    // speed button, hint bubble, logo badge

    // --- Spacing
    const val SPACE_XS_DP = 4f
    const val SPACE_SM_DP = 8f
    const val SPACE_MD_DP = 12f
    const val SPACE_LG_DP = 16f
    const val SPACE_XL_DP = 24f

    // --- Sizes
    const val CONTROL_SIZE_DP = 44f       // tap target of EVERY control and pill button
    const val ICON_SIZE_DP = 20f          // icon inside a control
    const val PRIMARY_ICON_SIZE_DP = 24f  // play/pause glyph + the mark in the collapsed bar
    const val PILL_OVERFLOW_FRACTION = 0.8f      // how much of the window pill hangs above the stage
    const val MIN_STAGE_WIDTH_DP = 3 * 44f + 8f  // 140 — narrowest the stage may get
    const val MAX_PLAYER_SCREEN_FRACTION = 0.42f // share of screen height the whole player may take

    // --- Default avatar aspect (bundled idle clips are 900x828). Used before a video reports one.
    const val DEFAULT_ASPECT = 900f / 828f  // ~1.087

    // --- Loading veil (doc 05 / 10)
    const val LOADING_BLUR_SIGMA = 2.5f
    const val LOADING_INDICATOR_SIZE_DP = 24f
    const val LOADING_INDICATOR_STROKE_DP = 2.5f

    // --- Caption (doc 05 / 06)
    const val CAPTION_FONT_SIZE_SP = 12f
    const val CAPTION_LINE_HEIGHT = 1.35f  // multiplier
    const val CAPTION_MAX_LINES = 2
    const val CAPTION_HOLD_MS = 1600L
    const val CAPTION_SCROLL_DP_PER_SEC = 16f
    const val CAPTION_SCROLL_MIN_MS = 400L
    const val CAPTION_SCROLL_MAX_MS = 8000L
    const val CAPTION_REWIND_MS = 450L
    const val CAPTION_RESUME_AFTER_MANUAL_MS = 4000L

    // --- Motion (doc 05)
    const val CARD_TRANSITION_MS = 320L
    const val COLLAPSE_TRANSITION_MS = 260L
    const val SNAP_TRANSITION_MS = 300L

    /** Entrances — ease-out-cubic. */
    fun emphasized(): Interpolator = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)

    /** Snap-to-edge — ease-out-back, with its slight overshoot. */
    fun settle(): Interpolator = OvershootInterpolator(1.1f)

    // --- Elevation (doc 05). Shadow colors as ARGB.
    const val FLOATING_SHADOW_ALPHA = 0x1F  // 12%
    const val PILL_SHADOW_ALPHA = 0x26      // 15%
    const val FLOATING_SHADOW_ELEVATION_DP = 12f
    const val PILL_SHADOW_ELEVATION_DP = 6f

    // --- Neutral overlays (doc 05). Intentionally NOT themeable — they sit over unpredictable
    // content (video frames, host app) where a brand color cannot be trusted to stay legible.
    const val HINT_BACKGROUND = 0xCC000000.toInt()  // black 80%
    const val HINT_FOREGROUND = 0xFFFFFFFF.toInt()  // white
    const val CONTROL_FILL = 0x1FFFFFFF              // white 12% — fill behind the speed button
    const val CONTROL_BORDER = 0x59FFFFFF            // white 35% — speed button border
    const val DISABLED_OPACITY = 0.4f

    // --- Hint bubble (doc 07)
    const val HINT_WIDTH_DP = 200f
    const val HINT_MAX_LINES = 3
    const val HINT_FONT_SIZE_SP = 13f
    const val HINT_LINE_HEIGHT = 1.3f
    const val HINT_GAP_DP = 8f

    // --- Floating button (doc 07)
    const val TAP_MOVEMENT_THRESHOLD_DP = 6f
    const val IDLE_OPACITY = 0.55f
    const val PEEK_HIDDEN_FRACTION = 0.35f
    const val BUTTON_LOGO_FRACTION = 0.6f

    /** dp -> px for [context]'s current density. */
    fun dp(context: Context, value: Float): Float = value * context.resources.displayMetrics.density

    /** dp -> integer px. */
    fun dpInt(context: Context, value: Float): Int = (value * context.resources.displayMetrics.density).toInt()

    /** sp -> px, honoring the user's font scale. */
    fun sp(context: Context, value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, context.resources.displayMetrics)
}
