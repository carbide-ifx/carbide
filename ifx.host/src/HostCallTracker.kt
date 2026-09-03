package ifx.host

import ifx.protocol.contract.IInterceptor
import ifx.protocol.contract.InterceptorCall
import ifx.protocol.contract.InterceptorChain
import ifx.protocol.contract.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

internal class HostCallTracker {
    private val mutex = Mutex()
    private val activeCalls = MutableStateFlow(0)
    private var acceptingCalls = false
    private var draining = false

    suspend fun startAccepting() {
        mutex.withLock {
            acceptingCalls = true
            draining = false
        }
    }

    suspend fun stopAccepting() {
        mutex.withLock {
            acceptingCalls = false
            draining = false
        }
    }

    suspend fun beginDrain() {
        mutex.withLock {
            acceptingCalls = false
            draining = true
        }
    }

    suspend fun finishDrain() {
        mutex.withLock { draining = false }
    }

    suspend fun startCall(allowedWhileDraining: Boolean): Boolean = mutex.withLock {
        if (!acceptingCalls && !(draining && allowedWhileDraining)) return@withLock false
        activeCalls.value += 1
        true
    }

    suspend fun finishCall() {
        mutex.withLock {
            check(activeCalls.value > 0) { "Cannot finish a call that was not started" }
            activeCalls.value -= 1
        }
    }

    suspend fun awaitIdle(timeout: Duration): Boolean {
        if (activeCalls.value == 0) return true
        return withTimeoutOrNull(timeout) {
            activeCalls.first { it == 0 }
            true
        } ?: false
    }
}

internal class HostLifecycleInterceptor(
    private val tracker: HostCallTracker,
    private val allowedWhileDraining: (InterceptorCall) -> Boolean,
) : IInterceptor {
    override fun intercept(call: InterceptorCall, next: InterceptorChain): Flow<Message> = flow {
        check(tracker.startCall(allowedWhileDraining(call))) {
            "Host is not accepting new calls"
        }
        try {
            emitAll(next(call))
        } finally {
            tracker.finishCall()
        }
    }
}
