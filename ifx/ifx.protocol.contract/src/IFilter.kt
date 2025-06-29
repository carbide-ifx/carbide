package ifx.protocol.contract

fun interface IFilter {
    suspend operator fun invoke(operation: String, message: Message): Message
}
