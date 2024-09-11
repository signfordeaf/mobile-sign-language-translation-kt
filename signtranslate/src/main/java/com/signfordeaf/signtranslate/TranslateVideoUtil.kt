package com.signfordeaf.signtranslate

import android.app.Activity
import android.util.Log
import androidx.fragment.app.FragmentManager
import com.signfordeaf.signtranslate.view.ErrorActivity
import com.signfordeaf.signtranslate.view.TranslateVideo

object TranslateVideoUtil {

    val loadingActivity = com.signfordeaf.signtranslate.LoadingActivity()
    val errorActivity = ErrorActivity()

    fun showLoadingOverlay(activity: Activity, apiService: ApiService) {
        val loadingView = loadingActivity.showLoadingOverlay(activity, apiService = apiService)
    }

    fun showErrorMessage(activity: Activity) {
        errorActivity.showErrorOverlay(activity)
    }

    fun hideErrorMessage(activity: Activity) {
        errorActivity.removeErrorOverlay(activity)
    }

    fun hideLoadingOverlay() {
        loadingActivity.hideLoadingOverlay()
    }

    fun cancelLoadingOverlay(activity: Activity, apiService: ApiService) {
        loadingActivity.cancelLoadingOverlay(activity, apiService)
    }

    fun removeLoadingOverlay(activity: Activity) {
        loadingActivity.removeLoadingOverlay(activity)
    }


    fun showBottomSheet(fragmentManager: FragmentManager, videoUrl: String) {
        val bottomSheet = TranslateVideo(videoUrl)
        Log.d("DEVOPS-NEVI", "showBottomSheet: $videoUrl")
        bottomSheet.show(fragmentManager, bottomSheet.tag)
    }
}