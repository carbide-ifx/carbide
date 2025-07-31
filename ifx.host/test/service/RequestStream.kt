package ifx.host.service

import ifx.host.contract.IRequestStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

class RequestStream : IRequestStream {
    override suspend fun stream(): Flow<List<Int>> = flowOf(
        listOf(1, 2, 3),
        listOf(4, 5, 6)
    )

    override fun blockingStream(): Flow<List<Int>> = flowOf(
        listOf(1, 2, 3),
        listOf(4, 5, 6)
    )

    override suspend fun streamWithParams(number: Int): Flow<Int> = flow {
        repeat(number) {
            emit(it)
        }
    }

    override fun blockingStreamWithParams(number: Int): Flow<Int> = flow {
        repeat(number) {
            emit(it)
        }
    }

    override suspend fun streamWithException(): Flow<Int> = flow {
        emit(1)
        emit(2)
        MyClient.throwException()
    }

    override fun blockingStreamWithException(): Flow<Int> = flow {
        emit(1)
        emit(2)
        MyClient.throwException()
    }

}
