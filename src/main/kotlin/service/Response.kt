package ifx.service

sealed interface Response<T> {
    data class Success<T>(val value: T) : Response<T>
    data class Failure<T>(val errors: List<ErrorCode>) : Response<T>

    companion object {
        fun <T> of(obj: T): Success<T> = Success(obj)

        fun <T> emptyList(): Success<List<T>> = Success(listOf())

        fun <T> failure(errors: List<ErrorCode>): Failure<T> = Failure(errors)

        fun <T> failure(vararg errors: ErrorCode): Failure<T> = Failure(listOf(*errors))
    }

    interface ErrorCode {
        fun code(): String
        fun message(): String
    }

}
