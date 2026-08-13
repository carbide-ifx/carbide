package ifx.protocol.rsocket

import ifx.protocol.contract.IBinding
import ifx.protocol.contract.Message
import ifx.protocol.contract.ProtocolException
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.rsocket.kotlin.RSocket
import io.rsocket.kotlin.RSocketError
import io.rsocket.kotlin.RSocketLoggingApi
import io.rsocket.kotlin.core.WellKnownMimeType
import io.rsocket.kotlin.keepalive.KeepAlive
import io.rsocket.kotlin.ktor.client.RSocketSupport
import io.rsocket.kotlin.ktor.client.rSocket
import io.rsocket.kotlin.payload.PayloadMimeType
import io.rsocket.kotlin.payload.buildPayload
import io.rsocket.kotlin.payload.data
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private const val INITIAL_RECONNECT_DELAY_MILLIS = 100L
private const val MAX_RECONNECT_DELAY_MILLIS = 5_000L

/**
 * Keep-alive for calls between subsystems. It is proposed by the connecting side in the setup frame
 * and adopted by the server per connection, so it bounds how quickly *this* client notices a dead
 * peer without affecting anyone else on the same listener.
 *
 * Three missed keep-alives declare a peer dead: long enough to ride out a lost frame or a
 * stop-the-world pause, short enough that a lost connection is noticed in seconds rather than the
 * minute and a half the protocol default allows.
 */
val SUBSYSTEM_KEEP_ALIVE: KeepAlive = KeepAlive(interval = 5.seconds, maxLifetime = 15.seconds)

/**
 * The RSocket protocol defaults, for callers reaching a subsystem from outside the backend — a
 * browser tab, a mobile app, anything that is routinely suspended or on a slow network, where the
 * subsystem window would drop healthy connections during ordinary interruptions.
 */
val EXTERNAL_KEEP_ALIVE: KeepAlive = KeepAlive(interval = 20.seconds, maxLifetime = 90.seconds)

/**
 * How long a call may spend acquiring a connection. Derived from the keep-alive so that waiting for
 * a new connection is never longer than waiting for an established one to be declared dead.
 */
fun KeepAlive.connectTimeout(): Duration = (intervalMillis.toLong() + maxLifetimeMillis).milliseconds

/**
 * Builds the shared ktor client that carries every RSocket binding of one [RSocketClientProtocol].
 *
 * The RSocket connector is configured per ktor client, so [keepAlive] applies to every connection
 * opened through the returned client.
 */
