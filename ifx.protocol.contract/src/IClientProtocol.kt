package ifx.protocol.contract

interface IClientProtocol {
    /**
     * Creates a binding to [address]. [endpoint] overrides the destination this protocol was
     * configured with; `null` uses that default.
     */
    fun createClientBinding(address: String, endpoint: ServiceEndpoint? = null): IBinding

    /**
     * Releases the transport resources shared by every binding this protocol created. Bindings
     * obtained from a closed protocol are unusable.
     */
    fun close()
}
