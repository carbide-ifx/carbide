package ifx.rsocket.rsocket

import ifx.service.IService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

// Ensure we test the following (both blocking and suspend)
// 1. Fire and forget
//        - no args
//        - with args
// 2. Request-Response
//        - no args
//        - with args
//        - exception handling
//        - generic return types
// 3. Request-Stream
//        - no args
//        - with args


interface IMyService : IService {
    suspend fun exception(): Boolean
    suspend fun hello()
    fun blockingException(): Boolean
    fun blockingHello(): Unit
    suspend fun add(pair: IntPair): Int
    suspend fun add(pair: FloatPair): Float
    suspend fun stream(): Flow<List<Int>>
    fun blockingAdd(pair: IntPair): Int
    fun blockingStream(): Flow<List<Int>>
    fun blockingList(): List<Int>
    suspend fun list(): List<Int>
}

@Serializable
data class IntPair(val a: Int, val b: Int)

@Serializable
data class FloatPair(val a: Float, val b: Float)


class CustomException(override val message: String) : Exception(message)

class MyService() : IMyService {
    override suspend fun add(pair: IntPair) = withContext(Dispatchers.IO) {
        delay(1000)
        pair.a + pair.b
    }

    override suspend fun add(pair: FloatPair) = pair.a + pair.b

    override suspend fun exception() = withContext(Dispatchers.IO) {
        delay(1000)
        throw CustomException("Error in IMyService")
    }

    override suspend fun hello() = println("Service says: Hello World")
    override suspend fun stream(): Flow<List<Int>> = flowOf(listOf(1, 2), listOf(2, 3))

    override fun blockingAdd(pair: IntPair) = pair.a + pair.b

    override fun blockingException() = throw CustomException("Error in IMyBlockingService")

    override fun blockingHello() = println("Service says: Hello World")

    override fun blockingStream(): Flow<List<Int>> = flowOf(listOf(1, 2), listOf(2, 3))

    override fun blockingList(): List<Int> = listOf(1, 2, 3)

    override suspend fun list(): List<Int> = listOf(1, 2, 3)
}

