// signtranslate/src/main/java/com/signfordeaf/signtranslate/bottomsheet/SignLanguageBottomSheet.kt

package com.signfordeaf.signtranslate.bottomsheet

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.signfordeaf.signtranslate.R

class SignLanguageBottomSheet : BottomSheetDialogFragment() {

    companion object {
        private const val TAG = "SignLanguageBottomSheet"
        private const val ARG_VIDEO_URL = "video_url"
        private const val ARG_TEXT = "text"
        private const val ARG_BUSINESS_NAME = "business_name"
        private const val ARG_PRIMARY_COLOR = "primary_color"
        private const val ARG_TEXT_COLOR = "text_color"

        fun newInstance(
            videoUrl: String = "",
            text: String,
            businessName: String,
            primaryColor: String = "#6750A4",
            textColor: String = "#1C1B1F"
        ): SignLanguageBottomSheet {
            return SignLanguageBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_VIDEO_URL, videoUrl)
                    putString(ARG_TEXT, text)
                    putString(ARG_BUSINESS_NAME, businessName)
                    putString(ARG_PRIMARY_COLOR, primaryColor)
                    putString(ARG_TEXT_COLOR, textColor)
                }
            }
        }
    }

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var progressBar: ProgressBar
    private lateinit var titleTextView: TextView
    private lateinit var displayTextView: TextView
    private lateinit var closeButton: ImageButton
    private lateinit var logoImageView: ImageView
    private lateinit var errorView: View
    private lateinit var retryButton: View

    private var videoUrl: String = ""
    private var displayText: String = ""
    private var businessName: String = ""
    private var isWaitingForVideo: Boolean = true
    private var primaryColor: String = "#6750A4"
    private var textColor: String = "#1C1B1F"

    var onDismissListener: (() -> Unit)? = null
    var onVideoStartListener: (() -> Unit)? = null
    var onVideoEndListener: (() -> Unit)? = null

    /**
     * An ExoPlayer already created and buffered by the facade during the loading
     * overlay. When set, the sheet attaches it directly (no in-sheet spinner) and
     * takes over its lifecycle (released in [onDestroyView]).
     */
    var preparedPlayer: ExoPlayer? = null

    /**
     * Returns a themed context that ensures Material Components are available.
     * This wraps the context with our library's theme to prevent crashes when
     * the host app doesn't use Material theme.
     */
    private fun getThemedContext(): Context {
        return try {
            ContextThemeWrapper(requireContext(), R.style.SignLanguageBottomSheetStyle)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create themed context, using default", e)
            requireContext()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Apply our library's theme to ensure Material Components work
        // regardless of host app's theme
        try {
            setStyle(STYLE_NORMAL, R.style.SignLanguageBottomSheetStyle)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set bottom sheet style", e)
            // Fallback to AppCompat style which doesn't require MaterialComponents theme
            try {
                setStyle(STYLE_NORMAL, androidx.appcompat.R.style.Theme_AppCompat_Light_Dialog)
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to set fallback style", e2)
            }
        }

        arguments?.let {
            videoUrl = it.getString(ARG_VIDEO_URL, "")
            displayText = it.getString(ARG_TEXT, "")
            businessName = it.getString(ARG_BUSINESS_NAME, "")
            primaryColor = it.getString(ARG_PRIMARY_COLOR, "#6750A4")
            textColor = it.getString(ARG_TEXT_COLOR, "#1C1B1F")
            isWaitingForVideo = videoUrl.isEmpty()
        }
        
        Log.d(TAG, "Theme colors - primaryColor: $primaryColor, textColor: $textColor")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Use themed inflater to ensure Material Components work properly
        val themedInflater = inflater.cloneInContext(getThemedContext())
        return try {
            themedInflater.inflate(R.layout.bottom_sheet_sign_language, container, false)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inflate with themed context, trying default", e)
            inflater.inflate(R.layout.bottom_sheet_sign_language, container, false)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViews(view)
        setupAccessibility()
        setupVideoPlayer()
        setupBottomSheetBehavior()
    }

    private fun setupViews(view: View) {
        playerView = view.findViewById(R.id.playerView)
        progressBar = view.findViewById(R.id.progressBar)
        titleTextView = view.findViewById(R.id.titleTextView)
        displayTextView = view.findViewById(R.id.displayTextView)
        closeButton = view.findViewById(R.id.closeButton)
        logoImageView = view.findViewById(R.id.logoImageView)
        errorView = view.findViewById(R.id.errorView)
        retryButton = view.findViewById(R.id.retryButton)

        titleTextView.text = businessName
        displayTextView.text = displayText
        
        // Apply theme colors
        applyThemeColors()

        // Enable marquee effect for long text
        displayTextView.isSelected = true
        displayTextView.isFocusable = true
        displayTextView.isFocusableInTouchMode = true
        
        // Request focus to start marquee
        displayTextView.post {
            displayTextView.requestFocus()
            displayTextView.isSelected = true
        }

        closeButton.setOnClickListener {
            onVideoEndListener?.invoke()
            dismiss()
        }

        retryButton.setOnClickListener {
            setupVideoPlayer()
        }
    }
    
    private fun applyThemeColors() {
        try {
            val primaryColorInt = android.graphics.Color.parseColor(primaryColor)

            // Apply primary color to logo
            logoImageView.setColorFilter(primaryColorInt, android.graphics.PorterDuff.Mode.SRC_IN)
            
            // Apply primary color to title
            titleTextView.setTextColor(primaryColorInt)
            
            // Translated text uses the theme (primary) color, matching RN.
            displayTextView.setTextColor(primaryColorInt)
            
            // Apply primary color to close button tint
            closeButton.imageTintList = android.content.res.ColorStateList.valueOf(primaryColorInt)
            
            // Apply primary color to retry button background
            retryButton.background?.setTint(primaryColorInt)
            
            // Apply primary color to progress bar
            progressBar.indeterminateTintList = android.content.res.ColorStateList.valueOf(primaryColorInt)
            
            Log.d(TAG, "Theme colors applied successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply theme colors: ${e.message}", e)
        }
    }

    private fun setupAccessibility() {
        playerView.contentDescription = "İşaret dili videosu oynatılıyor"
        closeButton.contentDescription = "Kapat"

        view?.announceForAccessibility("İşaret dili çevirisi hazır")
    }

    private fun setupVideoPlayer() {
        // Preferred path: attach the player the facade already prepared during the
        // loading overlay, so the sheet appears already playing (no spinner).
        preparedPlayer?.let { ready ->
            preparedPlayer = null
            attachPreparedPlayer(ready)
            return
        }

        // If waiting for video URL, show loading state
        if (videoUrl.isEmpty()) {
            progressBar.visibility = View.VISIBLE
            playerView.visibility = View.GONE
            errorView.visibility = View.GONE
            return
        }

        // Video URL available, prepare player
        progressBar.visibility = View.VISIBLE
        playerView.visibility = View.VISIBLE
        errorView.visibility = View.GONE

        player = ExoPlayer.Builder(requireContext()).build().apply {
            playerView.player = this

            val mediaItem = MediaItem.fromUri(videoUrl)
            setMediaItem(mediaItem)

            repeatMode = Player.REPEAT_MODE_ALL

            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_READY -> {
                            progressBar.visibility = View.GONE
                            onVideoStartListener?.invoke()
                        }
                        Player.STATE_BUFFERING -> {
                            progressBar.visibility = View.VISIBLE
                        }
                        Player.STATE_ENDED -> {
                            // Handled by repeat mode
                        }
                        Player.STATE_IDLE -> {
                            // Initial state or error
                        }
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    progressBar.visibility = View.GONE
                    showError()
                }
            })

            prepare()
            playWhenReady = true
        }
    }
    
    fun updateVideoUrl(newVideoUrl: String) {
        videoUrl = newVideoUrl
        isWaitingForVideo = false

        if (isAdded && context != null) {
            setupVideoPlayer()
        }
    }

    /**
     * Attaches a player prepared by the facade. The sheet takes over its lifecycle
     * and shows the video immediately, only re-showing the spinner if the player
     * rebuffers mid-playback.
     */
    private fun attachPreparedPlayer(ready: ExoPlayer) {
        player = ready
        playerView.player = ready

        progressBar.visibility = View.GONE
        playerView.visibility = View.VISIBLE
        errorView.visibility = View.GONE

        ready.repeatMode = Player.REPEAT_MODE_ALL
        ready.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> progressBar.visibility = View.VISIBLE
                    Player.STATE_READY -> progressBar.visibility = View.GONE
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                progressBar.visibility = View.GONE
                showError()
            }
        })
        ready.playWhenReady = true

        onVideoStartListener?.invoke()
    }

    private fun setupBottomSheetBehavior() {
        dialog?.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as? com.google.android.material.bottomsheet.BottomSheetDialog
            val bottomSheet = bottomSheetDialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)

            bottomSheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                
                // Fixed height: half the screen.
                val displayMetrics = resources.displayMetrics
                val halfScreenHeight = displayMetrics.heightPixels / 2
                it.layoutParams.height = halfScreenHeight

                // Open fully expanded and let the window slide-up animation (set via
                // windowAnimationStyle in the dialog theme) provide the smooth entrance.
                // Keeping isFitToContents = true + skipCollapsed avoids the intermediate
                // collapsed/half-expanded settling that made the open look abrupt.
                behavior.peekHeight = halfScreenHeight
                behavior.isFitToContents = true
                behavior.skipCollapsed = true
                behavior.isDraggable = true
                behavior.state = BottomSheetBehavior.STATE_EXPANDED

                // Drag-to-dismiss handling.
                behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                    override fun onStateChanged(bottomSheet: View, newState: Int) {
                        if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                            dismiss()
                        }
                    }
                    
                    override fun onSlide(bottomSheet: View, slideOffset: Float) {
                        // Smooth slide animation handled by system
                    }
                })
            }
        }
    }

    private fun showError() {
        progressBar.visibility = View.GONE
        errorView.visibility = View.VISIBLE
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismissListener?.invoke()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        player?.release()
        player = null
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onResume() {
        super.onResume()
        player?.play()
    }
}
