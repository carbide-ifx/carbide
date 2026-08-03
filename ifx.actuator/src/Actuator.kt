package ifx.actuator

import ifx.host.IHost
import ifx.host.IHost.Companion.registerService
import ifx.logging.ActuatorLogEntry
import ifx.logging.ActuatorLogs
import kotlinx.coroutines.flow.Flow

class Actuator : IActuator {
    override fun latestLogs(serviceInterface: String): Flow<ActuatorLogEntry> =
        ActuatorLogs.latest(serviceInterface)
}

suspend fun IHost.registerActuator(): IHost = registerService<IActuator> { Actuator() }
