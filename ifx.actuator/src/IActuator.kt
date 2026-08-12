package ifx.actuator

import ifx.protocol.contract.ServiceCatalog
import ifx.protocol.contract.serviceAddressOf
import ifx.service.IService
import ifx.service.IUtility
import kotlinx.coroutines.flow.Flow

interface IActuator : IUtility {
    suspend fun catalog(): ServiceCatalog

    fun logTail(serviceInterface: String): Flow<LogTailEntry>
}

inline fun <reified T : IService> IActuator.logTail(): Flow<LogTailEntry> =
    logTail(serviceAddressOf<T>())
