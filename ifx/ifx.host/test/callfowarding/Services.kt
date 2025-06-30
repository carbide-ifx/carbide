package ifx.host.callfowarding

import ifx.proxy.factory.ProxyFactory
import ifx.service.IService
import ifx.service.Response
import ifx.service.getOrElse
import kotlinx.serialization.Serializable

interface ISomeManager: IService {
    suspend fun someUseCase(call: CallRequest): Response<CallResponse>
    fun someBlockingUseCase(call: CallRequest): Response<CallResponse>
}

interface ISomeEngine: IService {
    suspend fun engineComputation(call: CallRequest): Response<CallResponse>
    fun blockingEngineComputation(call: CallRequest): Response<CallResponse>
}

interface ISomeResourceAccess: IService {
    suspend fun storeSomething(call: CallRequest): CallResponse
    fun blockingStoreSomething(call: CallRequest): CallResponse
}


@Serializable
data class CallRequest(val values: List<String>)

@Serializable
data class CallResponse(val values: List<String>)



class SomeManager(val proxyFactory: ProxyFactory) : ISomeManager {
    override suspend fun someUseCase(call: CallRequest): Response<CallResponse> {
        val engine = proxyFactory.create<ISomeEngine>()
        val result = engine.engineComputation(call).getOrElse { null } ?: error("Engine response is null")
        return Response(CallResponse(result.values + "manager response"))
    }

    override fun someBlockingUseCase(call: CallRequest): Response<CallResponse> {
        val engine = proxyFactory.create<ISomeEngine>()
        val result = engine.blockingEngineComputation(call).getOrElse { null } ?: error("Engine response is null")
        return Response(CallResponse(result.values + "manager response"))
    }
}

class SomeEngine(val proxyFactory: ProxyFactory) : ISomeEngine {
    override suspend fun engineComputation(call: CallRequest): Response<CallResponse> {
        val ra = proxyFactory.create<ISomeResourceAccess>()
        val response = ra.storeSomething(call)
        return Response(CallResponse(response.values + "engine response"))
    }

    override fun blockingEngineComputation(call: CallRequest): Response<CallResponse> {
        val ra = proxyFactory.create<ISomeResourceAccess>()
        val response = ra.blockingStoreSomething(call)
        return Response(CallResponse(response.values + "engine response"))
    }
}

class SomeResourceAccess(val proxyFactory: ProxyFactory) : ISomeResourceAccess {
    override suspend fun storeSomething(call: CallRequest) = CallResponse(call.values + "resource response")
    override fun blockingStoreSomething(call: CallRequest) = CallResponse(call.values + "resource response")
}
