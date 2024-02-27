package arve.ctx

import ctx.Context
import io.grpc.*
import io.grpc.ForwardingServerCall.SimpleForwardingServerCall
import io.grpc.ForwardingServerCallListener.SimpleForwardingServerCallListener
import io.grpc.kotlin.CoroutineContextServerInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext


class ServerLogInterceptor : ServerInterceptor {
    override fun <ReqT, RespT> interceptCall(
        call: ServerCall<ReqT, RespT>?,
        headers: Metadata?,
        next: ServerCallHandler<ReqT, RespT>
    ): ServerCall.Listener<ReqT> {
        val listener: ServerCall<ReqT, RespT> = object : SimpleForwardingServerCall<ReqT, RespT>(call) {
            override fun sendMessage(message: RespT) {
                println("Server: Sending message to client: $message")
                super.sendMessage(message)
            }
        }

        return object : SimpleForwardingServerCallListener<ReqT>(next.startCall(listener, headers)) {
            override fun onMessage(message: ReqT) {
                println("Server: Received message from client: $message (Headers: $headers)")
                super.onMessage(message)
            }
        }
    }

}


object ArveInterceptor : CoroutineContextServerInterceptor() {
    override fun coroutineContext(
        call: ServerCall<*, *>,
        headers: Metadata
    ): CoroutineContext {
        val h = headers[Context.CUSTOM_HEADER_KEY]
        return h ?: EmptyCoroutineContext
    }
}
