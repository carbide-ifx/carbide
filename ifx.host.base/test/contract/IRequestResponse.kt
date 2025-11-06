package ifx.host.contract

import ifx.service.IService
import kotlinx.serialization.Serializable

interface IRequestResponse : IService {

    suspend fun hello(): String
    fun blockingHello(): String

    suspend fun exception(): Boolean
    fun blockingException(): Boolean

    suspend fun add(pair: IntPair): Int
    suspend fun add(pair: FloatPair): Float
    fun blockingAdd(pair: IntPair): Int

    suspend fun polymorphicSquare(pair: NumberPair): NumberPair
    fun blockingPolymorphicSquare(pair: NumberPair): NumberPair

    suspend fun polymorphicDefault(pair: NumberPair): NumberPair = when (pair) {
        is IntPair -> IntPair(pair.a * pair.a, pair.b * pair.b)
        is FloatPair -> FloatPair(pair.a * pair.a, pair.b * pair.b)
    }

    suspend fun list(): List<Int>
    fun blockingList(): List<Int>
}


@Serializable
sealed interface NumberPair

@Serializable
data class IntPair(val a: Int, val b: Int) : NumberPair

@Serializable
data class FloatPair(val a: Float, val b: Float) : NumberPair


class CustomException(override val message: String) : Exception(message)

