package ru.karasevm.privatednstoggle

import org.junit.Assert.*
import org.junit.Test
import ru.karasevm.privatednstoggle.management.*

class DomainRulesRegressionTest {
    @Test fun normalizesDomainsWithoutAcceptingRulesOrUrls() {
        assertEquals("xn--bcher-kva.example", DomainPolicy.normalize("Bücher.example."))
        listOf("https://example.com", "*.example.com", "||example.com^", "127.0.0.1",
            "example.com/path", "foo\n.example", "-bad.example", "example.com:443", "localhost").forEach {
            assertTrue(it, runCatching { DomainPolicy.normalize(it) }.isFailure)
        }
    }
    @Test fun separateWebPagesFallBackToExistingPanel() {
        val old = ManagementConfig(ManagementMode.WEB, "https://example.com/panel")
        assertEquals(old.url, old.pageUrl(1))
        assertEquals(old.url, old.pageUrl(2))
        val updated = old.copy(statisticsUrl="https://example.com/stats", queriesUrl="https://example.com/logs")
        assertEquals(updated.statisticsUrl, updated.pageUrl(1))
        assertEquals(updated.queriesUrl, updated.pageUrl(2))
    }
    @Test fun activeRoutingDoesNotFallBackToFirstHostname() {
        val hosts = listOf("abc123.dns.nextdns.io", "dns.example")
        assertEquals(0, ManagementRouting.activeIndex(hosts, "hostname", "ABC123.dns.nextdns.io."))
        assertEquals(-1, ManagementRouting.activeIndex(hosts, "off", hosts[0]))
        assertEquals(-1, ManagementRouting.activeIndex(hosts, "opportunistic", hosts[0]))
        assertEquals(-1, ManagementRouting.activeIndex(hosts, "hostname", "other.example"))
    }
    @Test fun knownProvidersAlwaysUseApiWithoutOverwritingMetadata() {
        val saved = ManagementConfig(ManagementMode.WEB, "https://example.com")
        val resolved = ManagementRouting.effective("abc123.dns.nextdns.io", saved)
        assertEquals(ManagementMode.API, resolved.mode)
        assertEquals("abc123", resolved.profile)
        assertEquals(ManagementMode.WEB, saved.mode)
        assertEquals(saved, ManagementRouting.effective("dns.example", saved))
        assertEquals("", saved.copy(mode=ManagementMode.NONE).pageUrl(1))
    }
    @Test fun firstTabSelectionWinsAcrossAllTabsEvenWhenAndroidDnsDiffers() {
        val ids = listOf(10, 20)
        val hosts = listOf("a.example", "b.example")
        assertEquals(1, ManagementRouting.chosenIndex(ids, hosts, 20, "hostname", "a.example"))
        assertEquals(1, ManagementRouting.chosenIndex(ids, hosts, 20, "off", null))
        assertEquals(1, ManagementRouting.chosenIndex(ids, listOf("a.example", "renamed.example"), 20, "off", null))
    }
    @Test fun removedSelectionNeverSilentlyUsesAnotherAccount() {
        assertEquals(-1, ManagementRouting.chosenIndex(listOf(10), listOf("a.example"), 20, "hostname", "a.example"))
        assertEquals(0, ManagementRouting.chosenIndex(listOf(10), listOf("a.example"), null, "hostname", "a.example"))
        assertEquals(-1, ManagementRouting.chosenIndex(listOf(10), listOf("a.example"), null, "off", null))
    }
    private class Fake : ReadTransport, DomainWriteTransport {
        val calls = mutableListOf<String>()
        var writes = 0
        var conflict = false
        var present = false
        var confirm = true
        var error = false
        override fun get(url: String, header: String, key: String): String {
            calls.add(url)
            assertTrue(url.startsWith("https://api.nextdns.io/profiles/abc123/"))
            assertEquals("X-Api-Key", header)
            if(error) return """{"errors":[{"code":"invalid"}]}"""
            val active = if(url.contains("/denylist")) conflict else present
            return if(active) """{"data":[{"id":"example.com","active":true}]}""" else """{"data":[]}"""
        }
        override fun add(profile: String, domain: String, allow: Boolean, key: String) {
            assertEquals("abc123", profile); assertEquals("example.com", domain); assertTrue(allow)
            writes++; present = confirm
        }
    }
    @Test fun writesOneDomainAndVerifiesResult() {
        val fake = Fake()
        NextDnsDomainRules(fake, fake).apply("abc123", "EXAMPLE.com.", true, "key")
        assertEquals(1, fake.writes); assertEquals(3, fake.calls.size)
    }
    @Test fun oppositeRuleNeverGetsDeletedOrOverwritten() {
        val fake = Fake().apply { conflict = true }
        assertTrue(runCatching { NextDnsDomainRules(fake, fake).apply("abc123", "example.com", true, "key") }.exceptionOrNull() is RuleFailure)
        assertEquals(0, fake.writes)
    }
    @Test fun alreadyActiveRuleDoesNotWriteAgain() {
        val fake = Fake().apply { present = true }
        NextDnsDomainRules(fake, fake).apply("abc123", "example.com", true, "key")
        assertEquals(0, fake.writes)
    }
    @Test fun unconfirmedWriteIsNotSuccessAndNotRetried() {
        val fake = Fake().apply { confirm = false }
        assertTrue(runCatching { NextDnsDomainRules(fake, fake).apply("abc123", "example.com", true, "key") }.exceptionOrNull() is RuleFailure)
        assertEquals(1, fake.writes)
    }
    @Test fun invalidProfileOrReadErrorNeverWrites() {
        val fake = Fake()
        assertTrue(runCatching { NextDnsDomainRules(fake, fake).apply("../other", "example.com", true, "key") }.isFailure)
        fake.error = true
        assertTrue(runCatching { NextDnsDomainRules(fake, fake).apply("abc123", "example.com", true, "key") }.isFailure)
        assertEquals(0, fake.writes)
    }
}
