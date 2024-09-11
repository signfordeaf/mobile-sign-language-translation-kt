package com.signfordeaf.mobilesignlanguage

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.signfordeaf.mobilesignlanguage.ui.theme.MobileSignLanguageTheme
import com.signfordeaf.signtranslate.SignForDeafTranslate
import com.signfordeaf.signtranslate.SignForDeafUtil

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        SignForDeafUtil.initialize("YOUR-API-KEY", requestUrl = "YOUR-REQUEST-URL")
        SignForDeafTranslate.makeAllTextSelectable(this)

    }
}

