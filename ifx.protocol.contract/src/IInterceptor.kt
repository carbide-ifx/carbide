package ifx.protocol.contract

fun interface IInterceptor {
    suspend operator fun invoke(operation: String, message: Message): Message
}
