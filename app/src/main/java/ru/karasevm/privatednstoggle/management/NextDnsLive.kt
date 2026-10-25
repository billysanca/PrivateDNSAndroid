package ru.karasevm.privatednstoggle.management

import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

/** Official NextDNS SSE, stopped by closing the socket when the collector leaves. */
class NextDnsLive(private val connect: (URL) -> HttpsURLConnection = { it.openConnection() as HttpsURLConnection }) {
    fun snapshots(profile: String, key: String, initial: String): Flow<String> = callbackFlow {
        require(ManagementPolicy.validProfile(profile))
        require(key.isNotBlank() && key.none { it.isISOControl() })
        val connection = AtomicReference<HttpsURLConnection?>()
        val worker = launch(Dispatchers.IO) {
            try {
                val buffer = LiveQueryBuffer(initial)
                val cursor = buffer.cursor?.also { require(it.length <= 1024 && it.none(Char::isISOControl)) }
                val suffix = cursor?.let { "?id=" + URLEncoder.encode(it, "UTF-8") }.orEmpty()
                val conn = connect(URL("https://api.nextdns.io/profiles/$profile/logs/stream$suffix"))
                connection.set(conn)
                ensureActive()
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 15000
                conn.readTimeout = 65000
                conn.setRequestProperty("X-Api-Key", key)
                conn.setRequestProperty("Accept", "text/event-stream")
                if(conn.responseCode !in 200..299) throw ApiFailure(conn.responseCode)
                require(conn.contentType.orEmpty().startsWith("text/event-stream", true))
                trySend(initial)
                val decoder = SseDecoder()
                conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    val line = StringBuilder()
                    while(isActive) {
                        val ch = reader.read()
                        if(ch < 0) throw RuleFailure("Transmissão encerrada. Toque em Tempo Real para reconectar.")
                        if(ch == 10) {
                            decoder.line(line.toString().trimEnd(13.toChar()))?.let { event -> trySend(buffer.append(event)) }
                            line.clear()
                        } else {
                            require(line.length < 65536)
                            line.append(ch.toChar())
                        }
                    }
                }
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { close(e) }
            finally { connection.getAndSet(null)?.disconnect() }
        }
        awaitClose {
            connection.getAndSet(null)?.disconnect()
            worker.cancel()
        }
    }.buffer(Channel.CONFLATED)
}
