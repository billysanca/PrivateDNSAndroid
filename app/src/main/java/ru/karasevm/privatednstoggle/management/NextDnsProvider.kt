package ru.karasevm.privatednstoggle.management

class NextDnsProvider(private val transport: ReadTransport) : DnsProvider {
    override fun discover(hostname: String, key: String): DiscoveryResult {
        val targets = DiscoveryPages(transport).read("https://api.nextdns.io/profiles", "X-Api-Key", key, false)
            .map { it.target() }.distinctBy { it.id }
        val hint = ProviderSetup.nextDnsProfile(hostname)
        return DiscoveryResult(targets, targets.singleOrNull { it.id == hint }?.id)
    }
    override val id = "nextdns"
    override fun statistics(profile: String, key: String, now: Long): List<ReadSection> {
        require(ManagementPolicy.validProfile(profile))
        return listOf(ReadSection("Estatísticas", transport.get("https://api.nextdns.io/profiles/$profile/analytics/status?from=-24h", "X-Api-Key", key)),
            ReadSection("", ""), ReadSection("Cota", "Indisponível na API pública documentada."))
    }
    override fun queries(profile: String, key: String, now: Long): ReadSection {
        require(ManagementPolicy.validProfile(profile))
        return ReadSection("Consultas", transport.get("https://api.nextdns.io/profiles/$profile/logs?from=-24h&limit=50", "X-Api-Key", key))
    }
    override fun read(profile: String, key: String, now: Long): List<ReadSection> {
        require(ManagementPolicy.validProfile(profile))
        val base = "https://api.nextdns.io/profiles/$profile"
        return listOf(
            ReadSection("Estatísticas · últimas 24h", transport.get("$base/analytics/status?from=-24h", "X-Api-Key", key)),
            ReadSection("Consultas · primeira página (50)", transport.get("$base/logs?from=-24h&limit=50", "X-Api-Key", key)),
            ReadSection("Cota da conta", "Indisponível na API pública documentada do NextDNS. Estatísticas de um perfil não representam a cota da conta."))
    }
}
