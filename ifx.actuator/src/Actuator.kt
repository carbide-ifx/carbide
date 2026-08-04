package ifx.actuator

import ifx.host.IHost
import ifx.host.IHost.Companion.registerService
import kotlinx.coroutines.flow.Flow

class Actuator : IActuator {
    init {
        LogTail.install()
    }

    override fun logTail(serviceInterface: String): Flow<LogTailEntry> =
        LogTail.latest(serviceInterface)
}

suspend fun IHost.registerActuator(): IHost {
    LogTail.install()
    return registerService<IActuator> { Actuator() }
}
