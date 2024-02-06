package host

import io.grpc.ServerServiceDefinition
import io.grpc.kotlin.AbstractCoroutineServerImpl
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

class GrpcServer(context: CoroutineContext = EmptyCoroutineContext): AbstractCoroutineServerImpl(context){

    override fun bindService(): ServerServiceDefinition {

        TODO("Not yet implemented")
    }
}
