package ru.karasevm.privatednstoggle.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/** Applies OLED after any DynamicColors overlay, including Android 9–11. */
open class NexoActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        if(ThemeSettings.mode(this) == ThemeMode.OLED) theme.applyStyle(ru.karasevm.privatednstoggle.R.style.ThemeOverlay_Nexo_Oled, true)
        super.onCreate(savedInstanceState)
    }
}
