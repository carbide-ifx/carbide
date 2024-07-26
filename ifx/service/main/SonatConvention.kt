package ifx.service

import kotlinx.rpc.RemoteService


object SonatConvention {
    inline fun <reified T : RemoteService> getPath() = "/${T::class.simpleName}"
}
