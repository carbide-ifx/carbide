//package ifx.host
//
//
//import ifx.context.Context
//import ifx.service.IService
//import ifx.service.SonatConvention
//import io.ktor.server.application.install
//import io.ktor.server.engine.embeddedServer
//import io.ktor.server.netty.Netty
//import io.ktor.server.request.ApplicationRequest
//import io.ktor.server.request.header
//import io.ktor.server.request.path
//import io.ktor.server.routing.routing
//import kotlinx.rpc.RemoteService
//import kotlinx.rpc.annotations.Rpc
//import kotlinx.rpc.krpc.ktor.server.Krpc
//import kotlinx.rpc.krpc.ktor.server.rpc
//import kotlinx.rpc.krpc.serialization.json.json
//import kotlinx.serialization.json.Json
//import kotlin.coroutines.CoroutineContext
//
//val ApplicationRequest.context
//    get() = this.header(Context.HEADER_KEY)?.let { Json.decodeFromString<Context>(it) } ?: Context()
//
//class RpcHost : IHost {
//
//
//    val server = embeddedServer(Netty, 8080) {}.apply {
//        application.install(Krpc)
//    }
//
//    override fun start(): RpcHost = apply {
//        server.start()
//    }
//
//    override fun stop(): RpcHost = apply {
//        server.stop()
//    }
//
//    override fun <Contract : IService, Impl : Contract> registerService(instance: Impl): IHost {
//        TODO("Not yet implemented")
//    }
//
//    inline fun < @Rpc reified Contract : RemoteService> registerService(
//        noinline factory: (CoroutineContext) -> Contract
//    ): RpcHost {
//        server.application.routing {
//            rpc(SonatConvention.getPath<Contract>()) {
//                rpcConfig { serialization { json() } }
//                registerService<Contract> { ctx ->
//                    factory(ctx + call.request.context)
//                }
//            }
//        }
//        return this
//    }
//
//
////     TODO
////    @InlineOnly
////    inline fun <reified Contract : IService, reified Impl: Contract> registerService2(): RpcHost {
////        val contractName = Contract::class.simpleName
////        val constructor = Impl::class.constructors.first()
////        Impl::class.
////        server.application.routing {
////            rpc("/$contractName") {
////                registerService<Contract>{ ctx -> constructor.call(ctx)}
////            }
////        }
////        return this
////    }
//
//
////    @InlineOnly
////    inline fun <reified Contract : IService, reified Service> registerServiceeee(noinline serviceFactory: (CoroutineContext) -> Service) where Service : ServiceBase, Service : Contract {
////        a.application.routing {
////            rpc("/image-recognizer") {
////                registerService<Contract>(serviceFactory)
////            }
////        }
////    }
//
//
//}
