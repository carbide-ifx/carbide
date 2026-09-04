package ifx.actuator

import ifx.host.HostHealth
import ifx.host.IHost
import ifx.protocol.contract.ServiceCatalog
import ifx.protocol.contract.ServiceDescriptor
import kotlinx.coroutines.flow.Flow

class Actuator(
    private val catalogProvider: () -> ServiceCatalog,
    private val healthProvider: suspend () -> HostHealth,
) : IActuator {
    init {
        LogTail.install()
    }

    override suspend fun catalog(): ServiceCatalog = catalogProvider()

    override suspend fun health(): HostHealth = healthProvider()

    override fun logTail(serviceInterface: String): Flow<LogTailEntry> =
        LogTail.latest(serviceInterface)
}

fun IHost.registerActuator(
    descriptor: ServiceDescriptor<IActuator> = missingActuatorDescriptor(),
): IHost {
    LogTail.install()
    return registerService(descriptor, Actuator(::serviceCatalog, ::health))
}

private fun missingActuatorDescriptor(): Nothing = error(
    "registerActuator() requires the ifx.rpc.compiler plugin or an explicit IActuator descriptor",
)
