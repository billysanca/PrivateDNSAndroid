package ru.karasevm.privatednstoggle.management

import kotlinx.serialization.json.*

data class DashboardStats(val total: Long, val blocked: Long) {
    val other: Long get() = total - blocked
}
data class DashboardQuery(val domain: String, val timestamp: String, val status: String, val device: String) {
    val blocked: Boolean get() = status.lowercase(java.util.Locale.ROOT) in setOf("blocked", "request_blocked", "response_blocked")
}
data class DashboardQuota(val name: String, val used: Long, val limit: Long)

/** Real API values only; a missing or invalid response is never turned into zero traffic. */
object DashboardData {
    private fun root(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject.also {
        require(it["errors"] == null) { "O provedor retornou erro" }
    }
    private fun JsonObject.value(key: String) = get(key)?.jsonPrimitive?.contentOrNull.orEmpty()
    private fun count(value: JsonElement?): Long = value?.jsonPrimitive?.longOrNull?.also { require(it >= 0) }
        ?: error("Contagem indisponível")
    fun stats(provider: String, json: String): DashboardStats {
        val body = root(json)
        var total = 0L; var blocked = 0L
        val rows = body[if(provider == "adguard") "stats" else "data"]?.jsonArray ?: error("Estatísticas indisponíveis")
        rows.forEach { row ->
            val item = row.jsonObject
            if (provider == "adguard") {
                val value = item.getValue("value").jsonObject
                total = Math.addExact(total, count(value["queries"]))
                blocked = Math.addExact(blocked, count(value["blocked"]))
            } else {
                val queries = count(item["queries"])
                total = Math.addExact(total, queries)
                if(item.value("status").equals("blocked", true)) blocked = Math.addExact(blocked, queries)
            }
        }
        require(blocked <= total)
        return DashboardStats(total, blocked)
    }
    fun queries(provider: String, json: String): List<DashboardQuery> {
        val body = root(json)
        return (body[if(provider == "adguard") "items" else "data"]?.jsonArray ?: error("Consultas indisponíveis")).map { row ->
            val item = row.jsonObject
            DashboardQuery(item.value("domain"), item.value(if(provider == "adguard") "time_iso" else "timestamp"),
                if(provider == "adguard") item["filtering_info"]?.jsonObject?.value("filtering_status").orEmpty() else item.value("status"),
                if(provider == "adguard") item.value("device_id") else (item["device"] as? JsonObject)?.value("name").orEmpty())
        }
    }
    fun quotas(json: String): List<DashboardQuota> = root(json).mapNotNull { (name, value) ->
        val obj = value as? JsonObject ?: return@mapNotNull null
        val used = obj["used"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
        val limit = obj["limit"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
        require(used >= 0)
        DashboardQuota(name, used, limit)
    }
}

/** Foreground-only snapshot shared by Stats and Queries. Never stored on disk. */
class DashboardCache(private val maxAge: Long = 60_000) {
    private var key: String? = null
    private var timestamp: Long = 0
    private var sections: List<ReadSection>? = null
    fun get(requestKey: String, now: Long): List<ReadSection>? = sections?.takeIf {
        key == requestKey && now >= timestamp && now - timestamp < maxAge
    }
    fun put(requestKey: String, data: List<ReadSection>, now: Long) { key = requestKey; timestamp = now; sections = data.toList() }
    fun clear() { key = null; sections = null; timestamp = 0 }
}
