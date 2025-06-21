package ifx.protocol.contract

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ExtensionPipeline(
    val requestFilters: List<IFilter> = emptyList(),
    val responseFilters: List<IFilter>,
    val nextHandler: IMessageHandler
) : IMessageHandler {

    private fun List<IFilter>.process(message: Message): Message = fold(message) { acc, filter ->
        filter.invoke(acc)
    }

    override suspend fun fireAndForget(operation: String, message: Message) = nextHandler
        .fireAndForget(operation, requestFilters.process(message))

    override suspend fun requestResponse(operation: String, message: Message): Message = nextHandler
        .requestResponse(operation, requestFilters.process(message))
        .let { response -> responseFilters.process(response) }


    override suspend fun requestStream(operation: String, message: Message): Flow<Message> = nextHandler
        .requestStream(operation, requestFilters.process(message))
        .map { response -> responseFilters.process(response) }

}
