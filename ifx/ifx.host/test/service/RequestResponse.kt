package ifx.host.service

import ifx.host.contract.CustomException
import ifx.host.contract.FloatPair
import ifx.host.contract.IRequestResponse
import ifx.host.contract.IntPair
import ifx.host.contract.NumberPair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.pow

class RequestResponse() : IRequestResponse {

    override suspend fun hello() = "Hello"

    override fun blockingHello() = "Hello"

    override fun blockingList(): List<Int> = listOf(1, 2, 3)

    override suspend fun list(): List<Int> = listOf(1, 2, 3)

    override suspend fun add(pair: IntPair) = withContext(Dispatchers.IO) {
        pair.a + pair.b
    }

    override suspend fun add(pair: FloatPair) = pair.a + pair.b

    override suspend fun exception() = withContext(Dispatchers.IO) {
        throw CustomException("Error in IRequestResponse")
    }


    override fun blockingAdd(pair: IntPair) = pair.a + pair.b
    override suspend fun polymorphicSquare(pair: NumberPair): NumberPair = when (pair) {
        is FloatPair -> FloatPair(pair.a.pow(2), pair.b.pow(2))
        is IntPair -> IntPair(pair.a * pair.a, pair.b * pair.b) // Danger: overflow
    }

    override fun blockingPolymorphicSquare(pair: NumberPair): NumberPair = when (pair) {
        is FloatPair -> FloatPair(pair.a.pow(2), pair.b.pow(2))
        is IntPair -> IntPair(pair.a * pair.a, pair.b * pair.b) // Danger: overflow
    }

    override fun blockingException() = throw CustomException("Error in IMyBlockingService")


}
