package test.gateway

import access.product.contract.IProductAccessDescriptor
import ifx.gateway.contract.gateway
import ifx.gateway.contract.named
import ifx.gateway.endpointSource
import ifx.host.EndpointSource
import ifx.protocol.contract.IClientProtocol
import manager.sales.contract.ISalesManagerDescriptor

val ProductWebApi = gateway("product-web") {
    expose(IProductAccessDescriptor) {
        only(filter, generateRandowProduct)
    }
    expose(ISalesManagerDescriptor)
}

val AliasedProductWebApi = gateway("product-web", version = 2) {
    expose(IProductAccessDescriptor) {
        only(filter.named("find"))
    }
}

fun productWebRemoteEndpointSource(protocol: IClientProtocol): EndpointSource = ProductWebApi.endpointSource {
    remote(IProductAccessDescriptor, protocol)
    remote(ISalesManagerDescriptor, protocol)
}
