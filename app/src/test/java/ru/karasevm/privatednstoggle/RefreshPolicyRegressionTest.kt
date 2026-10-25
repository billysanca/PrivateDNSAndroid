package ru.karasevm.privatednstoggle

import org.junit.Assert.*
import org.junit.Test
import ru.karasevm.privatednstoggle.management.RefreshPolicy

class RefreshPolicyRegressionTest {
    @Test fun refreshChoicesCannotCauseAnUnboundedBusyLoop() {
        assertEquals(0L, RefreshPolicy.interval(0))
        assertEquals(0L, RefreshPolicy.interval(-1))
        assertEquals(0L, RefreshPolicy.interval(Int.MAX_VALUE))
        val enabled = RefreshPolicy.labels.indices.drop(1).map { RefreshPolicy.interval(it) }
        assertTrue(enabled.isNotEmpty())
        assertTrue(enabled.all { it >= 5_000L && it <= 60_000L })
        assertEquals(enabled.sorted(), enabled)
    }
}
