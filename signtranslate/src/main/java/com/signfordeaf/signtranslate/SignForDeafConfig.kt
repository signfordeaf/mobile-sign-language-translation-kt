package com.signfordeaf.signtranslate

object SignForDeafConfig {
    var requestKey: String? =null
    var requestUrl: String? =null
    private var isActive = true

    fun initialize(requestKey: String, requestUrl: String) {
        this.requestKey = requestKey
        this.requestUrl = requestUrl
    }

    fun isActive(): Boolean {
        return isActive
    }

    fun setActive(isActive: Boolean) {
        this.isActive = isActive
    }
}