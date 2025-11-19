package njord.utility.common.time

import java.time.LocalDate

object PostgresDate {
    val MIN: LocalDate = LocalDate.of(-4712, 1, 1)
    val MAX: LocalDate = LocalDate.of(294275, 12, 31)

    fun LocalDate.postgresClamp() = when {
        this > MAX -> MAX
        this < MIN -> MIN
        else -> this
    }
}
