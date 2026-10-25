package ru.karasevm.privatednstoggle.management

import kotlinx.serialization.json.*

data class LiveEvent(val id: String, val data: String)

/** Bounded SSE decoder. Never interprets event content as executable instructions. */
class SseDecoder {
    private var id = ""
    private var kind = "message"
    private val data = StringBuilder()
    fun line(line: String): LiveEvent? {
        require(line.length <= 65536)
        if(line.isEmpty()) {
            if(kind == "error") throw RuleFailure("O provedor interrompeu a transmissão. Reconecte em Tempo Real.")
            val event = if(kind == "message" && data.isNotEmpty()) LiveEvent(id, data.toString().trimEnd(10.toChar())) else null
            data.clear(); kind = "message"
            return event
        }
        if(line.startsWith(":")) return null
        val name = line.substringBefore(':')
        val value = line.substringAfter(':', "").removePrefix(" ")
        when(name) {
            "event" -> kind = value
            "id" -> { require(value.length <= 1024 && value.none { it.isISOControl() }); id = value }
            "data" -> { require(data.length + value.length <= 65536); data.append(value).append(10.toChar()) }
        }
        return null
    }
}

class LiveQueryBuffer(initial: String, private val limit: Int = 50) {
    private val body = Json.parseToJsonElement(initial).jsonObject
    private val rows = body.getValue("data").jsonArray.take(limit).toMutableList()
    private val ids = linkedSetOf<String>()
    val cursor = body["meta"]?.jsonObject?.get("stream")?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
    init { require(limit in 1..200); DashboardData.queries("nextdns", initial) }
    fun append(event: LiveEvent): String {
        require(event.id.isNotBlank() && event.id.length <= 1024 && event.id.none { it.isISOControl() })
        if(ids.add(event.id)) {
            val row = Json.parseToJsonElement(event.data).jsonObject
            require(row["timestamp"]?.jsonPrimitive?.contentOrNull?.isNotBlank() == true && row["errors"] == null)
            DashboardData.queries("nextdns", buildJsonObject { put("data", buildJsonArray { add(row) }) }.toString())
            rows.add(0, row)
            while(rows.size > limit) rows.removeAt(rows.lastIndex)
            if(ids.size > 256) ids.remove(ids.first())
        }
        return buildJsonObject { put("data", JsonArray(rows)) }.toString()
    }
}
