package com.signfordeaf.signtranslate.view

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import com.signfordeaf.signtranslate.R

class ErrorActivity {
    private var errorView: View? = null

    fun showErrorOverlay(activity: Activity) {
        if (errorView == null) {
            errorView = LayoutInflater.from(activity).inflate(R.layout.error_layout, null)
            val rootView = activity.findViewById<FrameLayout>(android.R.id.content)
            rootView.addView(errorView)
            val closeButton = activity.findViewById<ImageButton>(R.id.closeButtonError)
            closeButton.setOnClickListener {
                removeErrorOverlay(activity)
            }
        } else {
            errorView?.visibility = View.VISIBLE
        }
    }

    fun removeErrorOverlay(activity: Activity) {
        (activity.findViewById<FrameLayout>(android.R.id.content) as? FrameLayout)?.removeView(errorView)
        errorView = null
    }
}