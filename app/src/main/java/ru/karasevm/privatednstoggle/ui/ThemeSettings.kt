package ru.karasevm.privatednstoggle.ui

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

enum class ThemeMode {
    SYSTEM, LIGHT, DARK, OLED;
    companion object { fun parse(value: String?) = entries.firstOrNull { it.name == value } ?: SYSTEM }
}
object ThemeSettings {
    private fun prefs(context: Context) = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    fun mode(context: Context) = ThemeMode.parse(prefs(context).getString("mode", null))
    fun dynamic(context: Context) = prefs(context).getBoolean("dynamic", true)
    fun night(mode: ThemeMode) = when(mode) {
        ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        ThemeMode.DARK, ThemeMode.OLED -> AppCompatDelegate.MODE_NIGHT_YES
    }
    fun save(context: Context, mode: ThemeMode, dynamic: Boolean) {
        prefs(context).edit().putString("mode", mode.name).putBoolean("dynamic", dynamic).apply()
    }
    fun apply(activity: androidx.appcompat.app.AppCompatActivity) {
        val wanted = night(mode(activity))
        if(AppCompatDelegate.getDefaultNightMode() != wanted) AppCompatDelegate.setDefaultNightMode(wanted)
        else activity.recreate()
    }
}
