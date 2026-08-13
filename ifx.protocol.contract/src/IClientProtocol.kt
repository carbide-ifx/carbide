package ifx.protocol.contract

interface IClientProtocol {
    fun createClientBinding(address: String): IBinding

    /**
     * Releases the transport resources shared by every binding this protocol created. Bindings
     * obtained from a closed protocol are unusable.
     */
    fun close()
}
