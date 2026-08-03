package ifx.protocol.rsocket

import io.ktor.util.logging.Logger

internal expect fun kermitKtorLogger(tag: String): Logger
