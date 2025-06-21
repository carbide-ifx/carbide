package ifx.host.contract

import ifx.proxy.factory.ProxyFactory
import ifx.service.IService
import ifx.service.Response
import ifx.service.getOrElse
import kotlinx.serialization.Serializable

interface ISomeManager: IService {
    suspend fun forwardCall(call: CallRequest): Response<CallResponse>
    fun blockingForwardCall(call: CallRequest): Response<CallResponse>
}

interface ISomeEngine: IService {
    suspend fun forwardCall(call: CallRequest): Response<CallResponse>
    fun blockingForwardCall(call: CallRequest): Response<CallResponse>
}

interface ISomeResourceAccess: IService {
    suspend fun receiveCall(call: CallRequest): CallResponse
    fun receiveBlockingCall(call: CallRequest): CallResponse
}


@Serializable
data class CallRequest(val values: List<String>)

@Serializable
data class CallResponse(val values: List<String>)



class SomeManager(val proxyFactory: ProxyFactory) : ISomeManager {
    override suspend fun forwardCall(call: CallRequest): Response<CallResponse> {
        val engine = proxyFactory.create<ISomeEngine>()
        val result = engine.forwardCall(call).getOrElse { null } ?: error("Engine response is null")
        return Response(CallResponse(result.values + "manager response"))
    }

    override fun blockingForwardCall(call: CallRequest): Response<CallResponse> {
        val engine = proxyFactory.create<ISomeEngine>()
        val result = engine.blockingForwardCall(call).getOrElse { null } ?: error("Engine response is null")
        return Response(CallResponse(result.values + "manager response"))
    }
}

class SomeEngine(val proxyFactory: ProxyFactory) : ISomeEngine {
    override suspend fun forwardCall(call: CallRequest): Response<CallResponse> {
        val ra = proxyFactory.create<ISomeResourceAccess>()
        val response = ra.receiveCall(call)
        return Response(CallResponse(response.values + "engine response"))
    }

    override fun blockingForwardCall(call: CallRequest): Response<CallResponse> {
        val ra = proxyFactory.create<ISomeResourceAccess>()
        val response = ra.receiveBlockingCall(call)
        return Response(CallResponse(response.values + "engine response"))
    }
}

class SomeResourceAccess(val proxyFactory: ProxyFactory) : ISomeResourceAccess {
    override suspend fun receiveCall(call: CallRequest) = CallResponse(call.values + "resource response")
    override fun receiveBlockingCall(call: CallRequest) = CallResponse(call.values + "resource response")
}
