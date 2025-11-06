package ifx.host.callfowarding

import ifx.context.Context
import ifx.proxy.contract.create
import ifx.proxy.factory.ProxyFactoryBase
import ifx.service.IService
import ifx.service.Response
import ifx.service.getOrElse
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

interface ISomeManager : IService {
    suspend fun someUseCase(call: CallRequest): Response<CallResponse>
    fun someBlockingUseCase(call: CallRequest): Response<CallResponse>
}

interface ISomeEngine : IService {
    suspend fun engineComputation(call: CallRequest): Response<CallResponse>
    fun blockingEngineComputation(call: CallRequest): Response<CallResponse>
}

interface ISomeResourceAccess : IService {
    suspend fun storeSomething(call: CallRequest): CallResponse
    fun blockingStoreSomething(call: CallRequest): CallResponse
}

@Serializable
data class CallRequest(val values: List<String>)

@Serializable
data class CallResponse(val values: List<String>)

class SomeManager(val proxyFactory: ProxyFactoryBase) : ISomeManager {
    val engine = proxyFactory.create<ISomeEngine>()

    override suspend fun someUseCase(call: CallRequest): Response<CallResponse> {
        val result = engine.engineComputation(call).getOrElse { null } ?: error("Engine response is null")
        return Response(CallResponse(result.values + "manager response"))
    }

    override fun someBlockingUseCase(call: CallRequest): Response<CallResponse> {
        val result = engine.blockingEngineComputation(call).getOrElse { null } ?: error("Engine response is null")
        return Response(CallResponse(result.values + "manager response"))
    }
}

class SomeEngine(val proxyFactory: ProxyFactoryBase) : ISomeEngine {
    val ra: ISomeResourceAccess = proxyFactory.create<ISomeResourceAccess>()

    override suspend fun engineComputation(call: CallRequest): Response<CallResponse> {
        val response = ra.storeSomething(call)
        return Response(CallResponse(response.values + "engine response"))
    }

    override fun blockingEngineComputation(call: CallRequest): Response<CallResponse> {
        val response = ra.blockingStoreSomething(call)
        return Response(CallResponse(response.values + "engine response"))
    }
}

class SomeResourceAccess() : ISomeResourceAccess {
    val mutex = Mutex()
    val storedAsync = mutableListOf<String>()
    var storedBlocking: Int = 0
    override suspend fun storeSomething(call: CallRequest): CallResponse {
        val trace = Context.get().traceId
        mutex.withLock {
            storedAsync.add(trace)
        }
        val responseBody = call.values + trace + "resource response"
        return CallResponse(responseBody)
    }

    override fun blockingStoreSomething(call: CallRequest): CallResponse {
        storedBlocking++
        val trace = Context.getBlocking().traceId
        val responseBody = call.values + trace + "resource response"
        return CallResponse(responseBody)
    }
}
