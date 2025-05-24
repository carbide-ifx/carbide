package ifx.host

import ifx.protocol.contract.IProtocol
import ifx.service.IService


interface IHost {
    fun addProtocol(protocol: IProtocol): IHost

    fun <Contract : IService, Impl: Contract> registerService(instance: Impl): IHost
    fun start(): IHost
    fun stop(): IHost

}

