package ifx.protocol.contract

interface IClientProtocol {
    fun createClientBinding(address: String): IBinding
}
