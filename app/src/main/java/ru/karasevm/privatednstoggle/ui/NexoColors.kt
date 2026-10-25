package ru.karasevm.privatednstoggle.ui

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun nexoColors(context: Context): ColorScheme {
    val dark = isSystemInDarkTheme()
    val base = if(ThemeSettings.dynamic(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && com.google.android.material.color.DynamicColors.isDynamicColorAvailable()) {
        if(dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if(dark) darkColorScheme() else lightColorScheme()
    return if(ThemeSettings.mode(context) == ThemeMode.OLED) base.copy(
        background = Color.Black, surface = Color.Black, surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF080808), surfaceContainer = Color(0xFF101010),
        surfaceContainerHigh = Color(0xFF181818), surfaceContainerHighest = Color(0xFF222222)
    ) else base
}
