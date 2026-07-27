// signtranslate/src/main/java/com/signfordeaf/signtranslate/sensitive/SignLanguageNoticeDialog.kt

package com.signfordeaf.signtranslate.sensitive

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.Log
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.signfordeaf.signtranslate.R

/**
 * Minimal branded notice: the corporate logo centered on a dim scrim with a short
 * message and an OK button. Same visual language as
 * [com.signfordeaf.signtranslate.loading.SignLanguageLoadingOverlay].
 *
 * Used both when a translation is blocked (sensitive data) and when a translation
 * request fails — in the failure case no bottom sheet is opened, the user just sees
 * this notice and can dismiss it.
 */
class SignLanguageNoticeDialog(
    private val activity: Activity,
    private val primaryColor: String,
    private val message: String
) {

    companion object {
        private const val TAG = "SignLanguageNotice"
    }

    private var dialog: Dialog? = null

    fun show() {
        if (activity.isFinishing || activity.isDestroyed) return

        val d = Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar)
        d.setContentView(R.layout.blocked_notice_sign_language)
        d.setCanceledOnTouchOutside(true)
        d.setCancelable(true)
        d.window?.let { configureWindow(it) }

        val primary = runCatching { Color.parseColor(primaryColor) }.getOrDefault(Color.parseColor("#6750A4"))
        d.findViewById<ImageView>(R.id.iconView)
            ?.setColorFilter(primary, android.graphics.PorterDuff.Mode.SRC_IN)
        d.findViewById<TextView>(R.id.messageView)?.text = message
        d.findViewById<Button>(R.id.okButton)?.apply {
            background?.setTint(primary)
            setOnClickListener { dismiss() }
        }
        // Tapping the backdrop (outside the card, which consumes its own clicks) dismisses.
        d.findViewById<android.view.View>(R.id.overlayRoot)?.setOnClickListener { dismiss() }

        try {
            d.show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show notice dialog: ${e.message}", e)
        }
        dialog = d
    }

    private fun configureWindow(window: Window) {
        window.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    fun dismiss() {
        try {
            dialog?.dismiss()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to dismiss notice dialog: ${e.message}")
        } finally {
            dialog = null
        }
    }
}
