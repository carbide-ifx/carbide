package ifx.protocol.contract

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class InterceptorPipeline(
    val requestInterceptors: List<IInterceptor> = emptyList(),
    val responseInterceptors: List<IInterceptor>,
    val nextHandler: IBinding
) : IBinding {

    private suspend fun List<IInterceptor>.process(operation: String, message: Message): Message = fold(message) { acc, filter ->
        filter.invoke(operation, acc)
    }

    override suspend fun fireAndForget(operation: String, message: Message) = nextHandler
        .fireAndForget(operation, requestInterceptors.process(operation,message))

    override suspend fun requestResponse(operation: String, message: Message): Message = nextHandler
        .requestResponse(operation, requestInterceptors.process(operation,message))
        .let { response -> responseInterceptors.process(operation,response) }


    override suspend fun requestStream(operation: String, message: Message): Flow<Message> = nextHandler
        .requestStream(operation, requestInterceptors.process(operation, message))
        .map { response -> responseInterceptors.process(operation, response) }

}
