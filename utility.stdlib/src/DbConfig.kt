package ifx.stdlib

data class DbConfig(
    val jdbcUrl: String,
    val dbUsername: String,
    val dbPassword: String,
    val maximumPoolSize: Int = DEFAULT_MAXIMUM_POOL_SIZE,
    val minimumIdleConnections: Int = DEFAULT_MINIMUM_IDLE_CONNECTIONS
) {

    companion object {
        const val DEFAULT_MAXIMUM_POOL_SIZE: Int = 10
        const val DEFAULT_MINIMUM_IDLE_CONNECTIONS: Int = 3
    }
}