@OptIn(RSocketLoggingApi::class)
fun rsocketHttpClient(keepAlive: KeepAlive = SUBSYSTEM_KEEP_ALIVE): HttpClient = HttpClient {
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

/**
 * An RSocket binding that replaces failed connections without replaying the failed RPC.
 *
 * The [httpClient] is owned by the [RSocketClientProtocol] that created this binding and is shared
 * with its sibling bindings, so closing it is the protocol's responsibility, not this client's.
 *
 * Calls themselves are unbounded: liveness is the keep-alive's job, and an application-level
 * deadline belongs to the caller. Only acquiring a connection is bounded, by [connectTimeout].
 */
class RSocketClient(
    private val httpClient: HttpClient,
    private val url: String,
    private val connectTimeout: Duration = SUBSYSTEM_KEEP_ALIVE.connectTimeout(),
) : IBinding {
    private val connectionMutex = Mutex()
    private var activeConnection: RSocket? = null

    override suspend fun fireAndForget(operation: String, message: Message) {
        withConnection(operation) { connection ->
            connection.fireAndForget(message.toRequestPayload(operation))
        }
    }

    override suspend fun requestResponse(operation: String, message: Message): Message =
        withConnection(operation) { connection ->
            connection.requestResponse(message.toRequestPayload(operation)).toMessage()
        }

    /**
     * A collector that stops early — `first()`, `take(n)`, or one that throws — aborts the upstream
     * flow with an exception that must not be mistaken for a transport failure, so failures raised
     * by the collector are tracked and excluded.
     */
    override suspend fun requestStream(operation: String, message: Message): Flow<Message> = flow {
        val connection = connection()
        var fromCollector: Throwable? = null
        try {
            connection.requestStream(message.toRequestPayload(operation))
                .map { it.toMessage() }
                .collect { response ->
                    try {
                        emit(response)
                    } catch (cause: Throwable) {
                        fromCollector = cause
                        throw cause
                    }
                }
        } catch (cause: Throwable) {
            // A collector that stopped early owns its own exception; `first()` relies on receiving it.
            if (cause === fromCollector) throw cause
            throw failure(connection, operation, cause)
        }
    }

    private suspend fun <T> withConnection(operation: String, block: suspend (RSocket) -> T): T {
        val connection = connection()
        return try {
            block(connection)
        } catch (cause: Throwable) {
            throw failure(connection, operation, cause)
        }
    }

    private suspend fun connection(): RSocket = connectionMutex.withLock {
        activeConnection?.takeIf { it.coroutineContext.isActive } ?: connect().also {
            activeConnection = it
        }
    }

    /**
     * Retries with capped, jittered backoff until [connectTimeout] is spent, then reports the last
     * connection failure rather than a bare timeout.
     */
    private suspend fun connect(): RSocket {
        val deadline = TimeSource.Monotonic.markNow() + connectTimeout
        var attempt = 0L
        var lastFailure: Throwable? = null
        while (true) {
            try {
                return httpClient.rSocket(url)
            } catch (cause: Throwable) {
                currentCoroutineContext().ensureActive()
                lastFailure = cause
            }
            if (deadline.hasPassedNow()) break
            delay(reconnectDelayMillis(attempt++))
        }
        throw ProtocolException(lastFailure) {
            "Failed to connect to $url within $connectTimeout: ${lastFailure?.message}"
        }
    }

    /**
     * Reports a failed call as a [ProtocolException] so that a dropped transport reaches callers as a
     * protocol error rather than a cancellation, which coroutines would treat as the caller giving up
     * and interceptors deliberately ignore. Cancellation of the caller itself is passed through
     * untouched.
     *
     * The connection is replaced only when the failure implicates the transport: a per-stream error
     * frame leaves it usable, and tearing it down would fail every other caller sharing it.
     */
    private suspend fun failure(connection: RSocket, operation: String, cause: Throwable): Throwable {
        if (!currentCoroutineContext().isActive) return cause // the caller was cancelled, not the call
        if (cause.indicatesBrokenTransport()) invalidate(connection, cause)

        return ProtocolException(cause) { "RSocket call $operation to $url failed: ${cause.message}" }
    }

    private suspend fun invalidate(connection: RSocket, cause: Throwable) {
        connectionMutex.withLock {
            if (activeConnection !== connection) return
            activeConnection = null
            connection.cancel("RSocket connection failed", cause)
        }
    }
}

/**
 * Per-stream RSocket errors arrive as error frames on a healthy connection. Anything else — a
 * cancelled transport channel, a connection-level error, an unrecognised failure — is treated as a
 * broken connection, so an unknown failure costs a reconnect rather than a permanently dead client.
 */
private fun Throwable.indicatesBrokenTransport(): Boolean = when (this) {
    is RSocketError.Setup,
    is RSocketError.RejectedResume,
    is RSocketError.ConnectionError,
    is RSocketError.ConnectionClose -> true

    is RSocketError -> false
    else -> true
}

/** Equal jitter: half the backoff is always waited, so a refusing peer is never hammered. */
private fun reconnectDelayMillis(attempt: Long): Long {
    val multiplier = 1L shl attempt.coerceAtMost(6L).toInt()
    val upperBound = (INITIAL_RECONNECT_DELAY_MILLIS * multiplier)
        .coerceAtMost(MAX_RECONNECT_DELAY_MILLIS)
    val fixed = upperBound / 2L
    return fixed + Random.nextLong(from = 0L, until = upperBound - fixed + 1L)
}
