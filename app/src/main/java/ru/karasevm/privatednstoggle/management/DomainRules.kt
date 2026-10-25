package ru.karasevm.privatednstoggle.management

import java.net.IDN
import java.net.URL
import java.util.Locale
import javax.net.ssl.HttpsURLConnection
import kotlinx.serialization.json.*

object DomainPolicy {
    fun normalize(value: String): String {
        val raw = value.trim().removeSuffix(".")
        require(raw.none { it.isWhitespace() || it.isISOControl() })
        val domain = IDN.toASCII(raw, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT)
        require(domain.length in 3..253 && domain.contains('.'))
        require(domain.split('.').all { it.length in 1..63 && it.matches(Regex("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?")) })
        require(!domain.split('.').all { it.all(Char::isDigit) })
        return domain
    }
}

class RuleFailure(message: String) : Exception(message)
interface DomainWriteTransport { fun add(profile: String, domain: String, allow: Boolean, key: String) }
/** Narrow write capability: one domain, one NextDNS list. Never replaces or deletes a list. */
class NextDnsWriteTransport : DomainWriteTransport {
    override fun add(profile: String, domain: String, allow: Boolean, key: String) {
        require(ManagementPolicy.validProfile(profile))
        require(DomainPolicy.normalize(domain) == domain)
        require(key.isNotBlank() && key.none { it.isISOControl() })
        val list = if(allow) "allowlist" else "denylist"
        val conn = URL("https://api.nextdns.io/profiles/$profile/$list").openConnection() as HttpsURLConnection
        try {
            conn.requestMethod = "POST"
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 15000; conn.readTimeout = 15000
            conn.setRequestProperty("X-Api-Key", key)
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            conn.doOutput = true
            val body = buildJsonObject { put("id", domain); put("active", true) }.toString().toByteArray(Charsets.UTF_8)
            conn.setFixedLengthStreamingMode(body.size)
            conn.outputStream.use { it.write(body) }
            if(conn.responseCode !in 200..299) throw ApiFailure(conn.responseCode)
            val response = conn.inputStream.bufferedReader().use { it.readTextLimited() }
            if(response.isNotBlank()) {
                val json = Json.parseToJsonElement(response).jsonObject
                if(json["errors"]?.jsonArray?.isNotEmpty() == true) throw RuleFailure("O provedor recusou a regra. Confira a lista no painel oficial.")
            }
        } finally { conn.disconnect() }
    }
    private fun java.io.Reader.readTextLimited(): String {
        val result = StringBuilder()
        val buffer = CharArray(4096)
        while(true) {
            val count = read(buffer)
            if(count < 0) break
            require(result.length + count <= 2_000_000)
            result.append(buffer, 0, count)
        }
        return result.toString()
    }
}

class NextDnsDomainRules(
    private val read: ReadTransport = OfficialReadTransport(),
    private val write: DomainWriteTransport = NextDnsWriteTransport()
) {
    private fun active(profile: String, domain: String, list: String, key: String): Boolean {
        var cursor: String? = null
        val visited = mutableSetOf<String>()
        repeat(20) {
            val suffix = cursor?.let { "&cursor=" + java.net.URLEncoder.encode(it, "UTF-8") }.orEmpty()
            val json = Json.parseToJsonElement(read.get("https://api.nextdns.io/profiles/$profile/$list?limit=500$suffix", "X-Api-Key", key)).jsonObject
            require(json["errors"] == null || json["errors"]?.jsonArray?.isEmpty() == true)
            val entries = json.getValue("data").jsonArray
            if(entries.any { item ->
                val entry = item.jsonObject
                entry["id"]?.jsonPrimitive?.content?.equals(domain, true) == true && entry.getValue("active").jsonPrimitive.boolean
            }) return true
            val next = json["meta"]?.jsonObject?.get("pagination")?.jsonObject?.get("cursor")
            if(next == null || next == JsonNull) return false
            cursor = next.jsonPrimitive.content
            require(visited.add(cursor))
        }
        throw RuleFailure("Lista extensa demais. Gerencie a regra no painel oficial.")
    }
    fun apply(profile: String, rawDomain: String, allow: Boolean, key: String) {
        require(ManagementPolicy.validProfile(profile))
        val domain = DomainPolicy.normalize(rawDomain)
        val target = if(allow) "allowlist" else "denylist"
        val opposite = if(allow) "denylist" else "allowlist"
        if(active(profile, domain, opposite, key)) throw RuleFailure("Existe uma regra ativa para este domínio na lista oposta. Revise-a no painel antes de continuar; nenhuma regra foi removida.")
        if(active(profile, domain, target, key)) return
        write.add(profile, domain, allow, key)
        if(!active(profile, domain, target, key)) throw RuleFailure("A regra foi enviada, mas não foi possível confirmá-la. Confira o painel antes de tentar novamente.")
    }
}
