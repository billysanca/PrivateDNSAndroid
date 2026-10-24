package ru.karasevm.privatednstoggle

import org.junit.Assert.*
import org.junit.Test
import ru.karasevm.privatednstoggle.management.*

class ManagementRegressionTest {
    @Test fun httpsOnlyAndNoEmbeddedCredentials() {
        listOf("http://example.org", "javascript:alert(1)", "file:///tmp/test", "https://user:pass@example.org", "https://example.org:65536", "https:///path", "https://example.org\n").forEach {
            assertFalse(it, ManagementPolicy.validUrl(it))
        }
        assertTrue(ManagementPolicy.validUrl("https://dashboard.example.org:8443/account?tab=stats#usage"))
    }
    @Test fun profileCannotInjectPathOrQuery() {
        listOf("../account", "foo?bar", "a/b", "", "a\r\nHeader: injected").forEach { assertFalse(ManagementPolicy.validProfile(it)) }
        assertTrue(ManagementPolicy.validProfile("abc123"))
    }
    @Test fun defaultIsOptInAndBrowserPreferred() {
        val config = ManagementConfig()
        assertEquals(ManagementMode.NONE, config.mode)
        assertEquals(WebMode.CUSTOM_TAB, config.webMode)
    }
    private class Fake : ReadTransport {
        val calls = mutableListOf<Triple<String,String,String>>()
        override fun get(url: String, header: String, key: String): String {
            calls += Triple(url, header, key)
            return "{\"data\":[]}"
        }
    }
    @Test fun nextDnsOnlyReadsOfficialEndpointsAndNeverInventsQuota() {
        val fake = Fake()
        val result = NextDnsProvider(fake).read("abc123", "test", 100000000L)
        assertEquals(2, fake.calls.size)
        assertTrue(fake.calls.all { it.first.startsWith("https://api.nextdns.io/profiles/abc123/") && it.second == "X-Api-Key" })
        assertTrue(fake.calls.any { it.first.endsWith("logs?from=-24h&limit=50") })
        assertTrue(result.last().json.contains("Indisponível"))
    }
    @Test fun adguardTimeScopeAndAccountQuotaAreExplicit() {
        val fake = Fake()
        AdGuardProvider(fake).read("server-id", "test", 100000000L)
        assertEquals(3, fake.calls.size)
        assertTrue(fake.calls.all { it.first.startsWith("https://api.adguard-dns.io/oapi/v1/") && it.third == "ApiKey test" })
        assertTrue(fake.calls[0].first.contains("time_from_millis=13600000&time_to_millis=100000000&dns_servers=server-id"))
        assertEquals("https://api.adguard-dns.io/oapi/v1/account/limits", fake.calls.last().first)
    }
    @Test fun invalidProfilesDoNotTouchTransport() {
        val fake = Fake()
        for (provider in listOf(NextDnsProvider(fake), AdGuardProvider(fake))) {
            try { provider.read("../escape", "test"); fail("Accepted injection") } catch (_: IllegalArgumentException) {}
        }
        assertTrue(fake.calls.isEmpty())
    }
    @Test fun unknownProviderFailsClosed() {
        try { Providers.get("untrusted"); fail("Accepted unknown provider") } catch (_: IllegalStateException) {}
    }
    @Test fun transportRejectsUntrustedDestinationsBeforeNetwork() {
        for (url in listOf("http://api.nextdns.io/profiles", "https://attacker.example/", "https://api.nextdns.io.attacker.example/")) {
            try { OfficialReadTransport().get(url, "X-Api-Key", "secret"); fail("Accepted $url") }
            catch (_: IllegalArgumentException) {}
        }
    }
    @Test fun originalBackupPreservesLabelsOrderAndDisabledHostnames() {
        val backup = kotlinx.serialization.json.Json.decodeFromString<ru.karasevm.privatednstoggle.util.BackupUtils.Backup>(
            """{"dns_servers":[{"id":4,"server":"one.example","label":"One","enabled":false,"sortOrder":2},{"id":5,"server":"two.example","label":"Two","enabled":true,"sortOrder":1}],"auto_mode":3,"require_unlock":true}""")
        assertEquals(2, backup.dnsServers.size)
        assertEquals("One", backup.dnsServers[0].label)
        assertFalse(backup.dnsServers[0].enabled)
        assertEquals(2, backup.dnsServers[0].sortOrder)
        assertEquals(3, backup.autoMode)
        assertTrue(backup.requireUnlock!!)
        val roundTrip = kotlinx.serialization.json.Json.decodeFromString<ru.karasevm.privatednstoggle.util.BackupUtils.Backup>(
            kotlinx.serialization.json.Json.encodeToString(ru.karasevm.privatednstoggle.util.BackupUtils.Backup.serializer(), backup))
        assertEquals(backup, roundTrip)
    }}
