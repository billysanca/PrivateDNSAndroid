package ru.karasevm.privatednstoggle.management

/** Foreground polling only; live means a bounded 5-second refresh, not server push. */
object RefreshPolicy {
    val labels = listOf("Manual", "Contínuo · 5 segundos", "A cada 15 segundos", "A cada 30 segundos", "A cada 1 minuto")
    fun interval(index: Int): Long = when(index) {
        1 -> 5_000L
        2 -> 15_000L
        3 -> 30_000L
        4 -> 60_000L
        else -> 0L
    }
}
