package ru.karasevm.privatednstoggle.management

import android.content.Context
import org.json.JSONObject

/** Metadata separate from the upstream database and backup format. */
class ManagementStore(context: Context) {
    private val prefs = context.getSharedPreferences("management", Context.MODE_PRIVATE)
    fun contains(host: String) = prefs.contains(host)
    fun get(host: String): ManagementConfig = runCatching {
        val j = JSONObject(prefs.getString(host, "{}")!!)
        ManagementConfig(ManagementMode.valueOf(j.optString("mode", "NONE")), j.optString("url"),
            WebMode.valueOf(j.optString("web", "CUSTOM_TAB")), j.optString("provider", "nextdns"), j.optString("profile"), j.optString("statisticsUrl"), j.optString("queriesUrl"))
    }.getOrDefault(ManagementConfig())
    fun save(host: String, c: ManagementConfig) {
        require(listOf(c.statisticsUrl, c.queriesUrl).all { it.isEmpty() || ManagementPolicy.validUrl(it) })
        require(c.url.isEmpty() || ManagementPolicy.validUrl(c.url))
        require(c.mode != ManagementMode.WEB || ManagementPolicy.validUrl(c.url))
        require(c.mode != ManagementMode.API || ManagementPolicy.validProfile(c.profile))
        prefs.edit().putString(host, JSONObject().put("mode", c.mode.name).put("url", c.url)
            .put("web", c.webMode.name).put("provider", c.provider).put("profile", c.profile).put("statisticsUrl", c.statisticsUrl).put("queriesUrl", c.queriesUrl).toString()).apply()
    }
}
