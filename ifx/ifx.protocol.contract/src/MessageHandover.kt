data class Message(
    val header: String,
    val body :String
)


interface IClientHandler {
    fun <T> encodeRequest(request: T): Message
    fun <T> decodeResponse(response: Message): T
}

interface IServerHandler {
    fun <T> decodeRequest(request: Message): T
    fun <T> encodeResponse(response: T): Message
}


