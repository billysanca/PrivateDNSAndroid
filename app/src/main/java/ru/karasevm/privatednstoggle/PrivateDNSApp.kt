package ru.karasevm.privatednstoggle

import android.app.Application
import android.os.StrictMode
import com.google.android.material.color.DynamicColors
import ru.karasevm.privatednstoggle.data.DnsServerRepository
import ru.karasevm.privatednstoggle.data.database.DnsServerRoomDatabase

class PrivateDNSApp : Application() {

    private val database by lazy { DnsServerRoomDatabase.getDatabase(this) }
    val repository by lazy { DnsServerRepository(database.dnsServerDao()) }

    override fun onCreate() {
        super.onCreate()
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
            ru.karasevm.privatednstoggle.ui.ThemeSettings.night(ru.karasevm.privatednstoggle.ui.ThemeSettings.mode(this)))
        DynamicColors.applyToActivitiesIfAvailable(this,
            com.google.android.material.color.DynamicColorsOptions.Builder()
                .setPrecondition { activity, _ -> ru.karasevm.privatednstoggle.ui.ThemeSettings.dynamic(activity) }
                .build())

        if (BuildConfig.DEBUG){
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
        }
    }
}