package com.signfordeaf.mobilesignlanguage

import android.content.Context
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.signfordeaf.mobilesignlanguage.databinding.ActivityMainBinding
import com.signfordeaf.signtranslate.Language
import com.signfordeaf.signtranslate.SignLanguage
import com.signfordeaf.signtranslate.SignLanguageConfig
import com.signfordeaf.signtranslate.SignLanguageTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Example app for the SignForDeaf sign-language SDK.
 *
 * Shows every entry point of the [SignLanguage] facade: configuration with
 * runtime credentials, text-selection translation, tap-to-translate mode, the
 * floating button, programmatic [SignLanguage.translate], and the event
 * listeners — with a live event log so developers can watch the flow.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val prefs by lazy { getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val logLines = ArrayDeque<String>()
    private var configured = false

    // Guards the switch listener while we programmatically sync it to the SDK's mode
    // (e.g. when the floating button toggles tap-to-translate), to avoid a feedback loop.
    private var syncingSwitch = false

    // Languages offered in the dropdown (label shown, enum applied).
    private val languages = Language.values()

    companion object {
        private const val PREFS = "demo_prefs"
        private const val KEY_API = "api_key"
        private const val KEY_URL = "api_url"
        private const val KEY_LANG = "language"
        private const val MAX_LOG_LINES = 40
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupLanguageDropdown()
        restoreSavedValues()
        registerSdkListeners()
        wireActions()

        // 1) Credentials injected at build time (the --dart-define equivalent) win: prefill the
        //    fields and configure immediately.
        if (BuildConfig.SIGNFORDEAF_API_KEY.isNotBlank() && BuildConfig.SIGNFORDEAF_API_URL.isNotBlank()) {
            binding.etApiKey.setText(BuildConfig.SIGNFORDEAF_API_KEY)
            binding.etApiUrl.setText(BuildConfig.SIGNFORDEAF_API_URL)
            configureSdk(
                key = BuildConfig.SIGNFORDEAF_API_KEY,
                url = BuildConfig.SIGNFORDEAF_API_URL,
                language = currentLanguage(),
                persist = false,
                originUrl = BuildConfig.SIGNFORDEAF_ORIGIN_URL,
                fdid = BuildConfig.SIGNFORDEAF_FDID,
                tid = BuildConfig.SIGNFORDEAF_TID
            )
            log("configured from BuildConfig (dart-define equivalent)")
        } else {
            // 2) Otherwise fall back to values saved on the device.
            val savedKey = prefs.getString(KEY_API, "").orEmpty()
            val savedUrl = prefs.getString(KEY_URL, "").orEmpty()
            if (savedKey.isNotBlank() && savedUrl.isNotBlank()) {
                configureSdk(savedKey, savedUrl, currentLanguage(), persist = false)
            }
        }
    }

    // MARK: - Setup

    private fun setupLanguageDropdown() {
        val labels = languages.map { "${it.displayName} (${it.code})" }
        binding.dropdownLanguage.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
        )
        binding.dropdownLanguage.setText(labels[0], false) // default: Turkish (first enum entry)
    }

    private fun currentLanguage(): Language {
        val text = binding.dropdownLanguage.text?.toString().orEmpty()
        return languages.firstOrNull { text.startsWith(it.displayName) } ?: Language.TURKISH
    }

    private fun restoreSavedValues() {
        binding.etApiKey.setText(prefs.getString(KEY_API, ""))
        binding.etApiUrl.setText(prefs.getString(KEY_URL, ""))
        val savedLang = prefs.getString(KEY_LANG, Language.TURKISH.code)
        val idx = languages.indexOfFirst { it.code == savedLang }.coerceAtLeast(0)
        binding.dropdownLanguage.setText("${languages[idx].displayName} (${languages[idx].code})", false)
    }

    private fun registerSdkListeners() {
        SignLanguage.setOnTranslationListener(object : SignLanguage.TranslationListener {
            override fun onTextSelected(text: String) = log("textSelected: \"$text\"")
            override fun onTranslationStart(text: String) = log("translationStart: \"$text\"")
            override fun onTranslationComplete(text: String, videoUrl: String) =
                log("translationComplete: $videoUrl")
            override fun onTranslationError(code: String, message: String) =
                log("translationError [$code]: $message")
            override fun onTranslationBlocked(text: String) =
                log("blockedSensitive: \"$text\"")
        })
        SignLanguage.setOnBottomSheetListener(object : SignLanguage.BottomSheetListener {
            override fun onBottomSheetOpen() = log("bottomSheetOpen")
            override fun onBottomSheetClose() = log("bottomSheetClose")
            override fun onVideoStart() = log("videoStart")
            override fun onVideoEnd() = log("videoEnd")
        })

        // Keep the switch in sync with the mode, no matter where it changed from
        // (switch, floating button, or a programmatic call).
        SignLanguage.setOnTapToTranslateModeChangeListener { enabled ->
            runOnUiThread {
                if (binding.switchTap.isChecked != enabled) {
                    syncingSwitch = true
                    binding.switchTap.isChecked = enabled
                    syncingSwitch = false
                }
                log("tapToTranslateMode = $enabled")
            }
        }
    }

    private fun wireActions() {
        binding.btnConfigure.setOnClickListener {
            val key = binding.etApiKey.text?.toString()?.trim().orEmpty()
            val url = binding.etApiUrl.text?.toString()?.trim().orEmpty()
            if (key.isBlank() || url.isBlank()) {
                binding.tvStatus.text = getString(R.string.status_missing)
                return@setOnClickListener
            }
            configureSdk(key, url, currentLanguage(), persist = true)
        }

        binding.switchTap.setOnCheckedChangeListener { _, checked ->
            if (syncingSwitch) return@setOnCheckedChangeListener // programmatic sync, not a user action
            if (!configured) {
                if (checked) {
                    binding.switchTap.isChecked = false
                    toast(getString(R.string.toast_configure_first))
                }
                return@setOnCheckedChangeListener
            }
            // The mode-change listener does the logging, covering every source.
            SignLanguage.setTapToTranslateMode(checked)
        }

        binding.btnTranslate.setOnClickListener {
            if (!configured) {
                toast(getString(R.string.toast_configure_first)); return@setOnClickListener
            }
            val text = binding.etTranslate.text?.toString()?.trim().orEmpty()
            if (text.isBlank()) {
                toast(getString(R.string.toast_enter_text)); return@setOnClickListener
            }
            SignLanguage.translate(text)
        }

        binding.btnClearLog.setOnClickListener {
            logLines.clear()
            binding.tvLog.text = getString(R.string.log_empty)
        }
    }

    // MARK: - SDK

    private fun configureSdk(
        key: String,
        url: String,
        language: Language,
        persist: Boolean,
        originUrl: String? = null,
        fdid: String? = null,
        tid: String? = null
    ) {
        // Only override fdid/tid when explicitly provided; otherwise keep the SDK defaults
        // (Hesna). This is what makes "default Hesna" actually reach the backend.
        var config = SignLanguageConfig(
            apiKey = key,
            apiUrl = url,
            originUrl = originUrl?.takeIf { it.isNotBlank() },
            language = language,
            theme = SignLanguageTheme(primaryColor = "#6750A4")
        )
        fdid?.takeIf { it.isNotBlank() }?.let { config = config.copy(fdid = it) }
        tid?.takeIf { it.isNotBlank() }?.let { config = config.copy(tid = it) }
        SignLanguage.configure(this, config)
        SignLanguage.showFloatingButton(this)
        // Demo of manual marking: this line is never sent for translation.
        SignLanguage.markSensitive(binding.tvSensitiveMarked)
        configured = true

        if (persist) {
            prefs.edit()
                .putString(KEY_API, key)
                .putString(KEY_URL, url)
                .putString(KEY_LANG, language.code)
                .apply()
        }

        binding.tvStatus.setTextColor(getColor(R.color.success_green))
        binding.tvStatus.text = getString(R.string.status_configured)
        log("configured (lang=${language.code})")
    }

    // MARK: - Event log

    private fun log(message: String) {
        runOnUiThread {
            logLines.addFirst("${timeFmt.format(Date())}  $message")
            while (logLines.size > MAX_LOG_LINES) logLines.removeLast()
            binding.tvLog.text = logLines.joinToString("\n")
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
