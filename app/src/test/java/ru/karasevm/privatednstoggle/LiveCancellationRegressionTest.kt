package ru.karasevm.privatednstoggle

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import ru.karasevm.privatednstoggle.management.NextDnsLive
import java.io.InputStream
import java.net.URL
import java.security.cert.Certificate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection

class LiveCancellationRegressionTest {
    private class FakeConnection : HttpsURLConnection(URL("https://api.nextdns.io")) {
        val closed = AtomicBoolean()
        private val latch = CountDownLatch(1)
        override fun disconnect() { closed.set(true); latch.countDown() }
        override fun connect() {}
        override fun usingProxy() = false
        override fun getCipherSuite() = "fixture"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
        override fun getResponseCode() = 200
        override fun getContentType() = "text/event-stream"
        override fun getInputStream() = object : InputStream() {
            override fun read(): Int {
                check(latch.await(5, TimeUnit.SECONDS)) { "Connection was not closed on cancellation" }
                return -1
            }
        }
    }
    @Test fun leavingLiveCollectionClosesTheSocketAndKeepsKeyInHeader() = runBlocking {
        val connection = FakeConnection()
        val initial = """{"data":[]}"""
        val received = withTimeout(5000) {
            NextDnsLive { target ->
                assertEquals("api.nextdns.io", target.host)
                assertEquals("/profiles/abcdef/logs/stream", target.path)
                assertFalse(target.toString().contains("fake-key"))
                connection
            }.snapshots("abcdef", "fake-key", initial).first()
        }
        assertEquals(initial, received)
        assertTrue(connection.closed.get())
        assertFalse(connection.instanceFollowRedirects)
        assertEquals("fake-key", connection.getRequestProperty("X-Api-Key"))
    }
    @Test fun invalidProfileNeverOpensAConnection() = runBlocking {
        var opened = false
        val failure = runCatching {
            NextDnsLive { opened = true; FakeConnection() }.snapshots("../other", "fake-key", """{"data":[]}""").first()
        }
        assertTrue(failure.isFailure)
        assertFalse(opened)
    }
}
