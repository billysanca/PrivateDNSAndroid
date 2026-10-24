package ru.karasevm.privatednstoggle

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import ru.karasevm.privatednstoggle.data.database.DnsServerRoomDatabase
import ru.karasevm.privatednstoggle.management.*

class ManagementDeviceRegressionTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun metadataDoesNotChangeHostnamesOrderOrEnabledState() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, DnsServerRoomDatabase::class.java).build()
        try {
            val dao = db.dnsServerDao()
            dao.insert("one.example", "One", true)
            dao.insert("two.example", "Two", false)
            dao.insert("three.example", "Three", true)
            val first = dao.getFirstEnabled()!!
            val store = ManagementStore(context)
            store.save(first.server, ManagementConfig(ManagementMode.WEB, "https://example.org/dashboard"))
            assertEquals(first, dao.getFirstEnabled())
            assertEquals("three.example", dao.getNextEnabledByServer(first.server)!!.server)
            assertFalse(dao.getFirstByServer("two.example")!!.enabled)
            assertEquals(ManagementMode.NONE, store.get("two.example").mode)
            assertEquals("https://example.org/dashboard", ManagementStore(context).get(first.server).url)
        } finally { db.close() }
    }
    @Test fun vaultRoundTripRemovalAndProviderIsolation() {
        val vault = ApiKeyVault(context)
        val id = "regression:nextdns"
        try {
            vault.put(id, "synthetic-test-key")
            assertEquals("synthetic-test-key", ApiKeyVault(context).get(id))
            assertNull(vault.get("regression:adguard"))
            vault.remove(id)
            assertNull(vault.get(id))
        } finally { vault.remove(id) }
    }
    @Test fun nativeResponsePresentation() {
        assertEquals("blocked: 12", ReadPresentation.format("nextdns", 0,
            """{"data":[{"status":"blocked","queries":12}]}"""))
        assertEquals("Consultas: 100\nBloqueadas: 20", ReadPresentation.format("adguard", 0,
            """{"stats":[{"value":{"queries":100,"blocked":20}}]}"""))
        assertTrue(ReadPresentation.format("adguard", 2,
            """{"requests":{"used":250,"limit":1000}}""").contains("250 / 1000"))
    }
}
