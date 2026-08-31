package ifx.protocol.contract

/**
 * One addressable service surface installed into a protocol listener.
 *
 * The address is the routing key on every protocol and is owned by [description], so an endpoint
 * cannot be routed under one address while describing itself as another.
 */
data class Endpoint(
    val binding: IBinding,
    val description: ServiceDescription,
) {
    val address: String get() = description.address
}
