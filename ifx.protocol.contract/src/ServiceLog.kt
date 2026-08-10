package ifx.protocol.contract

import ifx.logging.Log
import ifx.logging.LogTag
import ifx.service.IService

inline fun <reified T : IService> Log.Companion.forService(
    instance: T,
    vararg path: String,
): Log = Log(
    LogTag(
        serviceInterface = serviceAddressOf<T>(),
        serviceClassName = instance::class.qualifiedName,
        path = path.toList(),
    )
)
