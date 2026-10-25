package ru.karasevm.privatednstoggle

import org.junit.Assert.*
import org.junit.Test
import ru.karasevm.privatednstoggle.management.*

class DashboardRegressionTest {
    @Test fun nextDnsTotalsIncludeAllStatusesAndBlockedCountIsNotAllTraffic() {
        val data = DashboardData.stats("nextdns", """{"data":[{"status":"default","queries":90},{"status":"blocked","queries":8},{"status":"allowed","queries":2}]}""")
        assertEquals(100L, data.total)
        assertEquals(8L, data.blocked)
        assertEquals(92L, data.other)
    }
    @Test fun adguardAddsTimeBucketsWithoutTreatingThemAsAccountQuota() {
        val data = DashboardData.stats("adguard", """{"stats":[{"value":{"queries":10,"blocked":3}},{"value":{"queries":30,"blocked":4}}]}""")
        assertEquals(40L, data.total)
        assertEquals(7L, data.blocked)
        val quota = DashboardData.quotas("""{"requests":{"used":900,"limit":1000}}""").single()
        assertEquals(900L, quota.used)
        assertEquals(1000L, quota.limit)
    }
    @Test fun invalidOrMissingStatisticsDoNotBecomeZeroTraffic() {
        listOf("{}", """{"errors":[{"code":"denied"}]}""", """{"data":[{"status":"blocked","queries":-1}]}""").forEach {
            try { DashboardData.stats("nextdns", it); fail("Invalid response accepted") } catch (_: Exception) {}
        }
    }
    @Test fun emptyProviderResultMeansNoRecords() {
        assertEquals(DashboardStats(0, 0), DashboardData.stats("nextdns", """{"data":[]}"""))
        assertTrue(DashboardData.queries("adguard", """{"items":[]}""").isEmpty())
    }
    @Test fun queryStatusFiltersUseExactBlockingStates() {
        val rows = DashboardData.queries("adguard", """{"items":[{"domain":"ads.example","filtering_info":{"filtering_status":"REQUEST_BLOCKED"}},{"domain":"safe.example","filtering_info":{"filtering_status":"REQUEST_ALLOWED"}},{"domain":"unknown.example","filtering_info":{"filtering_status":"UNBLOCKED"}}]}""")
        assertEquals(listOf(true,false,false), rows.map { it.blocked })
    }
    @Test fun cacheSharesForegroundSnapshotButNeverAnotherHostProfileOrExpiredData() {
        val cache = DashboardCache(100)
        val data = listOf(ReadSection("Stats", "synthetic"))
        cache.put("host|provider|profile1", data, 1000)
        assertEquals(data, cache.get("host|provider|profile1", 1050))
        assertNull(cache.get("host|provider|profile2", 1050))
        assertNull(cache.get("other|provider|profile1", 1050))
        assertNull(cache.get("host|provider|profile1", 1100))
        assertNull(cache.get("host|provider|profile1", 999))
        cache.clear()
        assertNull(cache.get("host|provider|profile1", 1050))
    }
}
