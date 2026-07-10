package ifx.protocol.contract

interface IProtocol {
    fun expose(endpoint: Endpoint): IProtocol
    fun createClientBinding(address: String): IBinding
    fun open(): IProtocol
    fun close(): IProtocol
}
