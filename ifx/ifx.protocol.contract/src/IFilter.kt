package ifx.protocol.contract

fun interface IFilter {
    operator fun invoke(message: Message): Message
}
