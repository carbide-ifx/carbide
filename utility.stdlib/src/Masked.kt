package ifx.stdlib

data class Masked(val value: String) {
    @Deprecated("Should rarely be used explicitly. Are you looking for .value?", ReplaceWith("value"))
    override fun toString(): String = "****"
}

fun String.toMasked(): Masked = Masked(this)

