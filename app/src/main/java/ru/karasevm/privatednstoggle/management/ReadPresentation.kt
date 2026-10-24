package ru.karasevm.privatednstoggle.management

import org.json.JSONArray
import org.json.JSONObject

/** Provider-specific presentation stays out of the generic web launcher. */
object ReadPresentation {
    fun format(provider: String, index: Int, json: String): String {
        if (provider == "nextdns" && index == 2) return json
        val root = JSONObject(json)
        if (root.has("errors")) error("API retornou erro")
        if (index == 2) {
            return root.keys().asSequence().joinToString("\n") { name ->
                val limit = root.optJSONObject(name)
                if (limit != null && limit.has("used") && limit.has("limit"))
                    "$name: ${limit.getLong("used")} / ${limit.getLong("limit")}" else "$name: indisponível"
            }
        }
        if (index == 0 && provider == "adguard") {
            val rows = root.getJSONArray("stats")
            var queries = 0L; var blocked = 0L
            for (i in 0 until rows.length()) {
                val value = rows.getJSONObject(i).getJSONObject("value")
                queries += value.getLong("queries"); blocked += value.getLong("blocked")
            }
            return "Consultas: $queries\nBloqueadas: $blocked"
        }
        if (index == 0) {
            val rows = root.getJSONArray("data")
            if (rows.length() == 0) return "Sem estatísticas no período."
            return (0 until rows.length()).joinToString("\n") {
                val row = rows.getJSONObject(it)
                "${row.getString("status")}: ${row.getLong("queries")}"
            }
        }
        val rows = root.getJSONArray(if (provider == "nextdns") "data" else "items")
        if (rows.length() == 0) return "Nenhuma consulta no período (ou registro desativado pelo provedor)."
        return (0 until rows.length()).joinToString("\n\n") {
            val row = rows.getJSONObject(it)
            if (provider == "nextdns") "${row.optString("timestamp")} · ${row.optString("domain")}\n${row.optString("status")}"
            else "${row.optString("time_iso")} · ${row.optString("domain")}\n${row.optJSONObject("filtering_info")?.optString("filtering_status").orEmpty()}"
        } + "\n\nExibindo apenas a primeira página, até 50 consultas."
    }
}
