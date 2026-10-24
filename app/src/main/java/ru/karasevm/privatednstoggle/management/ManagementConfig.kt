package ru.karasevm.privatednstoggle.management

import java.net.URI

enum class ManagementMode { NONE, WEB, API }
enum class WebMode { CUSTOM_TAB, WEBVIEW }
data class ManagementConfig(val mode: ManagementMode = ManagementMode.NONE,
    val url: String = "", val webMode: WebMode = WebMode.CUSTOM_TAB,
    val provider: String = "nextdns", val profile: String = "")
object ManagementPolicy {
    fun validUrl(value: String): Boolean = runCatching {
        val u = URI(value)
        u.scheme.equals("https", true) && !u.host.isNullOrBlank() && u.rawUserInfo == null &&
            (u.port == -1 || u.port in 1..65535) && value.none { it.isISOControl() }
    }.getOrDefault(false)
    fun validProfile(value: String) = value.matches(Regex("[A-Za-z0-9_-]{1,80}"))
}
