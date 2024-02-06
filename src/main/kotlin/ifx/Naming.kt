package ifx

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder

object Naming {
    fun defautlChannel(port: Int): ManagedChannel = ManagedChannelBuilder
        .forAddress("localhost", port)
        .usePlaintext()
        .build()
}
