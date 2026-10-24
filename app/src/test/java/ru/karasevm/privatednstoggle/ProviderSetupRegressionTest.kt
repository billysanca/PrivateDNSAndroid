package ru.karasevm.privatednstoggle

import org.junit.Assert.*
import org.junit.Test
import ru.karasevm.privatednstoggle.management.*

class ProviderSetupRegressionTest {
    private class Fixture(private val responses: List<String>) : ReadTransport {
        val calls = mutableListOf<Triple<String,String,String>>()
        override fun get(url: String, header: String, key: String): String {
            calls += Triple(url, header, key)
            return responses[calls.lastIndex]
        }
    }
    @Test fun nextDnsHintHandlesDeviceNameCaseAndTrailingDot() {
        assertEquals("abc123", ProviderSetup.nextDnsProfile(" Phone-ABC123.dns.nextdns.io. "))
        assertEquals("abc123", ProviderSetup.nextDnsProfile("abc123.dns.nextdns.io"))
        assertNull(ProviderSetup.nextDnsProfile("abc123.dns.nextdns.io.attacker.example"))
        assertNull(ProviderSetup.detect("dns.adguard-dns.com.attacker.example"))
        assertNull(ProviderSetup.detect("unrelated.example"))
        assertEquals("adguard", ProviderSetup.detect("device.d.adguard-dns.com"))
    }
    @Test fun nextDnsDiscoversAllPagesAndMatchesHostnameNotFirstProfile() {
        val transport = Fixture(listOf(
            """{"data":[{"id":"def456","name":"Other"}],"meta":{"pagination":{"cursor":"a+/="}}}""",
            """{"data":[{"id":"abc123","name":"Phone"}],"meta":{"pagination":{"cursor":null}}}"""))
        val result = NextDnsProvider(transport).discover("phone-abc123.dns.nextdns.io", "synthetic-key")
        assertEquals("abc123", result.matchedId)
        assertEquals(2, result.targets.size)
        assertEquals("https://api.nextdns.io/profiles?limit=100&cursor=a%2B%2F%3D", transport.calls[1].first)
        assertTrue(transport.calls.all { it.second == "X-Api-Key" && it.third == "synthetic-key" && !it.first.contains("synthetic-key") })
    }
    @Test fun oneUnrelatedProfileIsNeverSelectedAutomatically() {
        val transport = Fixture(listOf("""{"data":[{"id":"def456","name":"Other"}]}"""))
        val result = NextDnsProvider(transport).discover("abc123.dns.nextdns.io", "key")
        assertNull(result.matchedId)
        assertEquals(1, result.targets.size)
    }
    @Test fun adguardMapsExactDeviceTlsHostnameToServerId() {
        val transport = Fixture(listOf(
            """{"items":[{"id":"server1","name":"Home"},{"id":"server2","name":"Phone"}],"navigation":{}}""",
            """{"items":[{"id":"device1","dns_server_id":"server2","dns_addresses":{"dns_over_tls_url":"tls://device1.d.adguard-dns.com"}}],"navigation":{}}"""))
        val result = AdGuardProvider(transport).discover("device1.d.adguard-dns.com", "key")
        assertEquals("server2", result.matchedId)
        assertNotEquals("device1", result.matchedId)
        assertEquals(2, transport.calls.size)
        assertTrue(transport.calls.all { it.second == "Authorization" && it.third == "ApiKey key" })
    }
    @Test fun conflictingAdguardDeviceMappingsRequireUserSelection() {
        val transport = Fixture(listOf(
            """{"items":[{"id":"server1"},{"id":"server2"}],"navigation":{}}""",
            """{"items":[{"dns_server_id":"server1","dns_addresses":{"dns_over_tls_url":"tls://same.example"}},{"dns_server_id":"server2","dns_addresses":{"dns_over_tls_url":"tls://same.example"}}],"navigation":{}}"""))
        assertNull(AdGuardProvider(transport).discover("same.example", "key").matchedId)
    }
    @Test fun adguardDoesNotMatchSuffixSpoofOrEmbeddedCredentials() {
        for (address in listOf("tls://device.example.attacker.example", "tls://user:password@device.example")) {
            val transport = Fixture(listOf(
                """{"items":[{"id":"server1"}],"navigation":{}}""",
                """{"items":[{"dns_server_id":"server1","dns_addresses":{"dns_over_tls_url":"$address"}}],"navigation":{}}"""))
            assertNull(AdGuardProvider(transport).discover("device.example", "key").matchedId)
        }
    }
    @Test fun repeatedCursorFailsRatherThanLoopingOrReturningPartialAssociation() {
        val page = """{"data":[{"id":"abc123"}],"meta":{"pagination":{"cursor":"repeat"}}}"""
        val transport = Fixture(listOf(page, page))
        try { NextDnsProvider(transport).discover("abc123.dns.nextdns.io", "key"); fail("Accepted loop") }
        catch (_: IllegalArgumentException) {}
        assertEquals(2, transport.calls.size)
    }
    @Test fun officialKeyLinksNeverContainProfileOrSecret() {
        assertEquals("https://my.nextdns.io/account", ProviderSetup.keyUrl("nextdns"))
        assertEquals("https://adguard-dns.io/en/dashboard/user-settings/api-keys", ProviderSetup.keyUrl("adguard"))
        assertEquals("https://my.nextdns.io/abc123/setup", ProviderSetup.dashboardUrl("nextdns", "abc123"))
        assertEquals("https://my.nextdns.io/", ProviderSetup.dashboardUrl("nextdns", "../escape?secret=key"))
        for (id in listOf("nextdns", "adguard")) assertTrue(ManagementPolicy.validUrl(ProviderSetup.keyUrl(id)))
    }
}
