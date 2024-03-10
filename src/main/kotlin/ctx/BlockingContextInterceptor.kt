package ifx.ctx

import io.grpc.Contexts
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCall.Listener
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.Context as GrpcContext
object  BlockingContextInterceptor: ServerInterceptor {
    override fun <ReqT : Any, RespT : Any> interceptCall(
        call: ServerCall<ReqT, RespT>,
        headers: Metadata,
        next: ServerCallHandler<ReqT, RespT>
    ): Listener<ReqT> {
        val h = headers[Context.METADATA_KEY] ?: Context()
        val context: GrpcContext = GrpcContext.current().withValue(Context.BLOCKING_CONTEXT_KEY, h)
        return Contexts.interceptCall(context, call, headers, next)
    }
}
