package ru.karasevm.privatednstoggle.management

class AdGuardProvider(private val transport: ReadTransport) : DnsProvider {
    override val id = "adguard"
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
