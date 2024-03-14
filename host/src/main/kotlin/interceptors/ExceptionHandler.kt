package ifx.host.interceptors

import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.ForwardingServerCallListener.SimpleForwardingServerCallListener
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCall.Listener
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.Status


class ExceptionHandler : ServerInterceptor {
    override fun <ReqT, RespT> interceptCall(
        serverCall: ServerCall<ReqT, RespT>,
        metadata: Metadata?,
        serverCallHandler: ServerCallHandler<ReqT, RespT>
    ): Listener<ReqT> =
        ExceptionHandlingServerCallListener(serverCallHandler.startCall(serverCall, metadata), serverCall, metadata)

    private class ExceptionHandlingServerCallListener<ReqT, RespT>(
        listener: Listener<ReqT>?, private val serverCall: ServerCall<ReqT, RespT>,
        private val metadata: Metadata?
    ) : SimpleForwardingServerCallListener<ReqT>(listener) {
        private val log = KotlinLogging.logger { }
        override fun onHalfClose() {
            try {
                super.onHalfClose()
            } catch (ex: RuntimeException) {
                handleException(ex, serverCall, metadata)
                throw ex
            }
        }

        override fun onReady() {
            try {
                super.onReady()
            } catch (ex: RuntimeException) {
                handleException(ex, serverCall, metadata)
                throw ex
            }
        }

        private fun handleException(
            exception: RuntimeException,
            serverCall: ServerCall<ReqT, RespT>,
            metadata: Metadata?
        ) {
            log.error(exception) { "Server Exception" }
            if (exception is IllegalArgumentException) {
                serverCall.close(Status.INVALID_ARGUMENT.withDescription(exception.message), metadata)
            } else {
                serverCall.close(Status.UNKNOWN, metadata)
            }
        }
    }
}
