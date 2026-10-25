package ru.karasevm.privatednstoggle.management

import java.net.URL
import javax.net.ssl.HttpsURLConnection

interface ReadTransport { fun get(url: String, header: String, key: String): String }
class OfficialReadTransport : ReadTransport {
    override fun get(url: String, header: String, key: String): String {
        val target = URL(url)
        require(target.protocol == "https" && target.host in setOf("api.nextdns.io", "api.adguard-dns.io"))
        require(key.isNotBlank() && key.none { it.isISOControl() })
        val conn = target.openConnection() as HttpsURLConnection
        try {
            conn.requestMethod = "GET"
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.setRequestProperty(header, key)
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            if (code !in 200..299) throw ApiFailure(code)
            return conn.inputStream.bufferedReader().use { reader ->
                val data = StringBuilder()
                val buf = CharArray(4096)
                while (true) {
                    val count = reader.read(buf)
                    if (count < 0) break
                    require(data.length + count <= 2_000_000) { "Resposta muito grande" }
                    data.append(buf, 0, count)
                }
                data.toString()
            }
        } finally { conn.disconnect() }
    }
}
class ApiFailure(val status: Int) : Exception(when (status) {
    401, 403 -> "Chave expirada, inválida ou sem permissão. Atualize a chave."
    429 -> "Limite de requisições atingido. Aguarde antes de atualizar."
    else -> "Falha HTTP $status"
})
data class ReadSection(val title: String, val json: String)
interface DnsProvider {
    fun discover(hostname: String, key: String): DiscoveryResult
    val id: String
    fun statistics(profile: String, key: String, now: Long = System.currentTimeMillis()): List<ReadSection> = read(profile, key, now)
    fun queries(profile: String, key: String, now: Long = System.currentTimeMillis()): ReadSection = read(profile, key, now)[1]
    fun read(profile: String, key: String, now: Long = System.currentTimeMillis()): List<ReadSection>
}
object Providers {
    fun get(id: String, transport: ReadTransport = OfficialReadTransport()): DnsProvider = when (id) {
        "nextdns" -> NextDnsProvider(transport)
        "adguard" -> AdGuardProvider(transport)
        else -> error("Provedor não suportado")
    }
}
