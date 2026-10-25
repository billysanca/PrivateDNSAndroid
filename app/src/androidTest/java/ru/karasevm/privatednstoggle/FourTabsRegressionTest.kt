package ru.karasevm.privatednstoggle

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.bottomnavigation.BottomNavigationView
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import ru.karasevm.privatednstoggle.ui.MainActivity
import ru.karasevm.privatednstoggle.util.PrivateDNSUtils

@RunWith(AndroidJUnit4::class)
class FourTabsRegressionTest {
    @Test fun startsWithOriginalHostnameListAndNavigatingDoesNotChangeDns() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val nav = activity.findViewById<BottomNavigationView>(R.id.bottom_navigation)
                val list = activity.findViewById<RecyclerView>(R.id.recyclerView)
                val mode = PrivateDNSUtils.getPrivateMode(activity.contentResolver)
                val hostname = PrivateDNSUtils.getPrivateProvider(activity.contentResolver)
                assertEquals(4, nav.menu.size())
                assertEquals(R.id.nav_dns, nav.selectedItemId)
                assertEquals(View.VISIBLE, list.visibility)
                nav.selectedItemId = R.id.nav_stats
                assertEquals(View.GONE, list.visibility)
                nav.selectedItemId = R.id.nav_queries
                nav.selectedItemId = R.id.nav_settings
                nav.selectedItemId = R.id.nav_dns
                assertEquals(View.VISIBLE, list.visibility)
                assertEquals(mode, PrivateDNSUtils.getPrivateMode(activity.contentResolver))
                assertEquals(hostname, PrivateDNSUtils.getPrivateProvider(activity.contentResolver))
            }
        }
    }
}
