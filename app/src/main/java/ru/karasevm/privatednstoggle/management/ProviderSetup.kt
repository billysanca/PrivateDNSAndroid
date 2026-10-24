package ru.karasevm.privatednstoggle.management

import java.util.Locale

/** Public provider URLs and conservative, local-only hostname hints. No credentials in links. */
object ProviderSetup {
    fun normalizedHost(host: String) = host.trim().trimEnd('.').lowercase(Locale.ROOT)
    fun detect(host: String): String? {
        val value = normalizedHost(host)
        return when {
            value.endsWith(".dns.nextdns.io") -> "nextdns"
            listOf("adguard-dns.com", "adguard-dns.io", "adguard.ch").any { value.endsWith(".$it") } -> "adguard"
            else -> null
        }
    }
    fun nextDnsProfile(host: String): String? = Regex("(?:[a-z0-9-]+-)?([a-f0-9]{6})\\.dns\\.nextdns\\.io")
        .matchEntire(normalizedHost(host))?.groupValues?.get(1)
    fun keyUrl(provider: String): String = when (provider) {
        "nextdns" -> "https://my.nextdns.io/account"
        "adguard" -> "https://adguard-dns.io/en/dashboard/user-settings/api-keys"
        else -> error("Provedor desconhecido")
    }
    fun dashboardUrl(provider: String, profile: String = ""): String = when (provider) {
        "nextdns" -> if (ManagementPolicy.validProfile(profile)) "https://my.nextdns.io/$profile/setup" else "https://my.nextdns.io/"
        "adguard" -> "https://adguard-dns.io/en/dashboard/"
        else -> error("Provedor desconhecido")
    }
}

data class ProviderTarget(val id: String, val name: String)
data class DiscoveryResult(val targets: List<ProviderTarget>, val matchedId: String?)
