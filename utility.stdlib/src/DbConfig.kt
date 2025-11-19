package ifx.stdlib

data class DbConfig(
    val jdbcUrl: String,
    val dbUsername: String,
    val dbPassword: Masked,
    val maximumPoolSize: Int,
    val minimumIdleConnections: Int
) {
    constructor(
        jdbcUrl: String,
        dbUsername: String,
        dbPassword: String,
        maximumPoolSize: Int = DEFAULT_MAXIMUM_POOL_SIZE,
        minimumIdleConnections: Int = DEFAULT_MINIMUM_IDLE_CONNECTIONS
    ) : this(
        jdbcUrl = jdbcUrl,
        dbUsername = dbUsername,
        dbPassword = dbPassword.toMasked(),
        maximumPoolSize = maximumPoolSize,
        minimumIdleConnections = minimumIdleConnections
    )

    companion object {
        const val DEFAULT_MAXIMUM_POOL_SIZE: Int = 10
        const val DEFAULT_MINIMUM_IDLE_CONNECTIONS: Int = 3
    }
}
