package ifx.host


interface IHost {

//    fun <Contract : IService> registerService(factory: (CoroutineContext) -> Contract)
    fun start(): IHost
    fun stop(): IHost

}
