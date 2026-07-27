// signtranslate/src/main/java/com/signfordeaf/signtranslate/textselection/CustomActionModeCallback.kt

package com.signfordeaf.signtranslate.textselection

import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView

class CustomActionModeCallback(
    private val menuTitle: String,
    private val onSignLanguageSelected: (String) -> Unit,
    private val getSelectedText: (TextView) -> String
) : ActionMode.Callback {

    companion object {
        private const val SIGN_LANGUAGE_MENU_ID = 9999
        private const val SIGN_LANGUAGE_ORDER = 0
    }

    private var textView: TextView? = null

    fun setTextView(view: TextView) {
        textView = view
    }

    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        // Add Sign Language menu item at the beginning
        menu?.add(
            Menu.NONE,
            SIGN_LANGUAGE_MENU_ID,
            SIGN_LANGUAGE_ORDER,
            menuTitle
        )?.apply {
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        // Ensure our menu item is always visible
        menu?.findItem(SIGN_LANGUAGE_MENU_ID)?.let { item ->
            item.isVisible = true
        }
        return true
    }

    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
        return when (item?.itemId) {
            SIGN_LANGUAGE_MENU_ID -> {
                textView?.let { tv ->
                    val selectedText = getSelectedText(tv)
                    if (selectedText.isNotEmpty()) {
                        onSignLanguageSelected(selectedText)
                    }
                }
                mode?.finish()
                true
            }
            else -> false
        }
    }

    override fun onDestroyActionMode(mode: ActionMode?) {
        // Cleanup if needed
    }
}

// Floating action mode callback for Android 6.0+
class CustomActionModeCallback2(
    private val menuTitle: String,
    private val onSignLanguageSelected: (String) -> Unit,
    private val getSelectedText: (TextView) -> String
) : ActionMode.Callback2() {

    companion object {
        private const val SIGN_LANGUAGE_MENU_ID = 9999
        private const val SIGN_LANGUAGE_ORDER = 0
    }

    private var textView: TextView? = null

    fun setTextView(view: TextView) {
        textView = view
    }

    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        menu?.add(
            Menu.NONE,
            SIGN_LANGUAGE_MENU_ID,
            SIGN_LANGUAGE_ORDER,
            menuTitle
        )?.apply {
            setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        }
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        return true
    }

    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
        return when (item?.itemId) {
            SIGN_LANGUAGE_MENU_ID -> {
                textView?.let { tv ->
                    val selectedText = getSelectedText(tv)
                    if (selectedText.isNotEmpty()) {
                        onSignLanguageSelected(selectedText)
                    }
                }
                mode?.finish()
                true
            }
            else -> false
        }
    }

    override fun onDestroyActionMode(mode: ActionMode?) {
        // Cleanup
    }
}
