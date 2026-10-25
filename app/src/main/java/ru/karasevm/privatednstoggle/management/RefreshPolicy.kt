package ru.karasevm.privatednstoggle.management

/** Foreground polling only; NextDNS supports SSE; other providers use a bounded polling fallback. */
object RefreshPolicy {
    val labels = listOf("Manual", "Tempo Real", "A cada 15 segundos", "A cada 30 segundos", "A cada 1 minuto")
    fun interval(index: Int): Long = when(index) {
        1 -> 5_000L
        2 -> 15_000L
        3 -> 30_000L
        4 -> 60_000L
        else -> 0L
    }
}
