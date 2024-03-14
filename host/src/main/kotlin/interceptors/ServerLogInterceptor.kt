package ifx.host.interceptors

import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.ForwardingServerCall.SimpleForwardingServerCall
import io.grpc.ForwardingServerCallListener.SimpleForwardingServerCallListener
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCall.Listener
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor

class ServerLogInterceptor : ServerInterceptor {
    override fun <ReqT, RespT> interceptCall(
        call: ServerCall<ReqT, RespT>?,
        headers: Metadata?,
        next: ServerCallHandler<ReqT, RespT>
    ): Listener<ReqT> {
        val listener: ServerCall<ReqT, RespT> = object : SimpleForwardingServerCall<ReqT, RespT>(call) {
            private val log = KotlinLogging.logger { }
            override fun sendMessage(message: RespT) {
                log.info { "Server: Sending Response to client: $message" }
                super.sendMessage(message)
            }
        }

        return object : SimpleForwardingServerCallListener<ReqT>(next.startCall(listener, headers)) {
            private val log = KotlinLogging.logger { }
            override fun onMessage(message: ReqT) {
                log.info{"Server: Received request from client: $message (Headers: $headers)"}
                super.onMessage(message)
            }
        }
    }
}

