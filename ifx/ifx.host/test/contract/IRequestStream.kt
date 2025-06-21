package ifx.host.contract

import ifx.service.IService
import kotlinx.coroutines.flow.Flow

interface IRequestStream: IService {
    suspend fun stream(): Flow<List<Int>>
    fun blockingStream(): Flow<List<Int>>

    suspend fun streamWithParams(number: Int): Flow<Int>
    fun blockingStreamWithParams(number: Int): Flow<Int>

    suspend fun streamWithException(): Flow<Int>
    fun blockingStreamWithException(): Flow<Int>

}

