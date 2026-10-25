package ru.karasevm.privatednstoggle.management

/** Routing only: never writes Android DNS settings or management metadata. */
object ManagementRouting {
    fun activeIndex(hosts: List<String>, mode: String?, active: String?): Int {
        if(mode != "hostname" || active.isNullOrBlank()) return -1
        return hosts.indexOfFirst { ProviderSetup.normalizedHost(it) == ProviderSetup.normalizedHost(active) }
    }
    fun effective(host: String, saved: ManagementConfig): ManagementConfig {
        val detected = ProviderSetup.detect(host) ?: return saved
        val inferred = ProviderSetup.nextDnsProfile(host).orEmpty()
        return saved.copy(mode = ManagementMode.API, provider = detected,
            profile = if(saved.provider == detected) saved.profile.ifBlank { inferred } else inferred)
    }
}
