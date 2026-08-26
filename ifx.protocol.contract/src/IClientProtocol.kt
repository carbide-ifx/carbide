package ifx.protocol.contract

interface IClientProtocol {
    fun createClientBinding(address: String): IBinding

    /** Creates a binding to [address] at an explicit network [endpoint]. */
    fun createClientBinding(endpoint: ServiceEndpoint, address: String): IBinding =
        throw UnsupportedOperationException("This client protocol does not support explicit service endpoints")

    /**
     * Releases the transport resources shared by every binding this protocol created. Bindings
     * obtained from a closed protocol are unusable.
     */
    fun close()
}
