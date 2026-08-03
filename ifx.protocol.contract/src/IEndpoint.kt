package ifx.protocol.contract

data class Endpoint(
    val address: String,
    val binding: IBinding,
    val description: ServiceDescription,
)
