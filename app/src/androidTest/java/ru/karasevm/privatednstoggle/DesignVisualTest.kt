package ru.karasevm.privatednstoggle

import android.content.Intent
import android.graphics.Bitmap
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import ru.karasevm.privatednstoggle.ui.*
import java.io.File

class DesignVisualTest {
    @Test fun capturesSyntheticScreensAcrossThemes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val oldMode = ThemeSettings.mode(context)
        val oldDynamic = ThemeSettings.dynamic(context)
        val cases = listOf(
            Triple(ThemeMode.LIGHT, 0, false), Triple(ThemeMode.DARK, 0, false),
            Triple(ThemeMode.OLED, 0, false), Triple(ThemeMode.LIGHT, 1, false),
            Triple(ThemeMode.DARK, 2, false), Triple(ThemeMode.OLED, 3, true))
        try {
            for((mode, page, dynamic) in cases) {
                instrumentation.runOnMainSync {
                    ThemeSettings.save(context, mode, dynamic)
                    AppCompatDelegate.setDefaultNightMode(ThemeSettings.night(mode))
                }
                val intent = Intent(context, DesignPreviewActivity::class.java).putExtra("page", page)
                ActivityScenario.launch<DesignPreviewActivity>(intent).use { scenario ->
                    instrumentation.waitForIdleSync()
                    Thread.sleep(1800)
                    scenario.onActivity { activity ->
                        assertEquals(0, activity.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.topAppBar).menu.size())
                        fun labels(view: android.view.View): List<String> {
                            val own = if(view is android.widget.TextView) listOf(view.text.toString()) else emptyList()
                            return own + if(view is android.view.ViewGroup) (0 until view.childCount).flatMap { labels(view.getChildAt(it)) } else emptyList()
                        }
                        val texts = labels(activity.window.decorView)
                        if(page == 1) assertTrue(texts.contains(java.text.NumberFormat.getIntegerInstance().format(26100)))
                        if(page == 2) assertTrue(texts.any { it.contains("ads.example.com") })
                        if(page == 3) assertTrue(texts.any { it.contains("Material You") })
                        if(mode == ThemeMode.OLED) assertEquals(android.graphics.Color.BLACK,
                            com.google.android.material.color.MaterialColors.getColor(activity.findViewById(R.id.topAppBar), com.google.android.material.R.attr.colorSurface))
                    }
                    instrumentation.waitForIdleSync()
                    val image = instrumentation.uiAutomation.takeScreenshot()
                    assertNotNull(image)
                    val file = File(context.getExternalFilesDir("screenshots"), "nexo-${mode.name.lowercase()}-$page.png")
                    file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    image.recycle()
                }
            }
        } finally {
            instrumentation.runOnMainSync {
                ThemeSettings.save(context, oldMode, oldDynamic)
                AppCompatDelegate.setDefaultNightMode(ThemeSettings.night(oldMode))
            }
        }
    }
}
