// signtranslate/src/main/java/com/signfordeaf/signtranslate/loading/SignLanguageLoadingOverlay.kt

package com.signfordeaf.signtranslate.loading

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageButton
import android.widget.ImageView
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.signfordeaf.signtranslate.R

/**
 * Full-screen loading screen shown while a sign-language video is being prepared.
 * Faithful to the original design: a large indeterminate ring with the corporate
 * logo centered inside it, over a light scrim, and a close (X) button top-right —
 * no card, no label. The bottom sheet is opened only *after* the video is ready to
 * play, so the user never sees a spinner inside the sheet.
 *
 * The overlay is cancelable — the X button, tapping the backdrop, or pressing back
 * all invoke [onCancel]. On failure the facade dismisses this overlay and shows a
 * separate minimal branded notice instead (see `SignLanguage.showErrorNotice`); the
 * loading screen itself never turns into an error card and the sheet never opens.
 */
class SignLanguageLoadingOverlay(
    private val activity: Activity,
    private val primaryColor: String,
    private val onCancel: () -> Unit
) {

    companion object {
        private const val TAG = "SignLanguageLoading"
    }

    private var dialog: Dialog? = null
    private var overlayRoot: View? = null
    private var progressBar: CircularProgressIndicator? = null
    private var logoImageView: ImageView? = null
    private var closeButtonLoading: ImageButton? = null

    /** Shows the loading overlay (big ring + centered logo). */
    fun show() {
        if (activity.isFinishing || activity.isDestroyed) return

        val d = Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar)
        d.setContentView(R.layout.loading_overlay_sign_language)
        d.setCanceledOnTouchOutside(true)
        d.setCancelable(true)
        d.setOnCancelListener { onCancel() }

        d.window?.let { configureWindow(it) }
        bindViews(d)
        applyPrimaryColor()

        try {
            d.show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show loading overlay: ${e.message}", e)
        }
        dialog = d
    }

    private fun configureWindow(window: Window) {
        window.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        // Transparent window; the light scrim is provided by the layout background so
        // the app stays visible behind it (matching the original design).
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    private fun bindViews(d: Dialog) {
        overlayRoot = d.findViewById(R.id.overlayRoot)
        progressBar = d.findViewById(R.id.progressBar)
        logoImageView = d.findViewById(R.id.logoImageView)
        closeButtonLoading = d.findViewById(R.id.closeButtonLoading)

        // X button and backdrop taps both cancel while loading.
        closeButtonLoading?.setOnClickListener { onCancel() }
        overlayRoot?.setOnClickListener { onCancel() }
    }

    private fun applyPrimaryColor() {
        try {
            val primary = Color.parseColor(primaryColor)
            progressBar?.setIndicatorColor(primary)
            logoImageView?.setColorFilter(primary, android.graphics.PorterDuff.Mode.SRC_IN)
            closeButtonLoading?.imageTintList = android.content.res.ColorStateList.valueOf(primary)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply primary color to loading overlay: ${e.message}")
        }
    }

    fun isShowing(): Boolean = dialog?.isShowing ?: false

    /** Dismisses the overlay without firing [onCancel]. */
    fun dismiss() {
        try {
            dialog?.setOnCancelListener(null)
            dialog?.dismiss()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to dismiss loading overlay: ${e.message}")
        } finally {
            dialog = null
        }
    }
}
