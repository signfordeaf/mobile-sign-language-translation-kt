// signtranslate/src/main/java/com/signfordeaf/signtranslate/views/SignPlayerView.kt

package com.signfordeaf.signtranslate.views

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.TextureView
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.RawResourceDataSource
import androidx.media3.exoplayer.ExoPlayer
import com.signfordeaf.signtranslate.R
import com.signfordeaf.signtranslate.SignLanguageConfig
import com.signfordeaf.signtranslate.core.SignState
import com.signfordeaf.signtranslate.core.SignUiState
import com.signfordeaf.signtranslate.core.SignerResolver
import com.signfordeaf.signtranslate.core.VideoController
import com.signfordeaf.signtranslate.l10n.SignStrings
import com.signfordeaf.signtranslate.tokens.ContrastGuard
import com.signfordeaf.signtranslate.tokens.SignTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min

/** Fixed width of the speed chip, wide enough for "1.5x" with modest side padding (doc 06). */
private const val SPEED_CHIP_WIDTH_DP = 60f

/**
 * The non-modal corner player (docs 05/06/10). Two independent blocks — the stage and the control
 * block (bar + caption) — with the window pill hanging above the stage's top-right. Renders one-way
 * from [SignUiState]; also serves as the controller's [VideoController] for the translation player.
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class SignPlayerView(
    context: Context,
    private var config: SignLanguageConfig,
    private var strings: SignStrings
) : FrameLayout(context), VideoController {

    var onCollapse: (() -> Unit)? = null
    var onExpand: (() -> Unit)? = null
    var onClose: (() -> Unit)? = null
    var onTogglePlay: (() -> Unit)? = null
    var onCycleSpeed: (() -> Unit)? = null
    var onToggleLoop: (() -> Unit)? = null
    var onVideoEnded: (() -> Unit)? = null

    private val d = context.resources.displayMetrics.density

    // Colors resolved through the contrast guard.
    private val primary = parse(config.theme.primaryColor, 0xFF6750A4.toInt())
    private val surface = parse(config.theme.surfaceColor, Color.WHITE)
    private val onPrimary get() = ContrastGuard.resolve(parse(config.theme.onPrimaryColor, Color.WHITE), primary)
    private val onSurface get() = ContrastGuard.resolve(parse(config.theme.textColor, 0xFF1C1B1F.toInt()), surface)
    private val cornerPx get() = config.theme.cornerRadius * d

    // --- Views
    private val column = LinearLayout(context)
    private val stageWrap = FrameLayout(context)
    private val stageCard = FrameLayout(context)
    // TextureViews, not PlayerView/SurfaceView: a SurfaceView punches a transparent hole through
    // the overlay (the host app shows through the stage). TextureView composites in-view, so the
    // stage's surface fill shows when there's no video, and it can be blurred for the veil.
    private val idleSurface = TextureView(context)
    private val idleWrap = FrameLayout(context)
    private val translationSurface = TextureView(context)
    private val veilScrim = View(context)
    private val spinner = ProgressBar(context)
    private val messageText = TextView(context)
    private val markBadge = ImageView(context)
    private val pill = LinearLayout(context)
    private val controlCard = LinearLayout(context)
    private val controlBar = LinearLayout(context)
    private val playPause = ImageView(context)
    private val speedButton = TextView(context)
    private val loopButton = ImageView(context)
    private val caption = CaptionView(context)
    private val collapsedBar = LinearLayout(context)
    private val collapsedMark = ImageView(context)

    private var idlePlayer: ExoPlayer? = null
    private var translationPlayer: ExoPlayer? = null
    private var resolvedRawName: String? = null

    // Drag
    private val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop
    private var downRawX = 0f
    private var downRawY = 0f
    private var viewStartX = 0f
    private var viewStartY = 0f
    private var dragging = false

    init {
        clipChildren = false
        clipToPadding = false
        buildStage()
        buildControls()
        buildCollapsedBar()

        column.orientation = LinearLayout.VERTICAL
        column.clipChildren = false
        column.clipToPadding = false
        column.addView(stageWrap)
        (controlCard.layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(SignTokens.SPACE_SM_DP)
        column.addView(controlCard)

        // The column is pushed down by the pill's overflow so the pill sits inside the root's
        // touchable bounds (above the stage). The collapsed bar sits flush at the top instead.
        val pillOverflowPx = (SignTokens.PILL_OVERFLOW_FRACTION * SignTokens.CONTROL_SIZE_DP * d).toInt()
        addView(column, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).also {
            it.topMargin = pillOverflowPx
        })
        addView(collapsedBar, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))

        buildPill() // added last so it draws on top of the stage
        applyThemeChrome()
    }

    // ---------------------------------------------------------------- Build

    private fun buildStage() {
        stageWrap.clipChildren = false
        idleSurface.isOpaque = false
        translationSurface.isOpaque = false
        translationSurface.visibility = GONE

        val fill = FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        // The idle loop lives in its own wrapper so the loading blur can be applied to the wrapper's
        // composited layer — a RenderEffect set directly on a TextureView does not blur the video on
        // some devices.
        idleWrap.addView(idleSurface, fill)
        idleWrap.visibility = GONE
        stageCard.addView(idleWrap, FrameLayout.LayoutParams(fill))
        stageCard.addView(translationSurface, FrameLayout.LayoutParams(fill))

        // A translucent darkening laid over the idle signer while loading (reliable on every device,
        // reads clearly as "not the final video").
        veilScrim.setBackgroundColor(withAlpha(Color.BLACK, 0.45f))
        veilScrim.visibility = GONE
        stageCard.addView(veilScrim, FrameLayout.LayoutParams(fill))

        spinner.isIndeterminate = true
        spinner.indeterminateTintList = android.content.res.ColorStateList.valueOf(primary)
        val sp = dp(SignTokens.LOADING_INDICATOR_SIZE_DP)
        stageCard.addView(spinner, FrameLayout.LayoutParams(sp, sp, Gravity.CENTER))
        spinner.visibility = GONE

        messageText.setTextColor(onSurface)
        messageText.gravity = Gravity.CENTER
        messageText.setPadding(dp(SignTokens.SPACE_MD_DP), dp(SignTokens.SPACE_MD_DP), dp(SignTokens.SPACE_MD_DP), dp(SignTokens.SPACE_MD_DP))
        messageText.visibility = GONE
        stageCard.addView(messageText, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER))

        markBadge.setImageResource(R.drawable.logo_head)
        markBadge.setColorFilter(primary, PorterDuff.Mode.SRC_IN)
        val mb = dp(SignTokens.PRIMARY_ICON_SIZE_DP)
        stageCard.addView(markBadge, FrameLayout.LayoutParams(mb, mb, Gravity.TOP or Gravity.START).also {
            it.leftMargin = dp(SignTokens.SPACE_SM_DP + SignTokens.SPACE_XS_DP) // ~12dp off the corner
            it.topMargin = dp(SignTokens.SPACE_SM_DP + SignTokens.SPACE_XS_DP)
        })

        stageCard.background = rounded(surface, cornerPx)
        stageCard.clipToOutline = true
        stageCard.outlineProvider = ViewOutlineProvider.BACKGROUND
        stageWrap.addView(stageCard, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private fun buildControls() {
        controlCard.orientation = LinearLayout.VERTICAL
        controlCard.background = rounded(primary, cornerPx)

        controlBar.orientation = LinearLayout.HORIZONTAL
        controlBar.gravity = Gravity.CENTER_VERTICAL

        // Weighted spacers before, between and after the controls spread them evenly across the
        // bar width instead of packing them together in the middle.
        addSpacer()
        control(playPause) { onTogglePlay?.invoke() }
        playPause.setImageResource(R.drawable.sfd_ic_play)
        controlBar.addView(playPause)

        if (config.card.showSpeed) {
            addSpacer()
            speedButton.text = "1.0x"
            speedButton.setTextColor(onPrimary)
            speedButton.gravity = Gravity.CENTER
            speedButton.textSize = 13f
            speedButton.typeface = android.graphics.Typeface.DEFAULT_BOLD
            speedButton.includeFontPadding = false
            // Rounded "chip" — a lighter translucent fill with a hairline border, matching the
            // iOS/RN/web speed control rather than a plain square. A FIXED width (not WRAP) is what
            // gives the "1.0x"/"1.5x" label its left/right breathing room and, crucially, stops the
            // weighted spacers from squeezing (and clipping) the chip on a narrow bar. The inset
            // keeps the visible chip a short pill while the tap target stays a full 44dp.
            speedButton.background = android.graphics.drawable.InsetDrawable(chip(), 0, dp(6f), 0, dp(6f))
            controlBar.addView(
                speedButton,
                LinearLayout.LayoutParams(dp(SPEED_CHIP_WIDTH_DP), dp(SignTokens.CONTROL_SIZE_DP)).also {
                    it.gravity = Gravity.CENTER_VERTICAL
                }
            )
            speedButton.setOnClickListener { onCycleSpeed?.invoke() }
        }
        if (config.card.showLoop) {
            addSpacer()
            control(loopButton) { onToggleLoop?.invoke() }
            loopButton.setImageResource(R.drawable.sfd_ic_loop)
            controlBar.addView(loopButton)
        }
        addSpacer()

        controlCard.addView(controlBar, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(SignTokens.CONTROL_SIZE_DP)))
        caption.setTextColor(onPrimary)
        controlCard.addView(caption, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    private fun buildPill() {
        pill.orientation = LinearLayout.HORIZONTAL
        pill.gravity = Gravity.CENTER
        // Primary-colored, matching the control bar (the theme, not a hardcoded black).
        pill.background = rounded(primary, dp(SignTokens.RADIUS_MEDIUM_DP).toFloat())
        val collapse = ImageView(context)
        control(collapse) { onCollapse?.invoke() }
        collapse.setImageResource(R.drawable.sfd_ic_chevron_down)
        collapse.contentDescription = strings.collapseLabel
        val close = ImageView(context)
        control(close) { onClose?.invoke() }
        close.setImageResource(R.drawable.sfd_ic_close)
        close.contentDescription = strings.close
        pill.addView(collapse)
        pill.addView(close)
        // Added to the root (not the stage) at the top-right so it sits inside the touchable area
        // and overlaps the stage's top edge. A child positioned outside its parent's bounds gets no
        // touch events — which is why the pill's buttons were dead before.
        val h = dp(SignTokens.CONTROL_SIZE_DP)
        addView(pill, LayoutParams(LayoutParams.WRAP_CONTENT, h, Gravity.TOP or Gravity.END))
    }

    private fun buildCollapsedBar() {
        collapsedBar.orientation = LinearLayout.HORIZONTAL
        collapsedBar.gravity = Gravity.CENTER_VERTICAL
        collapsedBar.background = rounded(primary, cornerPx)
        collapsedBar.visibility = GONE
        collapsedMark.setImageResource(R.drawable.logo_head)
        collapsedMark.setColorFilter(onPrimary, PorterDuff.Mode.SRC_IN)
        collapsedMark.scaleType = ImageView.ScaleType.FIT_CENTER
        // Pad the 44dp cell down to a ~24dp glyph so the mark matches the expand/close icons
        // instead of filling the whole bar.
        val markPad = dp((SignTokens.CONTROL_SIZE_DP - SignTokens.PRIMARY_ICON_SIZE_DP) / 2f)
        collapsedMark.setPadding(markPad, markPad, markPad, markPad)
        collapsedBar.addView(
            collapsedMark,
            LinearLayout.LayoutParams(dp(SignTokens.CONTROL_SIZE_DP), dp(SignTokens.CONTROL_SIZE_DP))
        )
        val expand = ImageView(context)
        control(expand) { onExpand?.invoke() }
        expand.setImageResource(R.drawable.sfd_ic_chevron_up)
        val close = ImageView(context)
        control(close) { onClose?.invoke() }
        close.setImageResource(R.drawable.sfd_ic_close)
        collapsedBar.addView(expand)
        collapsedBar.addView(close)
    }

    /** A flexible spacer that spreads the control-bar buttons evenly across its width. */
    private fun addSpacer() {
        controlBar.addView(View(context), LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
    }

    /** A 44x44 tinted control button. */
    private fun control(v: ImageView, onClick: () -> Unit) {
        val s = dp(SignTokens.CONTROL_SIZE_DP)
        v.layoutParams = LinearLayout.LayoutParams(s, s)
        val pad = dp((SignTokens.CONTROL_SIZE_DP - SignTokens.ICON_SIZE_DP) / 2f)
        v.setPadding(pad, pad, pad, pad)
        v.scaleType = ImageView.ScaleType.FIT_CENTER
        v.setColorFilter(onPrimary, PorterDuff.Mode.SRC_IN)
        v.setOnClickListener { onClick() }
    }

    private fun applyThemeChrome() {
        speedButton.setTextColor(onPrimary)
    }

    // ---------------------------------------------------------------- Render

    fun render(state: SignUiState) {
        visibility = if (state.playerVisible) VISIBLE else GONE
        if (!state.playerVisible) { releaseIdle(); return }

        if (state.collapsed) {
            column.visibility = GONE
            pill.visibility = GONE
            collapsedBar.visibility = VISIBLE
            releaseIdle()
            translationPlayer?.pause()
            return
        }
        collapsedBar.visibility = GONE
        column.visibility = VISIBLE
        pill.visibility = VISIBLE

        sizeStage(state)
        renderStage(state)
        renderControls(state)
    }

    private fun renderStage(state: SignUiState) {
        val loading = state.state == SignState.LOADING
        val idle = state.state == SignState.IDLE
        val ready = state.state == SignState.READY
        val message = state.state == SignState.ERROR || state.state == SignState.BLOCKED

        translationSurface.visibility = if (ready) VISIBLE else GONE

        // Idle loop plays in idle + loading; released for ready/message.
        if ((idle || loading)) ensureIdlePlayer(state) else releaseIdle()

        val hasIdleClip = idlePlayer != null
        idleWrap.visibility = if ((idle || loading) && hasIdleClip) VISIBLE else GONE

        // Veil (translucent darkening + spinner) only while loading.
        veilScrim.visibility = if (loading) VISIBLE else GONE
        spinner.visibility = if (loading) VISIBLE else GONE

        // Fallback mark when no clip: big tinted mark while idle, hidden while loading (spinner alone).
        val showFallbackMark = !hasIdleClip && idle
        markBadge.visibility = if (showFallbackMark) VISIBLE else if (message) GONE else VISIBLE

        if (message) {
            messageText.visibility = VISIBLE
            messageText.setTextColor(onSurface)
            messageText.text = if (state.state == SignState.BLOCKED) strings.sensitiveBlocked else strings.error
            markBadge.visibility = GONE // no logo competes with the message
        } else {
            messageText.visibility = GONE
        }
    }

    private fun renderControls(state: SignUiState) {
        val ready = state.state == SignState.READY
        playPause.isEnabled = ready
        playPause.alpha = if (ready) 1f else SignTokens.DISABLED_OPACITY
        playPause.setImageResource(
            if (ready && translationPlayer?.isPlaying == true) R.drawable.sfd_ic_pause else R.drawable.sfd_ic_play
        )
        speedButton.text = formatSpeed(state.speed)
        loopButton.alpha = if (state.looping) 1f else SignTokens.DISABLED_OPACITY

        val hasCaption = state.state == SignState.LOADING || state.state == SignState.READY
        caption.visibility = if (hasCaption) VISIBLE else GONE
        if (hasCaption) caption.setSentence(state.caption)
    }

    // ---------------------------------------------------------------- Sizing (doc 06)

    private fun sizeStage(state: SignUiState) {
        val dm = context.resources.displayMetrics
        val aspect = SignTokens.DEFAULT_ASPECT
        val pillOverflow = SignTokens.PILL_OVERFLOW_FRACTION * SignTokens.CONTROL_SIZE_DP * d
        val gap = SignTokens.SPACE_SM_DP * d
        val controlPx = SignTokens.CONTROL_SIZE_DP * d
        val captionLine = SignTokens.sp(context, SignTokens.CAPTION_FONT_SIZE_SP) * SignTokens.CAPTION_LINE_HEIGHT
        val captionBlock = captionLine * SignTokens.CAPTION_MAX_LINES + SignTokens.SPACE_SM_DP * 2 * d
        val chrome = pillOverflow + gap + controlPx + captionBlock
        val available = dm.heightPixels * SignTokens.MAX_PLAYER_SCREEN_FRACTION - chrome

        // The bar must be wide enough for play + the (wider) speed chip + loop + contact, plus the
        // spacers between them — otherwise the weighted spacers squeeze and clip the chip. The stage
        // is floored to this so the stage and bar stay the same width (doc 06: grow, don't clip).
        val controlsDp = SignTokens.CONTROL_SIZE_DP +
            (if (config.card.showSpeed) SPEED_CHIP_WIDTH_DP else 0f) +
            (if (config.card.showLoop) SignTokens.CONTROL_SIZE_DP else 0f) +
            (if (config.card.showContact) SignTokens.CONTROL_SIZE_DP else 0f)
        val spacerCount = 2 + (if (config.card.showSpeed) 1 else 0) +
            (if (config.card.showLoop) 1 else 0) + (if (config.card.showContact) 1 else 0)
        val minBarPx = (controlsDp + spacerCount * SignTokens.SPACE_SM_DP) * d

        val stageH = min(
            min(config.card.avatarHeight * d, config.card.avatarMaxWidth / aspect * d),
            available
        ).coerceAtLeast(SignTokens.MIN_STAGE_WIDTH_DP / aspect * d)
        val stageW = (stageH * aspect).coerceAtLeast(max(SignTokens.MIN_STAGE_WIDTH_DP * d, minBarPx))

        stageWrap.layoutParams = (stageWrap.layoutParams as? LinearLayout.LayoutParams
            ?: LinearLayout.LayoutParams(0, 0)).also { it.width = stageW.toInt(); it.height = stageH.toInt() }
        controlCard.layoutParams = (controlCard.layoutParams as LinearLayout.LayoutParams).also {
            it.width = stageW.toInt(); it.topMargin = dp(SignTokens.SPACE_SM_DP)
        }
    }

    // ---------------------------------------------------------------- Idle loop + blur

    private fun ensureIdlePlayer(state: SignUiState) {
        val rawName = resolveClipName(state)
        if (rawName == resolvedRawName && idlePlayer != null) return
        releaseIdle()
        resolvedRawName = rawName
        val uri = clipUri(rawName) ?: return // no clip -> fallback mark; no player
        val p = ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) { releaseIdle() }
            })
            prepare()
        }
        p.setVideoTextureView(idleSurface)
        idlePlayer = p
    }

    private fun resolveClipName(state: SignUiState): String? {
        val asset = config.card.placeholderAsset
        if (asset != null) return if (asset.isEmpty()) null else asset // "" opts out; a path is host-supplied
        return SignerResolver.resolve(config.card.placeholderAvatar, state.tid, state.fdid).rawName
    }

    private fun clipUri(name: String?): Uri? {
        if (name == null) return null
        val custom = config.card.placeholderAsset
        if (custom != null && custom.isNotEmpty()) return Uri.parse("file:///android_asset/$custom")
        val id = context.resources.getIdentifier(name, "raw", context.packageName)
        return if (id != 0) RawResourceDataSource.buildRawResourceUri(id) else null
    }

    private fun releaseIdle() {
        idlePlayer?.release(); idlePlayer = null; resolvedRawName = null
        idleWrap.visibility = GONE
    }

    // ---------------------------------------------------------------- VideoController

    override suspend fun prepare(videoUrl: String, speed: Float, looping: Boolean): Boolean =
        withContext(Dispatchers.Main) {
            releaseTranslation()
            suspendCancellableCoroutine { cont ->
                val p = ExoPlayer.Builder(context).build()
                translationPlayer = p
                p.setVideoTextureView(translationSurface)
                p.setMediaItem(MediaItem.fromUri(videoUrl))
                p.repeatMode = if (looping) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
                p.setPlaybackSpeed(if (speed > 0f) speed else 1f)
                p.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY && cont.isActive) cont.resume(true)
                        if (playbackState == Player.STATE_ENDED) onVideoEnded?.invoke()
                    }
                    override fun onPlayerError(error: PlaybackException) {
                        if (cont.isActive) cont.resume(false)
                    }
                })
                p.playWhenReady = true
                p.prepare()
                cont.invokeOnCancellation { }
            }
        }

    /** Toggle play/pause on the translation player and refresh the glyph. */
    fun togglePlay() {
        val p = translationPlayer ?: return
        if (p.isPlaying) p.pause() else p.play()
        playPause.setImageResource(if (p.isPlaying) R.drawable.sfd_ic_pause else R.drawable.sfd_ic_play)
    }

    override fun setSpeed(speed: Float) { translationPlayer?.setPlaybackSpeed(if (speed > 0f) speed else 1f) }
    override fun setLooping(looping: Boolean) {
        translationPlayer?.repeatMode = if (looping) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
    }

    override fun release() { releaseTranslation(); releaseIdle() }

    private fun releaseTranslation() {
        translationPlayer?.release(); translationPlayer = null
        translationSurface.visibility = GONE
    }

    // ---------------------------------------------------------------- Drag (doc 06)

    // Dragging anywhere on the player moves it (doc 06). A stationary tap must still reach the
    // controls underneath, so the drag is only claimed once the finger travels past the slop:
    // ACTION_DOWN never intercepts (children get the tap), and interception starts on the first
    // MOVE beyond the slop. When the DOWN lands on empty stage area, onTouchEvent handles it.
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!config.card.draggable) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downRawX = ev.rawX; downRawY = ev.rawY; viewStartX = x; viewStartY = y; dragging = false }
            MotionEvent.ACTION_MOVE ->
                if (!dragging && kotlin.math.hypot(ev.rawX - downRawX, ev.rawY - downRawY) > touchSlop) {
                    dragging = true
                    return true
                }
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!config.card.draggable) return super.onTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = ev.rawX; downRawY = ev.rawY; viewStartX = x; viewStartY = y; dragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && kotlin.math.hypot(ev.rawX - downRawX, ev.rawY - downRawY) > touchSlop) dragging = true
                if (dragging) { x = clampX(viewStartX + (ev.rawX - downRawX)); y = clampY(viewStartY + (ev.rawY - downRawY)) }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { dragging = false; return true }
        }
        return super.onTouchEvent(ev)
    }

    private fun clampX(v: Float): Float {
        val parentW = (parent as? ViewGroup)?.width ?: return v
        return v.coerceIn(0f, (parentW - width).coerceAtLeast(0).toFloat())
    }

    private fun clampY(v: Float): Float {
        val parentH = (parent as? ViewGroup)?.height ?: return v
        return v.coerceIn(0f, (parentH - height).coerceAtLeast(0).toFloat())
    }

    // ---------------------------------------------------------------- Helpers

    fun updateConfig(newConfig: SignLanguageConfig, newStrings: SignStrings) {
        config = newConfig; strings = newStrings
    }

    private fun formatSpeed(speed: Float): String =
        if (speed == speed.toLong().toFloat()) "${speed.toLong()}.0x" else "${speed}x"

    private fun dp(v: Float) = (v * d).toInt()
    private fun parse(s: String, fallback: Int) = try { Color.parseColor(s) } catch (e: Exception) { fallback }
    private fun withAlpha(color: Int, alpha: Float) =
        (color and 0x00FFFFFF) or ((alpha * 255).toInt() shl 24)

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius
        setColor(color)
    }

    /** The speed control's rounded chip: translucent fill + hairline border, fully rounded ends. */
    private fun chip() = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(SignTokens.RADIUS_MEDIUM_DP).toFloat()
        setColor(SignTokens.CONTROL_FILL)
        setStroke(dp(1f).coerceAtLeast(1), SignTokens.CONTROL_BORDER)
    }
}
