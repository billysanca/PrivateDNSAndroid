package ru.karasevm.privatednstoggle.management

import java.net.URI
import java.net.URLEncoder
import kotlinx.serialization.json.*

/** Only follows opaque cursors against a fixed API URL, never provider-supplied navigation URLs. */
internal class DiscoveryPages(private val transport: ReadTransport) {
    fun read(base: String, header: String, key: String, adguard: Boolean): List<JsonObject> {
        val rows = mutableListOf<JsonObject>()
        var cursor: String? = null
        val visited = mutableSetOf<String>()
        repeat(20) {
            val query = "?limit=100" + (cursor?.let { "&cursor=" + URLEncoder.encode(it, "UTF-8") } ?: "")
            val root = Json.parseToJsonElement(transport.get(base + query, header, key)).jsonObject
            require(root["errors"] == null) { "O provedor recusou a consulta." }
            val items = root[if (adguard) "items" else "data"] as? JsonArray
                ?: error("Resposta de descoberta incompatível")
            rows += items.map { it.jsonObject }
            cursor = if (adguard) {
                root["navigation"]?.jsonObject?.get("next")?.takeUnless { it is JsonNull }
                    ?.jsonObject?.get("page_cursor")?.jsonPrimitive?.contentOrNull
            } else {
                root["meta"]?.jsonObject?.get("pagination")?.jsonObject?.get("cursor")?.jsonPrimitive?.contentOrNull
            }
            if (cursor.isNullOrEmpty()) return rows
            require(visited.add(cursor)) { "Cursor repetido pelo provedor" }
        }
        error("Muitos resultados. Use o painel oficial ou informe o ID manualmente.")
    }
}

internal fun JsonObject.target(): ProviderTarget {
    val id = getValue("id").jsonPrimitive.content
    require(ManagementPolicy.validProfile(id))
    return ProviderTarget(id, get("name")?.jsonPrimitive?.contentOrNull?.take(120)?.takeIf { it.isNotBlank() } ?: id)
}

internal fun matchesTlsAddress(address: String, hostname: String): Boolean = runCatching {
    val parsed = URI(if (address.contains("://")) address else "tls://$address")
    parsed.scheme.equals("tls", true) && parsed.rawUserInfo == null &&
        parsed.host?.let(ProviderSetup::normalizedHost) == ProviderSetup.normalizedHost(hostname)
}.getOrDefault(false)
