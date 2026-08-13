package ifx.protocol.rsocket

import ifx.protocol.contract.IBinding
import ifx.protocol.contract.Message
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.rsocket.kotlin.RSocket
import io.rsocket.kotlin.RSocketLoggingApi
import io.rsocket.kotlin.core.WellKnownMimeType
import io.rsocket.kotlin.keepalive.KeepAlive
import io.rsocket.kotlin.ktor.client.RSocketSupport
import io.rsocket.kotlin.ktor.client.rSocket
import io.rsocket.kotlin.payload.PayloadMimeType
import io.rsocket.kotlin.payload.buildPayload
import io.rsocket.kotlin.payload.data
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private const val INITIAL_RECONNECT_DELAY_MILLIS = 100L
private const val MAX_RECONNECT_DELAY_MILLIS = 5_000L

/**
 * An RSocket binding that replaces failed connections without replaying the failed RPC.
 *
 * Unary calls are bounded by [requestTimeout]. A transport failure or timeout invalidates the
 * current connection; the next call establishes a replacement with capped, jittered backoff.
 */
@OptIn(RSocketLoggingApi::class)
class RSocketClient(
    private val url: String,
    keepAlive: KeepAlive = KeepAlive(intervalMillis = 20_000, maxLifetimeMillis = 90_000),
    private val requestTimeout: Duration =
        (keepAlive.intervalMillis.toLong() + keepAlive.maxLifetimeMillis).milliseconds,
) : IBinding {
    val httpClient = HttpClient {
        this.install(WebSockets) // rsocket requires websockets plugin installed
        this.install(RSocketSupport) {
            // configure rSocket connector (all values have defaults)
            connector {
                loggerFactory = KermitRSocketLoggerFactory
                connectionConfig {
                    this.keepAlive = keepAlive
                    // payload for setup frame
                    setupPayload {
                        buildPayload {
                            data("""{ "data": "setup" }""")
                        }
                    }
                    // mime types
                    payloadMimeType = PayloadMimeType(
                        data = WellKnownMimeType.ApplicationJson,
                        metadata = WellKnownMimeType.MessageRSocketCompositeMetadata
                    )
                }
            }
        }
    }
    private val connectionMutex = Mutex()
    private var activeConnection: RSocket? = null

    @Deprecated("Direct sockets are not stable across reconnects; use this client through IBinding")
    val rsocketClient by lazy { runBlocking { async { connection() } } }

    override suspend fun fireAndForget(operation: String, message: Message) {
        withConnection { connection ->
            connection.fireAndForget(message.toRequestPayload(operation))
        }
    }

    override suspend fun requestResponse(operation: String, message: Message): Message =
        withConnection { connection ->
            connection.requestResponse(message.toRequestPayload(operation)).toMessage()
        }

    override suspend fun requestStream(operation: String, message: Message): Flow<Message> = flow {
        val connection = withTimeout(requestTimeout) { connection() }
        try {
            emitAll(connection.requestStream(message.toRequestPayload(operation)).map { it.toMessage() })
        } catch (cause: Throwable) {
            if (currentCoroutineContext().isActive) invalidate(connection, cause)
            throw cause
        }
    }

    private suspend fun <T> withConnection(block: suspend (RSocket) -> T): T {
        var connection: RSocket? = null
        return try {
            withTimeout(requestTimeout) {
                val current = connection().also { connection = it }
                block(current)
            }
        } catch (cause: Throwable) {
            if (currentCoroutineContext().isActive) {
                connection?.let { invalidate(it, cause) }
            }
            throw cause
        }
    }

    private suspend fun connection(): RSocket = connectionMutex.withLock {
        activeConnection?.takeIf { it.coroutineContext.isActive } ?: connect().also {
            activeConnection = it
        }
    }

    private suspend fun connect(): RSocket {
        var attempt = 0L
        while (true) {
            try {
                return httpClient.rSocket(url)
            } catch (_: Throwable) {
                currentCoroutineContext().ensureActive()
                delay(reconnectDelayMillis(attempt++))
            }
        }
    }

    private suspend fun invalidate(connection: RSocket, cause: Throwable) {
        connectionMutex.withLock {
            if (activeConnection !== connection) return
            activeConnection = null
            connection.cancel("RSocket connection failed", cause)
        }
    }
}

private fun reconnectDelayMillis(attempt: Long): Long {
    val multiplier = 1L shl attempt.coerceAtMost(6L).toInt()
    val upperBound = (INITIAL_RECONNECT_DELAY_MILLIS * multiplier)
        .coerceAtMost(MAX_RECONNECT_DELAY_MILLIS)
    return Random.nextLong(from = 0L, until = upperBound + 1L)
}
