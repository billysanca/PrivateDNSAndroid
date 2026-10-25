package ru.karasevm.privatednstoggle.management

import kotlinx.serialization.json.*

class AdGuardProvider(private val transport: ReadTransport) : DnsProvider {
    override fun discover(hostname: String, key: String): DiscoveryResult {
        val pages = DiscoveryPages(transport)
        val targets = pages.read("https://api.adguard-dns.io/oapi/v2/dns_servers", "Authorization", "ApiKey $key", true)
            .map { it.target() }.distinctBy { it.id }
        val devices = pages.read("https://api.adguard-dns.io/oapi/v2/devices", "Authorization", "ApiKey $key", true)
        val matchingServers = devices.filter { device ->
            device["dns_addresses"]?.jsonObject?.get("dns_over_tls_url")?.jsonPrimitive?.contentOrNull
                ?.let { matchesTlsAddress(it, hostname) } == true
        }.mapNotNull { it["dns_server_id"]?.jsonPrimitive?.contentOrNull }.distinct()
        val match = matchingServers.singleOrNull()?.takeIf { id -> targets.any { it.id == id } }
        return DiscoveryResult(targets, match)
    }
    override val id = "adguard"
    override fun statistics(profile: String, key: String, now: Long): List<ReadSection> {
        require(ManagementPolicy.validProfile(profile))
        val base = "https://api.adguard-dns.io/oapi/v1"
        val q = "time_from_millis=${now - 86400000}&time_to_millis=$now&dns_servers=$profile"
        return listOf(ReadSection("Estatísticas", transport.get("$base/stats/time?$q", "Authorization", "ApiKey $key")),
            ReadSection("", ""), ReadSection("Cotas", transport.get("$base/account/limits", "Authorization", "ApiKey $key")))
    }
    override fun queries(profile: String, key: String, now: Long): ReadSection {
        require(ManagementPolicy.validProfile(profile))
        val q = "time_from_millis=${now - 86400000}&time_to_millis=$now&dns_servers=$profile"
        return ReadSection("Consultas", transport.get("https://api.adguard-dns.io/oapi/v1/query_log?$q&limit=50", "Authorization", "ApiKey $key"))
    }
    override fun read(profile: String, key: String, now: Long): List<ReadSection> {
        require(ManagementPolicy.validProfile(profile))
        val base = "https://api.adguard-dns.io/oapi/v1"
        val q = "time_from_millis=${now - 86400000}&time_to_millis=$now&dns_servers=$profile"
        return listOf(
            ReadSection("Estatísticas · últimas 24h", transport.get("$base/stats/time?$q", "Authorization", "ApiKey $key")),
            ReadSection("Consultas · primeira página (50)", transport.get("$base/query_log?$q&limit=50", "Authorization", "ApiKey $key")),
            ReadSection("Cotas e consumo · conta inteira", transport.get("$base/account/limits", "Authorization", "ApiKey $key")))
    }
}
