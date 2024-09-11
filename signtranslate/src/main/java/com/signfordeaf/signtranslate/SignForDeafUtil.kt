package com.signfordeaf.signtranslate

import android.app.Activity

class SignForDeafUtil {
     companion object {
       fun initialize(requestKey: String, requestUrl: String) {
            SignForDeafConfig.initialize(requestKey, requestUrl)
        }

        fun isActive(): Boolean {
            return SignForDeafConfig.isActive()
        }

        fun setActive(isActive: Boolean) {
            SignForDeafConfig.setActive(isActive)
        }
    }

    fun translateSignLanguage(activity: Activity) {
        if (SignForDeafConfig.requestKey == null || SignForDeafConfig.requestUrl == null || !SignForDeafConfig.isActive()) {
            return
        }
        SignForDeafTranslate.makeAllTextSelectable(activity)
    }
}