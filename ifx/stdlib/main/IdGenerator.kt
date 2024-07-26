package ifx.stdlib


object IdGenerator {
    private const val DEFAULT_SIZE = 24
    private val CharacterPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')

    /**
     * Generates a random ID with the given prefix, for use as primary key in a database.
     *
     * Typically the prefix should be initials of the resource access + entity name,
     * e.g. `la_line` for LineAccess.Line
     */
    fun generate(prefix: String, size: Int = DEFAULT_SIZE): String =
        "${prefix}_" + CharArray(size) { CharacterPool.random() }.concatToString()
}
