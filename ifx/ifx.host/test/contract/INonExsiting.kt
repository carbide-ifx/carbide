package ifx.host.contract

import ifx.service.IService

interface INonExsiting : IService {
    fun a(): Int
}
