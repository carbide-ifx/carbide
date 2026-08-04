package ifx.actuator

import ifx.protocol.contract.serviceDescriptorOf
import ifx.service.IService
import kotlinx.coroutines.flow.Flow

interface IActuator : IService {
    fun latestLogs(serviceInterface: String): Flow<ActuatorLogEntry>
}

inline fun <reified T : IService> IActuator.latestLogs(): Flow<ActuatorLogEntry> =
    latestLogs(serviceDescriptorOf<T>().address)
