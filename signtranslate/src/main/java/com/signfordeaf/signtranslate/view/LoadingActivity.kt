package com.signfordeaf.signtranslate

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton


class LoadingActivity {

    private var loadingView: View? = null

    fun showLoadingOverlay(activity: Activity, apiService: ApiService) {
        if (loadingView == null) {
            loadingView = LayoutInflater.from(activity).inflate(R.layout.loading_layout, null)
            val rootView = activity.findViewById<FrameLayout>(android.R.id.content) // Ekranın ana görünümüne ekleyin
            rootView.addView(loadingView)
            val closeButton = activity.findViewById<ImageButton>(R.id.closeButtonLoading)
            closeButton.setOnClickListener {
                cancelLoadingOverlay(activity, apiService)
            }
        } else {
            loadingView?.visibility = View.VISIBLE
        }
    }

    fun hideLoadingOverlay() {
        loadingView?.visibility = View.GONE
    }

    fun cancelLoadingOverlay(activity: Activity, apiService: ApiService) {
        (activity.findViewById<FrameLayout>(android.R.id.content) as? FrameLayout)?.removeView(loadingView)
        loadingView = null
        apiService.cancelRequest()
    }

    fun removeLoadingOverlay(activity: Activity) {
        (activity.findViewById<FrameLayout>(android.R.id.content) as? FrameLayout)?.removeView(loadingView)
        loadingView = null
    }
}