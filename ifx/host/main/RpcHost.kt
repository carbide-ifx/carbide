package ifx.host


import ifx.service.SonatConvention
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.WebSockets
import kotlinx.rpc.RemoteService
import kotlinx.rpc.krpc.ktor.server.RPC
import kotlinx.rpc.krpc.ktor.server.rpc
import kotlinx.rpc.krpc.serialization.json.json


import kotlin.coroutines.CoroutineContext

class RpcHost : IHost {


    val server = embeddedServer(Netty, 8080) {
    }.apply {
        application.install(RPC)
    }

    override fun start(): RpcHost = apply {
        server.start()
    }

    override fun stop(): RpcHost = apply {
        server.stop()
    }

    inline fun <reified Contract : RemoteService> registerService(noinline factory: (CoroutineContext) -> Contract): RpcHost {

        server.application.routing {
            rpc(SonatConvention.getPath<Contract>()) {
                rpcConfig {
                    serialization { json() }
                }
                registerService<Contract>(factory)
            }
        }
        return this
    }

    // TODO
//    @InlineOnly
//    inline fun <reified Contract : IService, reified Impl: Contract> registerService2(): RpcHost {
//        val contractName = Contract::class.simpleName
//        val constructor = Impl::class.constructors.first()
//        Impl::class.
//        server.application.routing {
//            rpc("/$contractName") {
//                registerService<Contract>{ ctx -> constructor.call(ctx)}
//            }
//        }
//        return this
//    }


//    @InlineOnly
//    inline fun <reified Contract : IService, reified Service> registerServiceeee(noinline serviceFactory: (CoroutineContext) -> Service) where Service : ServiceBase, Service : Contract {
//        a.application.routing {
//            rpc("/image-recognizer") {
//                registerService<Contract>(serviceFactory)
//            }
//        }
//    }


}
