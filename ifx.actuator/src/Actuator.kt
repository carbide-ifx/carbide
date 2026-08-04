package ifx.actuator

import ifx.host.IHost
import ifx.host.IHost.Companion.registerService
import kotlinx.coroutines.flow.Flow

class Actuator : IActuator {
    init {
        ActuatorLogs.install()
    }

    override fun latestLogs(serviceInterface: String): Flow<ActuatorLogEntry> =
        ActuatorLogs.latest(serviceInterface)
}

suspend fun IHost.registerActuator(): IHost {
    ActuatorLogs.install()
    return registerService<IActuator> { Actuator() }
}
