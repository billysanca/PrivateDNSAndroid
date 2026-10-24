package ru.karasevm.privatednstoggle.management

class NextDnsProvider(private val transport: ReadTransport) : DnsProvider {
    override val id = "nextdns"
    override fun read(profile: String, key: String, now: Long): List<ReadSection> {
        require(ManagementPolicy.validProfile(profile))
        val base = "https://api.nextdns.io/profiles/$profile"
        return listOf(
            ReadSection("Estatísticas · últimas 24h", transport.get("$base/analytics/status?from=-24h", "X-Api-Key", key)),
            ReadSection("Consultas · primeira página (50)", transport.get("$base/logs?from=-24h&limit=50", "X-Api-Key", key)),
            ReadSection("Cota da conta", "Indisponível na API pública documentada do NextDNS. Estatísticas de um perfil não representam a cota da conta."))
    }
}
