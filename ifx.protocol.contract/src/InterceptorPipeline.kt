package ifx.protocol.contract

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map


class ServerInterceptorPipeline(
    val interceptors: List<IInterceptor> = emptyList(),
    val nextBinding: IBinding,
) : IBinding {
    private val reversedInterceptors = interceptors.reversed()

    override suspend fun fireAndForget(operation: String, message: Message) = nextBinding
        .fireAndForget(operation, reversedInterceptors.fold(message) { acc, interceptor ->
            interceptor.onServerReceive(operation, acc)
        })

    override suspend fun requestResponse(operation: String, message: Message): Message {
        val request = reversedInterceptors.fold(message) { acc, interceptor ->
            interceptor.onServerReceive(operation, acc)
        }
        val response = nextBinding.requestResponse(operation, request)
        return interceptors.fold(response) { acc, interceptor ->
            interceptor.onServerSend(operation, acc)
        }
    }

    override suspend fun requestStream(operation: String, message: Message): Flow<Message> {
        val request = reversedInterceptors.fold(message) { acc, interceptor ->
            interceptor.onServerReceive(operation, acc)
        }
        return nextBinding.requestStream(operation, request)
            .map { response ->
                interceptors.fold(response) { acc, interceptor ->
                    interceptor.onServerSend(operation, acc)
                }
            }
    }
}


class ClientInterceptorPipeline(
    val interceptors: List<IInterceptor> = emptyList(),
    val nextHandler: IBinding,
) : IBinding {
    private val reversedInterceptors = interceptors.reversed()

    override suspend fun fireAndForget(operation: String, message: Message) = nextHandler
        .fireAndForget(operation, interceptors.fold(message) { acc, interceptor ->
            interceptor.onClientSend(operation, acc)
        })

    override suspend fun requestResponse(operation: String, message: Message): Message {
        val request = interceptors.fold(message) { acc, interceptor ->
            interceptor.onClientSend(operation, acc)
        }
        val response = nextHandler.requestResponse(operation, request)
        return reversedInterceptors.fold(response) { acc, interceptor ->
            interceptor.onClientReceive(operation, acc)
        }
    }

    override suspend fun requestStream(operation: String, message: Message): Flow<Message> {
        val request = interceptors.fold(message) { acc, interceptor ->
            interceptor.onClientSend(operation, acc)
        }
        return nextHandler.requestStream(operation, request)
            .map { response ->
                reversedInterceptors.fold(response) { acc, interceptor ->
                    interceptor.onClientReceive(operation, acc)
                }
            }
    }
}
