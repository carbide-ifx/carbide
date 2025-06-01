package ifx.service


interface IService {
    suspend fun status() = Status(isReady(), isLive())

    suspend fun init() = Unit

    suspend fun isReady(): Boolean = true

    suspend fun isLive(): Boolean = true
}

data class Status(val ready: Boolean, val live: Boolean)
