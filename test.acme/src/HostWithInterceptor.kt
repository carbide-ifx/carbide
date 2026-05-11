import ifx.host.IHost.Companion.registerService
import ifx.host.rsocket.Host
import ifx.protocol.contract.interceptors.Encryption
import ifx.protocol.contract.interceptors.LoggingInterceptor
import ifx.proxy.contract.create
import ifx.proxy.factory.ProxyFactory
import ifx.service.IService

interface IEchoServie : IService {
    fun echo(message: String) = "Echo: $message"
}


fun main() {
    val interceptors = listOf(LoggingInterceptor("test"), Encryption )
    val host = Host(0, "localhost")
        .addInterceptors(interceptors)
        .registerService<IEchoServie> { object : IEchoServie {} }
        .open()

    val pf = ProxyFactory.forHost(host).addInterceptors(interceptors)
    val proxy = pf.create<IEchoServie>()

   println( proxy.echo("hello"))
}

//})
