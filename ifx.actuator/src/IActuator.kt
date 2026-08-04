package ifx.actuator

import ifx.protocol.contract.serviceDescriptorOf
import ifx.service.IService
import ifx.service.IUtility
import kotlinx.coroutines.flow.Flow

interface IActuator : IUtility {
    fun logTail(serviceInterface: String): Flow<LogTailEntry>
}

inline fun <reified T : IService> IActuator.logTail(): Flow<LogTailEntry> =
    logTail(serviceDescriptorOf<T>().address)
