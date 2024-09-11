package com.signfordeaf.signtranslate

import android.app.Activity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import com.google.gson.Gson
import java.util.Locale

object SignForDeafTranslate {

    private val gson = Gson()
    private val apiService = ApiService()


    fun makeAllTextSelectable(activity: Activity) {
        if (SignForDeafConfig.requestKey == null) {
            Log.e("DEVOPS-NEVI", "requestKey is null. Please initialize SignForDeafConfig.requestKey")
            return
        }
        val rootView: View = activity.findViewById(android.R.id.content)
        setTextSelectableRecursive(rootView)

    }

    fun getLocalizedTexts(): Map<String, String> {
        val locale = Locale.getDefault()

        val textsMap = when (locale.language) {
            "tr" -> mapOf(
                "logoName" to "Engelsiz Çeviri",
                "buttonName" to "İşaret Dili",
                "errorDesc" to "Çeviri işlemi şu anda gerçekleştirilemiyor. Lütfen daha sonra tekrar deneyiniz."
            )
            "en" -> mapOf(
                "logoName" to "SignForDeaf",
                "buttonName" to "Sign Language",
                "errorDesc" to "The translation process cannot be performed at the moment. Please try again later."
            )
            "ar" -> mapOf(
                "logoName" to "SignForDeaf",
                "buttonName" to "لغة الإشارة",
                "errorDesc" to "لا يمكن إجراء عملية الترجمة في الوقت الحالي. يرجى المحاولة مرة أخرى في وقت لاحق."
            )
            else -> mapOf(
                "logoName" to "Engelsiz Çeviri",
                "buttonName" to "İşaret Dili",
                "errorDesc" to "Çeviri işlemi şu anda gerçekleştirilemiyor. Lütfen daha sonra tekrar deneyiniz."
            )
        }

        return textsMap
    }

    fun getText(key: String): String {
        val localizedTexts = getLocalizedTexts()
        return localizedTexts[key] ?: "Text not found"
    }

    private fun convertToHttps(url: String): String {
        return if (url.startsWith("http://")) {
            url.replaceFirst("http://", "https://")
        } else {
            url
        }
    }

    private fun fetchSignVideo(selectedText: String, view: View) {
        TranslateVideoUtil.showLoadingOverlay(view.context as Activity, apiService)

        apiService.getSignVideo(selectedText) { result, error ->
            if (error != null) {
                Log.e("DEVOPS-NEVI", "onActionItemClicked: $error")
            } else {
                val response = gson.fromJson(result, SignModel::class.java)
                if (response.state == true) {
                   var videoUrlHttps =  convertToHttps("${response.baseUrl}${response.name}")
                    //(view.context as? AppCompatActivity)?.supportFragmentManager?.let { fragmentManager -> TranslateVideoUtil.showBottomSheet(fragmentManager, videoUrlHttps) }
                    (view.context as? AppCompatActivity)?.supportFragmentManager?.let { fragmentManager ->
                        TranslateVideoUtil.showBottomSheet(fragmentManager, videoUrlHttps)
                    }
                    Log.d("DEVOPS-NEVI", "showBottomSheet: ${view.context}")

                    (view.context as? Activity)?.runOnUiThread { TranslateVideoUtil.hideLoadingOverlay() }

                } else if (response.state == null) {
                    (view.context as? Activity)?.runOnUiThread {
                        TranslateVideoUtil.removeLoadingOverlay(view.context as Activity)
                        TranslateVideoUtil.showErrorMessage(view.context as Activity)
                    }

                }
                Log.d("DEV-SIGN", "service result: ${response.state}")
                Log.d("DEV-SIGN", "service result: ${response.baseUrl}${response.name}")
                Log.d("DEV-SIGN", "service result: translate success")
            }
        }
    }

    private fun setTextSelectableRecursive(view: View) {
        when (view) {
            is TextView -> {
                view.isFocusable = true
                view.isFocusableInTouchMode = true
                view.setTextIsSelectable(true)
                view.customSelectionActionModeCallback = object : ActionMode.Callback,
                    android.view.ActionMode.Callback {
                    override fun onCreateActionMode(
                        mode: android.view.ActionMode?,
                        menu: Menu?
                    ): Boolean {

                        menu?.add(0,1,0, getText("buttonName"))
                        return true
                    }

                    override fun onPrepareActionMode(
                        mode: android.view.ActionMode?,
                        menu: Menu?
                    ): Boolean {
                        return false
                    }

                    override fun onActionItemClicked(
                        mode: android.view.ActionMode?,
                        item: MenuItem?
                    ): Boolean {
                        return when (item?.itemId) {
                            1 -> {
                                val selectedText = view.text.subSequence(
                                    view.selectionStart,
                                    view.selectionEnd
                                )
                                mode?.finish()
                                fetchSignVideo(selectedText.toString(), view)

                                true
                            }
                            else -> false
                        }
                    }

                    override fun onDestroyActionMode(mode: android.view.ActionMode?) {


                    }

                    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {

                        menu?.add(0,1,0,"Copy")
                        return true
                    }

                    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {

                        return false
                    }

                    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {

                        return when (item?.itemId) {
                            1 -> {
                                Log.d("DEVOPS-NEVi", "onActionItemClicked: ${item.title}clicked")
                                true
                            }
                            else -> false
                        }
                    }

                    override fun onDestroyActionMode(mode: ActionMode?) {

                    }
                }

            }
            is EditText -> {
                view.isFocusable = true
                view.isFocusableInTouchMode = true
                view.setTextIsSelectable(true)
            }
            is ViewGroup -> {
                for (i in 0 until view.childCount) {
                    val childView = view.getChildAt(i)
                    setTextSelectableRecursive(childView)
                }
            }
        }
    }
}