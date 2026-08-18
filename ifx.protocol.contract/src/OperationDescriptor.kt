package ifx.protocol.contract

import ifx.service.IService

/**
 * Owner-typed reference to one generated RPC operation.
 *
 * The type parameters make gateway projections and other tooling reject operations from the wrong
 * service while [description] remains the runtime source of wire names, interaction, and schemas.
 */
data class OperationDescriptor<Service : IService, Request, Response>(
    val serviceAddress: String,
    val description: OperationDescription,
)
