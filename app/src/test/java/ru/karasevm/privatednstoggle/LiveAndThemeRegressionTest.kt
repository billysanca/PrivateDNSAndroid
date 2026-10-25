package ru.karasevm.privatednstoggle

import org.junit.Assert.*
import org.junit.Test
import ru.karasevm.privatednstoggle.management.*
import ru.karasevm.privatednstoggle.ui.ThemeMode

class LiveAndThemeRegressionTest {
    private fun event(id: String, domain: String) = LiveEvent(id,
        """{"timestamp":"2026-10-25T10:00:00Z","domain":"$domain","status":"default"}""")
    @Test fun decoderFramesEventsAndIgnoresHeartbeats() {
        val d = SseDecoder()
        assertNull(d.line(":keepalive"))
        d.line("id: abc")
        d.line("""data: {"timestamp":"2026-10-25T10:00:00Z","domain":"a.example"}""")
        val e = d.line("")!!
        assertEquals("abc", e.id)
        assertTrue(e.data.contains("a.example"))
        d.line("event: heartbeat"); d.line("data: {}")
        assertNull(d.line(""))
    }
    @Test fun oversizedOrInjectedStreamMetadataIsRejected() {
        val d = SseDecoder()
        assertTrue(runCatching { d.line("data: " + "a".repeat(65537)) }.isFailure)
        assertTrue(runCatching { d.line("id: bad" + 0.toChar() + "id") }.isFailure)
        d.line("event: error")
        assertTrue(runCatching { d.line("") }.exceptionOrNull() is RuleFailure)
    }
    @Test fun streamUsesResumeCursorAndKeepsLatestFiftyQueries() {
        val b = LiveQueryBuffer("""{"data":[],"meta":{"stream":{"id":"resume"}}}""")
        assertEquals("resume", b.cursor)
        var json = ""
        repeat(80) { json = b.append(event("id$it", "d$it.example")) }
        val rows = DashboardData.queries("nextdns", json)
        assertEquals(50, rows.size)
        assertEquals("d79.example", rows.first().domain)
        assertEquals("d30.example", rows.last().domain)
    }
    @Test fun duplicateEventIdsAreIgnoredButDistinctRequestsAreKept() {
        val b = LiveQueryBuffer("""{"data":[]}""")
        b.append(event("one", "same.example"))
        assertEquals(1, DashboardData.queries("nextdns", b.append(event("one", "same.example"))).size)
        assertEquals(2, DashboardData.queries("nextdns", b.append(event("two", "same.example"))).size)
        assertTrue(runCatching { b.append(LiveEvent("three", """{"error":"bad"}""")) }.isFailure)
    }
    @Test fun queryRefreshNeverCallsStatsOrAccountEndpoints() {
        val paths = mutableListOf<String>()
        val t = object : ReadTransport {
            override fun get(url: String, header: String, key: String): String {
                paths.add(url)
                return """{"data":[]}"""
            }
        }
        Providers.get("nextdns", t).queries("abc123", "key", 100000000L)
        Providers.get("adguard", t).queries("server_1", "key", 100000000L)
        assertEquals(2, paths.size)
        assertTrue(paths[0].contains("/logs?"))
        assertTrue(paths[1].contains("/query_log?"))
        assertTrue(paths.none { it.contains("/stats/") || it.contains("/analytics/") || it.contains("/account/") })
    }
    @Test fun openingStatisticsDoesNotReadQueryLogs() {
        val paths = mutableListOf<String>()
        val t = object : ReadTransport {
            override fun get(url: String, header: String, key: String): String {
                paths.add(url)
                return """{"data":[]}"""
            }
        }
        Providers.get("nextdns", t).statistics("abc123", "key", 100000000L)
        Providers.get("adguard", t).statistics("server_1", "key", 100000000L)
        assertEquals(3, paths.size)
        assertTrue(paths.none { it.contains("/logs") || it.contains("/query_log") })
    }
    @Test fun unknownThemeSafelyFallsBackToSystem() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.parse(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.parse("unavailable"))
        assertEquals(ThemeMode.OLED, ThemeMode.parse("OLED"))
        assertNotEquals(ThemeMode.LIGHT, ThemeMode.parse("DARK"))
    }
}
